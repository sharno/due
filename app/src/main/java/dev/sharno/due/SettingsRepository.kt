package dev.sharno.due

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class DueSettings(
    val remindersEnabled: Boolean = true,
)

private val Context.dueSettingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val remindersEnabledKey = booleanPreferencesKey("reminders_enabled")

    private val applicationContext = context.applicationContext

    val settings: Flow<DueSettings> = applicationContext.dueSettingsDataStore.data.map { preferences ->
        DueSettings(
            remindersEnabled = preferences[remindersEnabledKey] ?: true,
        )
    }

    suspend fun setRemindersEnabled(enabled: Boolean) {
        applicationContext.dueSettingsDataStore.edit { preferences ->
            preferences[remindersEnabledKey] = enabled
        }
    }
}
