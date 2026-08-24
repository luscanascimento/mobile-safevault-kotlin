package com.safevault.app.domain.model

/** User-configurable appearance mode. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Idle auto-lock durations offered in Settings.
 * [millis] is the amount of background/idle time before the vault re-locks.
 */
enum class AutoLockTimeout(val millis: Long) {
    IMMEDIATELY(0L),
    THIRTY_SECONDS(30_000L),
    ONE_MINUTE(60_000L),
    FIVE_MINUTES(5 * 60_000L),
    TEN_MINUTES(10 * 60_000L);

    companion object {
        fun fromMillis(value: Long): AutoLockTimeout =
            entries.firstOrNull { it.millis == value } ?: ONE_MINUTE
    }
}

/** Aggregate of all persisted user preferences. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val autoLockTimeout: AutoLockTimeout = AutoLockTimeout.ONE_MINUTE,
)
