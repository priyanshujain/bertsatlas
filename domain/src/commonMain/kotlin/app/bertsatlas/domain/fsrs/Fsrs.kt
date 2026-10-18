package app.bertsatlas.domain.fsrs

import kotlin.math.exp
import kotlin.math.expm1
import kotlin.math.ln
import kotlin.math.pow

enum class Rating(val number: Int) {
    Again(1),
    Hard(2),
    Good(3),
    Easy(4),
    ;

    companion object {
        fun fromNumber(number: Int): Rating = entries.first { it.number == number }
    }
}

data class MemoryState(
    val stability: Float,
    val difficulty: Float,
)

data class ItemState(
    val memory: MemoryState,
    val intervalDays: Float,
)

data class NextStates(
    val again: ItemState,
    val hard: ItemState,
    val good: ItemState,
    val easy: ItemState,
) {
    fun forRating(rating: Rating): ItemState = when (rating) {
        Rating.Again -> again
        Rating.Hard -> hard
        Rating.Good -> good
        Rating.Easy -> easy
    }
}

data class FsrsReview(
    val rating: Rating,
    val daysSincePreviousReview: Int,
)

class FsrsParameters private constructor(val weights: List<Float>) {
    val decay: Float get() = weights[20]

    val allowsShortTermScheduling: Boolean get() = weights[17] > 0f && weights[18] > 0f

    companion object {
        val FSRS6_DEFAULT: List<Float> = listOf(
            0.212f, 1.2931f, 2.3065f, 8.2956f, 6.4133f, 0.8334f, 3.0194f, 0.001f, 1.8722f, 0.1666f, 0.796f,
            1.4835f, 0.0614f, 0.2629f, 1.6483f, 0.6014f, 1.8729f, 0.5425f, 0.0912f, 0.0658f, 0.1542f,
        )
        const val FSRS5_DEFAULT_DECAY = 0.5f

        fun fromPreset(preset: List<Double>): FsrsParameters = fromWeights(preset.map { it.toFloat() })

        fun fromWeights(weights: List<Float>): FsrsParameters {
            val filled = when (weights.size) {
                0 -> FSRS6_DEFAULT
                17 -> weights.toMutableList().apply {
                    this[4] = this[5] * 2f + this[4]
                    this[5] = ln(this[5] * 3f + 1f) / 3f
                    this[6] = this[6] + 0.5f
                    addAll(listOf(0f, 0f, 0f, FSRS5_DEFAULT_DECAY))
                }
                19 -> weights + listOf(0f, FSRS5_DEFAULT_DECAY)
                21 -> weights
                else -> FSRS6_DEFAULT
            }.takeIf { candidate -> candidate.all { it.isFinite() } } ?: FSRS6_DEFAULT
            return FsrsParameters(clipped(filled))
        }

        private fun clipped(weights: List<Float>): List<Float> {
            val bounds = listOf(
                Fsrs.STABILITY_MIN to Fsrs.INITIAL_STABILITY_MAX, Fsrs.STABILITY_MIN to Fsrs.INITIAL_STABILITY_MAX,
                Fsrs.STABILITY_MIN to Fsrs.INITIAL_STABILITY_MAX, Fsrs.STABILITY_MIN to Fsrs.INITIAL_STABILITY_MAX,
                Fsrs.DIFFICULTY_MIN to Fsrs.DIFFICULTY_MAX, 0.001f to 4f, 0.001f to 4f, 0.001f to 0.75f,
                0f to 4.5f, 0f to 0.8f, 0.001f to 3.5f, 0.001f to 5f, 0.001f to 0.25f, 0.001f to 0.9f,
                0f to 4f, 0f to 1f, 1f to 6f, 0f to 2f, 0f to 2f, 0f to 0.8f, 0.1f to 0.8f,
            )
            return weights.zip(bounds) { weight, (low, high) -> weight.coerceIn(low, high) }
        }
    }
}

class Fsrs(val parameters: FsrsParameters) {
    private val w = parameters.weights

    fun nextStates(current: MemoryState?, desiredRetention: Float, daysElapsed: Int): NextStates {
        val states = Rating.entries.map { rating ->
            val memory = if (current == null) {
                initialState(rating)
            } else {
                step(current, rating, daysElapsed.toFloat())
            }
            ItemState(memory, nextIntervalDays(memory.stability, desiredRetention))
        }
        return NextStates(states[0], states[1], states[2], states[3])
    }

    fun memoryStateAfter(reviews: List<FsrsReview>, startingState: MemoryState? = null): MemoryState? {
        if (reviews.isEmpty()) return startingState
        var state = startingState ?: initialState(reviews.first().rating)
        val remaining = if (startingState == null) reviews.drop(1) else reviews
        remaining.forEach { review -> state = step(state, review.rating, review.daysSincePreviousReview.toFloat()) }
        return state
    }

