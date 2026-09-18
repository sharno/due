package dev.sharno.due

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

data class DueSettings(
    val remindersEnabled: Boolean = true,
    val automaticBackupConfigured: Boolean = false,
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
