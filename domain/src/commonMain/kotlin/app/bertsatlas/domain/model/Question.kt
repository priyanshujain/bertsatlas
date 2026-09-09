package app.bertsatlas.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface Question {
    val id: String
    val prompt: String
    val countryIds: List<String>
    val skill: Skill
    val topic: QuizTopic
    val difficulty: DifficultyTier
    val explanation: String?
    val supportedHints: Set<HintType>
    val hintText: Map<HintType, String>

    @Serializable
    @SerialName("multiple_choice")
    data class MultipleChoice(
        override val id: String,
        override val prompt: String,
        override val countryIds: List<String>,
        override val skill: Skill,
        override val topic: QuizTopic,
        override val difficulty: DifficultyTier,
        override val explanation: String? = null,
        override val supportedHints: Set<HintType>,
        override val hintText: Map<HintType, String> = emptyMap(),
        val choices: List<Choice>,
        val correctChoiceId: String,
        val promptAssetId: String? = null,
    ) : Question

    @Serializable
    @SerialName("letter_bank")
    data class LetterBank(
        override val id: String,
        override val prompt: String,
        override val countryIds: List<String>,
        override val skill: Skill,
        override val topic: QuizTopic,
        override val difficulty: DifficultyTier,
        override val explanation: String? = null,
        override val supportedHints: Set<HintType>,
        override val hintText: Map<HintType, String> = emptyMap(),
        val displayAnswer: String,
        val acceptedAliases: List<String> = emptyList(),
        val promptAssetId: String,
        val decoyLetters: List<Char>,
    ) : Question
}

@Serializable
data class Choice(
    val id: String,
    val label: String,
    val assetId: String? = null,
)

@Serializable
enum class HintType {
    RevealCorrectLetter,
    RemoveIncorrectOptions,
    RevealContinent,
    FunnyFactualClue,
}

@Serializable
data class HintPresentation(
    val type: HintType,
    val message: String? = null,
    val revealedTileId: String? = null,
    val removedChoiceIds: Set<String> = emptySet(),
    val removedTileIds: Set<String> = emptySet(),
)

@Serializable
data class LetterTile(
    val id: String,
    val character: Char,
    val isDecoy: Boolean,
)

@Serializable
data class LetterPlacement(
    val slotIndex: Int,
    val tileId: String,
)

@Serializable
sealed interface SubmittedAnswer {
    @Serializable
    @SerialName("choice")
    data class ChoiceAnswer(val choiceId: String) : SubmittedAnswer

    @Serializable
    @SerialName("letters")
    data class LetterAnswer(val tileIds: List<String>) : SubmittedAnswer
}
