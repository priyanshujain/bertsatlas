package app.bertsatlas.domain

import app.bertsatlas.domain.model.Attempt
import app.bertsatlas.domain.model.Continent
import app.bertsatlas.domain.model.Mastery
import app.bertsatlas.domain.model.MasteryLevel
import app.bertsatlas.domain.model.MasteryOutcome
import app.bertsatlas.domain.model.PassportStamp
import app.bertsatlas.domain.model.PassportStampKind
import app.bertsatlas.domain.model.Streak

object MasteryRules {
    fun applyAttempt(current: Mastery?, attempt: Attempt): Mastery {
        require(attempt.countryIds.isNotEmpty()) { "A mastery attempt requires a country" }
        val existing = current ?: Mastery(
            countryId = attempt.countryIds.first(),
            skill = attempt.skill,
        )
        val outcome = MasteryOutcome(
            correct = attempt.correct,
            hinted = attempt.hinted,
            runId = attempt.runId,
            localEpochDay = attempt.localEpochDay,
        )
        val qualifyingRunIds = if (attempt.correct && !attempt.hinted) {
            existing.unhintedCorrectRunIds + attempt.runId
        } else {
            existing.unhintedCorrectRunIds
        }
        val qualifyingDays = if (attempt.correct && !attempt.hinted) {
            existing.unhintedCorrectEpochDays + attempt.localEpochDay
        } else {
            existing.unhintedCorrectEpochDays
        }
        val recent = (existing.recentOutcomes + outcome).takeLast(3)
        val unhintedCorrectAnswers = existing.unhintedCorrectAnswers +
            if (attempt.correct && !attempt.hinted) 1 else 0
        val level = when {
            attempt.hinted -> MasteryLevel.Learning
            unhintedCorrectAnswers >= 4 &&
                qualifyingDays.size >= 3 &&
                recent.none { !it.correct && !it.hinted } -> MasteryLevel.Mastered
            qualifyingRunIds.size >= 2 -> MasteryLevel.Familiar
            else -> MasteryLevel.Learning
        }
        val reviewDelayDays = when {
            !attempt.correct || attempt.hinted -> 1
            level == MasteryLevel.Mastered -> 30
            level == MasteryLevel.Familiar -> 7
            else -> 1
        }
        return existing.copy(
            level = level,
            attempts = existing.attempts + 1,
            correctAnswers = existing.correctAnswers + if (attempt.correct) 1 else 0,
            unhintedCorrectAnswers = unhintedCorrectAnswers,
            unhintedCorrectRunIds = qualifyingRunIds,
            unhintedCorrectEpochDays = qualifyingDays,
            recentOutcomes = recent,
            nextReviewEpochDay = attempt.localEpochDay + reviewDelayDays,
        )
    }

    fun applyAttemptToCountries(
        mastery: List<Mastery>,
        attempt: Attempt,
    ): List<Mastery> {
        val byKey = mastery.associateBy { it.countryId to it.skill }.toMutableMap()
        attempt.countryIds.forEach { countryId ->
            val countryAttempt = attempt.copy(countryIds = listOf(countryId))
            val key = countryId to attempt.skill
            byKey[key] = applyAttempt(byKey[key], countryAttempt)
        }
        return byKey.values.sortedWith(compareBy(Mastery::countryId, { it.skill.ordinal }))
    }
}

object StreakRules {
    fun recordCompletedRun(current: Streak, completedEpochDay: Int): Streak {
        val lastDay = current.lastCompletedEpochDay
        if (lastDay != null && completedEpochDay <= lastDay) return current

        val currentDays = if (lastDay != null && completedEpochDay == lastDay + 1) {
            current.currentDays + 1
        } else {
            1
        }
        return Streak(
            currentDays = currentDays,
            bestDays = maxOf(current.bestDays, currentDays),
            lastCompletedEpochDay = completedEpochDay,
        )
    }
}

data class PassportEvidence(
    val completedRunCount: Int,
    val recognizedFlagContinents: Set<Continent>,
    val totalAnswered: Int,
    val totalCorrect: Int,
    val confusedFlagMastery: List<Mastery>,
    val streak: Streak,
)

object PassportRules {
    fun newlyEarned(
        evidence: PassportEvidence,
        existing: List<PassportStamp>,
        earnedAtEpochMillis: Long,
    ): List<PassportStamp> {
        val alreadyEarned = existing.mapTo(mutableSetOf()) { it.kind }
        val earned = buildList {
            if (evidence.completedRunCount >= 1) add(PassportStampKind.FirstCompletedRun)
            if (evidence.recognizedFlagContinents.containsAll(PLAYED_CONTINENTS)) {
                add(PassportStampKind.FlagsFromEveryContinent)
            }
            if (evidence.totalAnswered >= 50 && evidence.totalCorrect * 100 >= evidence.totalAnswered * 80) {
                add(PassportStampKind.SustainedAccuracy)
            }
            if (evidence.confusedFlagMastery.isNotEmpty() &&
                evidence.confusedFlagMastery.all { it.level == MasteryLevel.Mastered }
            ) {
                add(PassportStampKind.ConfusedFlagsMastered)
            }
            if (evidence.streak.bestDays >= 7) add(PassportStampKind.SevenActiveDays)
        }
        return earned
            .filterNot { it in alreadyEarned }
            .map { PassportStamp(it, earnedAtEpochMillis) }
    }

    private val PLAYED_CONTINENTS = Continent.entries.filterNot { it == Continent.Antarctica }.toSet()
}
