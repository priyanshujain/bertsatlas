package app.bertopia.domain.fsrs

import app.bertopia.anki.model.Deck
import app.bertopia.anki.model.DeckConfig
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.math.abs

data class StudyPreset(
    val newCardsPerDay: Int,
    val reviewsPerDay: Int,
    val learningStepsMinutes: List<Double>,
    val relearningStepsMinutes: List<Double>,
    val desiredRetention: Float,
    val fsrsParameters: FsrsParameters,
    val maximumReviewInterval: Int,
    val initialEaseFactor: Float,
    val leechThreshold: Int,
    val suspendsLeeches: Boolean,
    val sm2Retention: Float,
    val maximumAnswerSeconds: Int,
) {
    val decay: Float get() = fsrsParameters.decay

    companion object {
        fun from(config: DeckConfig, homeDeck: Deck? = null): StudyPreset {
            val preserved = config.preserved
            val new = preserved["new"] as? JsonObject
            val review = preserved["rev"] as? JsonObject
            val lapse = preserved["lapse"] as? JsonObject
            val deckRetention = homeDeck?.preserved?.number("desiredRetention")
            return StudyPreset(
                newCardsPerDay = config.newCardsPerDay,
                reviewsPerDay = config.reviewsPerDay,
                learningStepsMinutes = config.learningStepsMinutes,
                relearningStepsMinutes = config.relearningStepsMinutes,
                desiredRetention = (deckRetention ?: config.desiredRetention).toFloat(),
                fsrsParameters = FsrsParameters.fromPreset(config.fsrsParameters),
                maximumReviewInterval = review?.integer("maxIvl") ?: DEFAULT_MAXIMUM_INTERVAL,
                initialEaseFactor = (new?.integer("initialFactor") ?: DEFAULT_INITIAL_FACTOR) / 1000f,
                leechThreshold = lapse?.integer("leechFails") ?: DEFAULT_LEECH_THRESHOLD,
                suspendsLeeches = (lapse?.integer("leechAction") ?: LEECH_ACTION_TAG_ONLY) == LEECH_ACTION_SUSPEND,
                sm2Retention = (preserved.number("sm2Retention") ?: DEFAULT_SM2_RETENTION).toFloat(),
                maximumAnswerSeconds = preserved.integer("maxTaken") ?: DEFAULT_MAXIMUM_ANSWER_SECONDS,
            )
        }

        private fun JsonObject.integer(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
        private fun JsonObject.number(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

        private const val DEFAULT_MAXIMUM_INTERVAL = 36_500
        private const val DEFAULT_INITIAL_FACTOR = 2_500
        private const val DEFAULT_LEECH_THRESHOLD = 8
        private const val LEECH_ACTION_SUSPEND = 0
        private const val LEECH_ACTION_TAG_ONLY = 1
        private const val DEFAULT_SM2_RETENTION = 0.9
        private const val DEFAULT_MAXIMUM_ANSWER_SECONDS = 60
    }
}

enum class StudyIntensity(val desiredRetention: Double) {
    Relaxed(0.85),
    Balanced(0.90),
    Intense(0.95),
    ;

    companion object {
        fun forRetention(desiredRetention: Double): StudyIntensity = entries.minBy { abs(it.desiredRetention - desiredRetention) }
    }
}

data class FriendlyStudySettings(
    val dailyNewCardGoal: Int,
    val intensity: StudyIntensity,
)

object FriendlyStudySettingsMapping {
    const val MAXIMUM_DAILY_NEW_CARD_GOAL = 9_999

    fun read(config: DeckConfig): FriendlyStudySettings = FriendlyStudySettings(
        dailyNewCardGoal = config.newCardsPerDay,
        intensity = StudyIntensity.forRetention(config.desiredRetention),
    )

    fun applied(config: DeckConfig, settings: FriendlyStudySettings): DeckConfig = config.copy(
        newCardsPerDay = settings.dailyNewCardGoal.coerceIn(0, MAXIMUM_DAILY_NEW_CARD_GOAL),
        desiredRetention = settings.intensity.desiredRetention,
    )
}
