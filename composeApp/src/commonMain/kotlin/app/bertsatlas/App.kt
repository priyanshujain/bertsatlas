package app.bertsatlas

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.bertsatlas.catalog.BundledCatalogProvider
import app.bertsatlas.data.BertsAtlasDatabase
import app.bertsatlas.data.PreferencesStore
import app.bertsatlas.domain.model.FeedbackKind
import app.bertsatlas.domain.model.HintType
import app.bertsatlas.domain.model.Question
import app.bertsatlas.domain.model.QuizDuration
import app.bertsatlas.domain.model.RunPhase
import app.bertsatlas.domain.model.RunStatus
import app.bertsatlas.ui.AnswerState
import app.bertsatlas.ui.BertsAtlasTheme
import app.bertsatlas.ui.BootstrapScreen
import app.bertsatlas.ui.ChoiceUi
import app.bertsatlas.ui.CreditsScreen
import app.bertsatlas.ui.DebugPanelScreen
import app.bertsatlas.ui.FeedbackScreen
import app.bertsatlas.ui.FlagUi
import app.bertsatlas.ui.FunFactScreen
import app.bertsatlas.ui.HintChooserScreen
import app.bertsatlas.ui.HomeScreen
import app.bertsatlas.ui.LeaveQuizConfirmationScreen
import app.bertsatlas.ui.LetterSlotUi
import app.bertsatlas.ui.LetterTileUi
import app.bertsatlas.ui.OptionsScreen
import app.bertsatlas.ui.PrivacyScreen
import app.bertsatlas.ui.ProgressScreen
import app.bertsatlas.ui.QuizScreen
import app.bertsatlas.ui.RecoveryScreen
import app.bertsatlas.ui.ResetConfirmationScreen
import app.bertsatlas.ui.ResultsScreen
import app.bertsatlas.ui.SettingsScreen
import kotlinx.coroutines.delay

@Composable
fun App(
    database: BertsAtlasDatabase,
    preferences: PreferencesStore,
    debugBuild: Boolean,
    deviceFeedback: DeviceFeedback = NoDeviceFeedback,
    onControllerReady: (AppController) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var controller by remember { mutableStateOf<AppController?>(null) }
    var loadFailure by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(database, preferences) {
        runCatching { BundledCatalogProvider().loadCatalog() }
            .onSuccess { catalog -> controller = AppController(catalog, database, preferences, scope, debugBuild, deviceFeedback).also(onControllerReady) }
            .onFailure { loadFailure = it.message ?: "The bundled catalog could not be opened." }
    }
    val activeController = controller
    val appState = activeController?.state?.collectAsState()?.value
    BertsAtlasTheme(reducedMotion = appState?.preferences?.motionMode == "Reduced") {
        if (activeController == null || appState == null) BootstrapScreen(loadFailure ?: "Opening the atlas…")
        else AtlasDestination(activeController, appState)
    }
}

