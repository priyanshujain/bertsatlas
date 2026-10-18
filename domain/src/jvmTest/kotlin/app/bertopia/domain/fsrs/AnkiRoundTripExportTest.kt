package app.bertopia.domain.fsrs

import app.bertopia.anki.collection.AnkiCollection
import app.bertopia.anki.model.CardData
import app.bertopia.anki.model.Deck
import app.bertopia.anki.model.Note
import app.bertopia.domain.pack.BertNoteTypes
import app.bertopia.domain.pack.toAnkiNotetype
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class AnkiRoundTripExportTest {
    @Test
    fun writesReviewedCollectionForAnkiCheck() {
        val directory = File("build/fsrs-anki-check").apply { deleteRecursively(); mkdirs() }
        val realNow = System.currentTimeMillis()
        var clock = realNow - 40 * DAY_MILLIS
        val path = File(directory, "collection.anki2").absolutePath
        val expectations = mutableListOf<JsonObject>()
        AnkiCollection.create(path) { clock }.use { collection ->
            val notetype = collection.saveNotetype(BertNoteTypes.Basic.toAnkiNotetype())
            val deck = collection.saveDeck(Deck(id = 0, name = "Round Trip"))
            repeat(30) { index ->
                collection.addNote(Note(0, "round-trip-$index", notetype.id, listOf("Question $index", "Answer $index", "", "", "", "", ""), emptyList()), deck.id)
            }
            val zone = TimeZone.currentSystemDefault()
            val customParameters = listOf(0.2172, 1.1771, 3.2602, 16.1507, 7.0114, 0.57, 2.0966, 0.0069, 1.5261, 0.112, 1.0178, 1.849, 0.1133, 0.3127, 2.2934, 0.2191, 3.0004, 0.7536, 0.3332, 0.1437, 0.2)
            val config = collection.saveDeckConfig(collection.deckConfig(deck.configId)!!.copy(fsrsParameters = customParameters, desiredRetention = 0.85))
            val preset = StudyPreset.from(config, deck)
            val scheduler = CardScheduler(preset, fuzzEnabled = false)
            val ratingsPerCard = listOf(
                listOf(Rating.Good, Rating.Good, Rating.Good, Rating.Good),
                listOf(Rating.Easy, Rating.Good, Rating.Hard),
                listOf(Rating.Again, Rating.Good, Rating.Good, Rating.Again, Rating.Good),
                listOf(Rating.Hard, Rating.Good, Rating.Easy, Rating.Good),
                listOf(Rating.Good, Rating.Good, Rating.Again, Rating.Good, Rating.Good, Rating.Good),
                listOf(Rating.Easy),
                listOf(Rating.Good),
            )
            val cards = collection.cards().sortedBy { it.id }
            ratingsPerCard.forEachIndexed { cardIndex, ratings ->
                clock = realNow - 40 * DAY_MILLIS + cardIndex * 60_000L
                var card = collection.card(cards[cardIndex].id)!!
                ratings.forEachIndexed { step, rating ->
                    val timing = SchedulerTiming.forCollection(collection.creationSecs, collection.configuration(), clock, zone)
                    val record = scheduler.answer(card, rating, timing, 5_000, collection.revlogOfCard(card.id))
                    collection.recordReview(record)
                    card = collection.card(card.id)!!
                    val waitDays = if (card.scheduling.queue == CardScheduler.QUEUE_LEARN) 0L else card.scheduling.interval.toLong().coerceAtMost(9)
                    clock += if (waitDays == 0L) 11 * 60_000L else waitDays * DAY_MILLIS
                    check(clock < realNow) { "scenario ran past now at card $cardIndex step $step" }
                }
            }
            clock = realNow - 120_000
            collection.cards().filter { it.scheduling.queue == CardScheduler.QUEUE_NEW }.sortedBy { it.scheduling.due }.take(2).forEach { card ->
                val timing = SchedulerTiming.forCollection(collection.creationSecs, collection.configuration(), clock, zone)
                collection.recordReview(scheduler.answer(card, Rating.Good, timing, 3_000, emptyList()))
                clock += 1_000
            }
            clock = realNow
            val timing = SchedulerTiming.forCollection(collection.creationSecs, collection.configuration(), realNow, zone)
            val allCards = collection.cards()
            val queue = StudyQueue.build(allCards, preset, collection.studiedToday(deck.id, timing.today), timing)
            allCards.filter { it.scheduling.reps > 0 }.forEach { card ->
                val data = CardData.decode(card.scheduling.data)
                val states = scheduler.nextStates(card, timing, collection.revlogOfCard(card.id))
                expectations += JsonObject(
                    mapOf(
                        "cardId" to JsonPrimitive(card.id),
                        "s" to JsonPrimitive(data.stability),
                        "d" to JsonPrimitive(data.difficulty),
                        "next" to JsonObject(Rating.entries.associate { rating -> rating.name.lowercase() to describe(states.forRating(rating)) }),
                    ),
                )
            }
            File(directory, "expectations.json").writeText(
                JsonObject(
                    mapOf(
                        "deckId" to JsonPrimitive(deck.id),
                        "today" to JsonPrimitive(timing.today),
                        "counts" to JsonArray(listOf(queue.counts.newCards, queue.counts.learning, queue.counts.reviews).map { JsonPrimitive(it) }),
                        "cards" to JsonArray(expectations),
                    ),
                ).toString(),
            )
        }
        assertTrue(expectations.size == 9)
    }

    private fun describe(state: CardState): JsonObject = when (state) {
        is CardState.Learning -> JsonObject(mapOf("kind" to JsonPrimitive("learning"), "secs" to JsonPrimitive(state.scheduledSecs), "s" to JsonPrimitive(state.memory?.stability), "d" to JsonPrimitive(state.memory?.difficulty)))
        is CardState.Review -> JsonObject(mapOf("kind" to JsonPrimitive("review"), "days" to JsonPrimitive(state.scheduledDays), "s" to JsonPrimitive(state.memory?.stability), "d" to JsonPrimitive(state.memory?.difficulty)))
        is CardState.Relearning -> JsonObject(mapOf("kind" to JsonPrimitive("relearning"), "secs" to JsonPrimitive(state.learning.scheduledSecs), "days" to JsonPrimitive(state.review.scheduledDays), "s" to JsonPrimitive(state.memory?.stability), "d" to JsonPrimitive(state.memory?.difficulty)))
        is CardState.New -> JsonObject(mapOf("kind" to JsonPrimitive("new")))
    }

    private companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}
