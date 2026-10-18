package app.bertsatlas.domain.fsrs

import app.bertsatlas.domain.model.Question

enum class QuizAnswerFormat(val fastAnswerMillis: Long) {
    MultipleChoice(4_000),
    LetterBank(8_000),
    ;

    companion object {
        fun of(question: Question): QuizAnswerFormat? = when (question) {
            is Question.MultipleChoice -> MultipleChoice
            is Question.LetterBank -> LetterBank
            is Question.FlipAndGrade -> null
        }
    }
}

data class QuizAnswerOutcome(
    val correct: Boolean,
    val timedOut: Boolean,
    val usedHint: Boolean,
    val answeredInOvertime: Boolean,
    val responseMillis: Long,
    val format: QuizAnswerFormat,
)

object QuizGrading {
    fun ratingFor(outcome: QuizAnswerOutcome): Rating = when {
        !outcome.correct || outcome.timedOut -> Rating.Again
        outcome.usedHint || outcome.answeredInOvertime -> Rating.Hard
        outcome.responseMillis <= outcome.format.fastAnswerMillis -> Rating.Easy
        else -> Rating.Good
    }
}