@Composable
private fun AtlasDestination(controller: AppController, state: AtlasAppState) {
    val run = state.run
    if (state.leaveConfirmationRequested) {
        LeaveQuizConfirmationScreen(
            onCancel = controller::cancelLeave,
            onLeave = controller::confirmLeave,
        )
        return
    }
    when (val destination = state.destination) {
        Destination.Bootstrap -> BootstrapScreen()
        Destination.Home -> HomeScreen(
            isFirstLaunch = state.firstLaunch,
            streakDays = state.streak.currentDays,
            progressSummary = if (state.attempts.size == 1) "1 question explored" else "${state.attempts.size} questions explored",
            totalPoints = state.attempts.sumOf { it.pointsAwarded } - state.attempts.sumOf { it.hintPointsDeducted },
            hasStartedTrivia = !state.firstLaunch,
            onStart = controller::start,
            onOptions = controller::openOptions,
            onProgress = { controller.navigate(Destination.Progress) },
            onSettings = { controller.navigate(Destination.Settings) },
        )
        is Destination.Options -> OptionsScreen(
            step = destination.step,
            choices = when (destination.step) {
                1 -> durationChoices(state.selectedDuration)
                2 -> topicChoices(state.selectedTopic)
                else -> emptyList()
            },
            durationSummary = state.selectedDuration.displayName(),
            topicSummary = state.selectedTopic.displayName(),
            onChoose = controller::chooseOption,
            onStart = controller::startSavedOptions,
            onBack = controller::back,
        )
        Destination.Recovery -> if (run == null) BootstrapScreen() else RecoveryScreen(
            questionSummary = run.activeQuestion.question.prompt,
            score = run.score,
            timeRemaining = run.sessionRemainingMillis.formatTime(),
            onResume = controller::resume,
            onAbandon = controller::requestLeave,
        )
        Destination.Quiz -> if (run == null) BootstrapScreen() else RunDestination(controller, state)
        Destination.Results -> {
            val result = state.result
            if (result == null) BootstrapScreen() else ResultsScreen(
                score = result.score,
                correctAnswers = result.correctAnswers,
                answeredQuestions = result.answeredQuestions,
                accuracy = "${result.accuracyPercent}%",
                averageResponseTime = result.averageResponseMillis.formatSeconds(),
                bestCorrectStreak = result.bestCorrectStreak,
                masteryImproved = result.improvedMasteryItems,
                revisitCountries = result.revisitCountryIds.mapNotNull { id -> controller.catalog.countries.firstOrNull { it.id == id }?.displayName },
                newPersonalBest = state.newPersonalBest,
                onPlayAgain = controller::start,
                onHome = { controller.navigate(Destination.Home) },
                abandoned = result.status == RunStatus.Abandoned,
            )
        }
        Destination.Progress -> ProgressScreen(
            currentStreak = state.streak.currentDays,
            bestStreak = state.streak.bestDays,
            overallAccuracy = if (state.attempts.isEmpty()) "Not yet" else "${state.attempts.count { it.correct } * 100 / state.attempts.size}%",
            totalAnswered = state.attempts.size,
            masteryGroups = controller.progressGroups,
            strongCountries = countryNames(controller, state.mastery.filter { it.level.name in setOf("Familiar", "Mastered") }.map { it.countryId }),
            revisitCountries = countryNames(controller, state.mastery.filter { it.level.name == "Learning" }.map { it.countryId }),
            stamps = controller.stampUi,
            onBack = controller::back,
        )
        Destination.Settings -> SettingsScreen(
            settings = controller.settings,
            version = "1.0.0",
            debugEntryEnabled = controller.debugBuild,
            onToggle = controller::toggleSetting,
            onPrivacy = { controller.navigate(Destination.Privacy) },
            onCredits = { controller.navigate(Destination.Credits) },
            onReset = { controller.navigate(Destination.Reset) },
            onOpenDebug = { if (controller.debugBuild) controller.navigate(Destination.Debug) },
            onBack = controller::back,
        )
        Destination.Privacy -> PrivacyScreen(controller::back)
        Destination.Credits -> CreditsScreen(
            catalogVersion = controller.catalog.contentVersion,
            credits = listOf("Country metadata: United Nations and CLDR", "Durable facts: cited per catalog record", "Flags: pinned public-domain country-flags revision"),
            licenses = listOf("Atkinson Hyperlegible Next · OFL-1.1", "Compottie · Apache-2.0", "country-flags · public domain"),
            onBack = controller::back,
        )
        Destination.Reset -> ResetConfirmationScreen(controller::resetAll, controller::back)
        Destination.Debug -> if (controller.debugBuild) DebugPanelScreen(
            buildVersion = "1.0.0 debug",
            catalogVersion = controller.catalog.contentVersion,
            activeSnapshot = run?.let { "${it.runId} · ${it.phase} · ${it.activeQuestion.question.id} · ${it.score} points" } ?: "No active run",
            actions = controller.debugActions,
            onAction = controller::debugAction,
            onClose = controller::back,
        ) else HomeScreen(true, onStart = controller::start)
    }
}

