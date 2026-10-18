package app.bertopia.domain.fsrs

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

sealed interface CardState {
    val intervalKind: IntervalKind
    val revlogKind: Int
    val memory: MemoryState?

    data class New(val position: Long) : CardState {
        override val intervalKind: IntervalKind get() = IntervalKind.InSeconds(0)
        override val revlogKind: Int get() = REVLOG_LEARNING
        override val memory: MemoryState? get() = null
    }

    data class Learning(
        val remainingSteps: Int,
        val scheduledSecs: Int,
        override val memory: MemoryState?,
    ) : CardState {
        override val intervalKind: IntervalKind get() = IntervalKind.InSeconds(scheduledSecs)
        override val revlogKind: Int get() = REVLOG_LEARNING
    }

    data class Review(
        val scheduledDays: Int,
        val elapsedDays: Int,
        val easeFactor: Float,
        val lapses: Int,
        val leeched: Boolean,
        override val memory: MemoryState?,
    ) : CardState {
        val daysLate: Int get() = elapsedDays - scheduledDays
        override val intervalKind: IntervalKind get() = IntervalKind.InDays(scheduledDays)
        override val revlogKind: Int get() = if (daysLate < 0) REVLOG_FILTERED else REVLOG_REVIEW
    }

    data class Relearning(
        val learning: Learning,
        val review: Review,
    ) : CardState {
        override val intervalKind: IntervalKind get() = learning.intervalKind
        override val revlogKind: Int get() = REVLOG_RELEARNING
        override val memory: MemoryState? get() = learning.memory
    }

    companion object {
        const val REVLOG_LEARNING = 0
        const val REVLOG_REVIEW = 1
        const val REVLOG_RELEARNING = 2
        const val REVLOG_FILTERED = 3
    }
}

val CardState.leeched: Boolean
    get() = when (this) {
        is CardState.Review -> leeched
        is CardState.Relearning -> review.leeched
        else -> false
    }

sealed interface IntervalKind {
    data class InSeconds(val seconds: Int) : IntervalKind
    data class InDays(val days: Int) : IntervalKind

    fun asDaysIfPastRollover(secondsUntilRollover: Int): IntervalKind = when (this) {
        is InSeconds -> if (seconds >= secondsUntilRollover) InDays((seconds - secondsUntilRollover) / SECONDS_PER_DAY + 1) else this
        is InDays -> this
    }

    val revlogInterval: Int
        get() = when (this) {
            is InDays -> days
            is InSeconds -> -seconds
        }
}

data class SchedulingStates(
    val current: CardState,
    val again: CardState,
    val hard: CardState,
    val good: CardState,
    val easy: CardState,
) {
    fun forRating(rating: Rating): CardState = when (rating) {
        Rating.Again -> again
        Rating.Hard -> hard
        Rating.Good -> good
        Rating.Easy -> easy
    }
}

class LearningSteps(private val stepsMinutes: List<Double>) {
    val isEmpty: Boolean get() = stepsMinutes.isEmpty()

    private fun index(remaining: Int): Int {
        val total = stepsMinutes.size
        return (total - remaining % 1000).coerceAtLeast(0).coerceAtMost((total - 1).coerceAtLeast(0))
    }

    private fun secondsAt(index: Int): Int? = stepsMinutes.getOrNull(index)?.let { (it.toFloat() * 60f).toInt() }

    fun againDelaySecs(): Int? = secondsAt(0)

    fun hardDelaySecs(remaining: Int): Int? {
        val index = index(remaining)
        val current = secondsAt(index) ?: secondsAt(0) ?: return null
        return if (index == 0) hardDelayForFirstStep(current) else current
    }

    private fun hardDelayForFirstStep(againSecs: Int): Int {
        val next = secondsAt(1)
        val secs = if (next != null) {
            (againSecs.toLong() + next) / 2
        } else {
            minOf(againSecs.toLong() * 3 / 2, againSecs.toLong() + SECONDS_PER_DAY)
        }
        return roundedToDaysIfLonger(secs).toInt()
    }

    fun goodDelaySecs(remaining: Int): Int? = secondsAt(index(remaining) + 1)

    fun currentDelaySecs(remaining: Int): Int = secondsAt(index(remaining)) ?: 0

    fun remainingForGood(remaining: Int): Int = (stepsMinutes.size - (index(remaining) + 1)).coerceAtLeast(0)

