package app.bertsatlas.domain

import app.bertsatlas.domain.model.ActiveQuestion
import app.bertsatlas.domain.model.ActiveRunSnapshot
import app.bertsatlas.domain.model.AdaptiveState
import app.bertsatlas.domain.model.AnswerFeedback
import app.bertsatlas.domain.model.Attempt
import app.bertsatlas.domain.model.Catalog
import app.bertsatlas.domain.model.Choice
import app.bertsatlas.domain.model.FeedbackKind
import app.bertsatlas.domain.model.FactRecord
import app.bertsatlas.domain.model.EditorialReviewStatus
import app.bertsatlas.domain.model.HintPresentation
import app.bertsatlas.domain.model.HintType
import app.bertsatlas.domain.model.LetterPlacement
import app.bertsatlas.domain.model.LetterTile
import app.bertsatlas.domain.model.Mastery
import app.bertsatlas.domain.model.Question
import app.bertsatlas.domain.model.QuizConfiguration
import app.bertsatlas.domain.model.QuizDuration
import app.bertsatlas.domain.model.QuizRunState
import app.bertsatlas.domain.model.RunPhase
import app.bertsatlas.domain.model.RunResult
import app.bertsatlas.domain.model.RunStatus
import app.bertsatlas.domain.model.SubmittedAnswer

