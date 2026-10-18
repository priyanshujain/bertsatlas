package app.bertopia.domain.fsrs

import app.bertopia.anki.model.Card
import app.bertopia.anki.model.CardData
import app.bertopia.anki.model.CardScheduling
import app.bertopia.anki.model.ReviewRecord
import app.bertopia.anki.model.RevlogEntry
import app.bertopia.domain.RandomSource
import app.bertopia.domain.SeededRandomSource
import kotlin.math.roundToInt

class CardScheduler(
    private val preset: StudyPreset,
    private val shortTermWithSteps: Boolean = false,
    private val fuzzEnabled: Boolean = true,
    private val randomSource: RandomSource = SeededRandomSource,
) {
    private val fsrs = Fsrs(preset.fsrsParameters)
    private val learningSteps = LearningSteps(preset.learningStepsMinutes)
    private val relearningSteps = LearningSteps(preset.relearningStepsMinutes)

    fun nextStates(card: Card, timing: SchedulerTiming, history: List<RevlogEntry>): SchedulingStates {
        val memoryCard = withMemoryState(card, timing, history)
        return CardTransitions.nextStates(currentState(memoryCard, timing), stateContext(memoryCard, timing, history))
    }

    fun answer(card: Card, rating: Rating, timing: SchedulerTiming, answerMillis: Long, history: List<RevlogEntry>): ReviewRecord {
        val memoryCard = withMemoryState(card, timing, history)
        val current = currentState(memoryCard, timing)
        val next = CardTransitions.nextStates(current, stateContext(memoryCard, timing, history)).forRating(rating)
        val fuzzSeed = memoryCard.id + memoryCard.scheduling.reps
        val updated = applyState(memoryCard, current, next, timing, fuzzSeed)
        val easeForRevlog = next.memory?.let { difficultyShifted(it) } ?: when (next) {
            is CardState.Review -> next.easeFactor
            is CardState.Relearning -> next.review.easeFactor
            else -> 0f
        }
        val revlog = RevlogEntry(
            id = timing.nowMillis,
            cardId = card.id,
            ease = rating.number,
            interval = next.intervalKind.asDaysIfPastRollover(timing.secondsUntilRollover).revlogInterval,
            lastInterval = current.intervalKind.asDaysIfPastRollover(timing.secondsUntilRollover).revlogInterval,
            easeFactor = (easeForRevlog * 1000f).roundToInt(),
            timeMs = answerMillis.coerceIn(0, preset.maximumAnswerSeconds * 1000L).toInt(),
            kind = current.revlogKind,
        )
        val fromQueue = card.scheduling.queue
        return ReviewRecord(
            card = updated,
            revlog = revlog,
            today = timing.today,
            countsAsNewCard = fromQueue == QUEUE_NEW,
            countsAsReview = fromQueue == QUEUE_REVIEW || fromQueue == QUEUE_DAY_LEARN,
            leeched = next.leeched,
        )
    }

    fun currentState(card: Card, timing: SchedulerTiming): CardState {
        val scheduling = card.scheduling
        val data = CardData.decode(scheduling.data)
        val memory = data.memoryState()
        val easeFactor = scheduling.easeFactor / 1000f
        return when (scheduling.type) {
            TYPE_NEW -> CardState.New(scheduling.due.coerceAtLeast(0))
            TYPE_LEARN -> CardState.Learning(scheduling.left, learningSteps.currentDelaySecs(scheduling.left), memory)
            TYPE_REVIEW -> {
                val due = minOf(scheduling.due, timing.today.toLong())
                CardState.Review(
                    scheduledDays = scheduling.interval,
                    elapsedDays = (scheduling.interval - (due - timing.today)).coerceAtLeast(0).toInt(),
                    easeFactor = easeFactor,
                    lapses = scheduling.lapses,
                    leeched = false,
                    memory = memory,
                )
            }
            else -> CardState.Relearning(
                CardState.Learning(scheduling.left, relearningSteps.currentDelaySecs(scheduling.left), memory),
                CardState.Review(scheduling.interval, scheduling.interval, easeFactor, scheduling.lapses, false, memory),
            )
        }
    }

    fun withMemoryState(card: Card, timing: SchedulerTiming, history: List<RevlogEntry>): Card {
        val data = CardData.decode(card.scheduling.data)
        if (data.hasMemoryState || card.scheduling.type == TYPE_NEW) return card
        val memory = MemoryFromHistory.memoryState(fsrs, history, timing.nextDayAtSecs, preset.sm2Retention)
            ?: if (card.scheduling.interval == 0) {
                null
            } else {
                fsrs.memoryStateFromSm2(card.scheduling.easeFactor / 1000f, card.scheduling.interval.toFloat(), preset.sm2Retention)
            }
        memory ?: return card
        val updated = data.copy(stability = memory.stability, difficulty = memory.difficulty)
        return card.copy(scheduling = card.scheduling.copy(data = updated.encode()))
    }

    private fun stateContext(card: Card, timing: SchedulerTiming, history: List<RevlogEntry>): StateContext {
        val data = CardData.decode(card.scheduling.data)
        val lastReviewSecs = data.lastReviewSecs ?: MemoryFromHistory.lastReviewSecs(history)
        val daysElapsed = lastReviewSecs?.let { timing.daysSince(it) } ?: 0
        return StateContext(
            fuzzFactor = if (fuzzEnabled) fuzzFactor(card.id + card.scheduling.reps) else null,
            fsrsNextStates = fsrs.nextStates(data.memoryState(), preset.desiredRetention, daysElapsed),
            allowShortTerm = preset.fsrsParameters.allowsShortTermScheduling,
            shortTermWithSteps = shortTermWithSteps,
            steps = learningSteps,
            relearnSteps = relearningSteps,
            initialEaseFactor = preset.initialEaseFactor,
            maximumReviewInterval = preset.maximumReviewInterval,
            leechThreshold = preset.leechThreshold,
        )
    }

    private fun applyState(card: Card, current: CardState, next: CardState, timing: SchedulerTiming, fuzzSeed: Long): Card {
        val scheduling = card.scheduling
        val data = CardData.decode(scheduling.data)
        val originalPosition = (current as? CardState.New)?.position ?: data.originalPosition
        val nextData = data.copy(
            originalPosition = originalPosition,
            stability = next.memory?.stability,
            difficulty = next.memory?.difficulty,
            desiredRetention = preset.desiredRetention,
            decay = preset.decay,
            lastReviewSecs = timing.nowSecs,
        )
        val reps = scheduling.reps + 1
        val nextScheduling = when (next) {
            is CardState.New -> scheduling.copy(type = TYPE_NEW, queue = QUEUE_NEW, due = next.position)
            is CardState.Learning -> learningScheduling(scheduling.copy(type = TYPE_LEARN, left = next.remainingSteps), next.intervalKind, timing, fuzzSeed)
            is CardState.Review -> scheduling.copy(
                type = TYPE_REVIEW,
                queue = QUEUE_REVIEW,
                interval = next.scheduledDays,
                due = (timing.today + next.scheduledDays).toLong(),
                easeFactor = (next.easeFactor * 1000f).roundToInt(),
                lapses = next.lapses,
                left = 0,
            )
            is CardState.Relearning -> learningScheduling(
                scheduling.copy(
                    type = TYPE_RELEARN,
                    interval = next.review.scheduledDays,
                    left = next.learning.remainingSteps,
                    lapses = next.review.lapses,
                    easeFactor = (next.review.easeFactor * 1000f).roundToInt(),
                ),
                next.intervalKind,
                timing,
                fuzzSeed,
            )
        }
        val queue = if (next.leeched && preset.suspendsLeeches) QUEUE_SUSPENDED else nextScheduling.queue
        val dataForNext = if (next is CardState.New) nextData.copy(stability = null, difficulty = null) else nextData
        return card.copy(scheduling = nextScheduling.copy(queue = queue, reps = reps, data = dataForNext.encode()))
    }

    private fun learningScheduling(scheduling: CardScheduling, interval: IntervalKind, timing: SchedulerTiming, fuzzSeed: Long): CardScheduling =
        when (val kind = interval.asDaysIfPastRollover(timing.secondsUntilRollover)) {
            is IntervalKind.InSeconds -> scheduling.copy(queue = QUEUE_LEARN, due = timing.nowSecs + learningIntervalWithFuzz(kind.seconds, fuzzSeed))
            is IntervalKind.InDays -> scheduling.copy(queue = QUEUE_DAY_LEARN, due = (timing.today + kind.days).toLong())
        }

    private fun learningIntervalWithFuzz(seconds: Int, seed: Long): Int {
        if (!fuzzEnabled) return seconds
        val upperExclusive = seconds + minOf(seconds * 0.25f, 300f).toInt()
        if (seconds >= upperExclusive) return seconds
        return seconds + randomSource.nextInt(seed, upperExclusive - seconds).value
    }

    private fun fuzzFactor(seed: Long): Float = randomSource.nextInt(seed, FUZZ_RESOLUTION).value / FUZZ_RESOLUTION.toFloat()

    private fun difficultyShifted(memory: MemoryState): Float = (memory.difficulty - 1f) / 9f + 0.1f

    companion object {
        const val TYPE_NEW = 0
        const val TYPE_LEARN = 1
        const val TYPE_REVIEW = 2
        const val TYPE_RELEARN = 3
        const val QUEUE_SUSPENDED = -1
        const val QUEUE_NEW = 0
        const val QUEUE_LEARN = 1
        const val QUEUE_REVIEW = 2
        const val QUEUE_DAY_LEARN = 3
        private const val FUZZ_RESOLUTION = 1_000_000
    }
}