    fun remainingForFailed(): Int = stepsMinutes.size

    private fun roundedToDaysIfLonger(secs: Long): Long =
        if (secs > SECONDS_PER_DAY) (secs.toFloat() / SECONDS_PER_DAY).roundToInt().toLong() * SECONDS_PER_DAY else secs
}

class StateContext(
    val fuzzFactor: Float?,
    val fsrsNextStates: NextStates,
    val allowShortTerm: Boolean,
    val shortTermWithSteps: Boolean,
    val steps: LearningSteps,
    val relearnSteps: LearningSteps,
    val initialEaseFactor: Float,
    val maximumReviewInterval: Int,
    val leechThreshold: Int,
) {
    fun minAndMaxReviewIntervals(minimum: Int): Pair<Int, Int> {
        val maximum = maximumReviewInterval.coerceAtLeast(1)
        return minimum.coerceIn(1, maximum) to maximum
    }

    fun withReviewFuzz(interval: Float, minimum: Int, maximum: Int): Int = ReviewFuzz.apply(fuzzFactor, interval, minimum, maximum)

    fun shortTermApplies(steps: LearningSteps, intervalDays: Float): Boolean =
        allowShortTerm && (shortTermWithSteps || steps.isEmpty) && intervalDays < 0.5f
}

object ReviewFuzz {
    private data class FuzzRange(val start: Float, val end: Float, val factor: Float)

    private val ranges = listOf(
        FuzzRange(2.5f, 7f, 0.15f),
        FuzzRange(7f, 20f, 0.1f),
        FuzzRange(20f, Float.MAX_VALUE, 0.05f),
    )

    fun apply(fuzzFactor: Float?, interval: Float, minimum: Int, maximum: Int): Int {
        if (fuzzFactor == null) return roundHalfAwayFromZero(interval).coerceAtMost(maximum).coerceAtLeast(minimum)
        val (lower, upper) = constrainedBounds(interval, minimum, maximum)
        return floor(lower + fuzzFactor * (1 + upper - lower)).toInt()
    }

    fun minimumInterval(interval: Float, previousInterval: Int, maximumInterval: Int): Int {
        val rounded = roundHalfAwayFromZero(interval)
        val (_, upper) = constrainedBounds(interval, 1, maximumInterval)
        return when {
            rounded > previousInterval -> previousInterval + 1
            previousInterval <= upper -> previousInterval
            else -> 0
        }
    }

    fun constrainedBounds(interval: Float, minimum: Int, maximum: Int): Pair<Int, Int> {
        val lowest = minOf(minimum, maximum)
        val clamped = interval.coerceIn(lowest.toFloat(), maximum.toFloat())
        val delta = delta(clamped)
        var lower = roundHalfAwayFromZero(clamped - delta).coerceIn(lowest, maximum)
        var upper = roundHalfAwayFromZero(clamped + delta).coerceIn(lowest, maximum)
        if (upper == lower && upper > 2 && upper < maximum) upper = lower + 1
        return lower to upper
    }

    private fun delta(interval: Float): Float =
        if (interval < 2.5f) 0f else ranges.fold(1f) { total, range -> total + range.factor * (minOf(interval, range.end) - range.start).coerceAtLeast(0f) }
}

internal fun roundHalfAwayFromZero(value: Float): Int =
    if (value >= 0f) floor(value + 0.5f).toInt() else ceil(value - 0.5f).toInt()

object CardTransitions {
    fun nextStates(current: CardState, context: StateContext): SchedulingStates = when (current) {
        is CardState.New -> learningNextStates(
            CardState.Learning(context.steps.remainingForFailed(), 0, null),
            context,
        ).copy(current = current)
        is CardState.Learning -> learningNextStates(current, context)
        is CardState.Review -> reviewNextStates(current, context)
        is CardState.Relearning -> relearningNextStates(current, context)
    }

    private fun learningNextStates(state: CardState.Learning, context: StateContext): SchedulingStates {
        val fsrs = context.fsrsNextStates
        return SchedulingStates(
            current = state,
            again = learningAnswer(context, fsrs.again, context.steps.againDelaySecs(), context.steps.remainingForFailed()),
            hard = learningAnswer(context, fsrs.hard, context.steps.hardDelaySecs(state.remainingSteps), state.remainingSteps),
            good = learningAnswer(
                context,
                fsrs.good,
                context.steps.goodDelaySecs(state.remainingSteps),
                context.steps.remainingForGood(state.remainingSteps),
                remainingWhenShortTerm = state.remainingSteps,
            ),
            easy = graduateEasy(context, fsrs),
        )
    }

