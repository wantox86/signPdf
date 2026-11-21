package com.wantox86.signpdf.data.local

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

enum class ThemeMode(val storageKey: String, val nightMode: Int) {
    SYSTEM("system", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT("light", AppCompatDelegate.MODE_NIGHT_NO),
    DARK("dark", AppCompatDelegate.MODE_NIGHT_YES);

    companion object {
        fun fromStorageKey(key: String?): ThemeMode =
            values().firstOrNull { it.storageKey == key } ?: SYSTEM
    }
}

// Persists the user's light/dark/system theme preference in plain SharedPreferences
// and applies it via AppCompatDelegate.setDefaultNightMode(...).
class ThemePreferenceStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getThemeMode(): ThemeMode =
        ThemeMode.fromStorageKey(prefs.getString(KEY_THEME_MODE, null))

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.storageKey).apply()
    }

    fun applySavedTheme() {
        AppCompatDelegate.setDefaultNightMode(getThemeMode().nightMode)
    }

    fun setAndApplyThemeMode(mode: ThemeMode) {
        setThemeMode(mode)
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
    }

    fun clear() {
        prefs.edit().remove(KEY_THEME_MODE).apply()
    }

    companion object {
        private const val PREFS_NAME = "signpdf_theme_preferences"
        private const val KEY_THEME_MODE = "theme_mode"
    }
}
