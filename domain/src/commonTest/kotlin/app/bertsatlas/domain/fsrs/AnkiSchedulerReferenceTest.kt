package app.bertsatlas.domain.fsrs

import app.bertsatlas.anki.model.Card
import app.bertsatlas.anki.model.CardData
import app.bertsatlas.anki.model.CardScheduling
import app.bertsatlas.anki.model.DeckConfig
import app.bertsatlas.anki.model.RevlogEntry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnkiSchedulerReferenceTest {
    @Test
    fun replaysAnkiSchedulerDecisionsExactly() {
        val vectors = readVectors("anki-26.09.3-scheduler.json")
        var steps = 0
        vectors.arr("scenarios").forEach { scenarioElement ->
            val scenario = scenarioElement.jsonObject
            val name = "${scenario.string("preset")}/${scenario.string("scenario")}"
            val scheduler = CardScheduler(presetFrom(scenario.obj("config")), fuzzEnabled = false)
            var card = cardFrom(scenario.long("cardId"), scenario.obj("initial"))
            var history = listOf<RevlogEntry>()
            scenario.arr("steps").forEachIndexed { index, stepElement ->
                val step = stepElement.jsonObject
                val label = "$name step $index"
                val shift = step.int("shiftDays")
                card = shifted(card, shift)
                history = history.map { it.copy(id = it.id - shift * 86_400_000L) }
                assertCardMatches(step.obj("before"), card, "$label before", nowSecs = null)
                val timing = SchedulerTiming(step.long("nowMillis"), step.int("today"), step.long("nextDayAt"))
                val states = scheduler.nextStates(card, timing, history)
                Rating.entries.forEach { rating ->
                    assertOptionMatches(step.obj("options").obj(rating.name.lowercase()), states.forRating(rating), "$label option ${rating.name}")
                }
                val record = scheduler.answer(card, Rating.fromNumber(step.int("rating")), timing, 0, history)
                assertCardMatches(step.obj("after"), record.card, "$label after", nowSecs = timing.nowSecs)
                val revlog = step.obj("revlog")
                assertEquals(revlog.int("ease"), record.revlog.ease, "$label revlog ease")
                assertEquals(revlog.int("ivl"), record.revlog.interval, "$label revlog ivl")
                assertEquals(revlog.int("lastIvl"), record.revlog.lastInterval, "$label revlog lastIvl")
                assertTrue(abs(revlog.int("factor") - record.revlog.easeFactor) <= 1, "$label revlog factor ${revlog.int("factor")} vs ${record.revlog.easeFactor}")
                assertEquals(revlog.int("type"), record.revlog.kind, "$label revlog type")
                card = record.card
                history = history + record.revlog
                steps++
            }
        }
        assertEquals(141, steps)
    }

    private fun presetFrom(config: JsonObject): StudyPreset {
        val preserved = JsonObject(
            mapOf(
                "new" to JsonObject(mapOf("initialFactor" to JsonPrimitive(config.int("initialFactor")))),
                "rev" to JsonObject(mapOf("maxIvl" to JsonPrimitive(config.int("maxIvl")))),
                "lapse" to JsonObject(mapOf("leechFails" to JsonPrimitive(config.int("leechFails")), "leechAction" to JsonPrimitive(1))),
            ),
        )
        return StudyPreset.from(
            DeckConfig(
                id = 1,
                name = "Default",
                newCardsPerDay = config.int("newPerDay"),
                reviewsPerDay = config.int("revPerDay"),
                learningStepsMinutes = config.getValue("learn").doubles(),
                relearningStepsMinutes = config.getValue("relearn").doubles(),
                desiredRetention = config.double("dr"),
                fsrsParameters = config.getValue("params").doubles(),
                preserved = preserved,
            ),
        )
    }

    private fun cardFrom(id: Long, row: JsonObject) = Card(
        id = id,
        noteId = 1,
        deckId = 1,
        ordinal = 0,
        scheduling = CardScheduling(
            type = row.int("type"), queue = row.int("queue"), due = row.long("due"), interval = row.int("ivl"),
            easeFactor = row.int("factor"), reps = row.int("reps"), lapses = row.int("lapses"), left = row.int("left"),
            data = row.string("data"),
        ),
    )

    private fun shifted(card: Card, days: Int): Card {
        if (days == 0) return card
        val scheduling = card.scheduling
        val due = when (scheduling.queue) {
            2, 3 -> scheduling.due - days
            1 -> scheduling.due - days * 86_400L
            else -> scheduling.due
        }
        val data = CardData.decode(scheduling.data)
        val shiftedData = data.lastReviewSecs?.let { data.copy(lastReviewSecs = it - days * 86_400L) } ?: data
        return card.copy(scheduling = scheduling.copy(due = due, data = shiftedData.encode()))
    }

    private fun assertCardMatches(expected: JsonObject, actual: Card, label: String, nowSecs: Long?) {
        val scheduling = actual.scheduling
        assertEquals(expected.int("type"), scheduling.type, "$label type")
        assertEquals(expected.int("queue"), scheduling.queue, "$label queue")
        if (scheduling.queue == 1 && nowSecs != null) {
            assertTrue(abs(expected.long("due") - scheduling.due) <= 1, "$label learning due ${expected.long("due")} vs ${scheduling.due}")
        } else {
            assertEquals(expected.long("due"), scheduling.due, "$label due")
        }
        assertEquals(expected.int("ivl"), scheduling.interval, "$label ivl")
        assertEquals(expected.int("factor"), scheduling.easeFactor, "$label factor")
        assertEquals(expected.int("reps"), scheduling.reps, "$label reps")
        assertEquals(expected.int("lapses"), scheduling.lapses, "$label lapses")
        assertEquals(expected.int("left"), scheduling.left, "$label left")
        val expectedData = CardData.decode(expected.string("data"))
        val actualData = CardData.decode(scheduling.data)
        assertEquals(expectedData.originalPosition, actualData.originalPosition, "$label pos")
        assertEquals(expectedData.lastReviewSecs, actualData.lastReviewSecs, "$label lrt")
        assertEquals(expectedData.desiredRetention, actualData.desiredRetention, "$label dr")
        assertEquals(expectedData.decay, actualData.decay, "$label decay")
        if (expectedData.stability == null) {
            assertNull(actualData.stability, "$label s")
        } else {
            assertClose(expectedData.stability!!.toDouble(), actualData.stability!!.toDouble(), 2e-4, "$label s")
            assertClose(expectedData.difficulty!!.toDouble(), actualData.difficulty!!.toDouble(), 2e-3, "$label d")
        }
    }

    private fun assertOptionMatches(expected: JsonObject, actual: CardState, label: String) {
        when (expected.string("kind")) {
            "learning" -> {
                actual as CardState.Learning
                assertEquals(expected.int("remaining"), actual.remainingSteps, "$label remaining")
                assertEquals(expected.int("secs"), actual.scheduledSecs, "$label secs")
            }
            "review" -> {
                actual as CardState.Review
                assertEquals(expected.int("days"), actual.scheduledDays, "$label days")
                assertClose(expected.double("ease"), actual.easeFactor.toDouble(), 1e-5, "$label ease")
                assertEquals(expected.int("lapses"), actual.lapses, "$label lapses")
            }
            "relearning" -> {
                actual as CardState.Relearning
                assertEquals(expected.int("remaining"), actual.learning.remainingSteps, "$label remaining")
                assertEquals(expected.int("secs"), actual.learning.scheduledSecs, "$label secs")
                assertEquals(expected.int("days"), actual.review.scheduledDays, "$label days")
                assertClose(expected.double("ease"), actual.review.easeFactor.toDouble(), 1e-5, "$label ease")
                assertEquals(expected.int("lapses"), actual.review.lapses, "$label lapses")
            }
            else -> error("$label unexpected kind ${expected.string("kind")}")
        }
        expected.doubleOrNull("s")?.let { assertClose(it, actual.memory!!.stability.toDouble(), 2e-4, "$label s") }
        expected.doubleOrNull("d")?.let { assertClose(it, actual.memory!!.difficulty.toDouble(), 2e-4, "$label d") }
    }
}