    private fun learningAnswer(
        context: StateContext,
        item: ItemState,
        delaySecs: Int?,
        remainingSteps: Int,
        remainingWhenShortTerm: Int = remainingSteps,
    ): CardState {
        if (delaySecs != null) return CardState.Learning(remainingSteps, delaySecs, item.memory)
        val (minimum, maximum) = context.minAndMaxReviewIntervals(1)
        if (context.shortTermApplies(context.steps, item.intervalDays)) {
            return CardState.Learning(remainingWhenShortTerm, (item.intervalDays * SECONDS_PER_DAY).toInt(), item.memory)
        }
        return CardState.Review(
            scheduledDays = context.withReviewFuzz(roundHalfAwayFromZero(item.intervalDays).coerceAtLeast(1).toFloat(), minimum, maximum),
            elapsedDays = 0,
            easeFactor = context.initialEaseFactor,
            lapses = 0,
            leeched = false,
            memory = item.memory,
        )
    }

    private fun graduateEasy(context: StateContext, fsrs: NextStates): CardState.Review {
        val (minimumBeforeGood, maximum) = context.minAndMaxReviewIntervals(1)
        val good = context.withReviewFuzz(fsrs.good.intervalDays, minimumBeforeGood, maximum)
        val interval = roundHalfAwayFromZero(fsrs.easy.intervalDays).coerceAtLeast(1)
        return CardState.Review(
            scheduledDays = context.withReviewFuzz(interval.toFloat(), good + 1, maximum),
            elapsedDays = 0,
            easeFactor = context.initialEaseFactor,
            lapses = 0,
            leeched = false,
            memory = fsrs.easy.memory,
        )
    }

    private fun reviewNextStates(state: CardState.Review, context: StateContext): SchedulingStates {
        val fsrs = context.fsrsNextStates
        val hard = passingInterval(context, fsrs.hard.intervalDays, ReviewFuzz.minimumInterval(fsrs.hard.intervalDays, state.scheduledDays, context.maximumReviewInterval).coerceAtLeast(1))
        val good = passingInterval(context, fsrs.good.intervalDays, ReviewFuzz.minimumInterval(fsrs.good.intervalDays, state.scheduledDays, context.maximumReviewInterval).coerceAtLeast(hard + 1))
        val easy = passingInterval(context, fsrs.easy.intervalDays, ReviewFuzz.minimumInterval(fsrs.easy.intervalDays, state.scheduledDays, context.maximumReviewInterval).coerceAtLeast(good + 1))
        return SchedulingStates(
            current = state,
            again = reviewAgain(state, context),
            hard = state.copy(scheduledDays = hard, elapsedDays = 0, easeFactor = (state.easeFactor + EASE_HARD_DELTA).coerceAtLeast(MINIMUM_EASE), memory = fsrs.hard.memory),
            good = state.copy(scheduledDays = good, elapsedDays = 0, memory = fsrs.good.memory),
            easy = state.copy(scheduledDays = easy, elapsedDays = 0, easeFactor = state.easeFactor + EASE_EASY_DELTA, memory = fsrs.easy.memory),
        )
    }

    private fun passingInterval(context: StateContext, interval: Float, minimum: Int): Int {
        val (lowest, maximum) = context.minAndMaxReviewIntervals(minimum)
        return context.withReviewFuzz(interval, lowest, maximum)
    }

    private fun reviewAgain(state: CardState.Review, context: StateContext): CardState {
        val lapses = state.lapses + 1
        val item = context.fsrsNextStates.again
        val scheduledDays = item.intervalDays
        val againReview = CardState.Review(
            scheduledDays = roundHalfAwayFromZero(scheduledDays).coerceAtLeast(1),
            elapsedDays = 0,
            easeFactor = (state.easeFactor + EASE_AGAIN_DELTA).coerceAtLeast(MINIMUM_EASE),
            lapses = lapses,
            leeched = leechThresholdMet(lapses, context.leechThreshold),
            memory = item.memory,
        )
        val againDelay = context.relearnSteps.againDelaySecs()
        return when {
            againDelay != null -> CardState.Relearning(
                CardState.Learning(context.relearnSteps.remainingForFailed(), againDelay, item.memory),
                againReview,
            )
            context.shortTermApplies(context.relearnSteps, scheduledDays) -> CardState.Relearning(
                CardState.Learning(context.relearnSteps.remainingForFailed(), (scheduledDays * SECONDS_PER_DAY).toInt(), item.memory),
                againReview,
            )
            else -> againReview
        }
    }