@Composable
private fun RunDestination(controller: AppController, state: AtlasAppState) {
    val run = requireNotNull(state.run)
    when (run.phase) {
        RunPhase.Answering, RunPhase.Paused -> {
            val question = run.activeQuestion.question
            val country = question.countryIds.firstOrNull()?.let { id -> controller.catalog.countries.firstOrNull { it.id == id } }
            val promptFlag = when (question) {
                is Question.LetterBank -> country?.let { FlagUi(it.flagAssetId.removePrefix("flag-"), "mystery country") }
                is Question.MultipleChoice -> question.promptAssetId?.let { FlagUi(it.removePrefix("flag-"), "mystery country") }
            }
            QuizScreen(
                prompt = question.prompt,
                imageDescription = if (promptFlag == null) null else "A flag to identify",
                score = run.score,
                questionTime = run.activeQuestion.questionRemainingMillis.formatTime(),
                timerExpired = run.activeQuestion.questionRemainingMillis == 0L,
                choices = (question as? Question.MultipleChoice)?.let {
                    run.activeQuestion.orderedChoices.map { choice ->
                        ChoiceUi(
                            id = choice.id,
                            label = choice.label,
                            state = if (choice.id in run.activeQuestion.removedChoiceIds) AnswerState.Eliminated else AnswerState.Available,
                            enabled = choice.id !in run.activeQuestion.removedChoiceIds && run.phase == RunPhase.Answering,
                            flag = choice.assetId?.let { FlagUi(it.removePrefix("flag-"), choice.label) },
                        )
                    }
                }.orEmpty(),
                letterSlots = letterSlots(
                    question,
                    run.activeQuestion.placements,
                    run.activeQuestion.letterTiles,
                    run.activeQuestion.appliedHints.mapNotNullTo(linkedSetOf()) { it.revealedTileId },
                ),
                letterTiles = run.activeQuestion.letterTiles.map { tile ->
                    LetterTileUi(tile.id, tile.character.toString(), run.activeQuestion.placements.firstOrNull { it.tileId == tile.id }?.slotIndex, tile.id !in run.activeQuestion.removedTileIds)
                },
                appliedHintMessages = run.activeQuestion.appliedHints.mapNotNull { it.message },
                hintAvailable = run.activeQuestion.question.supportedHints.any {
                    it !in run.activeQuestion.usedHintTypes
                } && run.phase == RunPhase.Answering,
                unlimited = run.configuration.duration == QuizDuration.Unlimited,
                canSubmit = question is Question.LetterBank && run.activeQuestion.placements.size == question.displayAnswer.count { it.isLetter() },
                onChoice = controller::answerChoice,
                onLetter = controller::placeLetter,
                onRemoveLetter = controller::removeLetter,
                onClearLetters = controller::clearLetters,
                onSubmit = controller::submitLetters,
                onHint = controller::openHint,
                onFinish = controller::finishUnlimited,
                onLeave = controller::requestLeave,
                promptFlag = promptFlag,
                runProgress = run.configuration.duration.activeDurationMillis?.let { duration ->
                    1f - ((run.sessionRemainingMillis ?: duration).toFloat() / duration.toFloat())
                },
                runProgressDescription = "Trivia time remaining ${run.sessionRemainingMillis.formatTime()}",
            )
        }
        RunPhase.HintChooser -> HintChooserScreen(
            score = run.score,
            hints = run.activeQuestion.question.supportedHints.map { hint ->
                val used = hint in run.activeQuestion.usedHintTypes
                ChoiceUi(
                    id = hint.name,
                    label = hint.displayName(),
                    supportingText = if (used) "✓ Used" else null,
                    state = if (used) AnswerState.Selected else AnswerState.Available,
                    enabled = !used,
                )
            },
            chargedHintLabel = "−${run.score / 2} points",
            onChooseHint = { id -> controller.chooseHint(HintType.valueOf(id)) },
            onCancel = controller::cancelHint,
        )
        RunPhase.Feedback -> {
            val feedback = requireNotNull(run.feedback)
            LaunchedEffect(run.attempts.size, state.preferences.autoAdvanceEnabled) {
                if (state.preferences.autoAdvanceEnabled) {
                    delay(2_500)
                    controller.continueRun()
                }
            }
            FeedbackScreen(
                correct = feedback.kind != FeedbackKind.Incorrect,
                overtime = feedback.kind == FeedbackKind.OvertimeCorrect,
                heading = when (feedback.kind) { FeedbackKind.Correct -> "Exactly right"; FeedbackKind.Incorrect -> "Not quite"; FeedbackKind.OvertimeCorrect -> "Worth the wait" },
                selectedAnswer = feedback.submittedAnswer,
                correctAnswer = feedback.correctAnswer,
                pointsAwarded = feedback.pointsAwarded,
                explanation = feedback.explanation,
                autoAdvanceMessage = if (state.preferences.autoAdvanceEnabled) "Continuing shortly…" else null,
                onContinue = controller::continueRun,
            )
        }
        RunPhase.FunFact -> requireNotNull(run.visibleFact).let { fact ->
            FunFactScreen(fact.heading, fact.body, countryNames(controller, fact.countryIds).joinToString(), onContinue = controller::continueRun)
        }
        RunPhase.Completed -> BootstrapScreen("Preparing your summary…")
    }
}

