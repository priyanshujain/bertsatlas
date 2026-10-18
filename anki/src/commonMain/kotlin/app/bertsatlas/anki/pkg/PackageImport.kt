package app.bertsatlas.anki.pkg

import app.bertsatlas.anki.PackManifestText
import app.bertsatlas.anki.collection.AnkiCollection
import app.bertsatlas.anki.collection.renderCard
import app.bertsatlas.anki.io.Files
import app.bertsatlas.anki.io.writeTo
import app.bertsatlas.anki.model.Card
import app.bertsatlas.anki.model.DECK_NAME_SEPARATOR
import app.bertsatlas.anki.model.Deck
import app.bertsatlas.anki.model.Notetype
import app.bertsatlas.anki.template.HtmlText
import app.bertsatlas.anki.template.RenderedCard

data class TopLevelDeckSummary(
    val deckId: Long,
    val name: String,
    val cardCount: Long,
    val sectionNames: List<String>,
    val preview: RenderedCard? = null,
)

data class DeckImportResult(
    val targetDeckId: Long,
    val addedNotes: Int,
    val updatedNotes: Int,
    val addedCards: Int,
    val mediaFiles: Int,
)

class OpenedPackage internal constructor(
    private val layout: PackageLayout,
    val collection: AnkiCollection,
    private val collectionPath: String,
) : AutoCloseable {
    val format: PackageFormat get() = layout.format

    val media: List<PackageMediaEntry> get() = layout.media

    fun readMedia(entry: PackageMediaEntry): ByteArray = Files.readSource(layout.openMedia(entry))

    internal fun writeMedia(entry: PackageMediaEntry, path: String, beforeEachChunk: (Long) -> Unit) {
        layout.openMedia(entry).writeTo(path, beforeEachChunk)
    }

    fun topLevelDecks(): List<TopLevelDeckSummary> =
        collection.decks().filter { it.isTopLevel && !it.isFiltered }.map { deck ->
            val tree = listOf(deck) + collection.subdecksOf(deck).filter { !it.isFiltered }
            val treeIds = tree.map { it.id }
            TopLevelDeckSummary(
                deckId = deck.id,
                name = deck.name,
                cardCount = collection.cardCountInDecks(treeIds),
                sectionNames = tree.drop(1).map { it.name.removePrefix(deck.name + DECK_NAME_SEPARATOR) },
                preview = collection.firstCardInDecks(treeIds)?.let { collection.renderCard(it) },
            )
        }.filter { it.cardCount > 0 }

    fun noteGuidsOf(topLevelDeckId: Long): Set<String> {
        val deck = collection.deck(topLevelDeckId) ?: return emptySet()
        return collection.noteGuidsInDecks((listOf(deck) + collection.subdecksOf(deck)).map { it.id })
    }

    override fun close() {
        collection.close()
        layout.close()
        Files.delete(collectionPath)
    }
}

object PackageImport {
    private val mediaReference = Regex(
        """src\s*=\s*(?:"([^"]+)"|'([^']+)'|([^"'\s>]+))|\[sound:([^\]]+)]""",
        RegexOption.IGNORE_CASE,
    )

    fun open(packagePath: String, workingDirectory: String, monitor: ImportMonitor = ImportMonitor.None): OpenedPackage {
        Files.createDirectories(workingDirectory)
        val layout = AnkiPackage.openFile(packagePath)
        val collectionPath = Files.join(workingDirectory, "import-${packagePath.hashCode().toUInt()}-${layout.media.size}.anki2")
        try {
            Files.delete(collectionPath)
            layout.openCollection().writeTo(collectionPath) { bytesWritten ->
                monitor.checkCancelled()
                monitor.report(ImportStage.ReadingPackage, bytesWritten, null)
            }
            return OpenedPackage(layout, AnkiCollection.open(collectionPath), collectionPath)
        } catch (failure: Throwable) {
            layout.close()
            Files.delete(collectionPath)
            throw failure
        }
    }

