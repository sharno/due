package dev.sharno.due

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class DueSettings(
    val remindersEnabled: Boolean = true,
    val automaticBackupConfigured: Boolean = false,
)

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
}

/**
 * Appearance preferences.
 *
 * Deliberately separate from [DueSettings]: every emission of that flow re-runs alarm scheduling and
 * rewrites the encrypted automatic backup, so a value that changes while a colour picker is being
 * dragged must not live there.
 */
data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val seedArgb: Int = DEFAULT_SEED_ARGB,
) {
    companion object {
        /** The orange the app used before theming existed. */
        const val DEFAULT_SEED_ARGB: Int = 0xFFE65100.toInt()
    }
}

/** View preferences that must survive a restart but must not touch alarms or backups. */
data class UiPreferences(
    val taskFilter: TaskFilter = TaskFilter.TODO,
)

data class AutomaticBackupConfiguration(
    val uri: Uri,
    val passphrase: String,
)

private val Context.dueSettingsDataStore by preferencesDataStore(name = "settings")
private val Context.dueBackupDataStore by preferencesDataStore(name = "automatic_backup")

class SettingsRepository(private val context: Context) {
    private val remindersEnabledKey = booleanPreferencesKey("reminders_enabled")
    private val automaticBackupUriKey = stringPreferencesKey("automatic_backup_uri")
    private val automaticBackupPassphraseKey = stringPreferencesKey("automatic_backup_passphrase")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val themeSeedKey = intPreferencesKey("theme_seed")
    private val taskFilterKey = stringPreferencesKey("task_filter")

    private val applicationContext = context.applicationContext

    val settings: Flow<DueSettings> = combine(
        applicationContext.dueSettingsDataStore.data,
        applicationContext.dueBackupDataStore.data,
    ) { settings, backup ->
        DueSettings(
            remindersEnabled = settings[remindersEnabledKey] ?: true,
            automaticBackupConfigured = backup[automaticBackupUriKey] != null &&
                backup[automaticBackupPassphraseKey] != null,
        )
    }

    val themeSettings: Flow<ThemeSettings> = applicationContext.dueSettingsDataStore.data.map { preferences ->
        ThemeSettings(
            mode = preferences[themeModeKey]
                ?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
                ?: ThemeMode.SYSTEM,
            seedArgb = preferences[themeSeedKey] ?: ThemeSettings.DEFAULT_SEED_ARGB,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        applicationContext.dueSettingsDataStore.edit { preferences ->
            preferences[themeModeKey] = mode.name
        }
    }

    suspend fun setThemeSeed(argb: Int) {
        applicationContext.dueSettingsDataStore.edit { preferences ->
            preferences[themeSeedKey] = argb
        }
    }

    val uiPreferences: Flow<UiPreferences> = applicationContext.dueSettingsDataStore.data.map { preferences ->
        UiPreferences(
            taskFilter = preferences[taskFilterKey]
                ?.let { stored -> TaskFilter.entries.firstOrNull { it.name == stored } }
                ?: TaskFilter.TODO,
        )
    }

    suspend fun setTaskFilter(filter: TaskFilter) {
        applicationContext.dueSettingsDataStore.edit { preferences ->
            preferences[taskFilterKey] = filter.name
        }
    }

    suspend fun setRemindersEnabled(enabled: Boolean) {
        applicationContext.dueSettingsDataStore.edit { preferences ->
            preferences[remindersEnabledKey] = enabled
        }
    }

    suspend fun configureAutomaticBackup(uri: Uri, passphrase: String) {
        require(passphrase.isNotEmpty()) { "A backup passphrase is required" }
        val protectedPassphrase = BackupPassphraseProtector.encrypt(passphrase)
        applicationContext.dueBackupDataStore.edit { preferences ->
            preferences[automaticBackupUriKey] = uri.toString()
            preferences[automaticBackupPassphraseKey] = protectedPassphrase
        }
    }

    suspend fun clearAutomaticBackup() {
        applicationContext.dueBackupDataStore.edit { preferences ->
            preferences.remove(automaticBackupUriKey)
            preferences.remove(automaticBackupPassphraseKey)
        }
    }

    suspend fun automaticBackupConfiguration(): AutomaticBackupConfiguration? {
        val preferences = applicationContext.dueBackupDataStore.data.first()
        val uri = preferences[automaticBackupUriKey]?.let(Uri::parse) ?: return null
        val protectedPassphrase = preferences[automaticBackupPassphraseKey] ?: return null
        return AutomaticBackupConfiguration(
            uri = uri,
            passphrase = BackupPassphraseProtector.decrypt(protectedPassphrase),
        )
    }
}
