package app.bertsatlas.domain

object Scoring {
    const val HINT_COST = 25

    fun pointsForCorrectAnswer(remainingMillis: Long, responseWindowMillis: Long): Int {
        require(responseWindowMillis > 0)
        if (remainingMillis <= 0) return 50
        val speedPoints = (50L * remainingMillis / responseWindowMillis).toInt()
        return (50 + speedPoints).coerceAtMost(100)
    }

    fun deductHint(score: Int): Int = (score - HINT_COST).coerceAtLeast(0)
}