private fun durationChoices(selected: QuizDuration) = QuizDuration.entries.map { duration ->
    ChoiceUi(duration.name, duration.displayName(), state = if (duration == selected) AnswerState.Selected else AnswerState.Available)
}

private fun topicChoices(selected: app.bertsatlas.domain.model.QuizTopic) = listOf(
    ChoiceUi("MostlyFlags", "Mostly flags", "Recognize flags and spell country names", state = if (selected == app.bertsatlas.domain.model.QuizTopic.MostlyFlags) AnswerState.Selected else AnswerState.Available),
    ChoiceUi("Capitals", "Capitals", "Match countries and capitals", state = if (selected == app.bertsatlas.domain.model.QuizTopic.Capitals) AnswerState.Selected else AnswerState.Available),
    ChoiceUi("MixedGeography", "Mixed geography", "A broader grab bag", state = if (selected == app.bertsatlas.domain.model.QuizTopic.MixedGeography) AnswerState.Selected else AnswerState.Available),
)

private fun QuizDuration.displayName() = when (this) {
    QuizDuration.FiveMinutes -> "5 minutes"
    QuizDuration.TenMinutes -> "10 minutes"
    QuizDuration.TwentyMinutes -> "20 minutes"
    QuizDuration.Unlimited -> "Unlimited"
}

private fun app.bertsatlas.domain.model.QuizTopic.displayName() = when (this) {
    app.bertsatlas.domain.model.QuizTopic.MostlyFlags -> "Mostly flags"
    app.bertsatlas.domain.model.QuizTopic.Capitals -> "Capitals"
    app.bertsatlas.domain.model.QuizTopic.MixedGeography -> "Mixed geography"
}

private fun letterSlots(
    question: Question,
    placements: List<app.bertsatlas.domain.model.LetterPlacement>,
    tiles: List<app.bertsatlas.domain.model.LetterTile>,
    revealedTileIds: Set<String>,
): List<LetterSlotUi> {
    val letterBank = question as? Question.LetterBank ?: return emptyList()
    val tileById = tiles.associateBy { it.id }
    var editableIndex = 0
    return letterBank.displayAnswer.mapIndexed { displayIndex, character ->
        if (!character.isLetter()) LetterSlotUi(displayIndex, character.toString(), fixed = true)
        else {
            val placement = placements.firstOrNull { it.slotIndex == editableIndex }
            LetterSlotUi(
                position = displayIndex,
                character = placement?.let { tileById[it.tileId]?.character?.toString() },
                tileId = placement?.tileId,
                fixed = placement?.tileId in revealedTileIds,
            ).also { editableIndex++ }
        }
    }
}

private fun HintType.displayName() = when (this) {
    HintType.RevealCorrectLetter -> "Reveal one letter"
    HintType.RemoveIncorrectOptions -> "Remove two wrong choices"
    HintType.RevealContinent -> "Reveal the region"
    HintType.FunnyFactualClue -> "Give me a factual clue"
}

private fun Long?.formatTime(): String {
    if (this == null) return "∞"
    val seconds = (this.coerceAtLeast(0) + 999) / 1_000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

private fun Long.formatSeconds(): String = "${((coerceAtLeast(0) + 500) / 1_000)} sec"

private fun countryNames(controller: AppController, ids: List<String>): List<String> = ids.distinct().mapNotNull { id ->
    controller.catalog.countries.firstOrNull { it.id == id }?.displayName
}
