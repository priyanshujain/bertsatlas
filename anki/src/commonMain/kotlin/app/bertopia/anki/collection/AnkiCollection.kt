package app.bertopia.anki.collection

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.bertopia.anki.io.Files
import app.bertopia.anki.io.Sha1
import app.bertopia.anki.model.Card
import app.bertopia.anki.model.CardScheduling
import app.bertopia.anki.model.Deck
import app.bertopia.anki.model.DeckConfig
import app.bertopia.anki.model.Note
import app.bertopia.anki.model.NoteListing
import app.bertopia.anki.model.Notetype
import app.bertopia.anki.model.ReviewRecord
import app.bertopia.anki.model.RevlogEntry
import app.bertopia.anki.model.StudiedToday
import app.bertopia.anki.template.HtmlText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock

class ReadOnlyCollectionException : IllegalStateException("Anki schema 18 collections are read only; import them into a schema 11 collection")

class AnkiCollection private constructor(
    private val connection: SQLiteConnection,
    val path: String,
    val schemaVersion: Int,
    private val nowMillis: () -> Long,
) : AutoCloseable {
    private lateinit var configuration: JsonObject
    private lateinit var notetypesById: Map<Long, Notetype>
    private lateinit var decksById: Map<Long, Deck>
    private lateinit var deckConfigsById: Map<Long, DeckConfig>
    private var lastAssignedId = 0L
    private var loadedDataVersion = 0L

    val isWritable: Boolean get() = schemaVersion == SCHEMA_11

    init {
        loadState()
    }

    fun reloadIfChangedExternally(): Boolean {
        if (connection.longValue("pragma data_version") == loadedDataVersion) return false
        loadState()
        return true
    }

    private fun loadState() {
        loadedDataVersion = connection.longValue("pragma data_version")
        if (schemaVersion == SCHEMA_11) {
            val row = connection.rows("select conf, models, decks, dconf from col") { statement ->
                (0..3).map { column -> Json.parseToJsonElement(statement.getText(column)).jsonObject }
            }.single()
            configuration = row[0]
            notetypesById = row[1].values.map { Schema11Json.notetypeFrom(it.jsonObject) }.associateBy { it.id }
            decksById = row[2].values.map { Schema11Json.deckFrom(it.jsonObject) }.associateBy { it.id }
            deckConfigsById = row[3].values.map { Schema11Json.deckConfigFrom(it.jsonObject) }.associateBy { it.id }
        } else {
            val reader = Schema18Reader(connection)
            configuration = reader.configuration()
            notetypesById = reader.notetypes().associateBy { it.id }
            decksById = reader.decks().associateBy { it.id }
            deckConfigsById = reader.deckConfigs().associateBy { it.id }
        }
    }

    val creationSecs: Long get() = connection.longValue("select crt from col")

    fun configuration(): JsonObject = configuration

    fun notetypes(): List<Notetype> = notetypesById.values.sortedBy { it.id }

    fun notetype(id: Long): Notetype? = notetypesById[id]

    fun notetypeByName(name: String): Notetype? = notetypesById.values.firstOrNull { it.name == name }

    fun decks(): List<Deck> = decksById.values.sortedBy { it.name }

    fun deck(id: Long): Deck? = decksById[id]

    fun deckByName(name: String): Deck? = decksById.values.firstOrNull { it.name == name }

    fun subdecksOf(deck: Deck): List<Deck> = decksById.values.filter { it.name.startsWith(deck.name + "::") }.sortedBy { it.name }

    fun deckConfigs(): List<DeckConfig> = deckConfigsById.values.sortedBy { it.id }

    fun deckConfig(id: Long): DeckConfig? = deckConfigsById[id]

    fun notes(): List<Note> = connection.rows("$NOTE_COLUMNS order by id") { it.toNote() }

    fun note(id: Long): Note? = connection.rows("$NOTE_COLUMNS where id = ?", id) { it.toNote() }.firstOrNull()

    fun noteByGuid(guid: String): Note? = connection.rows("$NOTE_COLUMNS where guid = ?", guid) { it.toNote() }.firstOrNull()

    fun noteCount(): Long = connection.longValue("select count(*) from notes")

    fun noteIdsByGuid(): Map<String, Long> = connection.rows("select guid, id from notes") { it.getText(0) to it.getLong(1) }.toMap()

    fun noteGuidsInDecks(deckIds: Collection<Long>): Set<String> =
        if (deckIds.isEmpty()) emptySet()
        else connection.rows(
            "select distinct n.guid from notes n join cards c on c.nid = n.id where c.did in (${deckIds.joinToString(",")}) or c.odid in (${deckIds.joinToString(",")})",
        ) { it.getText(0) }.toSet()

    fun firstCardInDecks(deckIds: Collection<Long>): Card? =
        if (deckIds.isEmpty()) null
        else connection.rows("$CARD_COLUMNS where did in (${deckIds.joinToString(",")}) order by nid, ord limit 1") { it.toCard() }.firstOrNull()

    fun cards(): List<Card> = connection.rows("$CARD_COLUMNS order by id") { it.toCard() }

    fun card(id: Long): Card? = connection.rows("$CARD_COLUMNS where id = ?", id) { it.toCard() }.firstOrNull()

    fun cardsOfNote(noteId: Long): List<Card> = connection.rows("$CARD_COLUMNS where nid = ? order by ord", noteId) { it.toCard() }

    fun cardsInDecks(deckIds: Collection<Long>): List<Card> =
        if (deckIds.isEmpty()) emptyList()
        else connection.rows("$CARD_COLUMNS where did in (${deckIds.joinToString(",")}) or odid in (${deckIds.joinToString(",")}) order by id") { it.toCard() }

    fun cardCountInDecks(deckIds: Collection<Long>): Long =
        if (deckIds.isEmpty()) 0
        else connection.longValue("select count(*) from cards where did in (${deckIds.joinToString(",")}) or odid in (${deckIds.joinToString(",")})")

    fun revlog(): List<RevlogEntry> = connection.rows("$REVLOG_COLUMNS order by id") { it.toRevlogEntry() }

    fun revlogOfCard(cardId: Long): List<RevlogEntry> = connection.rows("$REVLOG_COLUMNS where cid = ? order by id", cardId) { it.toRevlogEntry() }

    fun noteListings(deckIds: Collection<Long>): List<NoteListing> =
        if (deckIds.isEmpty()) emptyList()
        else connection.rows(
            "select n.id, n.guid, n.mid, n.mod, n.tags, n.sfld, n.flds, min(c.did), count(c.id) from notes n join cards c on c.nid = n.id " +
                "where c.did in (${deckIds.joinToString(",")}) or c.odid in (${deckIds.joinToString(",")}) group by n.id order by n.id",
        ) {
            NoteListing(
                noteId = it.getLong(0),
                guid = it.getText(1),
                notetypeId = it.getLong(2),
                modifiedSecs = it.getLong(3),
                tags = it.getText(4).split(' ').filter { tag -> tag.isNotEmpty() },
                sortField = it.getText(5),
                fields = it.getText(6).split(FIELD_SEPARATOR),
                deckId = it.getLong(7),
                cardCount = it.getLong(8).toInt(),
            )
        }

    fun saveNotetype(notetype: Notetype): Notetype {
        requireWritable()
        val saved = notetype.copy(id = notetype.id.takeIf { it != 0L } ?: nextId(), modifiedSecs = nowSecs())
        notetypesById = notetypesById + (saved.id to saved)
        writeColumn("models", notetypesById.values.associate { it.id.toString() to Schema11Json.notetypeTo(it) })
        return saved
    }

    fun saveDeck(deck: Deck): Deck {
        requireWritable()
        val saved = deck.copy(id = deck.id.takeIf { it != 0L } ?: nextId(), modifiedSecs = nowSecs())
        decksById = decksById + (saved.id to saved)
        writeColumn("decks", decksById.values.associate { it.id.toString() to Schema11Json.deckTo(it) })
        return saved
    }

    fun saveDeckConfig(config: DeckConfig): DeckConfig {
        requireWritable()
        val saved = config.copy(id = config.id.takeIf { it != 0L } ?: nextId(), modifiedSecs = nowSecs())
        deckConfigsById = deckConfigsById + (saved.id to saved)
        writeColumn("dconf", deckConfigsById.values.associate { it.id.toString() to Schema11Json.deckConfigTo(it) })
        return saved
    }

    fun saveNote(note: Note): Note {
        requireWritable()
        val notetype = notetypesById[note.notetypeId] ?: error("Unknown notetype ${note.notetypeId}")
        val fields = note.fields.padded(notetype.fields.size)
        val saved = note.copy(
            id = note.id.takeIf { it != 0L } ?: nextId("notes"),
            fields = fields,
            modifiedSecs = note.modifiedSecs.takeIf { it != 0L } ?: nowSecs(),
        )
        connection.execute(
            "insert or replace into notes (id, guid, mid, mod, usn, tags, flds, sfld, csum, flags, data) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            saved.id, saved.guid, saved.notetypeId, saved.modifiedSecs, saved.usn,
            tagsColumn(saved.tags), fields.joinToString(FIELD_SEPARATOR),
            HtmlText.strip(fields.getOrElse(notetype.sortFieldIndex) { "" }), fieldChecksum(fields.firstOrNull().orEmpty()),
            saved.flags, saved.data,
        )
        return saved
    }

    fun generateMissingCards(noteId: Long, deckId: Long): List<Card> {
        requireWritable()
        val note = note(noteId) ?: error("Unknown note $noteId")
        val notetype = notetypesById.getValue(note.notetypeId)
        val existingOrdinals = cardsOfNote(noteId).map { it.ordinal }.toSet()
        return CardGeneration.ordinalsWithContent(notetype, note.fields)
            .filter { it !in existingOrdinals }
            .map { ordinal ->
                saveCard(Card(id = 0, noteId = noteId, deckId = deckId, ordinal = ordinal, scheduling = CardScheduling(due = takeNextNewPosition())))
            }
    }

    fun addNote(note: Note, deckId: Long): Note {
        val saved = saveNote(note.copy(id = 0))
        generateMissingCards(saved.id, deckId)
        return saved
    }

    fun deleteNote(noteId: Long) {
        requireWritable()
        val cardIds = cardsOfNote(noteId).map { it.id }
        cardIds.forEach { cardId -> connection.execute("insert into graves (usn, oid, type) values (-1, ?, 0)", cardId) }
        connection.execute("delete from cards where nid = ?", noteId)
        connection.execute("delete from notes where id = ?", noteId)
        connection.execute("insert into graves (usn, oid, type) values (-1, ?, 1)", noteId)
    }

    fun deleteCard(cardId: Long) {
        requireWritable()
        connection.execute("delete from cards where id = ?", cardId)
    }

    fun deleteDeck(deckId: Long) {
        requireWritable()
        if (deckId !in decksById) return
        decksById = decksById - deckId
        writeColumn("decks", decksById.values.associate { it.id.toString() to Schema11Json.deckTo(it) })
    }

    fun saveCard(card: Card): Card {
        requireWritable()
        val saved = card.copy(id = card.id.takeIf { it != 0L } ?: nextId("cards"), modifiedSecs = card.modifiedSecs.takeIf { it != 0L } ?: nowSecs())
        val scheduling = saved.scheduling
        connection.execute(
            "insert or replace into cards (id, nid, did, ord, mod, usn, type, queue, due, ivl, factor, reps, lapses, left, odue, odid, flags, data) " +
                "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            saved.id, saved.noteId, saved.deckId, saved.ordinal, saved.modifiedSecs, saved.usn,
            scheduling.type, scheduling.queue, scheduling.due, scheduling.interval, scheduling.easeFactor,
            scheduling.reps, scheduling.lapses, scheduling.left, scheduling.originalDue, scheduling.originalDeckId,
            saved.flags, scheduling.data,
        )
        return saved
    }

    fun addRevlogEntry(entry: RevlogEntry): RevlogEntry {
        requireWritable()
        val saved = entry.copy(id = entry.id.takeIf { it != 0L } ?: nextId("revlog"))
        connection.execute(
            "insert or replace into revlog (id, cid, usn, ease, ivl, lastIvl, factor, time, type) values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            saved.id, saved.cardId, saved.usn, saved.ease, saved.interval, saved.lastInterval, saved.easeFactor, saved.timeMs, saved.kind,
        )
        return saved
    }

    fun recordReview(review: ReviewRecord) {
        requireWritable()
        transaction {
            saveCard(review.card.copy(modifiedSecs = nowSecs(), usn = -1))
            addRevlogEntry(review.revlog.copy(id = unusedRevlogId(review.revlog.id), usn = -1))
            addStudiedToday(review.card.deckId, review.today, if (review.countsAsNewCard) 1 else 0, if (review.countsAsReview) 1 else 0, review.revlog.timeMs)
            if (review.leeched) tagNoteAsLeech(review.card.noteId)
        }
    }

    fun studiedToday(deckId: Long, today: Int): StudiedToday {
        val preserved = decksById[deckId]?.preserved ?: return StudiedToday(0, 0)
        return StudiedToday(newCards = preserved.countForDay("newToday", today), reviews = preserved.countForDay("revToday", today))
    }

    fun deleteRevlog() {
        requireWritable()
        connection.execute("delete from revlog")
    }

    fun <T> transaction(block: () -> T): T {
        connection.execSQL("begin immediate")
        return try {
            reloadIfChangedExternally()
            block().also { connection.execSQL("commit") }
        } catch (failure: Throwable) {
            connection.execSQL("rollback")
            throw failure
        }
    }

    override fun close() = connection.close()

    private fun unusedRevlogId(preferred: Long): Long {
        var id = preferred
        while (connection.longValue("select count(*) from revlog where id = ?", id) > 0) id++
        return id
    }

    private fun addStudiedToday(deckId: Long, today: Int, newCards: Int, reviews: Int, millis: Int) {
        val deck = decksById[deckId] ?: return
        val parents = decksById.values.filter { deck.name.startsWith(it.name + "::") }
        (listOf(deck) + parents).forEach { studied ->
            val preserved = studied.preserved
            val updated = preserved.with(
                "newToday" to dayCount(today, preserved.countForDay("newToday", today) + newCards),
                "revToday" to dayCount(today, preserved.countForDay("revToday", today) + reviews),
                "lrnToday" to dayCount(today, preserved.countForDay("lrnToday", today)),
                "timeToday" to dayCount(today, preserved.countForDay("timeToday", today) + millis),
            )
            saveDeck(studied.copy(preserved = updated))
        }
    }

    private fun JsonObject.countForDay(key: String, today: Int): Int {
        val pair = this[key] as? JsonArray ?: return 0
        val day = pair.getOrNull(0)?.jsonPrimitive?.intOrNull
        return if (day == today) pair.getOrNull(1)?.jsonPrimitive?.intOrNull ?: 0 else 0
    }

    private fun dayCount(day: Int, count: Int) = JsonArray(listOf(JsonPrimitive(day), JsonPrimitive(count)))

    private fun tagNoteAsLeech(noteId: Long) {
        val note = note(noteId) ?: return
        if (note.tags.any { it.equals(LEECH_TAG, ignoreCase = true) }) return
        saveNote(note.copy(tags = note.tags + LEECH_TAG, modifiedSecs = nowSecs(), usn = -1))
    }

    private fun requireWritable() {
        if (!isWritable) throw ReadOnlyCollectionException()
    }

    private fun writeColumn(column: String, values: Map<String, JsonElement>) {
        connection.execute("update col set $column = ?, mod = ?", JsonObject(values).toString(), nowMillis())
    }

    fun useWriteAheadLog() {
        connection.execSQL("pragma journal_mode = wal")
    }

    fun useRollbackJournal() {
        connection.execSQL("pragma journal_mode = delete")
    }

    fun dataVersion(): Long = connection.longValue("pragma data_version")

    fun copyConsistentSnapshotTo(path: String) {
        connection.execute("vacuum into ?", path)
    }

    fun enableFsrsScheduling() {
        requireWritable()
        configuration = configuration.with("fsrs" to JsonPrimitive(true))
        connection.execute("update col set conf = ?", configuration.toString())
    }

    private fun takeNextNewPosition(): Long {
        val position = (configuration["nextPos"] as? JsonPrimitive)?.longOrNull ?: 1
        configuration = configuration.with("nextPos" to JsonPrimitive(position + 1))
        connection.execute("update col set conf = ?", configuration.toString())
        return position
    }

    private fun nextId(table: String? = null): Long {
        val largestStored = table?.let { connection.longValue("select max(id) from $it") } ?: 0L
        val largestJson = (notetypesById.keys + decksById.keys + deckConfigsById.keys).maxOrNull() ?: 0L
        lastAssignedId = maxOf(nowMillis(), lastAssignedId + 1, largestStored + 1, if (table == null) largestJson + 1 else 0)
        return lastAssignedId
    }

    private fun nowSecs(): Long = nowMillis() / 1000

    private fun SQLiteStatement.toNote() = Note(
        id = getLong(0),
        guid = getText(1),
        notetypeId = getLong(2),
        modifiedSecs = getLong(3),
        usn = getLong(4).toInt(),
        tags = getText(5).split(' ').filter { it.isNotBlank() },
        fields = getText(6).split(FIELD_SEPARATOR),
        flags = getLong(7).toInt(),
        data = getText(8),
    )

    private fun SQLiteStatement.toCard() = Card(
        id = getLong(0),
        noteId = getLong(1),
        deckId = getLong(2),
        ordinal = getLong(3).toInt(),
        modifiedSecs = getLong(4),
        usn = getLong(5).toInt(),
        scheduling = CardScheduling(
            type = getLong(6).toInt(),
            queue = getLong(7).toInt(),
            due = getLong(8),
            interval = getLong(9).toInt(),
            easeFactor = getLong(10).toInt(),
            reps = getLong(11).toInt(),
            lapses = getLong(12).toInt(),
            left = getLong(13).toInt(),
            originalDue = getLong(14),
            originalDeckId = getLong(15),
            data = getText(17),
        ),
        flags = getLong(16).toInt(),
    )

    private fun SQLiteStatement.toRevlogEntry() = RevlogEntry(
        id = getLong(0),
        cardId = getLong(1),
        usn = getLong(2).toInt(),
        ease = getLong(3).toInt(),
        interval = getLong(4).toInt(),
        lastInterval = getLong(5).toInt(),
        easeFactor = getLong(6).toInt(),
        timeMs = getLong(7).toInt(),
        kind = getLong(8).toInt(),
    )

    companion object {
        const val SCHEMA_11 = 11
        const val FIELD_SEPARATOR = "\u001f"
        const val LEECH_TAG = "leech"
        const val BUSY_TIMEOUT_MILLIS = 10_000
        private const val NOTE_COLUMNS = "select id, guid, mid, mod, usn, tags, flds, flags, data from notes"
        private const val CARD_COLUMNS =
            "select id, nid, did, ord, mod, usn, type, queue, due, ivl, factor, reps, lapses, left, odue, odid, flags, data from cards"
        private const val REVLOG_COLUMNS = "select id, cid, usn, ease, ivl, lastIvl, factor, time, type from revlog"

        fun open(path: String, nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() }): AnkiCollection {
            require(Files.exists(path)) { "No collection at $path" }
            val connection = BundledSQLiteDriver().open(path)
            connection.execSQL("pragma busy_timeout = $BUSY_TIMEOUT_MILLIS")
            val version = connection.longValue("select ver from col").toInt()
            return AnkiCollection(connection, path, version, nowMillis)
        }

        fun create(path: String, nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() }): AnkiCollection {
            require(!Files.exists(path)) { "A file already exists at $path" }
            val connection = BundledSQLiteDriver().open(path)
            connection.execSQL("pragma busy_timeout = $BUSY_TIMEOUT_MILLIS")
            Schema11.create(connection, nowMillis())
            return AnkiCollection(connection, path, SCHEMA_11, nowMillis)
        }

        fun tagsColumn(tags: List<String>): String = if (tags.isEmpty()) "" else tags.joinToString(" ", prefix = " ", postfix = " ")

        fun fieldChecksum(firstField: String): Long = Sha1.hex(HtmlText.strip(firstField).encodeToByteArray()).take(8).toLong(16)
    }
}
