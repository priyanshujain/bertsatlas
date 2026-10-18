package app.bertopia.domain

import app.bertopia.domain.model.AdaptiveState
import app.bertopia.domain.model.DifficultyTier

object AdaptiveDifficulty {
    fun afterAnswer(current: AdaptiveState, correct: Boolean, hinted: Boolean): AdaptiveState {
        if (correct && !hinted) {
            val correctCount = current.consecutiveUnhintedCorrect + 1
            if (correctCount >= 3) {
                return current.copy(
                    tier = current.tier.raise(),
                    consecutiveUnhintedCorrect = 0,
                    consecutiveIncorrect = 0,
                )
            }
            return current.copy(
                consecutiveUnhintedCorrect = correctCount,
                consecutiveIncorrect = 0,
            )
        }
        if (!correct) {
            val incorrectCount = current.consecutiveIncorrect + 1
            if (incorrectCount >= 2) {
                return current.copy(
                    tier = current.tier.lower(),
                    consecutiveUnhintedCorrect = 0,
                    consecutiveIncorrect = 0,
                )
            }
            return current.copy(
                consecutiveUnhintedCorrect = 0,
                consecutiveIncorrect = incorrectCount,
            )
        }
        return current.copy(
            consecutiveUnhintedCorrect = 0,
            consecutiveIncorrect = 0,
        )
    }

    private fun DifficultyTier.raise(): DifficultyTier = when (this) {
        DifficultyTier.Casual -> DifficultyTier.Tricky
        DifficultyTier.Tricky, DifficultyTier.Brutal -> DifficultyTier.Brutal
    }

    private fun DifficultyTier.lower(): DifficultyTier = when (this) {
        DifficultyTier.Brutal -> DifficultyTier.Tricky
        DifficultyTier.Tricky, DifficultyTier.Casual -> DifficultyTier.Casual
    }
}