class QuizEngine(
    private val catalog: Catalog,
    private val monotonicClock: MonotonicClock,
    private val wallClock: WallClock,
    private val localDateProvider: LocalDateProvider,
    private val randomSource: RandomSource = SeededRandomSource,
    private val allowUnapprovedContent: Boolean = false,
) {
    private val questionSelector = QuestionSelector(randomSource)

    fun startRun(
        runId: String,
        configuration: QuizConfiguration,
        seed: Long,
        mastery: List<Mastery>,
    ): QuizRunState {
        require(runId.isNotBlank())
        val usableSeed = if (seed == 0L) DEFAULT_SEED else seed
        val selection = questionSelector.select(
            questions = catalog.questions,
            topic = configuration.topic,
            tier = AdaptiveState().tier,
            mastery = mastery,
            currentEpochDay = localDateProvider.currentEpochDay(),
            recentQuestionIds = emptyList(),
            randomState = usableSeed,
        )
        val prepared = prepareQuestion(selection.question, selection.randomState)
        val factGap = nextFactGap(prepared.randomState)
        return QuizRunState(
            runId = runId,
            configuration = configuration,
            seed = usableSeed,
            randomState = factGap.randomState,
            phase = RunPhase.Answering,
            status = RunStatus.Active,
            score = 0,
            sessionRemainingMillis = configuration.duration.activeDurationMillis,
            activeQuestion = prepared.activeQuestion,
            nextFactAfterAnswerCount = factGap.gap,
            tickingSinceMillis = monotonicClock.nowMillis(),
        )
    }

    fun advanceClocks(state: QuizRunState): QuizRunState {
        if (state.phase != RunPhase.Answering || state.tickingSinceMillis == null) return state
        val now = monotonicClock.nowMillis()
        val elapsed = (now - state.tickingSinceMillis).coerceAtLeast(0)
        val sessionRemaining = state.sessionRemainingMillis?.let { (it - elapsed).coerceAtLeast(0) }
        val sessionExpired = state.sessionExpired || sessionRemaining == 0L
        return state.copy(
            activeQuestion = state.activeQuestion.copy(
                questionRemainingMillis = (state.activeQuestion.questionRemainingMillis - elapsed).coerceAtLeast(0),
                questionElapsedMillis = state.activeQuestion.questionElapsedMillis + elapsed,
            ),
            sessionRemainingMillis = sessionRemaining,
            sessionExpired = sessionExpired,
            runCompletionPending = state.runCompletionPending || sessionExpired,
            tickingSinceMillis = now,
        )
    }

    fun snapshotActiveRun(state: QuizRunState): ActiveRunSnapshot {
        requireActive(state)
        return ActiveRunSnapshot(
            schemaVersion = ACTIVE_RUN_SNAPSHOT_SCHEMA_VERSION,
            catalogVersion = catalog.contentVersion,
            state = freezeClocks(state),
        )
    }

    fun restoreRun(snapshot: ActiveRunSnapshot): QuizRunState {
        require(snapshot.schemaVersion == ACTIVE_RUN_SNAPSHOT_SCHEMA_VERSION) {
            "Unsupported active-run snapshot schema"
        }
        require(snapshot.state.status == RunStatus.Active) { "The snapshot does not contain an active run" }
        val state = snapshot.state
        require(state.phase != RunPhase.Completed) { "An active snapshot cannot be completed" }
        require(state.score >= 0) { "A snapshot cannot contain a negative score" }
        require(state.activeQuestion.questionRemainingMillis >= 0) { "A snapshot has an invalid question timer" }
        require(state.sessionRemainingMillis == null || state.sessionRemainingMillis >= 0) {
            "A snapshot has an invalid session timer"
        }
        val completionPending = state.runCompletionPending ||
            state.sessionExpired ||
            (state.configuration.duration != QuizDuration.Unlimited && state.sessionRemainingMillis == 0L)
        if (state.phase == RunPhase.Paused) {
            require(state.pausedFromPhase in PAUSABLE_PHASES) { "A paused snapshot has no resumable phase" }
            return state.copy(
                runCompletionPending = completionPending,
                tickingSinceMillis = null,
            )
        }
        return state.copy(
            phase = RunPhase.Paused,
            pausedFromPhase = state.phase,
            runCompletionPending = completionPending,
            tickingSinceMillis = null,
        )
    }

    fun answerQuestion(state: QuizRunState, answer: SubmittedAnswer): QuizRunState {
        requireAnswering(state)
        val timed = advanceClocks(state)
        val evaluation = evaluateAnswer(timed.activeQuestion, answer)
        val points = if (evaluation.correct) {
            Scoring.pointsForCorrectAnswer(
                timed.activeQuestion.questionRemainingMillis,
                timed.activeQuestion.questionWindowMillis,
            )
        } else {
            0
        }
        val hinted = timed.activeQuestion.appliedHints.isNotEmpty()
        val overtime = evaluation.correct && timed.activeQuestion.questionRemainingMillis == 0L
        val attempt = Attempt(
            id = "${timed.runId}:${timed.attempts.size + 1}",
            runId = timed.runId,
            questionId = timed.activeQuestion.question.id,
            countryIds = timed.activeQuestion.question.countryIds,
            skill = timed.activeQuestion.question.skill,
            correct = evaluation.correct,
            hinted = hinted,
            overtime = overtime,
            submittedAnswer = evaluation.submittedText,
            correctAnswer = correctAnswer(timed.activeQuestion.question),
            responseMillis = timed.activeQuestion.questionElapsedMillis,
            pointsAwarded = points,
            answeredAtEpochMillis = wallClock.nowEpochMillis(),
            localEpochDay = localDateProvider.currentEpochDay(),
            hintType = timed.activeQuestion.appliedHints.lastOrNull()?.type,
            hintPointsDeducted = timed.activeQuestion.hintPointsDeducted,
            questionWindowMillis = timed.activeQuestion.questionWindowMillis,
            questionRemainingMillis = timed.activeQuestion.questionRemainingMillis,
            hintTypes = timed.activeQuestion.usedHintTypes,
        )
        val correctStreak = if (evaluation.correct) timed.currentCorrectStreak + 1 else 0
        return timed.copy(
            phase = RunPhase.Feedback,
            score = timed.score + points,
            answeredQuestionIds = timed.answeredQuestionIds + timed.activeQuestion.question.id,
            recentQuestionIds = (timed.recentQuestionIds + timed.activeQuestion.question.id).takeLast(10),
            answeredCountryIds = (timed.answeredCountryIds + timed.activeQuestion.question.countryIds).takeLast(10),
            attempts = timed.attempts + attempt,
            feedback = AnswerFeedback(
                kind = when {
                    !evaluation.correct -> FeedbackKind.Incorrect
                    overtime -> FeedbackKind.OvertimeCorrect
                    else -> FeedbackKind.Correct
                },
                submittedAnswer = evaluation.submittedText,
                correctAnswer = correctAnswer(timed.activeQuestion.question),
                explanation = timed.activeQuestion.question.explanation,
                pointsAwarded = points,
            ),
            adaptive = AdaptiveDifficulty.afterAnswer(timed.adaptive, evaluation.correct, hinted),
            currentCorrectStreak = correctStreak,
            bestCorrectStreak = maxOf(timed.bestCorrectStreak, correctStreak),
            tickingSinceMillis = null,
        )
    }

    fun openHintChooser(state: QuizRunState): QuizRunState {
        requireAnswering(state)
        require(state.activeQuestion.question.supportedHints.any { it !in state.activeQuestion.usedHintTypes }) {
            "Every available hint has already been used for this question"
        }
        return freezeClocks(state).copy(phase = RunPhase.HintChooser)
    }

    fun chooseHint(state: QuizRunState, type: HintType): QuizRunState {
        requireActive(state)
        require(state.phase == RunPhase.HintChooser) { "Hints can only be chosen from the hint chooser" }
        require(type in state.activeQuestion.question.supportedHints) { "This question does not support $type" }
        require(type !in state.activeQuestion.usedHintTypes) { "$type has already been used for this question" }

        val scoreAfterHint = Scoring.deductHint(state.score)
        val appliedQuestion = applyHint(state.activeQuestion, type)
        val chosenHint = requireNotNull(appliedQuestion.hint)
        val hintedQuestion = appliedQuestion.copy(
            usedHints = state.activeQuestion.appliedHints + chosenHint,
            hintPointsDeducted = state.activeQuestion.hintPointsDeducted + state.score - scoreAfterHint,
        )
        return state.copy(
            phase = RunPhase.Answering,
            score = scoreAfterHint,
            activeQuestion = hintedQuestion,
            tickingSinceMillis = monotonicClock.nowMillis(),
        )
    }

    fun dismissFeedback(state: QuizRunState, mastery: List<Mastery>): QuizRunState {
        requireActive(state)
        require(state.phase == RunPhase.Feedback) { "Feedback is not visible" }
        if (state.runAbandonmentPending) return abandonRun(state)
        val timedRunComplete = state.runCompletionPending || state.sessionExpired
        if (timedRunComplete || shouldShowScheduledFact(state)) {
            return showRelatedFact(state, finalFact = timedRunComplete, mastery = mastery)
        }
        return moveToNextQuestion(state, mastery)
    }

    fun continueFromFact(state: QuizRunState, mastery: List<Mastery>): QuizRunState {
        requireActive(state)
        require(state.phase == RunPhase.FunFact) { "A fun fact is not visible" }
        if (state.runAbandonmentPending) return abandonRun(state)
        if (state.finalFactPending) {
            return state.copy(
                phase = RunPhase.Completed,
                status = RunStatus.Completed,
                visibleFact = null,
                finalFactPending = false,
                runCompletionPending = false,
                runAbandonmentPending = false,
            )
        }
        return moveToNextQuestion(state.copy(visibleFact = null), mastery)
    }

    fun pauseForInterruption(state: QuizRunState): QuizRunState {
        requireActive(state)
        if (state.phase == RunPhase.Paused) return state
        val frozen = freezeClocks(state)
        return frozen.copy(
            phase = RunPhase.Paused,
            pausedFromPhase = frozen.phase,
            tickingSinceMillis = null,
        )
    }

    fun resumeRun(state: QuizRunState): QuizRunState {
        requireActive(state)
        require(state.phase == RunPhase.Paused) { "Only a paused run can be resumed" }
        val resumedPhase = requireNotNull(state.pausedFromPhase) { "A paused run has no resumable phase" }
        require(resumedPhase in PAUSABLE_PHASES) { "The paused run has an invalid resumable phase" }
        return state.copy(
            phase = resumedPhase,
            pausedFromPhase = null,
            tickingSinceMillis = if (resumedPhase == RunPhase.Answering) monotonicClock.nowMillis() else null,
        )
    }

    fun finishUnlimitedRun(state: QuizRunState, mastery: List<Mastery>): QuizRunState {
        requireActive(state)
        require(state.configuration.duration == QuizDuration.Unlimited) { "Only unlimited runs can be finished manually" }
        val frozen = freezeClocks(state)
        if (frozen.attempts.size < MINIMUM_UNLIMITED_ANSWERS) {
            return when (frozen.phase) {
                RunPhase.Feedback,
                RunPhase.FunFact,
                -> frozen.copy(runAbandonmentPending = true)
                RunPhase.Paused -> when (frozen.pausedFromPhase) {
                    RunPhase.Feedback,
                    RunPhase.FunFact,
                    -> frozen.copy(runAbandonmentPending = true)
                    else -> abandonRun(frozen)
                }
                else -> abandonRun(frozen)
            }
        }
        return when (frozen.phase) {
            RunPhase.Feedback -> frozen.copy(runCompletionPending = true)
            RunPhase.FunFact -> frozen.copy(
                runCompletionPending = true,
                finalFactPending = true,
            )
            RunPhase.Paused -> when (frozen.pausedFromPhase) {
                RunPhase.Feedback -> frozen.copy(runCompletionPending = true)
                RunPhase.FunFact -> frozen.copy(
                    runCompletionPending = true,
                    finalFactPending = true,
                )
                else -> showRelatedFact(frozen, finalFact = true, mastery = mastery)
            }
            RunPhase.Answering,
            RunPhase.HintChooser,
            -> showRelatedFact(frozen, finalFact = true, mastery = mastery)
            RunPhase.Completed -> error("An active run cannot already be completed")
        }
    }

    fun finishExpiredRun(state: QuizRunState): QuizRunState {
        requireActive(state)
        require(state.configuration.duration != QuizDuration.Unlimited) { "Unlimited trivia does not expire" }
        val frozen = freezeClocks(state)
        require(frozen.sessionExpired || frozen.sessionRemainingMillis == 0L) { "Trivia time has not expired" }
        return frozen.copy(
            phase = RunPhase.Completed,
            status = RunStatus.Completed,
            feedback = null,
            visibleFact = null,
            finalFactPending = false,
            runCompletionPending = false,
            runAbandonmentPending = false,
            tickingSinceMillis = null,
        )
    }

    fun abandonRun(state: QuizRunState): QuizRunState {
        requireActive(state)
        return freezeClocks(state).copy(
            phase = RunPhase.Completed,
            status = RunStatus.Abandoned,
            feedback = null,
            visibleFact = null,
            finalFactPending = false,
            runCompletionPending = false,
            runAbandonmentPending = false,
            tickingSinceMillis = null,
        )
    }

    fun placeLetter(state: QuizRunState, tileId: String): QuizRunState {
        requireAnswering(state)
        val timed = advanceClocks(state)
        val active = timed.activeQuestion
        val question = active.question as? Question.LetterBank ?: error("The active question is not a letter bank")
        require(active.letterTiles.any { it.id == tileId }) { "Unknown letter tile" }
        require(tileId !in active.removedTileIds) { "This letter tile has been removed" }
        require(active.placements.none { it.tileId == tileId }) { "This letter tile is already placed" }
        val answerLength = canonicalLetters(question.displayAnswer).length
        val availableSlot = (0 until answerLength).firstOrNull { slot ->
            active.placements.none { it.slotIndex == slot }
        } ?: error("Every answer slot is filled")
        return timed.copy(
            activeQuestion = active.copy(
                placements = (active.placements + LetterPlacement(availableSlot, tileId)).sortedBy { it.slotIndex },
            ),
        )
    }

    fun removeLetter(state: QuizRunState, slotIndex: Int): QuizRunState {
        requireAnswering(state)
        val timed = advanceClocks(state)
        require(timed.activeQuestion.question is Question.LetterBank) { "The active question is not a letter bank" }
        val placement = timed.activeQuestion.placements.firstOrNull { it.slotIndex == slotIndex }
            ?: error("The answer slot is empty")
        require(placement.tileId !in timed.activeQuestion.revealedTileIds()) {
            "A revealed letter cannot be removed"
        }
        return timed.copy(
            activeQuestion = timed.activeQuestion.copy(
                placements = timed.activeQuestion.placements.filterNot { it.slotIndex == slotIndex },
            ),
        )
    }

    fun clearLetters(state: QuizRunState): QuizRunState {
        requireAnswering(state)
        val timed = advanceClocks(state)
        require(timed.activeQuestion.question is Question.LetterBank) { "The active question is not a letter bank" }
        val revealedTileIds = timed.activeQuestion.revealedTileIds()
        return timed.copy(
            activeQuestion = timed.activeQuestion.copy(
                placements = timed.activeQuestion.placements.filter { it.tileId in revealedTileIds },
            ),
        )
    }

    fun submitLetters(state: QuizRunState): QuizRunState {
        requireAnswering(state)
        val question = state.activeQuestion.question as? Question.LetterBank
            ?: error("The active question is not a letter bank")
        val answerLength = canonicalLetters(question.displayAnswer).length
        require(state.activeQuestion.placements.size == answerLength) { "Every answer slot must be filled before submitting" }
        val tileIds = state.activeQuestion.placements.sortedBy { it.slotIndex }.map { it.tileId }
        return answerQuestion(state, SubmittedAnswer.LetterAnswer(tileIds))
    }

    fun runResult(state: QuizRunState, improvedMasteryItems: Int): RunResult {
        require(state.status == RunStatus.Completed) { "Only a completed run has results" }
        return buildRunSummary(state, improvedMasteryItems)
    }

    fun abandonedRunSummary(state: QuizRunState, improvedMasteryItems: Int): RunResult {
        require(state.status == RunStatus.Abandoned) { "Only an abandoned run has an abandoned summary" }
        return buildRunSummary(state, improvedMasteryItems)
    }

    private fun buildRunSummary(state: QuizRunState, improvedMasteryItems: Int): RunResult {
        val correct = state.attempts.count { it.correct }
        val averageResponse = if (state.attempts.isEmpty()) 0 else {
            state.attempts.sumOf { it.responseMillis } / state.attempts.size
        }
        return RunResult(
            runId = state.runId,
            configuration = state.configuration,
            score = state.score,
            correctAnswers = correct,
            answeredQuestions = state.attempts.size,
            averageResponseMillis = averageResponse,
            bestCorrectStreak = state.bestCorrectStreak,
            improvedMasteryItems = improvedMasteryItems,
            revisitCountryIds = state.attempts
                .filter { !it.correct || it.hinted }
                .flatMap { it.countryIds }
                .distinct(),
            completedAtEpochMillis = wallClock.nowEpochMillis(),
            status = state.status,
        )
    }

    private fun moveToNextQuestion(state: QuizRunState, mastery: List<Mastery>): QuizRunState {
        val selection = questionSelector.select(
            questions = catalog.questions,
            topic = state.configuration.topic,
            tier = state.adaptive.tier,
            mastery = mastery,
            currentEpochDay = localDateProvider.currentEpochDay(),
            recentQuestionIds = state.recentQuestionIds,
            randomState = state.randomState,
        )
        val prepared = prepareQuestion(selection.question, selection.randomState)
        return state.copy(
            randomState = prepared.randomState,
            phase = RunPhase.Answering,
            activeQuestion = prepared.activeQuestion,
            feedback = null,
            visibleFact = null,
            tickingSinceMillis = monotonicClock.nowMillis(),
        )
    }

    private fun showRelatedFact(
        state: QuizRunState,
        finalFact: Boolean,
        mastery: List<Mastery>,
    ): QuizRunState {
        val selectedFact = selectFact(state)
        if (selectedFact == null) {
            if (finalFact) {
                return state.copy(
                    phase = RunPhase.Completed,
                    status = RunStatus.Completed,
                    feedback = null,
                    runCompletionPending = false,
                )
            }
            return moveToNextQuestion(state, mastery)
        }
        val nextGap = nextFactGap(selectedFact.randomState)
        return state.copy(
            randomState = nextGap.randomState,
            phase = RunPhase.FunFact,
            feedback = null,
            visibleFact = selectedFact.fact,
            recentFactIds = (state.recentFactIds + selectedFact.fact.id).takeLast(10),
            nextFactAfterAnswerCount = state.attempts.size + nextGap.gap,
            finalFactPending = finalFact,
            tickingSinceMillis = null,
        )
    }

    private fun selectFact(state: QuizRunState): SelectedFact? {
        val approved = catalog.facts.filter {
            it.reviewStatus == EditorialReviewStatus.Approved || allowUnapprovedContent
        }
        val related = approved.filter { fact -> fact.countryIds.any { it in state.answeredCountryIds } }
        val relatedNotRecent = related.filter { it.id !in state.recentFactIds }
        val candidates = when {
            relatedNotRecent.isNotEmpty() -> relatedNotRecent
            related.isNotEmpty() -> related
            else -> return null
        }
        val random = randomSource.nextInt(state.randomState, candidates.size)
        return SelectedFact(candidates[random.value], random.nextState)
    }

    private fun shouldShowScheduledFact(state: QuizRunState): Boolean =
        state.attempts.size >= state.nextFactAfterAnswerCount

    private fun freezeClocks(state: QuizRunState): QuizRunState =
        advanceClocks(state).copy(tickingSinceMillis = null)

    private fun applyHint(active: ActiveQuestion, type: HintType): ActiveQuestion {
        val question = active.question
        return when (type) {
            HintType.RevealCorrectLetter -> revealCorrectLetter(active)
            HintType.RemoveIncorrectOptions -> when (question) {
                is Question.MultipleChoice -> {
                    val removed = active.orderedChoices
                        .filter { it.id != question.correctChoiceId }
                        .take(2)
                        .mapTo(linkedSetOf()) { it.id }
                    active.copy(
                        hint = HintPresentation(type = type, removedChoiceIds = removed),
                        removedChoiceIds = removed,
                    )
                }
                is Question.LetterBank -> {
                    val removed = active.letterTiles
                        .filter { it.isDecoy }
                        .mapTo(linkedSetOf()) { it.id }
                    active.copy(
                        hint = HintPresentation(type = type, removedTileIds = removed),
                        removedTileIds = removed,
                        placements = active.placements.filterNot { it.tileId in removed },
                    )
                }
            }
            HintType.RevealContinent,
            HintType.FunnyFactualClue,
            -> {
                val message = question.hintText[type]
                require(!message.isNullOrBlank()) { "$type requires hint text" }
                active.copy(hint = HintPresentation(type = type, message = message))
            }
        }
    }

    private fun revealCorrectLetter(active: ActiveQuestion): ActiveQuestion {
        val question = active.question as? Question.LetterBank
            ?: error("Reveal correct letter requires a letter-bank question")
        val required = canonicalLetters(question.displayAnswer)
        val tilesById = active.letterTiles.associateBy { it.id }
        val slot = required.indices.firstOrNull { index ->
            val placement = active.placements.firstOrNull { it.slotIndex == index }
            placement == null || tilesById[placement.tileId]?.character != required[index]
        } ?: 0
        val requiredTileId = "${question.id}:required:$slot"
        val placements = active.placements
            .filterNot { it.slotIndex == slot || it.tileId == requiredTileId }
            .plus(LetterPlacement(slot, requiredTileId))
            .sortedBy { it.slotIndex }
        return active.copy(
            placements = placements,
            hint = HintPresentation(
                type = HintType.RevealCorrectLetter,
                message = required[slot].toString(),
                revealedTileId = requiredTileId,
            ),
        )
    }

    private fun evaluateAnswer(active: ActiveQuestion, answer: SubmittedAnswer): AnswerEvaluation =
        when (val question = active.question) {
            is Question.MultipleChoice -> {
                val choiceAnswer = answer as? SubmittedAnswer.ChoiceAnswer
                    ?: error("A multiple-choice answer is required")
                val choice = active.orderedChoices.firstOrNull { it.id == choiceAnswer.choiceId }
                    ?: error("Unknown answer choice")
                require(choice.id !in active.removedChoiceIds) { "This answer choice has been removed" }
                AnswerEvaluation(choice.id == question.correctChoiceId, choice.label)
            }
            is Question.LetterBank -> {
                val letterAnswer = answer as? SubmittedAnswer.LetterAnswer
                    ?: error("A letter-bank answer is required")
                val placedTileIds = active.placements.sortedBy { it.slotIndex }.map { it.tileId }
                require(letterAnswer.tileIds == placedTileIds) {
                    "The submitted letters must match the placed answer"
                }
                val tiles = active.letterTiles.associateBy { it.id }
                require(letterAnswer.tileIds.distinct().size == letterAnswer.tileIds.size) {
                    "Each letter tile can only be used once"
                }
                require(letterAnswer.tileIds.none { it in active.removedTileIds }) { "A removed tile was submitted" }
                val submitted = letterAnswer.tileIds.map { id ->
                    tiles[id]?.character ?: error("Unknown letter tile")
                }.joinToString("")
                val expected = canonicalLetters(question.displayAnswer)
                require(submitted.length == expected.length) { "Every answer slot must be filled before submitting" }
                AnswerEvaluation(submitted.equals(expected, ignoreCase = true), submitted)
            }
        }

    private fun ActiveQuestion.revealedTileIds(): Set<String> =
        appliedHints.mapNotNullTo(linkedSetOf()) { it.revealedTileId }

    private fun correctAnswer(question: Question): String = when (question) {
        is Question.MultipleChoice -> question.choices.first { it.id == question.correctChoiceId }.label
        is Question.LetterBank -> question.displayAnswer
    }

    private fun prepareQuestion(question: Question, randomState: Long): PreparedQuestion = when (question) {
        is Question.MultipleChoice -> {
            require(question.choices.size == 4) { "Multiple-choice questions require four choices" }
            require(question.choices.map { it.id }.distinct().size == question.choices.size) {
                "Multiple-choice question IDs must be unique"
            }
            require(question.choices.map { it.label }.distinct().size == question.choices.size) {
                "Multiple-choice labels must be unique"
            }
            require(question.choices.any { it.id == question.correctChoiceId }) { "Correct choice is missing" }
            val shuffled = question.choices.shuffled(randomSource, randomState)
            PreparedQuestion(
                activeQuestion = ActiveQuestion(
                    question = question,
                    orderedChoices = shuffled.first,
                    questionWindowMillis = MULTIPLE_CHOICE_WINDOW_MILLIS,
                    questionRemainingMillis = MULTIPLE_CHOICE_WINDOW_MILLIS,
                ),
                randomState = shuffled.second,
            )
        }
        is Question.LetterBank -> {
            require(canonicalLetters(question.displayAnswer).isNotEmpty()) {
                "Letter-bank questions require a playable display answer"
            }
            require(question.decoyLetters.size == LETTER_BANK_DECOY_COUNT) {
                "Letter-bank questions require exactly three decoy letters"
            }
            val requiredTiles = canonicalLetters(question.displayAnswer).mapIndexed { index, character ->
                LetterTile("${question.id}:required:$index", character, isDecoy = false)
            }
            val decoyTiles = question.decoyLetters.mapIndexed { index, character ->
                LetterTile("${question.id}:decoy:$index", character.uppercaseChar(), isDecoy = true)
            }
            val shuffled = (requiredTiles + decoyTiles).shuffled(randomSource, randomState)
            PreparedQuestion(
                activeQuestion = ActiveQuestion(
                    question = question,
                    letterTiles = shuffled.first,
                    questionWindowMillis = LETTER_BANK_WINDOW_MILLIS,
                    questionRemainingMillis = LETTER_BANK_WINDOW_MILLIS,
                ),
                randomState = shuffled.second,
            )
        }
    }

    private fun nextFactGap(randomState: Long): FactGap {
        val random = randomSource.nextInt(randomState, MAX_FACT_GAP - MIN_FACT_GAP + 1)
        return FactGap(MIN_FACT_GAP + random.value, random.nextState)
    }

    private fun canonicalLetters(answer: String): String =
        answer.filter { it.isLetter() }.uppercase()

    private fun requireAnswering(state: QuizRunState) {
        requireActive(state)
        require(state.phase == RunPhase.Answering) { "The run is not accepting answers" }
    }

    private fun requireActive(state: QuizRunState) {
        require(state.status == RunStatus.Active) { "The run is no longer active" }
    }

    private data class PreparedQuestion(val activeQuestion: ActiveQuestion, val randomState: Long)
    private data class SelectedFact(val fact: FactRecord, val randomState: Long)
    private data class FactGap(val gap: Int, val randomState: Long)
    private data class AnswerEvaluation(val correct: Boolean, val submittedText: String)

    companion object {
        const val MULTIPLE_CHOICE_WINDOW_MILLIS = 10_000L
        const val LETTER_BANK_WINDOW_MILLIS = 20_000L
        const val LETTER_BANK_DECOY_COUNT = 3
        const val MINIMUM_UNLIMITED_ANSWERS = 5
        const val ACTIVE_RUN_SNAPSHOT_SCHEMA_VERSION = 1
        private const val MIN_FACT_GAP = 5
        private const val MAX_FACT_GAP = 8
        private const val DEFAULT_SEED = -7046029254386353131L
        private val PAUSABLE_PHASES = setOf(
            RunPhase.Answering,
            RunPhase.HintChooser,
            RunPhase.Feedback,
            RunPhase.FunFact,
        )
    }
}
