package app.bertopia

import app.bertopia.domain.taxonomy.BundledTaxonomy
import app.bertopia.ui.CoverImage
import app.bertopia.ui.editor.mobile.PackEditorDestination

import androidx.compose.runtime.Composable
import app.bertopia.ui.ImportFailedScreen
import app.bertopia.ui.ImportResultUi
import app.bertopia.ui.ImportDoneScreen
import app.bertopia.ui.CategoryOptionUi
import app.bertopia.ui.ImportCategoryRowUi
import app.bertopia.ui.ImportCategoryScreen
import app.bertopia.ui.ImportDeckUi
import app.bertopia.ui.ImportDeckPickerScreen
import app.bertopia.ui.ImportProgressScreen
import app.bertopia.ui.StudyPush
import app.bertopia.ui.StudySettingsUi
import app.bertopia.ui.ReviewGrade
import app.bertopia.ui.ReviewCardUi
import app.bertopia.ui.ReviewScreen
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import app.bertopia.cardrender.CardView
import app.bertopia.cardrender.CardDocumentBuilder
import app.bertopia.domain.fsrs.Rating
import app.bertopia.domain.fsrs.StudyIntensity
import app.bertopia.domain.fsrs.FriendlyStudySettings
import app.bertopia.data.ReviewCard
import app.bertopia.data.PackStore
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import app.bertopia.catalog.PackLibrary
import app.bertopia.ui.LocalFlagMediaDirectory
import app.bertopia.data.BertopiaDatabase
import app.bertopia.data.PreferencesStore
import androidx.compose.ui.graphics.Color
import app.bertopia.domain.MasteryRules
import app.bertopia.domain.model.BuiltInPacks
import app.bertopia.domain.model.FeedbackKind
import app.bertopia.domain.model.Mastery
import app.bertopia.domain.model.MasteryLevel
import app.bertopia.domain.model.HintType
import app.bertopia.domain.model.Question
import app.bertopia.domain.model.QuizDuration
import app.bertopia.domain.model.RunPhase
import app.bertopia.domain.model.RunStatus
import app.bertopia.ui.AnswerState
import app.bertopia.ui.BertopiaTheme
import app.bertopia.ui.BootstrapScreen
import app.bertopia.ui.ChoiceUi
import app.bertopia.ui.CreditsScreen
import app.bertopia.ui.DebugPanelScreen
import app.bertopia.ui.FeedbackScreen
import app.bertopia.ui.FlagUi
import app.bertopia.ui.FunFactScreen
import app.bertopia.ui.HintChooserScreen
import app.bertopia.ui.LibraryScreen
import app.bertopia.ui.PackCardUi
import app.bertopia.ui.PackCover
import app.bertopia.ui.PackHomeScreen
import app.bertopia.ui.MixCardUi
import app.bertopia.ui.MixHomeScreen
import app.bertopia.ui.LeaveQuizConfirmationScreen
import app.bertopia.ui.LetterSlotUi
import app.bertopia.ui.LetterTileUi
import app.bertopia.ui.PrivacyScreen
import app.bertopia.ui.ProgressScreen
import app.bertopia.ui.QuizScreen
import app.bertopia.ui.RecoveryScreen
import app.bertopia.ui.ResetConfirmationScreen
import app.bertopia.ui.ResultsScreen
import app.bertopia.ui.SettingsScreen
import app.bertopia.ui.CardGalleryAccent
import app.bertopia.ui.bertopiaCardRenderStyle
import app.bertopia.cardrender.CardGallery
import app.bertopia.domain.store.StoreIndex
import app.bertopia.store.StorePage
import app.bertopia.ui.store.PackShareButton
import app.bertopia.ui.store.PackUpdateBanner
import app.bertopia.ui.store.StoreDestination
import app.bertopia.ui.store.StoreNavigation
import kotlinx.coroutines.delay