    fun importDeck(
        source: OpenedPackage,
        topLevelDeckId: Long,
        target: AnkiCollection,
        targetMediaDirectory: String,
        monitor: ImportMonitor = ImportMonitor.None,
        intoTopLevelDeckId: Long? = null,
        updateExistingContent: Boolean = true,
        keepExistingScheduling: Boolean = false,
        packMedia: Set<String> = emptySet(),
    ): DeckImportResult {
        val createdMedia = mutableListOf<String>()
        return try {
            target.transaction {
                val copied = DeckCopy(source.collection, target, monitor, updateExistingContent, keepExistingScheduling).copy(topLevelDeckId, intoTopLevelDeckId)
                val mediaCount = copyReferencedMedia(source, copied.referencedMedia + packMedia, targetMediaDirectory, monitor, createdMedia)
                DeckImportResult(copied.targetDeckId, copied.addedNotes, copied.updatedNotes, copied.addedCards, mediaCount)
            }
        } catch (failure: Throwable) {
            createdMedia.forEach { Files.delete(it) }
            throw failure
        }
    }

    fun referencedMedia(fieldHtml: String): List<String> =
        mediaReference.findAll(fieldHtml).map { match ->
            HtmlText.decodeEntities(match.groupValues.drop(1).first { it.isNotEmpty() })
        }.toList()

    fun isSafeMediaName(name: String): Boolean =
        name.isNotBlank() && '/' !in name && '\\' !in name && name != "." && name != ".." && '\u0000' !in name

    private fun copyReferencedMedia(
        source: OpenedPackage,
        referenced: Set<String>,
        targetMediaDirectory: String,
        monitor: ImportMonitor,
        createdMedia: MutableList<String>,
    ): Int {
        val wanted = source.media.filter { (it.name in referenced || it.name.startsWith("_")) && isSafeMediaName(it.name) }
        if (wanted.isNotEmpty()) Files.createDirectories(targetMediaDirectory)
        wanted.forEachIndexed { index, entry ->
            monitor.checkCancelled()
            monitor.report(ImportStage.CopyingMedia, index.toLong(), wanted.size.toLong())
            val path = Files.join(targetMediaDirectory, entry.name)
            if (!Files.exists(path)) createdMedia += path
            source.writeMedia(entry, path) { monitor.checkCancelled() }
        }
        monitor.report(ImportStage.CopyingMedia, wanted.size.toLong(), wanted.size.toLong())
        return wanted.size
    }
}

