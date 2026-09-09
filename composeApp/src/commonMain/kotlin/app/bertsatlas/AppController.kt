package app.bertsatlas

import app.bertsatlas.data.ActiveRunSnapshotEntity
import app.bertsatlas.data.AttemptEntity
import app.bertsatlas.data.BertsAtlasDatabase
import app.bertsatlas.data.MasteryEntity
import app.bertsatlas.data.PersonalBestEntity
import app.bertsatlas.data.PreferencesStore
import app.bertsatlas.data.RunEntity
import app.bertsatlas.data.SavedPreferences
import app.bertsatlas.data.StampEntity
import app.bertsatlas.data.StreakEntity
import app.bertsatlas.domain.LocalDateProvider
import app.bertsatlas.domain.MasteryRules
import app.bertsatlas.domain.MonotonicClock
import app.bertsatlas.domain.PassportEvidence
import app.bertsatlas.domain.PassportRules
import app.bertsatlas.domain.QuizEngine
import app.bertsatlas.domain.StreakRules
import app.bertsatlas.domain.WallClock
import app.bertsatlas.domain.model.ActiveRunSnapshot
import app.bertsatlas.domain.model.Catalog
import app.bertsatlas.domain.model.Continent
import app.bertsatlas.domain.model.HintType
import app.bertsatlas.domain.model.Mastery
import app.bertsatlas.domain.model.MasteryLevel
import app.bertsatlas.domain.model.PassportStamp
import app.bertsatlas.domain.model.PassportStampKind
import app.bertsatlas.domain.model.Question
import app.bertsatlas.domain.model.QuizConfiguration
import app.bertsatlas.domain.model.QuizDuration
import app.bertsatlas.domain.model.QuizRunState
import app.bertsatlas.domain.model.QuizTopic
import app.bertsatlas.domain.model.RunPhase
import app.bertsatlas.domain.model.RunResult
import app.bertsatlas.domain.model.RunStatus
import app.bertsatlas.domain.model.Streak
import app.bertsatlas.domain.model.SubmittedAnswer
import app.bertsatlas.ui.DebugActionUi
import app.bertsatlas.ui.ProgressGroupUi
import app.bertsatlas.ui.SettingToggleUi
import app.bertsatlas.ui.StampUi
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
    data class Options(val step: Int) : Destination
    data object Quiz : Destination
    data object Results : Destination
    data object Progress : Destination
    data object Settings : Destination
    data object Privacy : Destination
    data object Credits : Destination
    data object Reset : Destination
    data object Debug : Destination
}

data class AtlasAppState(
    val destination: Destination = Destination.Bootstrap,
    val firstLaunch: Boolean = true,
    val preferences: SavedPreferences = SavedPreferences(),
    val selectedDuration: QuizDuration = QuizDuration.FiveMinutes,
    val run: QuizRunState? = null,
    val result: RunResult? = null,
    val newPersonalBest: Boolean = false,
    val mastery: List<Mastery> = emptyList(),
    val attempts: List<AttemptEntity> = emptyList(),
    val completedRuns: List<RunEntity> = emptyList(),
    val streak: Streak = Streak(),
    val stamps: List<PassportStamp> = emptyList(),
    val leaveConfirmationRequested: Boolean = false,
    val resumeRunAfterLeaveCancellation: Boolean = false,
)

