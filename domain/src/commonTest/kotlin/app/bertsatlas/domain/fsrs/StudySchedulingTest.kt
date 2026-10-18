package app.bertsatlas.domain.fsrs

import app.bertsatlas.anki.model.Card
import app.bertsatlas.anki.model.CardScheduling
import app.bertsatlas.anki.model.DeckConfig
import app.bertsatlas.anki.model.StudiedToday
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class StudySchedulingTest {
    @Test
    fun quizOutcomesMapToGrades() {
        fun outcome(correct: Boolean = true, timedOut: Boolean = false, hint: Boolean = false, overtime: Boolean = false, millis: Long = 6_000, format: QuizAnswerFormat = QuizAnswerFormat.MultipleChoice) =
            QuizGrading.ratingFor(QuizAnswerOutcome(correct, timedOut, hint, overtime, millis, format))
        assertEquals(Rating.Again, outcome(correct = false))
        assertEquals(Rating.Again, outcome(timedOut = true))
        assertEquals(Rating.Hard, outcome(hint = true, millis = 1_000))
        assertEquals(Rating.Hard, outcome(overtime = true))
        assertEquals(Rating.Good, outcome())
        assertEquals(Rating.Easy, outcome(millis = 3_000))
        assertEquals(Rating.Easy, outcome(millis = 7_000, format = QuizAnswerFormat.LetterBank))
        assertEquals(Rating.Good, outcome(millis = 9_000, format = QuizAnswerFormat.LetterBank))
    }

    @Test
    fun friendlySettingsMapOntoDeckPreset() {
        val config = DeckConfig(id = 1, name = "Default", newCardsPerDay = 20, desiredRetention = 0.9)
        assertEquals(FriendlyStudySettings(20, StudyIntensity.Balanced), FriendlyStudySettingsMapping.read(config))
        val updated = FriendlyStudySettingsMapping.applied(config, FriendlyStudySettings(35, StudyIntensity.Intense))
        assertEquals(35, updated.newCardsPerDay)
        assertEquals(0.95, updated.desiredRetention)
        assertEquals(StudyIntensity.Relaxed, StudyIntensity.forRetention(0.8))
        assertEquals(StudyIntensity.Intense, FriendlyStudySettingsMapping.read(config.copy(desiredRetention = 0.97)).intensity)
    }

    @Test
    fun presetReadsAnkiDeckConfigFields() {
        val preserved = JsonObject(
            mapOf(
                "new" to JsonObject(mapOf("initialFactor" to JsonPrimitive(2300))),
                "rev" to JsonObject(mapOf("maxIvl" to JsonPrimitive(180))),
                "lapse" to JsonObject(mapOf("leechFails" to JsonPrimitive(5), "leechAction" to JsonPrimitive(0))),
                "maxTaken" to JsonPrimitive(30),
            ),
        )
        val preset = StudyPreset.from(DeckConfig(id = 1, name = "x", learningStepsMinutes = listOf(2.0), fsrsParameters = List(19) { 0.5 }, preserved = preserved))
        assertEquals(180, preset.maximumReviewInterval)
        assertEquals(2.3f, preset.initialEaseFactor)
        assertEquals(5, preset.leechThreshold)
        assertEquals(true, preset.suspendsLeeches)
        assertEquals(30, preset.maximumAnswerSeconds)
        assertEquals(0.5f, preset.decay)
        assertEquals(listOf(2.0), preset.learningStepsMinutes)
    }

    @Test
    fun timingMatchesAnkiDayCutoff() {
        val legacy = SchedulerTiming.withLegacyCutoff(creationSecs = 1_792_195_200, nowMillis = 1_792_266_065_000, currentOffsetSecs = 19_800, rolloverHour = 4)
        assertEquals(0, legacy.today)
        assertEquals(1_792_276_200, legacy.nextDayAtSecs)
        val withOffset = SchedulerTiming.withCreationOffset(creationSecs = 1_792_189_800, creationOffsetSecs = 19_800, nowMillis = 1_792_266_065_000, currentOffsetSecs = 19_800, rolloverHour = 4)
        assertEquals(0, withOffset.today)
        assertEquals(1_792_276_200, withOffset.nextDayAtSecs)
        val threeDaysLater = SchedulerTiming.withCreationOffset(1_792_189_800, 19_800, 1_792_266_065_000 + 3 * 86_400_000L, 19_800, 4)
        assertEquals(3, threeDaysLater.today)
        val fromConfig = SchedulerTiming.forCollection(1_792_189_800, JsonObject(mapOf("creationOffset" to JsonPrimitive(-330))), 1_792_266_065_000, TimeZone.of("Asia/Kolkata"))
        assertEquals(withOffset, fromConfig)
    }

    @Test
    fun queueHonoursDailyLimitsAndOrder() {
        val timing = SchedulerTiming(nowMillis = 100_000_000, today = 10, nextDayAtSecs = 120_000)
        fun card(id: Long, queue: Int, due: Long) = Card(id, id, 1, 0, CardScheduling(type = queue.coerceAtMost(2), queue = queue, due = due))
        val cards = listOf(
            card(1, 0, 3), card(2, 0, 1), card(3, 0, 2),
            card(4, 2, 9), card(5, 2, 10), card(6, 2, 11),
            card(7, 1, 100_000 + 600), card(8, 1, 100_000 + 5_000),
            card(9, 3, 10), card(10, -1, 1),
        )
        val preset = StudyPreset.from(DeckConfig(id = 1, name = "x", newCardsPerDay = 2, reviewsPerDay = 4))
        val queue = StudyQueue.build(cards, preset, StudiedToday(0, 0), timing)
        assertEquals(listOf(7L, 9L), queue.learning.map { it.id })
        assertEquals(listOf(4L, 5L), queue.reviews.map { it.id })
        assertEquals(listOf(2L), queue.newCards.map { it.id })
        assertEquals(DueCounts(newCards = 1, learning = 2, reviews = 2), queue.counts)
        assertEquals(7L, queue.next()?.id)
        val afterStudying = StudyQueue.build(cards, preset, StudiedToday(newCards = 2, reviews = 1), timing)
        assertEquals(0, afterStudying.newCards.size)
        assertEquals(2, afterStudying.reviews.size)
    }
}
