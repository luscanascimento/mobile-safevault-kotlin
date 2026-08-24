package com.safevault.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.safevault.app.domain.model.AppSettings
import com.safevault.app.domain.model.AutoLockTimeout
import com.safevault.app.domain.model.ThemeMode
import com.safevault.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Preference persistence via Jetpack DataStore.
 *
 * These values are non-secret UI preferences, so a plain preferences DataStore
 * is appropriate; the sensitive material (note content, encryption key) lives
 * in the encrypted Room store and the Android Keystore respectively.
 */
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[KEY_THEME_MODE]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: true,
            autoLockTimeout = AutoLockTimeout.fromMillis(
                prefs[KEY_AUTO_LOCK] ?: AutoLockTimeout.ONE_MINUTE.millis,
            ),
        )
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    override suspend fun setAutoLockTimeout(timeout: AutoLockTimeout) {
        dataStore.edit { it[KEY_AUTO_LOCK] = timeout.millis }
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_AUTO_LOCK = longPreferencesKey("auto_lock_millis")
    }
}
