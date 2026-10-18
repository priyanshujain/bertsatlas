package app.bertopia.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class RunPhase {
    Answering,
    HintChooser,
    Feedback,
    FunFact,
    Paused,
    Completed,
}

@Serializable
enum class RunStatus {
    Active,
    Completed,
    Abandoned,
}

@Serializable
enum class FeedbackKind {
    Correct,
    Incorrect,
    OvertimeCorrect,
}

@Serializable
data class AnswerFeedback(
    val kind: FeedbackKind,
    val submittedAnswer: String,
    val correctAnswer: String,
    val explanation: String?,
    val pointsAwarded: Int,
)

@Serializable
data class AdaptiveState(
    val tier: DifficultyTier = DifficultyTier.Casual,
    val consecutiveUnhintedCorrect: Int = 0,
    val consecutiveIncorrect: Int = 0,
)

@Serializable
data class ActiveQuestion(
    val question: Question,
    val orderedChoices: List<Choice> = emptyList(),
    val letterTiles: List<LetterTile> = emptyList(),
    val placements: List<LetterPlacement> = emptyList(),
    val questionWindowMillis: Long,
    val questionRemainingMillis: Long,
    val questionElapsedMillis: Long = 0,
    val hint: HintPresentation? = null,
    val usedHints: List<HintPresentation> = emptyList(),
    val hintPointsDeducted: Int = 0,
    val removedChoiceIds: Set<String> = emptySet(),
    val removedTileIds: Set<String> = emptySet(),
    val answerRevealed: Boolean = false,
) {
    val appliedHints: List<HintPresentation>
        get() = if (usedHints.isEmpty()) listOfNotNull(hint) else usedHints

    val usedHintTypes: Set<HintType>
        get() = appliedHints.mapTo(linkedSetOf()) { it.type }
}

@Serializable
data class QuizRunState(
    val runId: String,
    val configuration: QuizConfiguration,
    val seed: Long,
    val randomState: Long,
    val phase: RunPhase,
    val status: RunStatus,
    val score: Int,
    val sessionRemainingMillis: Long?,
    val activeQuestion: ActiveQuestion,
    val answeredQuestionIds: List<String> = emptyList(),
    val recentQuestionIds: List<String> = emptyList(),
    val recentFactIds: List<String> = emptyList(),
    val answeredCountryIds: List<String> = emptyList(),
    val attempts: List<Attempt> = emptyList(),
    val feedback: AnswerFeedback? = null,
    val visibleFact: FactRecord? = null,
    val adaptive: AdaptiveState = AdaptiveState(),
    val currentCorrectStreak: Int = 0,
    val bestCorrectStreak: Int = 0,
    val nextFactAfterAnswerCount: Int,
    val sessionExpired: Boolean = false,
    val runCompletionPending: Boolean = false,
    val runAbandonmentPending: Boolean = false,
    val finalFactPending: Boolean = false,
    val tickingSinceMillis: Long? = null,
    val pausedFromPhase: RunPhase? = null,
)

@Serializable
data class ActiveRunSnapshot(
    val schemaVersion: Int,
    val catalogVersion: String,
    val state: QuizRunState,
)