    fun memoryStateFromSm2(easeFactor: Float, intervalDays: Float, sm2Retention: Float): MemoryState {
        val decay = -w[20]
        val factor = 0.9f.pow(1f / decay) - 1f
        val stability = intervalDays.coerceAtLeast(STABILITY_MIN) * factor / (sm2Retention.pow(1f / decay) - 1f)
        val difficulty = 11f - (easeFactor - 1f) /
            (exp(w[8]) * stability.pow(-w[9]) * expm1((1f - sm2Retention) * w[10]))
        return MemoryState(stability, difficulty.coerceIn(DIFFICULTY_MIN, DIFFICULTY_MAX))
    }

    fun retrievability(memory: MemoryState, daysElapsed: Float): Float = forgettingCurve(daysElapsed, memory.stability)

    fun nextIntervalDays(stability: Float, desiredRetention: Float): Float {
        val decay = -w[20]
        val factor = exp(ln(0.9f) / decay) - 1f
        return stability / factor * (desiredRetention.pow(1f / decay) - 1f)
    }

    private fun initialState(rating: Rating): MemoryState = MemoryState(
        stability = initialStability(rating).coerceIn(STABILITY_MIN, STABILITY_MAX),
        difficulty = initialDifficulty(rating).coerceIn(DIFFICULTY_MIN, DIFFICULTY_MAX),
    )

    private fun step(state: MemoryState, rating: Rating, deltaDays: Float): MemoryState {
        val lastStability = state.stability.coerceIn(STABILITY_MIN, STABILITY_MAX)
        val lastDifficulty = state.difficulty.coerceIn(DIFFICULTY_MIN, DIFFICULTY_MAX)
        val retrievability = forgettingCurve(deltaDays, lastStability)
        val grade = rating.number.toFloat()
        val stability = when {
            deltaDays == 0f -> shortTermStability(lastStability, grade)
            rating == Rating.Again -> stabilityAfterFailure(lastStability, lastDifficulty, retrievability)
            else -> stabilityAfterSuccess(lastStability, lastDifficulty, retrievability, grade)
        }
        val difficulty = meanReversion(nextDifficulty(lastDifficulty, grade)).coerceIn(DIFFICULTY_MIN, DIFFICULTY_MAX)
        return MemoryState(stability.coerceIn(STABILITY_MIN, STABILITY_MAX), difficulty)
    }

    private fun forgettingCurve(deltaDays: Float, stability: Float): Float {
        val decay = -w[20]
        val factor = exp(ln(0.9f) / decay) - 1f
        return (deltaDays / stability * factor + 1f).pow(decay)
    }

    private fun initialStability(rating: Rating): Float = w[rating.number - 1]

    private fun initialDifficulty(rating: Rating): Float = initialDifficulty(rating.number.toFloat())

    private fun initialDifficulty(grade: Float): Float = w[4] - exp(w[5] * (grade - 1f)) + 1f

    private fun nextDifficulty(difficulty: Float, grade: Float): Float {
        val delta = -w[6] * (grade - 3f)
        return difficulty + (10f - difficulty) * delta / 9f
    }

    private fun meanReversion(difficulty: Float): Float = w[7] * (initialDifficulty(4f) - difficulty) + difficulty

    private fun stabilityAfterSuccess(stability: Float, difficulty: Float, retrievability: Float, grade: Float): Float {
        val hardPenalty = if (grade == 2f) w[15] else 1f
        val easyBonus = if (grade == 4f) w[16] else 1f
        return stability * (
            exp(w[8]) * (11f - difficulty) * stability.pow(-w[9]) *
                (exp((1f - retrievability) * w[10]) - 1f) * hardPenalty * easyBonus + 1f
            )
    }

    private fun stabilityAfterFailure(stability: Float, difficulty: Float, retrievability: Float): Float {
        val longTerm = w[11] * difficulty.pow(-w[12]) * ((stability + 1f).pow(w[13]) - 1f) * exp((1f - retrievability) * w[14])
        val shortTermCeiling = stability / exp(w[17] * w[18])
        return minOf(longTerm, shortTermCeiling)
    }

    private fun shortTermStability(stability: Float, grade: Float): Float {
        val increase = exp(w[17] * (grade - 3f + w[18])) * stability.pow(-w[19])
        return stability * if (grade >= 2f) increase.coerceAtLeast(1f) else increase
    }

    companion object {
        const val STABILITY_MIN = 0.001f
        const val STABILITY_MAX = 36500f
        const val DIFFICULTY_MIN = 1f
        const val DIFFICULTY_MAX = 10f
        const val INITIAL_STABILITY_MAX = 100f
    }
}
