package app.bertsatlas.anki.collection

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import app.bertsatlas.anki.model.Deck
import app.bertsatlas.anki.model.DeckConfig
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal object Schema11 {
    private val tables = listOf(
        "create table col (id integer primary key, crt integer not null, mod integer not null, scm integer not null, ver integer not null, dty integer not null, usn integer not null, ls integer not null, conf text not null, models text not null, decks text not null, dconf text not null, tags text not null)",
        "create table notes (id integer primary key, guid text not null, mid integer not null, mod integer not null, usn integer not null, tags text not null, flds text not null, sfld integer not null, csum integer not null, flags integer not null, data text not null)",
        "create table cards (id integer primary key, nid integer not null, did integer not null, ord integer not null, mod integer not null, usn integer not null, type integer not null, queue integer not null, due integer not null, ivl integer not null, factor integer not null, reps integer not null, lapses integer not null, left integer not null, odue integer not null, odid integer not null, flags integer not null, data text not null)",
        "create table revlog (id integer primary key, cid integer not null, usn integer not null, ease integer not null, ivl integer not null, lastIvl integer not null, factor integer not null, time integer not null, type integer not null)",
        "create table graves (usn integer not null, oid integer not null, type integer not null)",
        "create index ix_notes_usn on notes (usn)",
        "create index ix_cards_usn on cards (usn)",
        "create index ix_revlog_usn on revlog (usn)",
        "create index ix_cards_nid on cards (nid)",
        "create index ix_cards_sched on cards (did, queue, due)",
        "create index ix_revlog_cid on revlog (cid)",
        "create index ix_notes_csum on notes (csum)",
    )

    fun create(connection: SQLiteConnection, nowMillis: Long) {
        tables.forEach { connection.execSQL(it) }
        val defaultDeck = Schema11Json.deckTo(Deck(id = DEFAULT_ID, name = "Default", configId = DEFAULT_ID, modifiedSecs = nowMillis / 1000))
        val defaultConfig = Schema11Json.deckConfigTo(DeckConfig(id = DEFAULT_ID, name = "Default", modifiedSecs = nowMillis / 1000))
        val configuration = JsonObject(
            mapOf(
                "schedVer" to JsonPrimitive(2),
                "fsrs" to JsonPrimitive(true),
                "sched2021" to JsonPrimitive(true),
                "nextPos" to JsonPrimitive(1),
                "curDeck" to JsonPrimitive(DEFAULT_ID),
                "activeDecks" to JsonArray(listOf(JsonPrimitive(DEFAULT_ID))),
                "collapseTime" to JsonPrimitive(1200),
                "estTimes" to JsonPrimitive(true),
                "dueCounts" to JsonPrimitive(true),
                "addToCur" to JsonPrimitive(true),
                "sortType" to JsonPrimitive("noteFld"),
                "sortBackwards" to JsonPrimitive(false),
                "timeLim" to JsonPrimitive(0),
                "newSpread" to JsonPrimitive(0),
                "dayLearnFirst" to JsonPrimitive(false),
            ),
        )
        connection.execute(
            "insert into col (id, crt, mod, scm, ver, dty, usn, ls, conf, models, decks, dconf, tags) values (1, ?, ?, ?, 11, 0, 0, 0, ?, '{}', ?, ?, '{}')",
            (nowMillis / 1000 / SECONDS_PER_DAY) * SECONDS_PER_DAY, nowMillis, nowMillis, configuration.toString(),
            JsonObject(mapOf(DEFAULT_ID.toString() to defaultDeck)).toString(),
            JsonObject(mapOf(DEFAULT_ID.toString() to defaultConfig)).toString(),
        )
    }

    const val DEFAULT_ID = 1L
    private const val SECONDS_PER_DAY = 86_400L
}
