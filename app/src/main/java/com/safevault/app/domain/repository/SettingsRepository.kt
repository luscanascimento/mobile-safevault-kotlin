package com.safevault.app.domain.repository

import com.safevault.app.domain.model.AppSettings
import com.safevault.app.domain.model.AutoLockTimeout
import com.safevault.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/** Persistent user-preference store. */
interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setAutoLockTimeout(timeout: AutoLockTimeout)
}
