package app.bertsatlas.domain

import app.bertsatlas.domain.model.DifficultyTier
import app.bertsatlas.domain.model.Mastery
import app.bertsatlas.domain.model.MasteryLevel
import app.bertsatlas.domain.model.Question
import app.bertsatlas.domain.model.PlayScope
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
        scope: PlayScope,
        tier: DifficultyTier,
        mastery: List<Mastery>,
        currentEpochDay: Int,
        recentQuestionIds: List<String>,
        questionIdsDueForRevisit: Set<String>,
        randomState: Long,
    ): SelectedQuestion {
        val scopeQuestions = questions.filter { it.packId in scope.packIds }
        require(scopeQuestions.isNotEmpty()) { "No questions are available for ${scope.progressKey}" }

        val dueForRevisit = scopeQuestions.filter { it.id in questionIdsDueForRevisit }
        if (dueForRevisit.isNotEmpty()) {
            val revisitRoll = randomSource.nextInt(randomState, REVISIT_CHANCE_ONE_IN)
            if (revisitRoll.value == 0) {
                val pick = randomSource.nextInt(revisitRoll.nextState, dueForRevisit.size)
                return SelectedQuestion(dueForRevisit[pick.value], pick.nextState)
            }
            return selectByWeight(scopeQuestions, questions, scope, tier, mastery, currentEpochDay, recentQuestionIds, revisitRoll.nextState)
        }
        return selectByWeight(scopeQuestions, questions, scope, tier, mastery, currentEpochDay, recentQuestionIds, randomState)
    }

    private fun selectByWeight(
        scopeQuestions: List<Question>,
        questions: List<Question>,
        scope: PlayScope,
        tier: DifficultyTier,
        mastery: List<Mastery>,
        currentEpochDay: Int,
        recentQuestionIds: List<String>,
        randomState: Long,
    ): SelectedQuestion {
        val priorQuestionIds = recentQuestionIds.takeLast(RECENT_QUESTION_WINDOW).toSet()
        val priorQuestions = questions.filter { it.id in priorQuestionIds }
        val immediateQuestion = questions.firstOrNull { it.id == recentQuestionIds.lastOrNull() }
        val withoutRecentConcepts = scopeQuestions.filter { candidate ->
            candidate.id !in priorQuestionIds && priorQuestions.none { recent -> candidate.repeatsConceptFrom(recent) }
        }
        val withoutImmediateConcept = scopeQuestions.filter { candidate ->
            candidate.id != immediateQuestion?.id &&
                (immediateQuestion == null || !candidate.repeatsConceptFrom(immediateQuestion))
        }
        val withoutPriorQuestions = scopeQuestions.filter { it.id !in priorQuestionIds }
        val withoutImmediateRepeat = scopeQuestions.filter { it.id != immediateQuestion?.id }
        val candidates = when {
            withoutRecentConcepts.isNotEmpty() -> withoutRecentConcepts
            withoutImmediateConcept.isNotEmpty() -> withoutImmediateConcept
            withoutPriorQuestions.isNotEmpty() -> withoutPriorQuestions
            withoutImmediateRepeat.isNotEmpty() -> withoutImmediateRepeat
            else -> scopeQuestions
        }
        val weighted = candidates.flatMap { question ->
            List(selectionWeight(question, scope, tier, mastery, currentEpochDay)) { question }
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
        Skill.CardRecall -> SelectionFamily.Card
    }

    private fun selectionWeight(
        question: Question,
        scope: PlayScope,
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
        val scopeWeight = when (scope) {
            is PlayScope.Pack -> when (question.skill) {
                Skill.FlagRecognition -> 8
                Skill.CountryToFlag -> 6
                else -> 1
            }
            is PlayScope.Mix -> when (question.skill) {
                Skill.FlagRecognition,
                Skill.CountryToFlag,
                -> 1
                Skill.CapitalRecall,
                Skill.CapitalRecognition,
                -> 4
                Skill.MixedGeography,
                Skill.FlagSymbolism,
                -> 6
                Skill.CardRecall -> 4
            }
        }
        return (difficultyWeight + reviewWeight) * scopeWeight
    }
}

private const val RECENT_QUESTION_WINDOW = 5
private const val REVISIT_CHANCE_ONE_IN = 3

private enum class SelectionFamily {
    Flag,
    Capital,
    Mixed,
    Card,
}