private class DeckCopy(
    private val source: AnkiCollection,
    private val target: AnkiCollection,
    private val monitor: ImportMonitor,
    private val updateExistingContent: Boolean,
    private val keepExistingScheduling: Boolean,
) {
    class Copied(val targetDeckId: Long, val referencedMedia: Set<String>, val addedNotes: Int, val updatedNotes: Int, val addedCards: Int)

    private val notetypeIds = mutableMapOf<Long, Long>()
    private val deckIds = mutableMapOf<Long, Long>()
    private val targetNoteIdsByGuid = target.noteIdsByGuid().toMutableMap()

    fun copy(topLevelDeckId: Long, intoTopLevelDeckId: Long?): Copied {
        val topLevel = source.deck(topLevelDeckId) ?: error("No deck $topLevelDeckId in package")
        val targetTopLevel = intoTopLevelDeckId?.let { target.deck(it) ?: error("No deck $it in pack") }
        val tree = (listOf(topLevel) + source.subdecksOf(topLevel)).filter { !it.isFiltered }
        tree.forEach { deck ->
            val targetName = if (targetTopLevel == null) deck.name else targetTopLevel.name + deck.name.removePrefix(topLevel.name)
            deckIds[deck.id] = copyDeck(deck, targetName)
        }
        val cardsByNote = source.cardsInDecks(tree.map { it.id }).map { it.withoutFilteredDeck() }.filter { it.deckId in deckIds }.groupBy { it.noteId }
        val referencedMedia = mutableSetOf<String>()
        var addedNotes = 0
        var updatedNotes = 0
        var addedCards = 0
        var processed = 0L
        cardsByNote.forEach { (noteId, noteCards) ->
            monitor.checkCancelled()
            if (processed % PROGRESS_INTERVAL == 0L) monitor.report(ImportStage.CopyingCards, processed, cardsByNote.size.toLong())
            processed++
            val note = source.note(noteId) ?: return@forEach
            val notetype = source.notetype(note.notetypeId) ?: return@forEach
            val notetypeId = notetypeIds.getOrPut(note.notetypeId) { copyNotetype(notetype) }
            val existing = targetNoteIdsByGuid[note.guid]?.let { target.note(it) }
            val saved = if (existing == null) {
                addedNotes++
                target.saveNote(note.copy(id = if (target.note(note.id) == null) note.id else 0, notetypeId = notetypeId))
                    .also { targetNoteIdsByGuid[it.guid] = it.id }
            } else {
                updatedNotes++
                if (updateExistingContent) {
                    val sameShape = existing.notetypeId == notetypeId
                    target.saveNote(
                        existing.copy(
                            fields = if (sameShape) note.fields else existing.fields,
                            tags = note.tags,
                            modifiedSecs = maxOf(existing.modifiedSecs, note.modifiedSecs),
                        ),
                    )
                } else {
                    existing
                }
            }
            saved.fields.forEach { field -> referencedMedia += PackageImport.referencedMedia(field) }
            addedCards += noteCards.count { card -> copyCard(card, saved.id) }
        }
        monitor.report(ImportStage.CopyingCards, processed, cardsByNote.size.toLong())
        notetypeIds.values.mapNotNull { target.notetype(it) }.forEach { notetype ->
            listOf(notetype.css).plus(notetype.templates.flatMap { listOf(it.questionFormat, it.answerFormat) })
                .forEach { referencedMedia += PackageImport.referencedMedia(it) }
        }
        return Copied(deckIds.getValue(topLevel.id), referencedMedia, addedNotes, updatedNotes, addedCards)
    }

    private fun copyDeck(deck: Deck, targetName: String): Long {
        val configId = source.deckConfig(deck.configId)?.let { config ->
            if (target.deckConfig(config.id) == null) target.saveDeckConfig(config).id else config.id
        } ?: 1L
        val existing = target.deckByName(targetName)
        val saved = if (existing != null) {
            if (updateExistingContent && targetName == deck.name) target.saveDeck(existing.copy(description = descriptionKeepingManifest(existing, deck))) else existing
        } else {
            target.saveDeck(deck.copy(id = if (target.deck(deck.id) == null) deck.id else 0, name = targetName, configId = configId))
        }
        return saved.id
    }

    private fun descriptionKeepingManifest(existing: Deck, incoming: Deck): String {
        val existingManifest = PackManifestText.read(existing.description) ?: return incoming.description
        return PackManifestText.write(PackManifestText.readableText(incoming.description), existingManifest)
    }

    private fun copyNotetype(notetype: Notetype): Long {
        val existing = target.notetype(notetype.id)
        return when {
            existing == null -> target.saveNotetype(notetype).id
            existing.fieldNames == notetype.fieldNames -> if (updateExistingContent) target.saveNotetype(notetype).id else existing.id
            else -> target.notetypes().firstOrNull { it.name == notetype.name && it.fieldNames == notetype.fieldNames }?.id
                ?: target.saveNotetype(notetype.copy(id = 0)).id
        }
    }

    private fun copyCard(card: Card, targetNoteId: Long): Boolean {
        val targetDeckId = deckIds.getValue(card.deckId)
        val existing = target.cardsOfNote(targetNoteId).firstOrNull { it.ordinal == card.ordinal }
        val revlog = source.revlogOfCard(card.id)
        if (existing != null) {
            if (!keepExistingScheduling && card.modifiedSecs > existing.modifiedSecs) {
                target.saveCard(existing.copy(scheduling = card.scheduling, modifiedSecs = card.modifiedSecs, flags = card.flags))
            }
            val known = target.revlogOfCard(existing.id).map { it.id }.toSet()
            revlog.filter { it.id !in known }.forEach { target.addRevlogEntry(it.copy(cardId = existing.id)) }
            return false
        }
        val saved = target.saveCard(card.copy(id = if (target.card(card.id) == null) card.id else 0, noteId = targetNoteId, deckId = targetDeckId))
        revlog.forEach { entry -> target.addRevlogEntry(entry.copy(cardId = saved.id)) }
        return true
    }

    private fun Card.withoutFilteredDeck(): Card =
        if (scheduling.originalDeckId == 0L) this
        else copy(
            deckId = scheduling.originalDeckId,
            scheduling = scheduling.copy(due = if (scheduling.originalDue != 0L) scheduling.originalDue else scheduling.due, originalDue = 0, originalDeckId = 0),
        )

    private companion object {
        const val PROGRESS_INTERVAL = 250L
    }
}
