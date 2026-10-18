package app.bertopia.domain.fsrs

import app.bertopia.anki.model.Card
import app.bertopia.anki.model.StudiedToday

data class DueCounts(
    val newCards: Int,
    val learning: Int,
    val reviews: Int,
) {
    val total: Int get() = newCards + learning + reviews
}

data class StudyQueue(
    val learning: List<Card>,
    val reviews: List<Card>,
    val newCards: List<Card>,
) {
    val counts: DueCounts get() = DueCounts(newCards.size, learning.size, reviews.size)

    val isEmpty: Boolean get() = learning.isEmpty() && reviews.isEmpty() && newCards.isEmpty()

    fun next(): Card? = learning.firstOrNull() ?: reviews.firstOrNull() ?: newCards.firstOrNull()

    companion object {
        const val DEFAULT_LEARN_AHEAD_SECS = 1_200

        fun build(
            cards: List<Card>,
            preset: StudyPreset,
            studiedToday: StudiedToday,
            timing: SchedulerTiming,
            learnAheadSecs: Int = DEFAULT_LEARN_AHEAD_SECS,
        ): StudyQueue {
            val intradayLearning = cards
                .filter { it.scheduling.queue == CardScheduler.QUEUE_LEARN && it.scheduling.due <= timing.nowSecs + learnAheadSecs }
                .sortedWith(compareBy({ it.scheduling.due }, { it.id }))
            var reviewLimit = (preset.reviewsPerDay - studiedToday.reviews).coerceAtLeast(0)
            val interdayLearning = cards
                .filter { it.scheduling.queue == CardScheduler.QUEUE_DAY_LEARN && it.scheduling.due <= timing.today }
                .sortedWith(compareBy({ it.scheduling.due }, { it.id }))
                .take(reviewLimit)
            reviewLimit -= interdayLearning.size
            val reviews = cards
                .filter { it.scheduling.queue == CardScheduler.QUEUE_REVIEW && it.scheduling.due <= timing.today }
                .sortedWith(compareBy({ it.scheduling.due }, { it.id }))
                .take(reviewLimit)
            reviewLimit -= reviews.size
            val newLimit = minOf((preset.newCardsPerDay - studiedToday.newCards).coerceAtLeast(0), reviewLimit)
            val newCards = cards
                .filter { it.scheduling.queue == CardScheduler.QUEUE_NEW }
                .sortedWith(compareBy({ it.scheduling.due }, { it.ordinal }, { it.id }))
                .take(newLimit)
            return StudyQueue(
                learning = intradayLearning + interdayLearning,
                reviews = reviews,
                newCards = newCards,
            )
        }
    }
}