fun CardData.memoryState(): MemoryState? {
    val stability = stability ?: return null
    val difficulty = difficulty ?: return null
    return MemoryState(stability, difficulty)
}

object MemoryFromHistory {
    fun memoryState(fsrs: Fsrs, history: List<RevlogEntry>, nextDayAtSecs: Long, sm2Retention: Float): MemoryState? {
        val reviews = reviewsForFsrs(history, nextDayAtSecs) ?: return null
        if (reviews.complete) return fsrs.memoryStateAfter(reviews.reviews)
        val first = reviews.entries.first()
        val easeFactor = (if (first.easeFactor == 0) 2500 else first.easeFactor) / 1000f
        var starting = fsrs.memoryStateFromSm2(easeFactor, first.interval.coerceAtLeast(1).toFloat(), sm2Retention)
        if (easeFactor <= 1.1f) starting = starting.copy(difficulty = (easeFactor - 0.1f) * 9f + 1f)
        return fsrs.memoryStateAfter(reviews.reviews.drop(1), starting) ?: starting
    }

    fun lastReviewSecs(history: List<RevlogEntry>): Long? =
        history.filter { it.affectsScheduling() }.maxOfOrNull { it.id }?.let { it / 1000 }

    private class Reviews(val entries: List<RevlogEntry>, val reviews: List<FsrsReview>, val complete: Boolean)

