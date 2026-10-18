package app.bertopia.domain

object Scoring {
    fun pointsForCorrectAnswer(remainingMillis: Long, responseWindowMillis: Long): Int {
        require(responseWindowMillis > 0)
        if (remainingMillis <= 0) return 50
        val speedPoints = (50L * remainingMillis / responseWindowMillis).toInt()
        return (50 + speedPoints).coerceAtMost(100)
    }

    fun hintCost(score: Int): Int = score.coerceAtLeast(0) / 2

    fun deductHint(score: Int): Int = score.coerceAtLeast(0) - hintCost(score)
}
