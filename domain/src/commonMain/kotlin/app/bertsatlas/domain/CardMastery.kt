package app.bertsatlas.domain

import app.bertsatlas.anki.model.Card
import app.bertsatlas.anki.model.CardData
import app.bertsatlas.anki.model.RevlogEntry
import app.bertsatlas.domain.fsrs.CardScheduler
import app.bertsatlas.domain.model.Mastery
import app.bertsatlas.domain.model.MasteryLevel
import app.bertsatlas.domain.model.MasteryOutcome
import app.bertsatlas.domain.model.Skill

object CardMastery {
    const val FAMILIAR_STABILITY_DAYS = 7f
    const val MASTERED_STABILITY_DAYS = 21f
    private const val RECENT_OUTCOMES = 3
    private const val EASE_AGAIN = 1
    private const val EASE_HARD = 2

    fun level(card: Card, revlog: List<RevlogEntry>): MasteryLevel {
        val graded = revlog.filter { it.ease > 0 }
        if (graded.isEmpty()) return MasteryLevel.New
        if (graded.last().ease == EASE_AGAIN) return MasteryLevel.Learning
        val stability = CardData.decode(card.scheduling.data).stability ?: return MasteryLevel.Learning
        return when {
            stability >= MASTERED_STABILITY_DAYS -> MasteryLevel.Mastered
            stability >= FAMILIAR_STABILITY_DAYS -> MasteryLevel.Familiar
            else -> MasteryLevel.Learning
        }
    }

    fun forCard(
        itemId: String,
        skills: List<Skill>,
        card: Card,
        revlog: List<RevlogEntry>,
        collectionToday: Int,
        todayEpochDay: Int,
        epochDayOfMillis: (Long) -> Int,
    ): List<Mastery> {
        val graded = revlog.filter { it.ease > 0 }.sortedBy { it.id }
        if (graded.isEmpty()) return emptyList()
        val level = level(card, graded)
        val recent = graded.takeLast(RECENT_OUTCOMES).map { entry ->
            MasteryOutcome(
                correct = entry.ease > EASE_AGAIN,
                hinted = entry.ease == EASE_HARD,
                runId = "",
                localEpochDay = epochDayOfMillis(entry.id),
            )
        }
        val unhintedCorrect = graded.filter { it.ease > EASE_HARD }
        return skills.map { skill ->
            Mastery(
                countryId = itemId,
                skill = skill,
                level = level,
                attempts = graded.size,
                correctAnswers = graded.count { it.ease > EASE_AGAIN },
                unhintedCorrectAnswers = unhintedCorrect.size,
                unhintedCorrectEpochDays = unhintedCorrect.mapTo(mutableSetOf()) { epochDayOfMillis(it.id) },
                recentOutcomes = recent,
                nextReviewEpochDay = nextReviewEpochDay(card, collectionToday, todayEpochDay),
            )
        }
    }

    private fun nextReviewEpochDay(card: Card, collectionToday: Int, todayEpochDay: Int): Int? = when (card.scheduling.queue) {
        CardScheduler.QUEUE_REVIEW, CardScheduler.QUEUE_DAY_LEARN -> todayEpochDay + (card.scheduling.due - collectionToday).toInt()
        CardScheduler.QUEUE_LEARN -> todayEpochDay
        else -> null
    }
}