class AppController(
    val catalog: Catalog,
    private val database: BertsAtlasDatabase,
    private val preferencesStore: PreferencesStore,
    private val scope: CoroutineScope,
    val debugBuild: Boolean,
    private val deviceFeedback: DeviceFeedback = NoDeviceFeedback,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var lastRunPersistence: Job? = null
    private val monotonicOrigin = TimeSource.Monotonic.markNow()
    private val monotonicClock = MonotonicClock { monotonicOrigin.elapsedNow().inWholeMilliseconds }
    private val wallClock = WallClock { Clock.System.now().toEpochMilliseconds() }
    private val localDateProvider = LocalDateProvider {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toEpochDays().toInt()
    }
    private val engine = QuizEngine(catalog, monotonicClock, wallClock, localDateProvider, allowUnapprovedContent = debugBuild)
    private val _state = MutableStateFlow(AtlasAppState())
    val state: StateFlow<AtlasAppState> = _state.asStateFlow()

    init {
        scope.launch { bootstrap() }
        scope.launch {
            while (isActive) {
                delay(100)
                val current = _state.value.run
                if (current?.phase == RunPhase.Answering) {
                    val advanced = engine.advanceClocks(current)
                    if (advanced.sessionExpired) acceptTransition(engine.finishExpiredRun(advanced))
                    else _state.value = _state.value.copy(run = advanced)
                }
            }
        }
    }

    private suspend fun bootstrap() = mutex.withLock {
        val preferences = preferencesStore.values.first()
        val mastery = readMastery()
        val attempts = database.progressReader().attemptsNow()
        val runs = database.progressReader().completedRunsNow()
        val streak = database.progressReader().streakNow().toDomain()
        val stamps = database.progressReader().stampsNow().mapNotNull { it.toDomain() }
        val snapshot = database.progressReader().activeRun()
        val restored = snapshot?.let {
            runCatching { engine.restoreRun(json.decodeFromString<ActiveRunSnapshot>(it.snapshotJson)) }.getOrNull()
        }
        if (snapshot != null && restored == null) database.runWriter().clearActiveRun()
        val hasStartedTrivia = preferences.hasStartedTrivia || restored != null || attempts.isNotEmpty() || runs.isNotEmpty()
        if (hasStartedTrivia && !preferences.hasStartedTrivia) preferencesStore.markTriviaStarted()
        _state.value = AtlasAppState(
            destination = if (restored == null) Destination.Home else Destination.Recovery,
            firstLaunch = !hasStartedTrivia,
            preferences = preferences,
            selectedDuration = enumValueOrDefault(preferences.duration, QuizDuration.FiveMinutes),
            run = restored,
            mastery = mastery,
            attempts = attempts,
            completedRuns = runs,
            streak = streak,
            stamps = stamps,
        )
    }

    fun start() {
        val current = _state.value
        if (current.firstLaunch) {
            beginRun(QuizConfiguration())
        } else {
            beginRun(
                QuizConfiguration(
                    enumValueOrDefault(current.preferences.duration, QuizDuration.FiveMinutes),
                    enumValueOrDefault(current.preferences.topic, QuizTopic.MostlyFlags),
                ),
            )
        }
    }

    fun openOptions() { _state.value = _state.value.copy(destination = Destination.Options(1)) }

    fun chooseOption(id: String) {
        when (val destination = _state.value.destination) {
            is Destination.Options -> if (destination.step == 1) {
                _state.value = _state.value.copy(
                    selectedDuration = enumValueOrDefault(id, QuizDuration.FiveMinutes),
                    destination = Destination.Options(2),
                )
            } else {
                val topic = enumValueOrDefault(id, QuizTopic.MostlyFlags)
                val duration = _state.value.selectedDuration
                scope.launch {
                    preferencesStore.saveConfiguration(duration.name, topic.name)
                    val saved = preferencesStore.values.first()
                    _state.value = _state.value.copy(preferences = saved)
                    beginRun(QuizConfiguration(duration, topic))
                }
            }
            else -> Unit
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
            is Destination.Options -> {
                _state.value = current.copy(
                    destination = if (destination.step == 2) Destination.Options(1) else Destination.Home,
                )
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
            Destination.Privacy, Destination.Credits, Destination.Reset, Destination.Debug -> {
                _state.value = current.copy(destination = Destination.Settings)
                true
            }
        }
    }

    private fun beginRun(configuration: QuizConfiguration) {
        val now = wallClock.nowEpochMillis()
        val run = engine.startRun("run-$now", configuration, now xor monotonicClock.nowMillis(), _state.value.mastery)
        _state.value = _state.value.copy(destination = Destination.Quiz, firstLaunch = false, run = run, result = null)
        scope.launch { preferencesStore.markTriviaStarted() }
        persistSnapshot(run)
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
        _state.value = _state.value.copy(
            destination = Destination.Results,
            run = abandoned,
            result = summary,
            newPersonalBest = false,
            leaveConfirmationRequested = false,
            resumeRunAfterLeaveCancellation = false,
        )
        enqueueRunPersistence {
            database.runWriter().abandonRun(abandoned.toRunEntity(summary))
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
        if (next.status == RunStatus.Completed) complete(next)
        else {
            _state.value = _state.value.copy(destination = destinationFor(next), run = next)
            persistSnapshot(next)
        }
    }

    private fun persistAcceptedAnswer() {
        val run = _state.value.run ?: return
        val attempt = run.attempts.lastOrNull() ?: return
        enqueueRunPersistence {
            val before = _state.value.mastery
            val after = MasteryRules.applyAttemptToCountries(before, attempt)
            val changed = after.filter { updated -> before.none { it.countryId == updated.countryId && it.skill == updated.skill && it == updated } }
            database.runWriter().recordAcceptedAnswer(
                attempt.toEntity(json),
                changed.map { it.toEntity() },
                run.toSnapshotEntity(engine, json),
            )
            _state.value = _state.value.copy(mastery = after)
        }
    }

    private fun playAnswerFeedback() {
        val run = _state.value.run ?: return
        val correct = run.feedback?.kind != app.bertsatlas.domain.model.FeedbackKind.Incorrect
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
                val oldBest = reader.personalBestsNow().firstOrNull {
                    it.duration == run.configuration.duration.name && it.topic == run.configuration.topic.name
                }
                val isBest = oldBest == null || result.score > oldBest.score
                val newStreak = StreakRules.recordCompletedRun(_state.value.streak, localDateProvider.currentEpochDay())
                val existingStamps = _state.value.stamps
                val attempts = reader.attemptsNow()
                val mastery = _state.value.mastery
                val flagContinents = mastery.filter { it.skill.name.contains("Flag") && it.correctAnswers > 0 }
                    .flatMap { masteryItem -> catalog.countries.firstOrNull { it.id == masteryItem.countryId }?.continentIds.orEmpty() }.toSet()
                val newlyEarned = PassportRules.newlyEarned(
                    PassportEvidence(
                        completedRunCount = reader.completedRunsNow().size + 1,
                        recognizedFlagContinents = flagContinents,
                        totalAnswered = attempts.size,
                        totalCorrect = attempts.count { it.correct },
                        confusedFlagMastery = emptyList(),
                        streak = newStreak,
                    ),
                    existingStamps,
                    wallClock.nowEpochMillis(),
                )
                database.runWriter().completeRun(
                    run.toRunEntity(result),
                    if (isBest) PersonalBestEntity(run.configuration.duration.name, run.configuration.topic.name, result.score, run.runId) else null,
                    newStreak.toEntity(),
                    newlyEarned.map { StampEntity(it.kind.name, it.earnedAtEpochMillis) },
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

    private suspend fun refreshProgress() {
        val reader = database.progressReader()
        _state.value = _state.value.copy(
            mastery = readMastery(),
            attempts = reader.attemptsNow(),
            completedRuns = reader.completedRunsNow(),
            streak = reader.streakNow().toDomain(),
            stamps = reader.stampsNow().mapNotNull { it.toDomain() },
        )
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
            _state.value = _state.value.copy(preferences = preferencesStore.values.first())
        }
    }

    fun resetAll() {
        enqueueRunPersistence {
            database.runWriter().resetProgress()
            preferencesStore.resetAll()
            _state.value = AtlasAppState(destination = Destination.Home)
        }
    }

    fun debugAction(id: String) {
        if (!debugBuild) return
        when (id) {
            "reset-first" -> scope.launch { preferencesStore.resetFirstLaunchAndConfiguration(); _state.value = _state.value.copy(firstLaunch = true, destination = Destination.Home) }
            "reset-progress" -> enqueueRunPersistence { database.runWriter().resetProgress(); refreshProgress(); _state.value = _state.value.copy(destination = Destination.Home, run = null) }
            "reset-all" -> resetAll()
            "question-zero" -> updateRunDebug { it.copy(activeQuestion = it.activeQuestion.copy(questionRemainingMillis = 0)) }
            "session-zero" -> updateRunDebug {
                it.copy(sessionRemainingMillis = 0, sessionExpired = true, runCompletionPending = true)
            }
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
        DebugActionUi("score-100", "Set run score to 100"),
        DebugActionUi("question-zero", "Set question timer to zero"),
        DebugActionUi("session-zero", "Expire session after this question"),
        DebugActionUi("reset-first", "Reset first launch"),
        DebugActionUi("reset-progress", "Reset progress", destructive = true),
        DebugActionUi("reset-all", "Reset all local data", destructive = true),
    )

    val progressGroups: List<ProgressGroupUi> get() = MasteryLevel.entries.map { level ->
        val count = _state.value.mastery.count { it.level == level }
        ProgressGroupUi(level.name, count / (catalog.countries.size * 6f), "$count knowledge items")
    }

    val stampUi: List<StampUi> get() = PassportStampKind.entries.map { kind ->
        StampUi(kind.name, kind.displayName(), kind.description(), _state.value.stamps.any { it.kind == kind })
    }

    private fun updateRunDebug(change: (QuizRunState) -> QuizRunState) {
        val run = _state.value.run ?: return
        val updated = change(run)
        _state.value = _state.value.copy(run = updated)
        persistSnapshot(updated)
    }

    private suspend fun readMastery(): List<Mastery> = database.progressReader().masteryNow().mapNotNull { entity ->
        runCatching { json.decodeFromString<Mastery>(entity.recentOutcomes) }.getOrNull()
    }
}

private fun destinationFor(run: QuizRunState): Destination = when (run.phase) {
    RunPhase.Answering, RunPhase.Paused -> Destination.Quiz
    RunPhase.HintChooser -> Destination.Quiz
    RunPhase.Feedback -> Destination.Quiz
    RunPhase.FunFact -> Destination.Quiz
    RunPhase.Completed -> Destination.Results
}

private fun app.bertsatlas.domain.model.Attempt.toEntity(json: Json) = AttemptEntity(
    id = id,
    runId = runId,
    questionId = questionId,
    countryIdsJson = json.encodeToString(countryIds.toSet()),
    skill = skill.name,
    correct = correct,
    hinted = hinted,
    overtime = overtime,
    submittedAnswer = submittedAnswer,
    correctAnswer = correctAnswer,
    responseMillis = responseMillis,
    pointsAwarded = pointsAwarded,
    answeredAtEpochMillis = answeredAtEpochMillis,
    localEpochDay = localEpochDay,
    hintType = hintType?.name,
    hintPointsDeducted = hintPointsDeducted,
    questionWindowMillis = questionWindowMillis,
    questionRemainingMillis = questionRemainingMillis,
)

private fun Mastery.toEntity() = MasteryEntity(
    countryId, skill.name, level.name, attempts, unhintedCorrectAnswers, unhintedCorrectRunIds.size,
    unhintedCorrectEpochDays.size, nextReviewEpochDay?.toString().orEmpty(), Json.encodeToString(this),
)

private fun QuizRunState.toSnapshotEntity(engine: QuizEngine, json: Json) = engine.snapshotActiveRun(this).let {
    ActiveRunSnapshotEntity(schemaVersion = it.schemaVersion, catalogVersion = it.catalogVersion, snapshotJson = json.encodeToString(it))
}

private fun QuizRunState.toRunEntity(result: RunResult? = null) = RunEntity(
    id = runId,
    duration = configuration.duration.name,
    topic = configuration.topic.name,
    status = status.name,
    score = score,
    correctAnswers = attempts.count { it.correct },
    answeredQuestions = attempts.size,
    totalResponseMillis = attempts.sumOf { it.responseMillis },
    bestCorrectStreak = bestCorrectStreak,
    completedAtEpochMillis = result?.completedAtEpochMillis,
    completedLocalDate = result?.completedAtEpochMillis?.let { attempts.lastOrNull()?.localEpochDay?.toString() },
    completedTimeZoneId = result?.let { TimeZone.currentSystemDefault().id },
)

private fun Streak.toEntity() = StreakEntity(currentDays = currentDays, bestDays = bestDays, lastCompletionDate = lastCompletedEpochDay?.toString(), lastCompletionTimeZoneId = TimeZone.currentSystemDefault().id)
private fun StreakEntity?.toDomain() = this?.let { Streak(currentDays, bestDays, lastCompletionDate?.toIntOrNull()) } ?: Streak()
private fun StampEntity.toDomain() = runCatching { PassportStamp(PassportStampKind.valueOf(id), earnedAtEpochMillis) }.getOrNull()

private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: fallback

private fun PassportStampKind.displayName() = when (this) {
    PassportStampKind.FirstCompletedRun -> "First expedition"
    PassportStampKind.FlagsFromEveryContinent -> "Around the world"
    PassportStampKind.SustainedAccuracy -> "Sharp atlas"
    PassportStampKind.ConfusedFlagsMastered -> "Lookalike detective"
    PassportStampKind.SevenActiveDays -> "Seven-day curiosity"
}

private fun PassportStampKind.description() = when (this) {
    PassportStampKind.FirstCompletedRun -> "Complete a scored run"
    PassportStampKind.FlagsFromEveryContinent -> "Recognize flags from all six regions"
    PassportStampKind.SustainedAccuracy -> "Reach 80% over 50 answers"
    PassportStampKind.ConfusedFlagsMastered -> "Master the confusing flag groups"
    PassportStampKind.SevenActiveDays -> "Complete runs on seven consecutive days"
}
