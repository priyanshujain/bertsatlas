package app.bertsatlas.anki

import app.bertsatlas.anki.collection.AnkiCollection
import app.bertsatlas.anki.model.CardData
import app.bertsatlas.anki.model.Deck
import app.bertsatlas.anki.model.Note
import app.bertsatlas.anki.model.ReviewRecord
import app.bertsatlas.anki.model.RevlogEntry
import app.bertsatlas.anki.model.StudiedToday
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReviewRecordTest {
    @Test
    fun cardDataEncodesLikeAnki() {
        val data = CardData(originalPosition = 1, stability = 2.30649995f, difficulty = 2.1181039f, desiredRetention = 0.9f, decay = 0.1542f, lastReviewSecs = 1792266065)
        assertEquals("""{"pos":1,"s":2.3065,"d":2.118,"dr":0.9,"decay":0.154,"lrt":1792266065}""", data.encode())
        assertEquals("""{"pos":1,"s":8.2956,"d":1.0,"dr":0.9,"decay":0.154,"lrt":1792266065}""", data.copy(stability = 8.2956f, difficulty = 1f).encode())
        assertEquals("{}", CardData().encode())
    }

    @Test
    fun cardDataDecodesAndIgnoresInvalidValues() {
        val decoded = CardData.decode("""{"pos":3,"s":13.8361,"d":2.104,"dr":0.9,"decay":0.154,"lrt":1792266065,"cd":"{\"x\":1}"}""")
        assertEquals(3, decoded.originalPosition)
        assertEquals(13.8361f, decoded.stability)
        assertEquals(1792266065, decoded.lastReviewSecs)
        assertEquals("{\"x\":1}", decoded.customData)
        assertEquals(CardData(), CardData.decode("not json"))
        assertEquals(CardData(stability = 1f), CardData.decode("""{"s":1,"d":"bad"}"""))
    }

    @Test
    fun recordReviewWritesCardRevlogDeckCountsAndLeechTag() {
        val directory = freshDirectory("review")
        val path: String
        val cardId: Long
        createCollection(directory).use { collection ->
            path = collection.path
            val notetype = collection.saveNotetype(basicNotetype())
            val parent = collection.saveDeck(Deck(id = 0, name = "Geo"))
            val section = collection.saveDeck(Deck(id = 0, name = "Geo::Europe"))
            val note = collection.addNote(Note(0, "guid-fr", notetype.id, listOf("France", "Paris", ""), emptyList()), section.id)
            val card = collection.cardsOfNote(note.id).single()
            cardId = card.id
            val reviewed = card.copy(scheduling = card.scheduling.copy(type = 2, queue = 2, due = 12, interval = 12, reps = 1, data = """{"s":12.0,"d":5.0}"""))
            collection.recordReview(ReviewRecord(reviewed, RevlogEntry(1_000, card.id, 3, 12, 0, 500, 4_000, 0), today = 7, countsAsNewCard = true, countsAsReview = false, leeched = false))
            collection.recordReview(ReviewRecord(reviewed, RevlogEntry(1_000, card.id, 1, -600, 12, 900, 90_000, 1), today = 7, countsAsNewCard = false, countsAsReview = true, leeched = true))
            assertEquals(StudiedToday(newCards = 1, reviews = 1), collection.studiedToday(section.id, 7))
            assertEquals(StudiedToday(newCards = 1, reviews = 1), collection.studiedToday(parent.id, 7))
            assertEquals(StudiedToday(0, 0), collection.studiedToday(parent.id, 8))
        }
        AnkiCollection.open(path).use { reopened ->
            val revlog = reopened.revlogOfCard(cardId)
            assertEquals(listOf(1_000L, 1_001L), revlog.map { it.id })
            assertTrue(revlog.all { it.usn == -1 })
            val card = reopened.card(cardId)!!
            assertEquals(12, card.scheduling.interval)
            assertEquals(-1, card.usn)
            assertEquals(listOf("leech"), reopened.noteByGuid("guid-fr")!!.tags)
            assertEquals(StudiedToday(1, 1), reopened.studiedToday(reopened.deckByName("Geo::Europe")!!.id, 7))
        }
    }
}
