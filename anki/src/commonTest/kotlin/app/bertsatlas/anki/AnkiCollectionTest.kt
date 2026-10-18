package app.bertsatlas.anki

import app.bertsatlas.anki.collection.AnkiCollection
import app.bertsatlas.anki.model.Card
import app.bertsatlas.anki.model.CardScheduling
import app.bertsatlas.anki.model.Deck
import app.bertsatlas.anki.model.Note
import app.bertsatlas.anki.model.RevlogEntry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnkiCollectionTest {
    @Test
    fun createdCollectionSchedulesWithFsrsAndKeepsParameterVersions() {
        val directory = freshDirectory("fsrs-config")
        val fsrs6 = List(21) { 0.1 * (it + 1) }
        val fsrs5 = List(19) { 0.2 * (it + 1) }
        val path: String
        val fsrs5ConfigId: Long
        createCollection(directory).use { collection ->
            path = collection.path
            assertEquals(JsonPrimitive(true), collection.configuration()["fsrs"])
            collection.saveDeckConfig(collection.deckConfig(1)!!.copy(fsrsParameters = fsrs6))
            fsrs5ConfigId = collection.saveDeckConfig(collection.deckConfig(1)!!.copy(id = 0, name = "Older", fsrsParameters = fsrs5)).id
        }
        AnkiCollection.open(path).use { reopened ->
            assertEquals(fsrs6, reopened.deckConfig(1)!!.fsrsParameters)
            assertEquals(fsrs5, reopened.deckConfig(fsrs5ConfigId)!!.fsrsParameters)
            assertEquals(JsonPrimitive(true), reopened.configuration()["fsrs"])
        }
    }

    @Test
    fun createdCollectionPersistsNotetypesDecksNotesCardsAndRevlog() {
        val directory = freshDirectory("create")
        val path: String
        val noteId: Long
        createCollection(directory).use { collection ->
            path = collection.path
            val notetype = collection.saveNotetype(basicNotetype())
            val deck = collection.saveDeck(Deck(id = 0, name = "Flags", description = "All the flags"))
            collection.saveDeck(Deck(id = 0, name = "Flags::Europe"))
            val note = collection.addNote(Note(0, "guid-france", notetype.id, listOf("France", "Paris", ""), listOf("bert::similar::tricolour")), deck.id)
            noteId = note.id
            val card = collection.cardsOfNote(note.id).single()
            collection.saveCard(card.copy(scheduling = card.scheduling.copy(type = 2, queue = 2, due = 10, interval = 3, data = """{"s":2.5,"d":5.0}""")))
            collection.addRevlogEntry(RevlogEntry(0, card.id, ease = 3, interval = 3, lastInterval = 0, easeFactor = 0, timeMs = 4000, kind = 1))
        }
        AnkiCollection.open(path).use { reopened ->
            assertEquals(11, reopened.schemaVersion)
            assertEquals(listOf("Default", "Flags", "Flags::Europe"), reopened.decks().map { it.name })
            assertEquals("All the flags", reopened.deckByName("Flags")?.description)
            assertEquals(listOf("Flags::Europe"), reopened.subdecksOf(reopened.deckByName("Flags")!!).map { it.name })
            val note = reopened.noteByGuid("guid-france")!!
            assertEquals(noteId, note.id)
            assertEquals(listOf("France", "Paris", ""), note.fields)
            assertEquals(listOf("bert::similar::tricolour"), note.tags)
            val card = reopened.cardsOfNote(note.id).single()
            assertEquals("""{"s":2.5,"d":5.0}""", card.scheduling.data)
            assertEquals(3, card.scheduling.interval)
            assertEquals(1, reopened.revlogOfCard(card.id).size)
            assertEquals(listOf("Question", "Answer", "Extra"), reopened.notetypeByName("Bert Basic")?.fieldNames)
        }
    }

    @Test
    fun cardsAreGeneratedOnlyForTemplatesWithContent() {
        createCollection(freshDirectory("generate")).use { collection ->
            val notetype = collection.saveNotetype(basicNotetype())
            val withoutExtra = collection.addNote(Note(0, "a", notetype.id, listOf("Q", "A", ""), emptyList()), 1)
            val withExtra = collection.addNote(Note(0, "b", notetype.id, listOf("Q", "A", "x"), emptyList()), 1)
            assertEquals(listOf(0), collection.cardsOfNote(withoutExtra.id).map { it.ordinal })
            assertEquals(listOf(0, 1), collection.cardsOfNote(withExtra.id).map { it.ordinal })
            collection.saveNote(withoutExtra.copy(fields = listOf("Q", "A", "now")))
            assertEquals(1, collection.generateMissingCards(withoutExtra.id, 1).size)
            assertEquals(listOf(1L, 2L, 3L, 4L), collection.cards().map { it.scheduling.due }.sorted())
        }
    }

    @Test
    fun clozeNotesGetOneCardPerDeletionNumber() {
        createCollection(freshDirectory("cloze")).use { collection ->
            val notetype = collection.saveNotetype(clozeNotetype())
            val note = collection.addNote(Note(0, "c", notetype.id, listOf("{{c1::Paris}} is in {{c3::France}} {{c1::x}}", ""), emptyList()), 1)
            assertEquals(listOf(0, 2), collection.cardsOfNote(note.id).map { it.ordinal })
        }
    }

    @Test
    fun unknownJsonKeysArePreservedOnSave() {
        val directory = freshDirectory("preserve")
        val path: String
        createCollection(directory).use { collection ->
            path = collection.path
            val notetype = collection.saveNotetype(basicNotetype().copy(preserved = JsonObject(mapOf("futureKey" to JsonPrimitive("kept")))))
            collection.saveNotetype(collection.notetype(notetype.id)!!.copy(css = "changed"))
        }
        AnkiCollection.open(path).use { reopened ->
            val notetype = reopened.notetypeByName("Bert Basic")!!
            assertEquals("changed", notetype.css)
            assertEquals(JsonPrimitive("kept"), notetype.preserved["futureKey"])
            assertEquals(JsonPrimitive(false), notetype.fields.first().preserved["sticky"])
        }
    }

    @Test
    fun deletingNoteRemovesItsCards() {
        createCollection(freshDirectory("delete")).use { collection ->
            val notetype = collection.saveNotetype(basicNotetype())
            val note = collection.addNote(Note(0, "d", notetype.id, listOf("Q", "A", "x"), emptyList()), 1)
            collection.deleteNote(note.id)
            assertNull(collection.note(note.id))
            assertTrue(collection.cards().isEmpty())
        }
    }

    @Test
    fun failedTransactionRollsBack() {
        createCollection(freshDirectory("rollback")).use { collection ->
            val notetype = collection.saveNotetype(basicNotetype())
            runCatching {
                collection.transaction {
                    collection.addNote(Note(0, "r", notetype.id, listOf("Q", "A", ""), emptyList()), 1)
                    error("boom")
                }
            }
            assertEquals(0, collection.noteCount())
        }
    }

    @Test
    fun checksumMatchesAnkiForStrippedFirstField() {
        assertEquals(3816237764L, AnkiCollection.fieldChecksum("<b>France</b>"))
        assertEquals(" a b ", AnkiCollection.tagsColumn(listOf("a", "b")))
    }

    @Test
    fun cardSchedulingDefaultsDescribeANewCard() {
        val card = Card(1, 1, 1, 0)
        assertEquals(CardScheduling(), card.scheduling)
    }
}
