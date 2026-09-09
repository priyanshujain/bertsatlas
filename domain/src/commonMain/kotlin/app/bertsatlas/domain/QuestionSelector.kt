package app.bertsatlas.domain

import app.bertsatlas.domain.model.DifficultyTier
import app.bertsatlas.domain.model.Mastery
import app.bertsatlas.domain.model.MasteryLevel
import app.bertsatlas.domain.model.Question
import app.bertsatlas.domain.model.QuizTopic
import app.bertsatlas.domain.model.Skill

data class SelectedQuestion(
    val question: Question,
    val randomState: Long,
)

class QuestionSelector(
    private val randomSource: RandomSource,
) {
    fun select(
        questions: List<Question>,
        topic: QuizTopic,
        tier: DifficultyTier,
        mastery: List<Mastery>,
        currentEpochDay: Int,
        recentQuestionIds: List<String>,
        randomState: Long,
    ): SelectedQuestion {
        val topicQuestions = questions.filter { matchesTopic(it, topic) }
        require(topicQuestions.isNotEmpty()) { "No questions are available for $topic" }

        val priorQuestionIds = recentQuestionIds.takeLast(RECENT_QUESTION_WINDOW).toSet()
        val priorQuestions = questions.filter { it.id in priorQuestionIds }
        val immediateQuestion = questions.firstOrNull { it.id == recentQuestionIds.lastOrNull() }
        val withoutRecentConcepts = topicQuestions.filter { candidate ->
            candidate.id !in priorQuestionIds && priorQuestions.none { recent -> candidate.repeatsConceptFrom(recent) }
        }
        val withoutImmediateConcept = topicQuestions.filter { candidate ->
            candidate.id != immediateQuestion?.id &&
                (immediateQuestion == null || !candidate.repeatsConceptFrom(immediateQuestion))
        }
        val withoutPriorQuestions = topicQuestions.filter { it.id !in priorQuestionIds }
        val withoutImmediateRepeat = topicQuestions.filter { it.id != immediateQuestion?.id }
        val candidates = when {
            withoutRecentConcepts.isNotEmpty() -> withoutRecentConcepts
            withoutImmediateConcept.isNotEmpty() -> withoutImmediateConcept
            withoutPriorQuestions.isNotEmpty() -> withoutPriorQuestions
            withoutImmediateRepeat.isNotEmpty() -> withoutImmediateRepeat
            else -> topicQuestions
        }
        val weighted = candidates.flatMap { question ->
            List(selectionWeight(question, topic, tier, mastery, currentEpochDay)) { question }
        }
        val random = randomSource.nextInt(randomState, weighted.size)
        return SelectedQuestion(weighted[random.value], random.nextState)
    }

    private fun Question.repeatsConceptFrom(other: Question): Boolean {
        if (countryIds.none { it in other.countryIds }) return false
        val family = skill.selectionFamily()
        return family != SelectionFamily.Mixed && family == other.skill.selectionFamily()
    }

    private fun Skill.selectionFamily(): SelectionFamily = when (this) {
        Skill.FlagRecognition,
        Skill.CountryToFlag,
        -> SelectionFamily.Flag
        Skill.CapitalRecall,
        Skill.CapitalRecognition,
        -> SelectionFamily.Capital
        Skill.MixedGeography,
        Skill.FlagSymbolism,
        -> SelectionFamily.Mixed
    }

    private fun matchesTopic(question: Question, topic: QuizTopic): Boolean = when (topic) {
        QuizTopic.MostlyFlags -> true
        QuizTopic.Capitals -> question.skill == Skill.CapitalRecall ||
            question.skill == Skill.CapitalRecognition
        QuizTopic.MixedGeography -> true
    }

    private fun selectionWeight(
        question: Question,
        topic: QuizTopic,
        tier: DifficultyTier,
        mastery: List<Mastery>,
        currentEpochDay: Int,
    ): Int {
        val difficultyDistance = kotlin.math.abs(question.difficulty.ordinal - tier.ordinal)
        val difficultyWeight = when (difficultyDistance) {
            0 -> 4
            1 -> 2
            else -> 1
        }
        val records = mastery.filter { it.skill == question.skill && it.countryId in question.countryIds }
        val reviewWeight = when {
            records.any { it.nextReviewEpochDay != null && it.nextReviewEpochDay <= currentEpochDay } -> 8
            records.any { it.level == MasteryLevel.Learning } -> 5
            records.isEmpty() -> 4
            records.all { it.level == MasteryLevel.Mastered } -> 1
            else -> 2
        }
        val topicWeight = when (topic) {
            QuizTopic.MostlyFlags -> when (question.skill) {
                Skill.FlagRecognition -> 8
                Skill.CountryToFlag -> 6
                Skill.CapitalRecall,
                Skill.CapitalRecognition,
                -> 1
                Skill.MixedGeography,
                Skill.FlagSymbolism,
                -> 2
            }
            QuizTopic.Capitals -> 1
            QuizTopic.MixedGeography -> when (question.skill) {
                Skill.FlagRecognition,
                Skill.CountryToFlag,
                -> 1
                Skill.CapitalRecall,
                Skill.CapitalRecognition,
                -> 4
                Skill.MixedGeography,
                Skill.FlagSymbolism,
                -> 6
            }
        }
        return (difficultyWeight + reviewWeight) * topicWeight
    }
}

private const val RECENT_QUESTION_WINDOW = 5

private enum class SelectionFamily {
    Flag,
    Capital,
    Mixed,
}