@Composable
fun App(
    database: BertopiaDatabase,
    preferences: PreferencesStore,
    libraryDirectory: String,
    debugBuild: Boolean,
    deviceFeedback: DeviceFeedback = NoDeviceFeedback,
    filePicker: PackFilePicker = NoPackFilePicker,
    packSharer: PackSharer = NoPackSharer,
    watchLibrary: Boolean = false,
    imagePicker: ImagePicker = NoImagePicker,
    onControllerReady: (AppController) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var controller by remember { mutableStateOf<AppController?>(null) }
    var loadFailure by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(database, preferences) {
        val packLibrary = PackLibrary(libraryDirectory)
        runCatching { packLibrary.open() }
            .onSuccess { library ->
                controller = AppController(library, packLibrary, database, preferences, scope, debugBuild, deviceFeedback, PackStore(libraryDirectory), filePicker, packSharer, imagePicker)
                    .also { if (watchLibrary) it.watchLibraryForExternalChanges() }
                    .also(onControllerReady)
            }
            .onFailure { loadFailure = it.message ?: "The bundled packs could not be opened." }
    }
    val activeController = controller
    val appState = activeController?.state?.collectAsState()?.value
    BertopiaTheme(reducedMotion = appState?.preferences?.motionMode == "Reduced") {
        if (activeController == null || appState == null) BootstrapScreen(loadFailure ?: "Opening Bertopia…")
        else CompositionLocalProvider(LocalFlagMediaDirectory provides activeController.flagMediaDirectory) {
            BertopiaDestination(activeController, appState)
        }
    }
}

