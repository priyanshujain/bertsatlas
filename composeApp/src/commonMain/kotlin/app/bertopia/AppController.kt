package app.bertopia

import app.bertopia.domain.pack.LevelSetting
import app.bertopia.anki.pkg.ImportCancelledException
import app.bertopia.anki.pkg.ImportMonitor
import app.bertopia.anki.pkg.ImportStage
import app.bertopia.domain.taxonomy.BundledTaxonomy
import app.bertopia.domain.taxonomy.Taxonomy
import app.bertopia.data.PackStore
import app.bertopia.ui.editor.mobile.PackEditingController
import app.bertopia.data.ReviewCard
import app.bertopia.domain.fsrs.FriendlyStudySettings
import app.bertopia.domain.fsrs.QuizAnswerFormat
import app.bertopia.domain.fsrs.QuizAnswerOutcome
import app.bertopia.domain.fsrs.QuizGrading
import app.bertopia.domain.fsrs.Rating
import app.bertopia.domain.pack.DeckSelection
import app.bertopia.domain.pack.ImportPlan
import app.bertopia.domain.pack.ImportedPack
import app.bertopia.domain.quiz.combineCatalogs
import kotlin.concurrent.Volatile
import app.bertopia.catalog.InstalledLibrary
import app.bertopia.catalog.PackLibrary
import app.bertopia.data.AnswerTotals
import app.bertopia.data.BertopiaDatabase
import app.bertopia.data.PreferencesStore
import app.bertopia.data.RunEntity
import app.bertopia.data.SavedPreferences
import app.bertopia.data.StreakEntity
import app.bertopia.data.toEntity
import app.bertopia.data.toPassportStamp
import app.bertopia.data.toRunEntity
import app.bertopia.data.toStreak
import app.bertopia.domain.LocalDateProvider
import app.bertopia.domain.MasteryRules
import app.bertopia.domain.MonotonicClock
import app.bertopia.domain.PassportEvidence
import app.bertopia.domain.PassportRules
import app.bertopia.domain.QuizEngine
import app.bertopia.domain.StreakRules
import app.bertopia.domain.WallClock
import app.bertopia.domain.model.ActiveRunSnapshot
import app.bertopia.domain.model.BuiltInPacks
import app.bertopia.domain.model.Catalog
import app.bertopia.domain.model.Continent
import app.bertopia.domain.model.HintType
import app.bertopia.domain.model.Mastery
import app.bertopia.domain.model.MasteryLevel
import app.bertopia.domain.model.PassportStamp
import app.bertopia.domain.model.PassportStampKind
import app.bertopia.domain.model.Question
import app.bertopia.domain.model.QuizConfiguration
import app.bertopia.domain.model.QuizDuration
import app.bertopia.domain.model.QuizRunState
import app.bertopia.domain.model.PlayScope
import app.bertopia.domain.pack.BundledMixes
import app.bertopia.domain.pack.Mix
import app.bertopia.store.StoreController
import app.bertopia.store.StorePage
import app.bertopia.domain.model.RunPhase
import app.bertopia.domain.model.RunResult
import app.bertopia.domain.model.RunStatus
import app.bertopia.domain.model.Skill
import app.bertopia.domain.pack.PackStamp
import app.bertopia.domain.model.Streak
import app.bertopia.domain.model.SubmittedAnswer
import app.bertopia.domain.model.packId
import app.bertopia.ui.DebugActionUi
import app.bertopia.ui.ProgressGroupUi
import app.bertopia.ui.SettingToggleUi
import app.bertopia.ui.StampUi
import kotlin.time.Clock
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed interface Destination {
    data object Bootstrap : Destination
    data object Recovery : Destination
    data object Home : Destination
    data class PackHome(val packId: String) : Destination
    data class MixHome(val mixId: String) : Destination
    data class Review(val packId: String) : Destination
    data object Import : Destination
    data object Quiz : Destination
    data object Results : Destination
    data object Progress : Destination
    data object Settings : Destination
    data object Privacy : Destination
    data object Credits : Destination
    data object Reset : Destination
    data object Debug : Destination
    data object Editor : Destination
    data object Store : Destination
}

data class BertopiaAppState(
    val destination: Destination = Destination.Bootstrap,
    val firstLaunch: Boolean = true,
    val preferences: SavedPreferences = SavedPreferences(),
    val selectedDuration: QuizDuration = QuizDuration.FiveMinutes,
    val run: QuizRunState? = null,
    val result: RunResult? = null,
    val newPersonalBest: Boolean = false,
    val mastery: List<Mastery> = emptyList(),
    val answerTotals: AnswerTotals = AnswerTotals(),
    val completedRuns: List<RunEntity> = emptyList(),
    val streak: Streak = Streak(),
    val stamps: List<PassportStamp> = emptyList(),
    val leaveConfirmationRequested: Boolean = false,
    val resumeRunAfterLeaveCancellation: Boolean = false,
    val loadingMessage: String? = null,
    val selectedSection: String? = null,
    val packNotice: String? = null,
    val review: ReviewSessionState? = null,
    val importFlow: ImportFlowState? = null,
)

data class ReviewSessionState(
    val packId: String,
    val section: String?,
    val card: ReviewCard? = null,
    val loading: Boolean = true,
    val revealed: Boolean = false,
    val typedAnswer: String = "",
    val reviewedCount: Int = 0,
    val gradeCounts: Map<Rating, Int> = emptyMap(),
    val shownAtMillis: Long = 0,
) {
    val finished: Boolean get() = !loading && card == null
}

enum class ImportPhase { Reading, ChooseDecks, ChooseCategories, Importing, Done, Failed }

data class ImportDeckChoice(
    val deckId: Long,
    val title: String,
    val cardCount: Long,
    val sections: List<String>,
    val previewHtml: String?,
    val existingPackTitle: String?,
)

