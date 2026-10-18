package app.bertsatlas.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Attempt(
    val id: String,
    val runId: String,
    val questionId: String,
    val countryIds: List<String>,
    val skill: Skill,
    val correct: Boolean,
    val hinted: Boolean,
    val overtime: Boolean,
    val submittedAnswer: String,
    val correctAnswer: String,
    val responseMillis: Long,
    val pointsAwarded: Int,
    val answeredAtEpochMillis: Long,
    val localEpochDay: Int,
    val hintType: HintType? = null,
    val hintPointsDeducted: Int = 0,
    val questionWindowMillis: Long = 0,
    val questionRemainingMillis: Long = 0,
    val hintTypes: Set<HintType> = emptySet(),
    val packId: String = "",
)

@Serializable
enum class MasteryLevel {
    New,
    Learning,
    Familiar,
    Mastered,
}

@Serializable
data class MasteryOutcome(
    val correct: Boolean,
    val hinted: Boolean,
    val runId: String,
    val localEpochDay: Int,
)

@Serializable
data class Mastery(
    val countryId: String,
    val skill: Skill,
    val level: MasteryLevel = MasteryLevel.New,
    val attempts: Int = 0,
    val correctAnswers: Int = 0,
    val unhintedCorrectAnswers: Int = 0,
    val unhintedCorrectRunIds: Set<String> = emptySet(),
    val unhintedCorrectEpochDays: Set<Int> = emptySet(),
    val recentOutcomes: List<MasteryOutcome> = emptyList(),
    val nextReviewEpochDay: Int? = null,
)

@Serializable
data class Streak(
    val currentDays: Int = 0,
    val bestDays: Int = 0,
    val lastCompletedEpochDay: Int? = null,
) {
    val lastActivityEpochDay: Int?
        get() = lastCompletedEpochDay
}

@Serializable
enum class PassportStampKind {
    FirstCompletedRun,
    FlagsFromEveryContinent,
    SustainedAccuracy,
    ConfusedFlagsMastered,
    SevenActiveDays,
    PackMastered,
}

@Serializable
data class PassportStamp(
    val kind: PassportStampKind,
    val earnedAtEpochMillis: Long,
)

@Serializable
data class RunResult(
    val runId: String,
    val configuration: QuizConfiguration,
    val score: Int,
    val correctAnswers: Int,
    val answeredQuestions: Int,
    val averageResponseMillis: Long,
    val bestCorrectStreak: Int,
    val improvedMasteryItems: Int,
    val revisitCountryIds: List<String>,
    val completedAtEpochMillis: Long,
    val status: RunStatus = RunStatus.Completed,
) {
    val accuracyPercent: Int
        get() = if (answeredQuestions == 0) 0 else (correctAnswers * 100) / answeredQuestions
}

@Serializable
data class ProgressSummary(
    val totalAnswered: Int,
    val totalCorrect: Int,
    val mastery: List<Mastery>,
    val streak: Streak,
    val stamps: List<PassportStamp>,
) {
    val accuracyPercent: Int
        get() = if (totalAnswered == 0) 0 else (totalCorrect * 100) / totalAnswered
}