@Composable
private fun BertopiaDestination(controller: AppController, state: BertopiaAppState) {
    val run = state.run
    if (state.leaveConfirmationRequested) {
        LeaveQuizConfirmationScreen(
            onCancel = controller::cancelLeave,
            onLeave = controller::confirmLeave,
        )
        return
    }
    when (val destination = state.destination) {
        Destination.Bootstrap -> BootstrapScreen(state.loadingMessage ?: "Opening Bertopia…")
        Destination.Home -> {
            val storeIndex = controller.store.state.collectAsState().value.index
            val packs by controller.installedPacks.collectAsState()
            val mixes by controller.installedMixes.collectAsState()
            LibraryScreen(
                packs = packs.map { it.toCardUi(controller::mediaDirectory) },
                mixes = mixes.map { it.toCardUi(packs, storeIndex) },
                isFirstLaunch = state.firstLaunch,
                hasCompletedRun = state.completedRuns.isNotEmpty(),
                streakDays = controller.currentStreakDays,
                progressSummary = if (state.answerTotals.answeredQuestions == 1) "1 question explored" else "${state.answerTotals.answeredQuestions} questions explored",
                totalPoints = state.answerTotals.netPoints,
                onOpenPack = controller::openPack,
                onOpenMix = controller::openMix,
                onProgress = { controller.navigate(Destination.Progress) },
                onSettings = { controller.navigate(Destination.Settings) },
                onAddPack = controller::pickPackFile,
                onCreatePack = controller::openCreatePack,
                onOpenStore = { controller.openStore() },
            )
        }
        is Destination.PackHome -> {
            val packs by controller.installedPacks.collectAsState()
            val pack = packs.firstOrNull { it.packId == destination.packId }
            if (pack == null) BootstrapScreen() else PackHomeScreen(
                pack = pack.toCardUi(controller::mediaDirectory),
                sections = pack.sections,
                selectedSection = state.selectedSection,
                studySettings = pack.studySettings?.toUi(),
                notice = state.packNotice,
                durations = durationChoices(state.selectedDuration),
                onChooseSection = controller::chooseSection,
                onReview = { controller.startReview(pack.packId) },
                onChooseDuration = { id -> controller.choosePackDuration(QuizDuration.valueOf(id)) },
                onPlay = { controller.playPack(pack) },
                onChangeStudySettings = { controller.saveStudySettings(pack.packId, it.toDomain()) },
                onBack = controller::back,
                onEditCards = { controller.openCardEditor(pack) },
                onEditPack = { controller.openPackDetailsEditor(pack.packId) },
                onMakeCopy = { controller.makePackCopy(pack.packId) },
                showLevelPicker = pack.levels.isNotEmpty(),
                selectedLevel = pack.level,
                onChooseLevel = { controller.chooseLevel(pack.packId, it) },
                banner = {
                    val storeState by controller.store.state.collectAsState()
                    PackUpdateBanner(controller.store, storeState, pack.packId, pack.title)
                },
                extras = {
                    val storeState by controller.store.state.collectAsState()
                    PackShareButton(controller.store, storeState, pack.packId, pack.title)
                },
            )
        }
        is Destination.MixHome -> {
            val packs by controller.installedPacks.collectAsState()
            val mixes by controller.installedMixes.collectAsState()
            val mix = mixes.firstOrNull { it.mixId == destination.mixId }
            val storeState by controller.store.state.collectAsState()
            if (mix == null) BootstrapScreen() else MixHomeScreen(
                mix = mix.toCardUi(packs, storeState.index),
                durations = durationChoices(state.selectedDuration),
                onChooseDuration = { id -> controller.choosePackDuration(QuizDuration.valueOf(id)) },
                onPlay = { controller.startMixRun(mix.mixId, state.selectedDuration) },
                onBack = controller::back,
                onFindMissingPacks = mix.missingPackIds.takeIf { it.isNotEmpty() }?.let { missing ->
                    { controller.openStore(missing.singleOrNull()?.takeIf { storeState.index?.pack(it) != null }?.let(StorePage::PackDetail) ?: StorePage.Home) }
                },
            )
        }
        is Destination.Review -> {
            val packs by controller.installedPacks.collectAsState()
            val pack = packs.firstOrNull { it.packId == destination.packId }
            val session = state.review
            if (pack == null || session == null) BootstrapScreen() else ReviewScreen(
                packTitle = pack.title,
                accent = pack.accentColor.toColor(),
                card = session.card?.toUi(),
                loading = session.loading,
                revealed = session.revealed,
                typedAnswer = session.typedAnswer,
                remaining = session.card?.counts?.total ?: 0,
                reviewedCount = session.reviewedCount,
                gradeCounts = session.gradeCounts.mapKeys { it.key.toReviewGrade() },
                onTypedAnswerChange = controller::typeReviewAnswer,
                onReveal = controller::revealReviewAnswer,
                onGrade = { controller.gradeReview(it.toRating()) },
                onDone = controller::finishReview,
            )
        }
        Destination.Import -> ImportDestination(controller, state)
        Destination.Recovery -> if (run == null) BootstrapScreen() else RecoveryScreen(
            questionSummary = run.activeQuestion.question.prompt.withoutMathDelimiters(),
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
                accuracy = if (result.answeredQuestions == 0) "Not yet" else "${result.accuracyPercent}%",
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
            currentStreak = controller.currentStreakDays,
            bestStreak = state.streak.bestDays,
            overallAccuracy = if (state.answerTotals.answeredQuestions == 0) "Not yet" else "${state.answerTotals.correctAnswers * 100 / state.answerTotals.answeredQuestions}%",
            totalAnswered = state.answerTotals.answeredQuestions,
            masteryGroups = controller.progressGroups,
            strongCountries = countryNames(controller, state.mastery.filter { it.level.name in setOf("Familiar", "Mastered") }.map { it.countryId } - revisitCountryIds(state.mastery).toSet()),
            revisitCountries = countryNames(controller, revisitCountryIds(state.mastery)),
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
        Destination.Store -> {
            val storeState by controller.store.state.collectAsState()
            StoreDestination(
                controller.store,
                storeState,
                StoreNavigation(onBack = controller::back, onOpenInstalledPack = controller::openPack, onOpenMix = controller::openMix),
            )
        }
        Destination.Editor -> {
            val editorState by controller.editor.state.collectAsState()
            PackEditorDestination(controller.editor, editorState)
        }
        Destination.Debug -> if (controller.debugBuild) {
            var showCardGallery by rememberSaveable { mutableStateOf(false) }
            if (showCardGallery) {
                CardGallery(style = bertopiaCardRenderStyle(accent = CardGalleryAccent), onClose = { showCardGallery = false })
            } else {
                DebugPanelScreen(
                    buildVersion = "1.0.0 debug",
                    catalogVersion = controller.catalog.contentVersion,
                    activeSnapshot = run?.let { "${it.runId} · ${it.phase} · ${it.activeQuestion.question.id} · ${it.score} points" } ?: "No active run",
                    actions = controller.debugActions,
                    onAction = controller::debugAction,
                    onOpenCardGallery = { showCardGallery = true },
                    onClose = controller::back,
                )
            }
        } else BootstrapScreen()
    }
}

@Composable
private fun RunDestination(controller: AppController, state: BertopiaAppState) {
    val run = requireNotNull(state.run)
    when (run.phase) {
        RunPhase.Answering, RunPhase.Paused -> {
            val question = run.activeQuestion.question
            val country = question.countryIds.firstOrNull()?.let { id -> controller.catalog.countries.firstOrNull { it.id == id } }
            val promptFlag = when (question) {
                is Question.LetterBank -> country?.let { FlagUi(it.flagAssetId.removePrefix("flag-"), "mystery country") }
                is Question.MultipleChoice -> question.promptAssetId?.let { FlagUi(it.removePrefix("flag-"), "mystery country") }
                is Question.FlipAndGrade -> null
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
                onOpenDebug = if (controller.debugBuild) controller::openDebugFromRun else null,
                promptFlag = promptFlag,
                runProgress = run.configuration.duration.activeDurationMillis?.let { duration ->
                    1f - ((run.sessionRemainingMillis ?: duration).toFloat() / duration.toFloat())
                },
                runProgressDescription = "Trivia time remaining ${run.sessionRemainingMillis.formatTime()}",
                promptContent = question.promptHtml()?.let { html -> { PackCardContent(html, controller.mediaDirectory(question.packId)) } },
                flipAnswerContent = (question as? Question.FlipAndGrade)?.let { flip -> { PackCardContent(flip.answerHtml.afterAnswerDivider(), controller.mediaDirectory(question.packId)) } },
                flipKey = question.id + run.attempts.size,
                flipRevealed = run.activeQuestion.answerRevealed,
                onRevealFlip = controller::revealFlipAnswer,
                onSelfGrade = controller::selfGrade,
            )
        }
        RunPhase.HintChooser -> HintChooserScreen(
            score = run.score,
            hints = run.activeQuestion.question.supportedHints.map { hint ->
                val used = hint in run.activeQuestion.usedHintTypes
                ChoiceUi(
                    id = hint.name,
                    label = hint.displayName(run.activeQuestion.question),
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

private fun PackSummary.toCardUi(mediaDirectoryOf: (String) -> String = { "" }): PackCardUi = PackCardUi(
    packId = packId,
    title = title,
    subtitle = subtitle,
    accent = accentColor.toColor(),
    cover = when (packId) {
        BuiltInPacks.FLAGS -> PackCover.Flags
        BuiltInPacks.CAPITALS -> PackCover.Capitals
        else -> PackCover.Generic
    },
    cardCount = cardCount,
    masteredCount = masteredCount,
    learningCount = learningCount,
    dueCount = dueCount,
    shelf = BundledTaxonomy.taxonomy.category(category)?.name ?: "",
    subshelf = BundledTaxonomy.taxonomy.category(category)?.subcategory(subcategory)?.name ?: "",
    official = official,
    coverImage = coverMedia?.let { CoverImage(mediaDirectoryOf(packId), it) },
)

private fun QuizDuration.displayName() = when (this) {
    QuizDuration.FiveMinutes -> "5 minutes"
    QuizDuration.TenMinutes -> "10 minutes"
    QuizDuration.TwentyMinutes -> "20 minutes"
    QuizDuration.Unlimited -> "Unlimited"
}

private fun MixSummary.toCardUi(packs: List<PackSummary>, index: StoreIndex? = null) = MixCardUi(
    mixId = mixId,
    title = title,
    subtitle = subtitle,
    accent = accentColor.toColor(),
    memberPacks = memberPackIds.mapNotNull { id -> packs.firstOrNull { it.packId == id }?.toCardUi() },
    missingPackNames = missingPackIds.map { id -> index?.pack(id)?.title ?: id.split('-').joinToString(" ") { it.replaceFirstChar(Char::uppercase) } },
)

private fun String.toColor(): Color = Color(0xFF000000 or removePrefix("#").toLong(16))

private fun letterSlots(
    question: Question,
    placements: List<app.bertopia.domain.model.LetterPlacement>,
    tiles: List<app.bertopia.domain.model.LetterTile>,
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

private fun HintType.displayName(question: Question) = when (this) {
    HintType.RevealCorrectLetter -> "Reveal one letter"
    HintType.RemoveIncorrectOptions -> if (question is Question.LetterBank) "Remove the extra letters" else "Remove two wrong choices"
    HintType.RevealContinent -> "Reveal the region"
    HintType.FunnyFactualClue -> "Give me a factual clue"
}

private fun Long?.formatTime(): String {
    if (this == null) return "∞"
    val seconds = (this.coerceAtLeast(0) + 999) / 1_000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

private val mathDelimiters = Regex("""\\\(|\\\)|\\\[|\\\]|\[/?\$\$?]|\[/?latex]""")

private fun String.withoutMathDelimiters(): String = replace(mathDelimiters, "")

private fun Long.formatSeconds(): String = "${((coerceAtLeast(0) + 500) / 1_000)} sec"

private fun countryNames(controller: AppController, ids: List<String>): List<String> = ids.distinct().mapNotNull { id ->
    controller.catalog.countries.firstOrNull { it.id == id }?.displayName
}

private fun revisitCountryIds(mastery: List<Mastery>): List<String> =
    mastery.filter(MasteryRules::needsRevisit).map { it.countryId }

@Composable
private fun PackCardContent(html: String, mediaDirectory: String) {
    val document = remember(html) { CardDocumentBuilder.build(html.replace(TYPE_ANSWER_MARKER, "")) }
    CardView(document, bertopiaCardRenderStyle(), mediaDirectory, Modifier.fillMaxWidth())
}

private val TYPE_ANSWER_MARKER = Regex("""\[\[type:[^\]]*\]\]""")

private fun Question.promptHtml(): String? = when (this) {
    is Question.MultipleChoice -> promptHtml
    is Question.LetterBank -> promptHtml
    is Question.FlipAndGrade -> promptHtml
}

private fun ReviewCard.toUi() = ReviewCardUi(
    questionHtml = questionHtml,
    answerHtml = answerHtml,
    mediaDirectory = mediaDirectory,
    typedAnswerExpected = typedAnswerExpected,
    nextIntervals = nextIntervals.mapKeys { it.key.toReviewGrade() },
)

private fun Rating.toReviewGrade() = when (this) {
    Rating.Again -> ReviewGrade.Forgot
    Rating.Hard -> ReviewGrade.Tough
    Rating.Good -> ReviewGrade.GotIt
    Rating.Easy -> ReviewGrade.Easy
}

private fun ReviewGrade.toRating() = when (this) {
    ReviewGrade.Forgot -> Rating.Again
    ReviewGrade.Tough -> Rating.Hard
    ReviewGrade.GotIt -> Rating.Good
    ReviewGrade.Easy -> Rating.Easy
}

private fun FriendlyStudySettings.toUi() = StudySettingsUi(
    dailyNewCards = dailyNewCardGoal,
    push = when (intensity) {
        StudyIntensity.Relaxed -> StudyPush.Relaxed
        StudyIntensity.Balanced -> StudyPush.Balanced
        StudyIntensity.Intense -> StudyPush.Intense
    },
)

private fun StudySettingsUi.toDomain() = FriendlyStudySettings(
    dailyNewCardGoal = dailyNewCards,
    intensity = when (push) {
        StudyPush.Relaxed -> StudyIntensity.Relaxed
        StudyPush.Balanced -> StudyIntensity.Balanced
        StudyPush.Intense -> StudyIntensity.Intense
    },
)

@Composable
private fun ImportDestination(controller: AppController, state: BertopiaAppState) {
    val flow = state.importFlow ?: return BootstrapScreen()
    val taxonomy by controller.taxonomyState.collectAsState()
    when (flow.phase) {
        ImportPhase.Reading -> ImportProgressScreen("Opening ${flow.fileName}", flow.progressLabel, flow.progressFraction, controller::cancelImport)
        ImportPhase.Importing -> ImportProgressScreen("Adding to your library", flow.progressLabel, flow.progressFraction, controller::cancelImport)
        ImportPhase.ChooseDecks -> ImportDeckPickerScreen(
            fileName = flow.fileName,
            decks = flow.decks.map { deck ->
                ImportDeckUi(
                    id = deck.deckId,
                    title = deck.title,
                    detail = listOfNotNull(
                        if (deck.cardCount == 1L) "1 card" else "${deck.cardCount} cards",
                        when (deck.sections.size) { 0 -> null; 1 -> "1 section"; else -> "${deck.sections.size} sections" },
                    ).joinToString(" · "),
                    previewHtml = deck.previewHtml,
                    updatesPackTitle = deck.existingPackTitle,
                    selected = deck.deckId in flow.selectedDeckIds,
                )
            },
            onToggle = controller::toggleImportDeck,
            onContinue = controller::confirmImportDecks,
            onCancel = controller::closeImport,
        )
        ImportPhase.ChooseCategories -> ImportCategoryScreen(
            rows = flow.decksNeedingCategory.map { deck ->
                val choice = flow.categories[deck.deckId]
                ImportCategoryRowUi(deck.deckId, deck.title, choice?.first?.ifBlank { null }, choice?.second?.ifBlank { null })
            },
            categories = taxonomy?.categories.orEmpty().map { category ->
                CategoryOptionUi(category.id, category.name, category.subcategories.map { it.id to it.name })
            },
            onChoose = controller::chooseImportCategory,
            onImport = controller::startImport,
            onCancel = controller::closeImport,
        )
        ImportPhase.Done -> ImportDoneScreen(
            results = flow.results.map { result ->
                ImportResultUi(
                    packId = result.packId,
                    title = result.title,
                    detail = if (result.mergedIntoExisting) {
                        "Updated ${result.updatedNotes} cards and added ${result.addedNotes} new ones. Your progress is kept."
                    } else {
                        "Added ${result.addedCards} ${if (result.addedCards == 1) "card" else "cards"}" + when {
                            result.mediaFiles == 1 -> " and 1 picture or sound."
                            result.mediaFiles > 1 -> " and ${result.mediaFiles} pictures and sounds."
                            else -> "."
                        }
                    },
                )
            },
            onOpen = controller::openImportedPack,
            onLibrary = controller::closeImport,
        )
        ImportPhase.Failed -> ImportFailedScreen(flow.failure.orEmpty(), controller::closeImport)
    }
}

private val answerDivider = Regex("(?i)<hr[^>]*id\\s*=\\s*[\"']?answer[\"']?[^>]*>")

private fun String.afterAnswerDivider(): String = answerDivider.split(this, limit = 2).getOrNull(1)?.takeIf { it.isNotBlank() } ?: this
