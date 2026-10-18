package app.bertsatlas.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.bertsatlas.domain.LaunchPreferences
import app.bertsatlas.domain.PlayerSettings
import app.bertsatlas.domain.PlayerSettingsRepository
import app.bertsatlas.domain.QuizConfigurationPreferences
import app.bertsatlas.domain.model.QuizConfiguration
import app.bertsatlas.domain.model.QuizDuration
import app.bertsatlas.domain.pack.LevelSetting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CancellationException
import okio.Path.Companion.toPath

data class SavedPreferences(
    val hasStartedTrivia: Boolean = false,
    val duration: String = "FiveMinutes",
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val motionMode: String = "System",
    val autoAdvanceEnabled: Boolean = true,
    val packLevels: Map<String, LevelSetting> = emptyMap(),
)

class PreferencesStore(
    private val dataStore: DataStore<Preferences>,
) : LaunchPreferences, QuizConfigurationPreferences, PlayerSettingsRepository {
    val values: Flow<SavedPreferences> = dataStore.data
        .catch { exception ->
            if (exception is CancellationException) throw exception
            emit(emptyPreferences())
        }
        .map { preferences ->
            SavedPreferences(
                hasStartedTrivia = preferences[Keys.hasStartedTrivia] ?: false,
                duration = preferences[Keys.duration] ?: "FiveMinutes",
                soundEnabled = preferences[Keys.soundEnabled] ?: true,
                hapticsEnabled = preferences[Keys.hapticsEnabled] ?: true,
                motionMode = preferences[Keys.motionMode] ?: "System",
                autoAdvanceEnabled = preferences[Keys.autoAdvanceEnabled] ?: true,
                packLevels = preferences.asMap().entries.mapNotNull { (key, value) ->
                    val packId = key.name.removePrefix(Keys.PACK_LEVEL_PREFIX).takeIf { key.name.startsWith(Keys.PACK_LEVEL_PREFIX) }
                    val level = LevelSetting.entries.firstOrNull { it.name == value }
                    if (packId != null && level != null) packId to level else null
                }.toMap(),
            )
        }

    override suspend fun hasStartedTrivia(): Boolean = values.first().hasStartedTrivia

    override suspend fun markTriviaStarted() {
        dataStore.edit { it[Keys.hasStartedTrivia] = true }
    }

    override suspend fun loadConfiguration(): QuizConfiguration {
        val preferences = values.first()
        return QuizConfiguration(
            duration = QuizDuration.entries.firstOrNull { it.name == preferences.duration } ?: QuizDuration.FiveMinutes,
        )
    }

    override suspend fun saveDuration(duration: QuizDuration) {
        dataStore.edit { it[Keys.duration] = duration.name }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.soundEnabled] = enabled }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.hapticsEnabled] = enabled }
    }

    suspend fun setMotionMode(mode: String) {
        require(mode == "System" || mode == "Reduced") { "Unknown motion mode: $mode" }
        dataStore.edit { it[Keys.motionMode] = mode }
    }

    suspend fun setAutoAdvanceEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.autoAdvanceEnabled] = enabled }
    }

    suspend fun savePackLevel(packId: String, level: LevelSetting) {
        dataStore.edit { it[Keys.packLevel(packId)] = level.name }
    }

    override fun observeSettings(): Flow<PlayerSettings> = values.map { preferences ->
        PlayerSettings(
            soundEnabled = preferences.soundEnabled,
            hapticsEnabled = preferences.hapticsEnabled,
            motionEnabled = preferences.motionMode != "Reduced",
            automaticAdvanceEnabled = preferences.autoAdvanceEnabled,
        )
    }

    override suspend fun saveSettings(settings: PlayerSettings) {
        dataStore.edit {
            it[Keys.soundEnabled] = settings.soundEnabled
            it[Keys.hapticsEnabled] = settings.hapticsEnabled
            it[Keys.motionMode] = if (settings.motionEnabled) "System" else "Reduced"
            it[Keys.autoAdvanceEnabled] = settings.automaticAdvanceEnabled
        }
    }

    suspend fun resetFirstLaunch() {
        dataStore.edit { it.remove(Keys.hasStartedTrivia) }
    }

    suspend fun resetConfiguration() {
        dataStore.edit {
            it.remove(Keys.duration)
        }
    }

    suspend fun resetSettings() {
        dataStore.edit {
            it.remove(Keys.soundEnabled)
            it.remove(Keys.hapticsEnabled)
            it.remove(Keys.motionMode)
            it.remove(Keys.autoAdvanceEnabled)
        }
    }

    suspend fun resetFirstLaunchAndConfiguration() {
        dataStore.edit {
            it.remove(Keys.hasStartedTrivia)
            it.remove(Keys.duration)
        }
    }

    suspend fun resetAll() {
        dataStore.edit { it.clear() }
    }

    private object Keys {
        val hasStartedTrivia = booleanPreferencesKey("has_started_trivia")
        val duration = stringPreferencesKey("quiz_duration")
        val soundEnabled = booleanPreferencesKey("sound_enabled")
        val hapticsEnabled = booleanPreferencesKey("haptics_enabled")
        val motionMode = stringPreferencesKey("motion_mode")
        val autoAdvanceEnabled = booleanPreferencesKey("auto_advance_enabled")
        const val PACK_LEVEL_PREFIX = "pack_level_"

        fun packLevel(packId: String) = stringPreferencesKey(PACK_LEVEL_PREFIX + packId)
    }
}

fun createPreferencesStore(filePath: String): PreferencesStore {
    val dataStore = PreferenceDataStoreFactory.createWithPath(
        produceFile = { filePath.toPath() },
    )
    return PreferencesStore(dataStore)
}
