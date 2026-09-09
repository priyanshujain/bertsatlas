package app.bertsatlas.domain

import app.bertsatlas.domain.model.ActiveRunSnapshot
import app.bertsatlas.domain.model.Attempt
import app.bertsatlas.domain.model.Catalog
import app.bertsatlas.domain.model.Mastery
import app.bertsatlas.domain.model.PassportStamp
import app.bertsatlas.domain.model.ProgressSummary
import app.bertsatlas.domain.model.QuizConfiguration
import app.bertsatlas.domain.model.QuizDuration
import app.bertsatlas.domain.model.QuizTopic
import app.bertsatlas.domain.model.RunResult
import app.bertsatlas.domain.model.Streak
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

interface CatalogProvider {
    suspend fun loadCatalog(): Catalog
}

interface ProgressReader {
    suspend fun mastery(): List<Mastery>
    fun observeProgress(): Flow<ProgressSummary>
}

interface ProgressWriter {
    suspend fun recordAcceptedAnswer(
        attempt: Attempt,
        updatedMastery: List<Mastery>,
        activeRunSnapshot: ActiveRunSnapshot,
    )

    suspend fun recordCompletedRun(
        result: RunResult,
        streak: Streak,
        newlyEarnedStamps: List<PassportStamp>,
    )

    suspend fun recordAbandonedRun(runId: String)
    suspend fun resetProgress()
}

interface ActiveRunRepository {
    suspend fun loadActiveRun(): ActiveRunSnapshot?
    suspend fun saveActiveRun(snapshot: ActiveRunSnapshot)
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
    suspend fun hasLaunchedBefore(): Boolean
    suspend fun markLaunched()
}

interface QuizConfigurationPreferences {
    suspend fun loadConfiguration(): QuizConfiguration
    suspend fun saveConfiguration(duration: QuizDuration, topic: QuizTopic)
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