data class ImportFlowState(
    val phase: ImportPhase,
    val fileName: String,
    val decks: List<ImportDeckChoice> = emptyList(),
    val selectedDeckIds: Set<Long> = emptySet(),
    val categories: Map<Long, Pair<String, String>> = emptyMap(),
    val progressLabel: String = "",
    val progressFraction: Float? = null,
    val results: List<ImportedPack> = emptyList(),
    val failure: String? = null,
) {
    val selectedDecks: List<ImportDeckChoice> get() = decks.filter { it.deckId in selectedDeckIds }
    val decksNeedingCategory: List<ImportDeckChoice> get() = selectedDecks.filter { it.existingPackTitle == null }
}

class AppController(
    library: InstalledLibrary,
    private val packLibrary: PackLibrary,
    private val database: BertopiaDatabase,
    private val preferencesStore: PreferencesStore,
    private val scope: CoroutineScope,
    val debugBuild: Boolean,
    private val deviceFeedback: DeviceFeedback = NoDeviceFeedback,
    private val packStore: PackStore,
    private val filePicker: PackFilePicker = NoPackFilePicker,
    packSharer: PackSharer = NoPackSharer,
    imagePicker: ImagePicker = NoImagePicker,
) {
    private var geography: InstalledLibrary = library
    val catalog: Catalog get() = geography.catalog
    val flagMediaDirectory: String = library.flagMediaDirectory
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var lastRunPersistence: Job? = null
    private val monotonicOrigin = TimeSource.Monotonic.markNow()
    private val monotonicClock = MonotonicClock { monotonicOrigin.elapsedNow().inWholeMilliseconds }
    private val wallClock = WallClock { Clock.System.now().toEpochMilliseconds() }
    private val localDateProvider = LocalDateProvider {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toEpochDays().toInt()
    }
    private var engine = engineFor(catalog)
    private var importPlan: ImportPlan? = null
    @Volatile
    private var importCancelRequested = false
    private val _state = MutableStateFlow(BertopiaAppState())
    val state: StateFlow<BertopiaAppState> = _state.asStateFlow()
    private val _installedPacks = MutableStateFlow<List<PackSummary>>(emptyList())
    val installedPacks: StateFlow<List<PackSummary>> = _installedPacks.asStateFlow()
    private val installedPackIds: Set<String> get() = _installedPacks.value.map { it.packId }.toSet()
    private val _installedMixes = MutableStateFlow<List<MixSummary>>(emptyList())
    val installedMixes: StateFlow<List<MixSummary>> = _installedMixes.asStateFlow()
    private val _taxonomyState = MutableStateFlow<Taxonomy?>(null)
    val taxonomyState: StateFlow<Taxonomy?> = _taxonomyState.asStateFlow()
    private var knownMixes: List<Mix> = BundledMixes.all
    val store = StoreController(
        packStore = packStore,
        scope = scope,
        debugBuild = debugBuild,
        packSharer = packSharer,
        onPackInstalled = { packId -> mutex.withLock { reloadGeographyIfChanged(packId); refreshProgress() } },
        onIndexChanged = ::useStoreIndex,
    )

    fun mediaDirectory(packId: String): String = packStore.mediaDirectory(packId)

    private var editorReturn: Destination = Destination.Home
    val editor = PackEditingController(
        packStore,
        scope,
        imagePicker,
        onLibraryChanged = { mutex.withLock { refreshProgress() } },
        onOpenPack = { packId -> openPack(packId) },
        onLeave = { leaveEditor() },
    )

    fun openCreatePack() = enterEditor(Destination.Home) { editor.startCreatingPack() }

    fun openPackDetailsEditor(packId: String) = enterEditor(Destination.PackHome(packId)) { editor.openPackDetails(packId) }

    fun openCardEditor(pack: PackSummary) = enterEditor(Destination.PackHome(pack.packId)) { editor.openCards(pack.packId, pack.title) }

    fun makePackCopy(packId: String) = enterEditor(Destination.PackHome(packId)) { editor.makeCopy(packId) }

    private fun enterEditor(returnTo: Destination, open: () -> Unit) {
        editorReturn = returnTo
        open()
        _state.value = _state.value.copy(destination = Destination.Editor)
    }

    private fun leaveEditor() {
        if (_state.value.destination != Destination.Editor) return
        val target = editorReturn
        val stillInstalled = target !is Destination.PackHome || _installedPacks.value.any { it.packId == target.packId }
        _state.value = _state.value.copy(destination = if (stillInstalled) target else Destination.Home)
    }

    init {
        scope.launch { bootstrap() }
        scope.launch {
            var ticksSinceSnapshot = 0
            while (isActive) {
                delay(CLOCK_TICK_MILLIS)
                val current = _state.value.run
                if (current?.phase == RunPhase.Answering) {
                    val advanced = engine.advanceClocks(current)
                    if (advanced.sessionExpired) {
                        acceptTransition(engine.finishExpiredRun(advanced))
                    } else {
                        _state.value = _state.value.copy(run = advanced)
                        ticksSinceSnapshot += 1
                        if (ticksSinceSnapshot >= TICKS_BETWEEN_CLOCK_SNAPSHOTS) {
                            ticksSinceSnapshot = 0
                            persistSnapshot(advanced)
                        }
                    }
                }
            }
        }
    }

    private suspend fun bootstrap() = mutex.withLock {
        val preferences = preferencesStore.values.first()
        preferences.packLevels.forEach { (packId, level) -> packStore.chooseLevel(packId, level) }
        publishPacks(packStore.summaries())
        if (_taxonomyState.value == null) _taxonomyState.value = BundledTaxonomy.taxonomy
        val mastery = packStore.mastery()
        val answerTotals = database.progressReader().answerTotalsAcrossPacksNow()
        val runs = database.progressReader().completedRunsNow()
        val streak = database.progressReader().streakNow()?.toStreak() ?: Streak()
        val stamps = readStamps()
        val snapshot = database.progressReader().activeRun()
        val restored = snapshot?.let {
            runCatching {
                val decoded = json.decodeFromString<ActiveRunSnapshot>(it.snapshotJson)
                engine = engineFor(catalogFor(decoded.state.configuration.scope))
                engine.restoreRun(decoded)
            }.getOrNull()
        }
        if (snapshot != null && restored == null) database.runWriter().clearActiveRun()
        val hasStartedTrivia = preferences.hasStartedTrivia || restored != null || answerTotals.answeredQuestions > 0 || runs.isNotEmpty()
        if (hasStartedTrivia && !preferences.hasStartedTrivia) preferencesStore.markTriviaStarted()
        _state.value = BertopiaAppState(
            destination = if (restored == null) Destination.Home else Destination.Recovery,
            firstLaunch = !hasStartedTrivia,
            preferences = preferences,
            selectedDuration = enumValueOrDefault(preferences.duration, QuizDuration.FiveMinutes),
            run = restored,
            mastery = mastery,
            answerTotals = answerTotals,
            completedRuns = runs,
            streak = streak,
            stamps = stamps,
        )
        scope.launch {
            IncomingPackFiles.pendingPath.collect { path ->
                if (path != null) IncomingPackFiles.take()?.let(::importFile)
            }
        }
    }

    fun watchLibraryForExternalChanges() {
        scope.launch {
            while (isActive) {
                delay(LIBRARY_WATCH_MILLIS)
                if (packStore.takeExternalChanges()) mutex.withLock { refreshProgress() }
            }
        }
    }

    private fun publishPacks(packs: List<PackSummary>) {
        _installedPacks.value = packs
        val ids = packs.map { it.packId }.toSet()
        _installedMixes.value = knownMixes.map { it.toSummary(ids) }
    }

    private fun engineFor(runCatalog: Catalog) =
        QuizEngine(runCatalog, monotonicClock, wallClock, localDateProvider, allowUnapprovedContent = debugBuild)

    private suspend fun catalogFor(playScope: PlayScope): Catalog {
        val sections = (playScope as? PlayScope.Pack)?.sections.orEmpty()
        val genericCatalogs = playScope.packIds
            .filter { it !in BuiltInPacks.GEOGRAPHY }
            .map { packStore.catalogFor(it, sections) }
        val geographyCatalogs = if (playScope.packIds.any { it in BuiltInPacks.GEOGRAPHY }) listOf(catalog) else emptyList()
        return combineCatalogs(catalog.contentVersion, geographyCatalogs + genericCatalogs)
    }

    fun start() {
        val current = _state.value
        val lastScope = current.result?.configuration?.scope ?: current.run?.configuration?.scope
        beginRun(
            QuizConfiguration(
                enumValueOrDefault(current.preferences.duration, QuizDuration.FiveMinutes),
                lastScope ?: PlayScope.Pack(BuiltInPacks.FLAGS),
            ),
        )
    }

    fun openPack(packId: String) {
        val current = _state.value
        _state.value = current.copy(
            destination = Destination.PackHome(packId),
            selectedDuration = enumValueOrDefault(current.preferences.duration, QuizDuration.FiveMinutes),
            selectedSection = null,
            packNotice = null,
        )
    }

    fun chooseSection(section: String?) {
        _state.value = _state.value.copy(selectedSection = section)
    }

    fun saveStudySettings(packId: String, settings: FriendlyStudySettings) {
        scope.launch {
            packStore.saveStudySettings(packId, settings)
            publishPacks(packStore.summaries())
        }
    }

    fun chooseLevel(packId: String, level: LevelSetting) {
        scope.launch {
            preferencesStore.savePackLevel(packId, level)
            packStore.chooseLevel(packId, level)
            publishPacks(packStore.summaries())
        }
    }

    fun openMix(mixId: String) {
        val current = _state.value
        _state.value = current.copy(
            destination = Destination.MixHome(mixId),
            selectedDuration = enumValueOrDefault(current.preferences.duration, QuizDuration.FiveMinutes),
        )
    }

    fun startMixRun(mixId: String, duration: QuizDuration) {
        val mix = knownMixes.firstOrNull { it.mixId == mixId } ?: return
        val mixScope = mix.playScope(installedPackIds) ?: return
        rememberDuration(duration)
        beginRun(QuizConfiguration(duration, mixScope))
    }

    fun choosePackDuration(duration: QuizDuration) {
        _state.value = _state.value.copy(selectedDuration = duration)
    }

    fun playPack(pack: PackSummary) {
        val duration = _state.value.selectedDuration
        rememberDuration(duration)
        val section = _state.value.selectedSection
        beginRun(QuizConfiguration(duration, PlayScope.Pack(pack.packId, listOfNotNull(section))))
    }

    private fun rememberDuration(duration: QuizDuration) {
        scope.launch {
            preferencesStore.saveDuration(duration)
            val saved = preferencesStore.values.first()
            _state.value = _state.value.copy(preferences = saved)
        }
    }

    fun back() { handleBack() }

    fun handleBack(): Boolean {
        val current = _state.value
        if (current.leaveConfirmationRequested) {
            cancelLeave()
            return true
        }
        return when (val destination = current.destination) {
            Destination.Bootstrap, Destination.Home -> false
            is Destination.PackHome, is Destination.MixHome -> {
                _state.value = current.copy(destination = Destination.Home)
                true
            }
            is Destination.Review -> {
                finishReview()
                true
            }
            Destination.Import -> {
                closeImport()
                true
            }
            Destination.Recovery -> {
                requestLeave()
                true
            }
            Destination.Quiz -> {
                if (current.run?.phase == RunPhase.HintChooser) cancelHint() else requestLeave()
                true
            }
            Destination.Results, Destination.Progress, Destination.Settings -> {
                _state.value = current.copy(destination = Destination.Home)
                true
            }
            Destination.Debug -> {
                if (current.run?.phase == RunPhase.Paused) resume() else _state.value = current.copy(destination = Destination.Settings)
                true
            }
            Destination.Store -> {
                if (!store.back()) _state.value = current.copy(destination = Destination.Home)
                true
            }
            Destination.Editor -> {
                if (!editor.back()) leaveEditor()
                true
            }
            Destination.Privacy, Destination.Credits, Destination.Reset -> {
                _state.value = current.copy(destination = Destination.Settings)
                true
            }
        }
    }

    private fun beginRun(configuration: QuizConfiguration) {
        val returnTo = _state.value.destination
        _state.value = _state.value.copy(destination = Destination.Bootstrap, loadingMessage = "Shuffling the cards…", packNotice = null)
        scope.launch {
            val runCatalog = runCatching { catalogFor(configuration.scope) }.getOrNull()
            if (runCatalog == null || runCatalog.questions.none { it.packId in configuration.scope.packIds }) {
                _state.value = _state.value.copy(destination = returnTo, loadingMessage = null, packNotice = "Bert couldn't find cards to quiz you on here yet.")
                return@launch
            }
            val runEngine = engineFor(runCatalog)
            val now = wallClock.nowEpochMillis()
            val run = runCatching { runEngine.startRun("run-$now", configuration, now xor monotonicClock.nowMillis(), _state.value.mastery) }
                .getOrNull()
            if (run == null) {
                _state.value = _state.value.copy(destination = returnTo, loadingMessage = null, packNotice = "Bert couldn't start a round from these cards yet.")
                return@launch
            }
            engine = runEngine
            _state.value = _state.value.copy(destination = Destination.Quiz, firstLaunch = false, run = run, result = null, loadingMessage = null)
            preferencesStore.markTriviaStarted()
            persistSnapshot(run)
        }
    }

    fun revealFlipAnswer() {
        if (mutateRun(engine::revealFlipAnswer)) persistSnapshot(_state.value.run)
    }

    fun selfGrade(remembered: Boolean) {
        if (mutateRun { engine.answerQuestion(it, SubmittedAnswer.SelfGrade(remembered)) }) {
            playAnswerFeedback()
            persistAcceptedAnswer()
        }
    }

    fun resume() {
        val run = _state.value.run ?: return
        val resumed = engine.resumeRun(run)
        _state.value = _state.value.copy(destination = destinationFor(resumed), run = resumed)
        persistSnapshot(resumed)
    }

    fun answerChoice(choiceId: String) {
        if (mutateRun { engine.answerQuestion(it, SubmittedAnswer.ChoiceAnswer(choiceId)) }) {
            playAnswerFeedback()
            persistAcceptedAnswer()
        }
    }

    fun placeLetter(tileId: String) = mutateRun { engine.placeLetter(it, tileId) }

    fun removeLetter(tileId: String) = mutateRun { state ->
        val slot = state.activeQuestion.placements.first { it.tileId == tileId }.slotIndex
        engine.removeLetter(state, slot)
    }

    fun clearLetters() = mutateRun(engine::clearLetters)

    fun submitLetters() {
        if (mutateRun(engine::submitLetters)) {
            playAnswerFeedback()
            persistAcceptedAnswer()
        }
    }

    fun openHint() = mutateRun(engine::openHintChooser)
    fun cancelHint() = mutateRun { state -> engine.resumeRun(state.copy(phase = RunPhase.Paused, pausedFromPhase = RunPhase.Answering)) }
    fun chooseHint(type: HintType) {
        if (mutateRun { engine.chooseHint(it, type) }) persistSnapshot(_state.value.run)
    }

    fun continueRun() {
        val before = _state.value.run ?: return
        val next = when (before.phase) {
            RunPhase.Feedback -> engine.dismissFeedback(before, _state.value.mastery)
            RunPhase.FunFact -> engine.continueFromFact(before, _state.value.mastery)
            else -> return
        }
        acceptTransition(next)
    }

    fun finishUnlimited() {
        val run = _state.value.run ?: return
        acceptTransition(engine.finishUnlimitedRun(run, _state.value.mastery))
    }

    fun requestLeave() {
        val current = _state.value
        val run = current.run ?: return
        if (run.status != RunStatus.Active || current.leaveConfirmationRequested) return
        val shouldResumeOnCancel = run.phase != RunPhase.Paused
        val paused = if (shouldResumeOnCancel) engine.pauseForInterruption(run) else run
        _state.value = current.copy(
            run = paused,
            leaveConfirmationRequested = true,
            resumeRunAfterLeaveCancellation = shouldResumeOnCancel,
        )
        persistSnapshot(paused)
    }

    fun cancelLeave() {
        val current = _state.value
        if (!current.leaveConfirmationRequested) return
        val run = current.run
        val resumed = if (
            current.resumeRunAfterLeaveCancellation &&
            run?.status == RunStatus.Active &&
            run.phase == RunPhase.Paused
        ) {
            engine.resumeRun(run)
        } else {
            run
        }
        _state.value = current.copy(
            run = resumed,
            leaveConfirmationRequested = false,
            resumeRunAfterLeaveCancellation = false,
        )
        persistSnapshot(resumed)
    }

    fun confirmLeave() { abandon() }

    fun abandon() {
        val run = _state.value.run ?: return
        val abandoned = if (run.status == RunStatus.Active) engine.abandonRun(run) else run
        if (abandoned.status != RunStatus.Abandoned) return
        val summary = engine.abandonedRunSummary(
            abandoned,
            improvedMasteryItems = abandoned.attempts
                .flatMap { attempt -> attempt.countryIds.map { it to attempt.skill } }
                .distinct()
                .size,
        )
        val activityDays = abandoned.attempts.map { it.localEpochDay }.distinct().sorted()
        val activityStreak = activityDays.fold(_state.value.streak, StreakRules::recordActivity)
        _state.value = _state.value.copy(
            destination = Destination.Results,
            run = abandoned,
            result = summary,
            newPersonalBest = false,
            streak = activityStreak,
            leaveConfirmationRequested = false,
            resumeRunAfterLeaveCancellation = false,
        )
        enqueueRunPersistence {
            database.runWriter().abandonRun(
                summary.toRunEntity(abandoned.configuration.scope.progressKey, json),
                activityStreak.takeIf { activityDays.isNotEmpty() }?.toEntity(),
            )
            refreshProgress()
        }
    }

    fun pauseForInterruption() {
        val run = _state.value.run ?: return
        if (run.status != RunStatus.Active || run.phase == RunPhase.Paused) return
        val paused = engine.pauseForInterruption(run)
        _state.value = _state.value.copy(run = paused)
        persistSnapshot(paused)
    }

    fun resumeAfterInterruption() {
        val run = _state.value.run ?: return
        if (_state.value.leaveConfirmationRequested) return
        if (_state.value.destination != Destination.Quiz || run.phase != RunPhase.Paused) return
        val resumed = engine.resumeRun(run)
        _state.value = _state.value.copy(run = resumed)
        persistSnapshot(resumed)
    }

    private fun mutateRun(change: (QuizRunState) -> QuizRunState): Boolean {
        val current = _state.value.run ?: return false
        val next = runCatching { change(current) }.getOrNull() ?: return false
        _state.value = _state.value.copy(destination = destinationFor(next), run = next)
        return true
    }

    private fun acceptTransition(next: QuizRunState) {
        when (next.status) {
            RunStatus.Completed -> complete(next)
            RunStatus.Abandoned -> {
                _state.value = _state.value.copy(run = next)
                abandon()
            }
            else -> {
                _state.value = _state.value.copy(destination = destinationFor(next), run = next)
                persistSnapshot(next)
            }
        }
    }

    private fun persistAcceptedAnswer() {
        val run = _state.value.run ?: return
        val attempt = run.attempts.lastOrNull() ?: return
        val question = run.activeQuestion.question
        enqueueRunPersistence {
            val activityStreak = StreakRules.recordActivity(_state.value.streak, attempt.localEpochDay)
            val netPoints = attempt.pointsAwarded - attempt.hintPointsDeducted
            val cardPackId = attempt.packId.ifEmpty { question.packId }
            database.runWriter().recordAcceptedAnswer(
                cardPackId = cardPackId,
                correct = attempt.correct,
                netPoints = netPoints,
                snapshot = run.toSnapshotEntity(engine, json),
                streak = activityStreak.toEntity(),
            )
            val rating = quizRating(question, attempt)
            val refreshed = attempt.countryIds.distinct().flatMap { itemId ->
                runCatching { packStore.recordQuizAnswer(cardPackId, itemId, rating, attempt.responseMillis) }.getOrDefault(emptyList())
            }
            val refreshedKeys = refreshed.map { it.countryId to it.skill }.toSet()
            val totals = _state.value.answerTotals
            _state.value = _state.value.copy(
                mastery = _state.value.mastery.filterNot { (it.countryId to it.skill) in refreshedKeys } + refreshed,
                streak = activityStreak,
                answerTotals = AnswerTotals(
                    answeredQuestions = totals.answeredQuestions + 1,
                    correctAnswers = totals.correctAnswers + if (attempt.correct) 1 else 0,
                    netPoints = totals.netPoints + netPoints,
                ),
            )
        }
    }

    private fun quizRating(question: Question, attempt: app.bertopia.domain.model.Attempt): Rating {
        val format = QuizAnswerFormat.of(question) ?: return if (attempt.correct) Rating.Good else Rating.Again
        return QuizGrading.ratingFor(
            QuizAnswerOutcome(
                correct = attempt.correct,
                timedOut = false,
                usedHint = attempt.hinted,
                answeredInOvertime = attempt.overtime,
                responseMillis = attempt.responseMillis,
                format = format,
            ),
        )
    }

    private fun playAnswerFeedback() {
        val run = _state.value.run ?: return
        val correct = run.feedback?.kind != app.bertopia.domain.model.FeedbackKind.Incorrect
        deviceFeedback.play(correct, _state.value.preferences.soundEnabled, _state.value.preferences.hapticsEnabled)
    }

    private fun persistSnapshot(run: QuizRunState?) {
        if (run == null || run.status != RunStatus.Active) return
        enqueueRunPersistence {
            database.runWriter().saveActiveRun(run.toSnapshotEntity(engine, json))
        }
    }

    private fun complete(run: QuizRunState) {
        val result = engine.runResult(run, improvedMasteryItems = run.attempts.map { it.countryIds.firstOrNull() to it.skill }.distinct().size)
        _state.value = _state.value.copy(destination = Destination.Results, run = run, result = result)
        enqueueRunPersistence {
                val reader = database.progressReader()
                val packId = run.configuration.scope.progressKey
                val oldBest = reader.personalBestNow(packId, run.configuration.duration.name)
                val isBest = result.score > (oldBest?.score ?: 0)
                val newStreak = StreakRules.recordCompletedRun(_state.value.streak, localDateProvider.currentEpochDay())
                val existingStamps = _state.value.stamps
                val answerTotals = reader.answerTotalsAcrossPacksNow()
                val mastery = _state.value.mastery
                val flagContinents = mastery.filter { it.skill.name.contains("Flag") && it.correctAnswers > 0 }
                    .flatMap { masteryItem -> catalog.countries.firstOrNull { it.id == masteryItem.countryId }?.continentIds.orEmpty() }.toSet()
                val summaries = packStore.summaries()
                publishPacks(summaries)
                val playedPack = (run.configuration.scope as? PlayScope.Pack)?.let { played -> summaries.firstOrNull { it.packId == played.packId } }
                val newlyEarned = PassportRules.newlyEarned(
                    PassportEvidence(
                        completedRunCount = reader.completedRunsNow().size + 1,
                        recognizedFlagContinents = flagContinents,
                        totalAnswered = answerTotals.answeredQuestions,
                        totalCorrect = answerTotals.correctAnswers,
                        confusedFlagMastery = lookalikeFlagMastery(mastery),
                        streak = newStreak,
                        packCardCount = playedPack?.cardCount ?: 0,
                        packMasteredCardCount = playedPack?.masteredCount ?: 0,
                    ),
                    existingStamps,
                    wallClock.nowEpochMillis(),
                ).filter { it.kind in offeredStampKinds }
                val touchedMasteryItems = run.attempts
                    .flatMap { attempt -> attempt.countryIds.map { it to attempt.skill } }
                    .distinct()
                    .size
                database.runWriter().completeRun(
                    result.copy(improvedMasteryItems = maxOf(result.improvedMasteryItems, touchedMasteryItems)).toRunEntity(packId, json),
                    newStreak.toEntity(),
                    newlyEarned.map { it.toEntity(packId) },
                )
                _state.value = _state.value.copy(newPersonalBest = isBest, streak = newStreak, stamps = existingStamps + newlyEarned)
                refreshProgress()
        }
    }

    private fun enqueueRunPersistence(block: suspend () -> Unit) {
        val precedingWrite = lastRunPersistence
        lastRunPersistence = scope.launch {
            precedingWrite?.join()
            mutex.withLock { block() }
        }
    }

    private suspend fun reloadGeographyIfChanged(packId: String) {
        if (packId !in BuiltInPacks.GEOGRAPHY) return
        runCatching { packLibrary.readInstalled() }.onSuccess { geography = it }
    }

    private suspend fun refreshProgress() {
        val reader = database.progressReader()
        publishPacks(packStore.summaries())
        val mastery = packStore.mastery()
        val answerTotals = reader.answerTotalsAcrossPacksNow()
        val completedRuns = reader.completedRunsNow()
        val streak = reader.streakNow()?.toStreak() ?: Streak()
        val stamps = readStamps()
        _state.value = _state.value.copy(
            mastery = mastery,
            answerTotals = answerTotals,
            completedRuns = completedRuns,
            streak = streak,
            stamps = stamps,
        )
    }

    fun openStore(page: StorePage = StorePage.Home) {
        store.open(page)
        _state.value = _state.value.copy(destination = Destination.Store)
    }

    private fun useStoreIndex(taxonomy: Taxonomy, storeMixes: List<Mix>) {
        _taxonomyState.value = taxonomy
        knownMixes = (BundledMixes.all.associateBy { it.mixId } + storeMixes.associateBy { it.mixId }).values.toList()
        _installedMixes.value = knownMixes.map { it.toSummary(installedPackIds) }
    }

    fun navigate(destination: Destination) { _state.value = _state.value.copy(destination = destination) }

    fun toggleSetting(id: String, enabled: Boolean) {
        scope.launch {
            when (id) {
                "sound" -> preferencesStore.setSoundEnabled(enabled)
                "haptics" -> preferencesStore.setHapticsEnabled(enabled)
                "motion" -> preferencesStore.setMotionMode(if (enabled) "Reduced" else "System")
                "auto" -> preferencesStore.setAutoAdvanceEnabled(enabled)
            }
            val saved = preferencesStore.values.first()
            _state.value = _state.value.copy(preferences = saved)
        }
    }

    fun resetAll() {
        enqueueRunPersistence {
            database.runWriter().resetProgress()
            packStore.resetAllProgress()
            preferencesStore.resetAll()
            packStore.forgetLevels()
            publishPacks(packStore.summaries())
            _state.value = BertopiaAppState(destination = Destination.Home, mastery = packStore.mastery())
        }
    }

    fun startReview(packId: String) {
        val section = _state.value.selectedSection
        _state.value = _state.value.copy(destination = Destination.Review(packId), review = ReviewSessionState(packId, section))
        loadNextReviewCard()
    }

    private fun loadNextReviewCard() {
        val session = _state.value.review ?: return
        scope.launch {
            val card = runCatching { packStore.nextReviewCard(session.packId, session.section) }.getOrNull()
            val current = _state.value.review ?: return@launch
            if (current.packId != session.packId) return@launch
            _state.value = _state.value.copy(
                review = current.copy(card = card, loading = false, revealed = false, typedAnswer = "", shownAtMillis = monotonicClock.nowMillis()),
            )
        }
    }

    fun revealReviewAnswer() {
        val session = _state.value.review ?: return
        if (session.card == null || session.revealed) return
        _state.value = _state.value.copy(review = session.copy(revealed = true))
    }

    fun typeReviewAnswer(text: String) {
        val session = _state.value.review ?: return
        _state.value = _state.value.copy(review = session.copy(typedAnswer = text))
    }

    fun gradeReview(rating: Rating) {
        val session = _state.value.review ?: return
        val card = session.card ?: return
        if (!session.revealed || session.loading) return
        val answerMillis = (monotonicClock.nowMillis() - session.shownAtMillis).coerceAtLeast(0)
        _state.value = _state.value.copy(
            review = session.copy(
                loading = true,
                reviewedCount = session.reviewedCount + 1,
                gradeCounts = session.gradeCounts + (rating to (session.gradeCounts[rating] ?: 0) + 1),
            ),
        )
        enqueueRunPersistence {
            packStore.answerReview(session.packId, card.cardId, rating, answerMillis)
            val streak = StreakRules.recordActivity(_state.value.streak, localDateProvider.currentEpochDay())
            database.runWriter().saveStreak(streak.toEntity())
            _state.value = _state.value.copy(streak = streak)
            loadNextReviewCard()
        }
    }

    fun finishReview() {
        val packId = _state.value.review?.packId
        _state.value = _state.value.copy(destination = packId?.let { Destination.PackHome(it) } ?: Destination.Home, review = null)
        enqueueRunPersistence { refreshProgress() }
    }

    fun pickPackFile() {
        filePicker.pickPackFile { path -> if (path != null) importFile(path) }
    }

    fun importFile(path: String) {
        val run = _state.value.run
        if (run != null && run.status == RunStatus.Active) pauseForInterruption()
        importPlan?.let { stale -> scope.launch { packStore.discardImport(stale) } }
        importPlan = null
        importCancelRequested = false
        val fileName = path.substringAfterLast('/')
        _state.value = _state.value.copy(
            destination = Destination.Import,
            importFlow = ImportFlowState(ImportPhase.Reading, fileName, progressLabel = "Opening $fileName"),
        )
        scope.launch {
            try {
                val plan = packStore.planImport(packStore.importer(), path, importMonitor())
                importPlan = plan
                val decks = plan.decks.map { deck ->
                    ImportDeckChoice(deck.deckId, deck.title, deck.cardCount, deck.sections, deck.preview?.question, deck.existingPack?.title)
                }
                if (decks.isEmpty()) {
                    failImport("This file has no cards Bert can read.")
                    return@launch
                }
                updateImportFlow {
                    it.copy(
                        phase = ImportPhase.ChooseDecks,
                        decks = decks,
                        selectedDeckIds = decks.map(ImportDeckChoice::deckId).toSet(),
                        progressFraction = null,
                    )
                }
                if (!plan.needsDeckPicker) confirmImportDecks()
            } catch (cancelled: ImportCancelledException) {
                leaveImportFlow()
            } catch (failure: Throwable) {
                failImport("Bert couldn't open this file. It may not be an Anki deck.")
            }
        }
    }

    fun toggleImportDeck(deckId: Long) = updateImportFlow { flow ->
        val selected = if (deckId in flow.selectedDeckIds) flow.selectedDeckIds - deckId else flow.selectedDeckIds + deckId
        flow.copy(selectedDeckIds = selected)
    }

    fun confirmImportDecks() {
        val flow = _state.value.importFlow ?: return
        if (flow.selectedDeckIds.isEmpty()) return
        if (flow.decksNeedingCategory.isEmpty()) startImport() else updateImportFlow { it.copy(phase = ImportPhase.ChooseCategories) }
    }

    fun chooseImportCategory(deckId: Long, categoryId: String, subcategoryId: String) = updateImportFlow { flow ->
        if (categoryId.isEmpty()) flow.copy(categories = flow.categories - deckId)
        else flow.copy(categories = flow.categories + (deckId to (categoryId to subcategoryId)))
    }

    fun startImport() {
        val flow = _state.value.importFlow ?: return
        val plan = importPlan ?: return
        if (flow.decksNeedingCategory.any { deck -> flow.categories[deck.deckId]?.let { it.first.isBlank() || it.second.isBlank() } ?: true }) return
        val selections = flow.selectedDecks.map { deck ->
            val (category, subcategory) = flow.categories[deck.deckId] ?: ("" to "")
            DeckSelection(deck.deckId, deck.title, category, subcategory)
        }
        importPlan = null
        updateImportFlow { it.copy(phase = ImportPhase.Importing, progressLabel = "Getting ready", progressFraction = null) }
        scope.launch {
            try {
                val results = packStore.runImport(packStore.importer(), plan, selections, importMonitor())
                publishPacks(packStore.summaries())
                val mastery = packStore.mastery()
                _state.value = _state.value.copy(mastery = mastery)
                updateImportFlow { it.copy(phase = ImportPhase.Done, results = results) }
            } catch (cancelled: ImportCancelledException) {
                leaveImportFlow()
            } catch (failure: Throwable) {
                failImport("The import stopped before it finished. Nothing was added.")
            }
        }
    }

    fun cancelImport() {
        val phase = _state.value.importFlow?.phase ?: return
        if (phase == ImportPhase.Reading || phase == ImportPhase.Importing) importCancelRequested = true else closeImport()
    }

    fun closeImport() {
        val phase = _state.value.importFlow?.phase
        if (phase == ImportPhase.Reading || phase == ImportPhase.Importing) {
            importCancelRequested = true
            return
        }
        importPlan?.let { plan -> scope.launch { packStore.discardImport(plan) } }
        importPlan = null
        leaveImportFlow()
    }

    fun openImportedPack(packId: String) {
        importPlan = null
        _state.value = _state.value.copy(importFlow = null)
        openPack(packId)
    }

    private fun leaveImportFlow() {
        val run = _state.value.run
        val destination = if (run != null && run.status == RunStatus.Active) Destination.Recovery else Destination.Home
        _state.value = _state.value.copy(destination = destination, importFlow = null)
    }

    private fun failImport(message: String) = updateImportFlow { it.copy(phase = ImportPhase.Failed, failure = message) }

    private fun updateImportFlow(change: (ImportFlowState) -> ImportFlowState) {
        val flow = _state.value.importFlow ?: return
        _state.value = _state.value.copy(importFlow = change(flow))
    }

    private fun importMonitor(): ImportMonitor {
        var lastLabel = ""
        var lastFraction = -1f
        return ImportMonitor(
            onProgress = { progress ->
                val label = when (progress.stage) {
                    ImportStage.ReadingPackage -> "Reading the file"
                    ImportStage.CopyingCards -> "Copying cards"
                    ImportStage.CopyingMedia -> "Copying pictures and sounds"
                }
                val fraction = progress.total?.takeIf { it > 0 }?.let { (progress.completed.toFloat() / it).coerceIn(0f, 1f) }
                if (label != lastLabel || fraction == null || fraction - lastFraction >= 0.01f || fraction >= 1f) {
                    lastLabel = label
                    lastFraction = fraction ?: -1f
                    updateImportFlow { it.copy(progressLabel = label, progressFraction = fraction) }
                }
            },
            isCancelled = { importCancelRequested },
        )
    }

    val currentStreakDays: Int
        get() = StreakRules.currentDaysOn(_state.value.streak, localDateProvider.currentEpochDay())

    fun openDebugFromRun() {
        if (!debugBuild) return
        val run = _state.value.run ?: return
        if (run.phase != RunPhase.Answering) return
        val paused = engine.pauseForInterruption(run)
        _state.value = _state.value.copy(run = paused, destination = Destination.Debug)
        persistSnapshot(paused)
    }

    fun debugAction(id: String) {
        if (!debugBuild) return
        when (id) {
            "reset-first" -> scope.launch { preferencesStore.resetFirstLaunchAndConfiguration(); _state.value = _state.value.copy(firstLaunch = true, destination = Destination.Home) }
            "reset-progress" -> enqueueRunPersistence { database.runWriter().resetProgress(); packStore.resetAllProgress(); refreshProgress(); _state.value = _state.value.copy(destination = Destination.Home, run = null) }
            "reset-all" -> resetAll()
            "question-full" -> setDebugQuestionRemaining(QuizEngine.MULTIPLE_CHOICE_WINDOW_MILLIS)
            "question-half" -> setDebugQuestionRemaining(QuizEngine.MULTIPLE_CHOICE_WINDOW_MILLIS / 2)
            "question-almost-zero" -> setDebugQuestionRemaining(1_000)
            "question-zero" -> setDebugQuestionRemaining(0)
            "session-zero" -> updateRunDebug {
                if (it.configuration.duration == QuizDuration.Unlimited) it
                else it.copy(sessionRemainingMillis = 0, sessionExpired = true, runCompletionPending = true)
            }
            "score-0" -> updateRunDebug { it.copy(score = 0) }
            "score-1" -> updateRunDebug { it.copy(score = 1) }
            "score-25" -> updateRunDebug { it.copy(score = 25) }
            "score-100" -> updateRunDebug { it.copy(score = 100) }
        }
    }

    val settings: List<SettingToggleUi> get() = listOf(
        SettingToggleUi("sound", "Sound", "Short offline cues", _state.value.preferences.soundEnabled),
        SettingToggleUi("haptics", "Haptics", "Touch feedback where supported", _state.value.preferences.hapticsEnabled),
        SettingToggleUi("motion", "Reduce motion", "Use Bert's static poses", _state.value.preferences.motionMode == "Reduced"),
        SettingToggleUi("auto", "Auto-advance", "Move on after feedback", _state.value.preferences.autoAdvanceEnabled),
    )

    val debugActions: List<DebugActionUi> get() = listOf(
        DebugActionUi("score-0", "Set run score to 0"),
        DebugActionUi("score-1", "Set run score to 1"),
        DebugActionUi("score-25", "Set run score to 25"),
        DebugActionUi("score-100", "Set run score to 100"),
        DebugActionUi("question-full", "Set question timer to full"),
        DebugActionUi("question-half", "Set question timer to half"),
        DebugActionUi("question-almost-zero", "Set question timer to 1 second"),
        DebugActionUi("question-zero", "Set question timer to zero"),
        DebugActionUi("session-zero", "Expire session after this question"),
        DebugActionUi("reset-first", "Reset first launch"),
        DebugActionUi("reset-progress", "Reset progress", destructive = true),
        DebugActionUi("reset-all", "Reset all local data", destructive = true),
    )

    val progressGroups: List<ProgressGroupUi> get() {
        val packs = _installedPacks.value
        val totalCards = packs.sumOf { it.cardCount }.coerceAtLeast(1)
        val mastered = packs.sumOf { it.masteredCount }
        val familiar = packs.sumOf { it.familiarCount }
        val learning = packs.sumOf { it.learningCount } - familiar
        val counts = mapOf(
            MasteryLevel.New to packs.sumOf { it.cardCount } - mastered - familiar - learning,
            MasteryLevel.Learning to learning,
            MasteryLevel.Familiar to familiar,
            MasteryLevel.Mastered to mastered,
        )
        return MasteryLevel.entries.map { level ->
            val count = counts.getValue(level)
            ProgressGroupUi(level.name, count / totalCards.toFloat(), if (count == 1) "1 card" else "$count cards")
        }
    }

    private val geographyStamps: Map<PassportStampKind, PackStamp> get() = geography.geographyStamps
        .mapNotNull { stamp -> GEOGRAPHY_STAMP_KINDS[stamp.id]?.let { it to stamp } }
        .toMap()

    private val offeredStampKinds: List<PassportStampKind> get() =
        PassportStampKind.entries.filter {
            it !in GEOGRAPHY_STAMP_KINDS.values || it in geographyStamps
        }

    val stampUi: List<StampUi> get() = offeredStampKinds.map { kind ->
        val packStamp = geographyStamps[kind]
        StampUi(
            kind.name,
            packStamp?.title ?: kind.displayName(),
            packStamp?.description ?: kind.description(),
            _state.value.stamps.any { it.kind == kind },
        )
    }

    private fun lookalikeFlagMastery(mastery: List<Mastery>): List<Mastery> =
        catalog.countries.filter { it.similarityGroupIds.isNotEmpty() }.map { country ->
            mastery.firstOrNull { it.countryId == country.id && it.skill == Skill.FlagRecognition }
                ?: Mastery(country.id, Skill.FlagRecognition)
        }

    private fun setDebugQuestionRemaining(millis: Long) = updateRunDebug {
        it.copy(activeQuestion = it.activeQuestion.copy(questionRemainingMillis = millis.coerceAtMost(it.activeQuestion.questionWindowMillis)))
    }

    private fun updateRunDebug(change: (QuizRunState) -> QuizRunState) {
        val run = _state.value.run ?: return
        val updated = change(run)
        _state.value = _state.value.copy(run = updated)
        persistSnapshot(updated)
    }

    private suspend fun readStamps(): List<PassportStamp> =
        database.progressReader().allStampsNow().mapNotNull { it.toPassportStamp() }.distinctBy { it.kind }
}

