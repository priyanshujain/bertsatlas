package app.bertsatlas.domain

import app.bertsatlas.domain.model.ActiveRunSnapshot
import app.bertsatlas.domain.model.Attempt
import app.bertsatlas.domain.model.Mastery
import app.bertsatlas.domain.model.PassportStamp
import app.bertsatlas.domain.model.ProgressSummary
import app.bertsatlas.domain.model.QuizConfiguration
import app.bertsatlas.domain.model.QuizDuration
import app.bertsatlas.domain.model.RunResult
import app.bertsatlas.domain.model.Streak
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

interface ProgressReader {
    suspend fun mastery(packId: String): List<Mastery>
    fun observeProgress(packId: String): Flow<ProgressSummary>
}

interface ProgressWriter {
    suspend fun recordAcceptedAnswer(
        packId: String,
        attempt: Attempt,
        updatedMastery: List<Mastery>,
        activeRunSnapshot: ActiveRunSnapshot,
        streak: Streak,
    )

    suspend fun recordCompletedRun(
        packId: String,
        result: RunResult,
        streak: Streak,
        newlyEarnedStamps: List<PassportStamp>,
    )

    suspend fun recordAbandonedRun(packId: String, result: RunResult, streak: Streak? = null)
    suspend fun resetProgress()
}

interface ActiveRunRepository {
    suspend fun loadActiveRun(): ActiveRunSnapshot?
    suspend fun saveActiveRun(packId: String, snapshot: ActiveRunSnapshot)
    suspend fun clearActiveRun(runId: String)
}

@Serializable
data class PlayerSettings(
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val motionEnabled: Boolean = true,
    val automaticAdvanceEnabled: Boolean = true,
)

interface LaunchPreferences {
    suspend fun hasStartedTrivia(): Boolean
    suspend fun markTriviaStarted()
}

interface QuizConfigurationPreferences {
    suspend fun loadConfiguration(): QuizConfiguration
    suspend fun saveDuration(duration: QuizDuration)
}

interface PlayerSettingsRepository {
    fun observeSettings(): Flow<PlayerSettings>
    suspend fun saveSettings(settings: PlayerSettings)
}

interface AudioPlayer {
    fun playCorrect()
    fun playIncorrect()
    fun playOvertime()
}

interface HapticPlayer {
    fun warnQuestionTimeExpiring()
    fun signalCorrect()
    fun signalIncorrect()
}

fun interface ReducedMotionProvider {
    fun isReducedMotionEnabled(): Boolean
}