    private fun relearningNextStates(state: CardState.Relearning, context: StateContext): SchedulingStates {
        val fsrs = context.fsrsNextStates
        val steps = context.relearnSteps
        return SchedulingStates(
            current = state,
            again = relearnAgain(state, context),
            hard = relearnAnswer(state, context, fsrs.hard, steps.hardDelaySecs(state.learning.remainingSteps), state.learning.remainingSteps),
            good = relearnAnswer(state, context, fsrs.good, steps.goodDelaySecs(state.learning.remainingSteps), steps.remainingForGood(state.learning.remainingSteps)),
            easy = relearnEasy(state, context),
        )
    }

    private fun relearnAgain(state: CardState.Relearning, context: StateContext): CardState {
        val item = context.fsrsNextStates.again
        val againDelay = context.relearnSteps.againDelaySecs()
        if (againDelay != null) {
            return CardState.Relearning(
                CardState.Learning(context.relearnSteps.remainingForFailed(), againDelay, item.memory),
                state.review.copy(scheduledDays = roundHalfAwayFromZero(item.intervalDays).coerceAtLeast(1), elapsedDays = 0, memory = item.memory),
            )
        }
        return relearnWithoutStep(state, context, item, context.relearnSteps.remainingForFailed())
    }

    private fun relearnAnswer(state: CardState.Relearning, context: StateContext, item: ItemState, delaySecs: Int?, remainingSteps: Int): CardState {
        if (delaySecs != null) {
            return CardState.Relearning(
                state.learning.copy(remainingSteps = remainingSteps, scheduledSecs = delaySecs, memory = item.memory),
                state.review.copy(elapsedDays = 0, memory = item.memory),
            )
        }
        return relearnWithoutStep(state, context, item, remainingSteps)
    }

    private fun relearnWithoutStep(state: CardState.Relearning, context: StateContext, item: ItemState, remainingSteps: Int): CardState {
        val (minimum, maximum) = context.minAndMaxReviewIntervals(1)
        val review = state.review.copy(
            scheduledDays = context.withReviewFuzz(roundHalfAwayFromZero(item.intervalDays).coerceAtLeast(1).toFloat(), minimum, maximum),
            memory = item.memory,
        )
        return if (context.shortTermApplies(context.relearnSteps, item.intervalDays)) {
            CardState.Relearning(
                state.learning.copy(remainingSteps = remainingSteps, scheduledSecs = (item.intervalDays * SECONDS_PER_DAY).toInt(), memory = item.memory),
                review,
            )
        } else {
            review
        }
    }

    private fun relearnEasy(state: CardState.Relearning, context: StateContext): CardState.Review {
        val fsrs = context.fsrsNextStates
        val (minimumBeforeGood, maximum) = context.minAndMaxReviewIntervals(1)
        val good = context.withReviewFuzz(fsrs.good.intervalDays, minimumBeforeGood, maximum)
        val interval = roundHalfAwayFromZero(fsrs.easy.intervalDays).coerceAtLeast(1)
        return state.review.copy(
            scheduledDays = context.withReviewFuzz(interval.toFloat(), good + 1, maximum),
            elapsedDays = 0,
            memory = fsrs.easy.memory,
        )
    }

    private fun leechThresholdMet(lapses: Int, threshold: Int): Boolean {
        if (threshold <= 0) return false
        val half = ceil(threshold / 2.0).toInt().coerceAtLeast(1)
        return lapses >= threshold && (lapses - threshold) % half == 0
    }

    const val INITIAL_EASE = 2.5f
    const val MINIMUM_EASE = 1.3f
    private const val EASE_AGAIN_DELTA = -0.2f
    private const val EASE_HARD_DELTA = -0.15f
    private const val EASE_EASY_DELTA = 0.15f
}

internal const val SECONDS_PER_DAY = 86_400
