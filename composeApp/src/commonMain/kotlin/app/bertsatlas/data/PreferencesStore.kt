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
import app.bertsatlas.domain.model.QuizTopic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CancellationException
import okio.Path.Companion.toPath

data class SavedPreferences(
    val hasPreviouslyLaunched: Boolean = false,
    val duration: String = "FiveMinutes",
    val topic: String = "MostlyFlags",
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val motionMode: String = "System",
    val autoAdvanceEnabled: Boolean = true,
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
                hasPreviouslyLaunched = preferences[Keys.previouslyLaunched] ?: false,
                duration = preferences[Keys.duration] ?: "FiveMinutes",
                topic = preferences[Keys.topic] ?: "MostlyFlags",
                soundEnabled = preferences[Keys.soundEnabled] ?: true,
                hapticsEnabled = preferences[Keys.hapticsEnabled] ?: true,
                motionMode = preferences[Keys.motionMode] ?: "System",
                autoAdvanceEnabled = preferences[Keys.autoAdvanceEnabled] ?: true,
            )
        }

    override suspend fun hasLaunchedBefore(): Boolean = values.first().hasPreviouslyLaunched

    override suspend fun markLaunched() {
        dataStore.edit { it[Keys.previouslyLaunched] = true }
    }

    override suspend fun loadConfiguration(): QuizConfiguration {
        val preferences = values.first()
        return QuizConfiguration(
            duration = QuizDuration.entries.firstOrNull { it.name == preferences.duration } ?: QuizDuration.FiveMinutes,
            topic = QuizTopic.entries.firstOrNull { it.name == preferences.topic } ?: QuizTopic.MostlyFlags,
        )
    }

    override suspend fun saveConfiguration(duration: QuizDuration, topic: QuizTopic) {
        saveConfiguration(duration.name, topic.name)
    }

    suspend fun saveConfiguration(duration: String, topic: String) {
        require(QuizDuration.entries.any { it.name == duration }) { "Unknown quiz duration: $duration" }
        require(QuizTopic.entries.any { it.name == topic }) { "Unknown quiz topic: $topic" }
        dataStore.edit {
            it[Keys.duration] = duration
            it[Keys.topic] = topic
        }
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
        dataStore.edit { it.remove(Keys.previouslyLaunched) }
    }

    suspend fun resetConfiguration() {
        dataStore.edit {
            it.remove(Keys.duration)
            it.remove(Keys.topic)
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
            it.remove(Keys.previouslyLaunched)
            it.remove(Keys.duration)
            it.remove(Keys.topic)
        }
    }

    suspend fun resetAll() {
        dataStore.edit { it.clear() }
    }

    private object Keys {
        val previouslyLaunched = booleanPreferencesKey("previously_launched")
        val duration = stringPreferencesKey("quiz_duration")
        val topic = stringPreferencesKey("quiz_topic")
        val soundEnabled = booleanPreferencesKey("sound_enabled")
        val hapticsEnabled = booleanPreferencesKey("haptics_enabled")
        val motionMode = stringPreferencesKey("motion_mode")
        val autoAdvanceEnabled = booleanPreferencesKey("auto_advance_enabled")
    }
}

fun createPreferencesStore(filePath: String): PreferencesStore {
    val dataStore = PreferenceDataStoreFactory.createWithPath(
        produceFile = { filePath.toPath() },
    )
    return PreferencesStore(dataStore)
}