private fun destinationFor(run: QuizRunState): Destination = when (run.phase) {
    RunPhase.Answering, RunPhase.Paused -> Destination.Quiz
    RunPhase.HintChooser -> Destination.Quiz
    RunPhase.Feedback -> Destination.Quiz
    RunPhase.FunFact -> Destination.Quiz
    RunPhase.Completed -> Destination.Results
}

private fun QuizRunState.toSnapshotEntity(engine: QuizEngine, json: Json) =
    engine.snapshotActiveRun(this).toEntity(configuration.scope.progressKey, json)

private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: fallback

private const val CLOCK_TICK_MILLIS = 100L
private const val LIBRARY_WATCH_MILLIS = 1_000L
private const val TICKS_BETWEEN_CLOCK_SNAPSHOTS = 50

private val GEOGRAPHY_STAMP_KINDS = mapOf(
    "flags-from-every-continent" to PassportStampKind.FlagsFromEveryContinent,
    "confused-flags-mastered" to PassportStampKind.ConfusedFlagsMastered,
)

private fun PassportStampKind.displayName() = when (this) {
    PassportStampKind.FirstCompletedRun -> "First expedition"
    PassportStampKind.FlagsFromEveryContinent -> "Around the world"
    PassportStampKind.SustainedAccuracy -> "Sharp mind"
    PassportStampKind.ConfusedFlagsMastered -> "Lookalike detective"
    PassportStampKind.SevenActiveDays -> "Seven-day curiosity"
    PassportStampKind.PackMastered -> "Pack mastered"
}

private fun PassportStampKind.description() = when (this) {
    PassportStampKind.FirstCompletedRun -> "Complete a scored run"
    PassportStampKind.FlagsFromEveryContinent -> "Recognize flags from all six regions"
    PassportStampKind.SustainedAccuracy -> "Reach 80% over 50 answers"
    PassportStampKind.ConfusedFlagsMastered -> "Master the confusing flag groups"
    PassportStampKind.SevenActiveDays -> "Complete runs on seven consecutive days"
    PassportStampKind.PackMastered -> "Master every card in a pack"
}