    private fun reviewsForFsrs(history: List<RevlogEntry>, nextDayAtSecs: Long): Reviews? {
        val entries = history.sortedBy { it.id }
        var firstOfLastLearning: Int? = null
        var firstUserGrade: Int? = null
        var complete = false
        for (index in entries.indices.reversed()) {
            val entry = entries[index]
            if (entry.isCramming()) continue
            val userGraded = entry.ease > 0
            val interday = entry.interval >= 1 || entry.interval <= -SECONDS_PER_DAY
            if (userGraded && interday) firstUserGrade = index
            if (userGraded && entry.kind == CardState.REVLOG_LEARNING) {
                firstOfLastLearning = index
                complete = true
            } else if (entry.isReset()) {
                if (firstOfLastLearning != null) {
                    complete = true
                    break
                } else if (firstUserGrade != null) {
                    complete = false
                    break
                } else {
                    return null
                }
            } else if (firstOfLastLearning != null) {
                break
            }
        }
        val start = firstOfLastLearning ?: firstUserGrade ?: return null
        val kept = entries.drop(start).filter { it.affectsScheduling() }
        if (kept.isEmpty()) return null
        val reviews = kept.mapIndexed { index, entry ->
            val delta = if (index == 0) 0 else daysElapsed(kept[index - 1], nextDayAtSecs) - daysElapsed(entry, nextDayAtSecs)
            FsrsReview(Rating.fromNumber(entry.ease.coerceIn(1, 4)), delta)
        }
        return Reviews(kept, reviews, complete)
    }

    private fun daysElapsed(entry: RevlogEntry, nextDayAtSecs: Long): Int = ((nextDayAtSecs - entry.id / 1000).coerceAtLeast(0) / SECONDS_PER_DAY).toInt()

    private fun RevlogEntry.isCramming(): Boolean = kind == CardState.REVLOG_FILTERED && easeFactor == 0

    private fun RevlogEntry.isReset(): Boolean = kind == REVLOG_MANUAL && easeFactor == 0

    private fun RevlogEntry.affectsScheduling(): Boolean = ease > 0 && !isCramming()

    private const val REVLOG_MANUAL = 4
}
