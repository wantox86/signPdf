package com.wantox86.signpdf.data.local

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ThemePreferenceStoreTest {

    private lateinit var context: Application
    private lateinit var store: ThemePreferenceStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = ThemePreferenceStore(context)
        store.clear()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    @After
    fun tearDown() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    @Test
    fun `defaults to SYSTEM when no preference is stored`() {
        assertEquals(ThemeMode.SYSTEM, store.getThemeMode())
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, store.getThemeMode().nightMode)
    }

    @Test
    fun `setThemeMode persists DARK and LIGHT across instances`() {
        store.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, ThemePreferenceStore(context).getThemeMode())

        store.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, ThemePreferenceStore(context).getThemeMode())
    }

    @Test
    fun `unknown stored value falls back safely to SYSTEM`() {
        context.getSharedPreferences("signpdf_theme_preferences", Context.MODE_PRIVATE)
            .edit()
            .putString("theme_mode", "corrupted_value")
            .commit()

        assertEquals(ThemeMode.SYSTEM, store.getThemeMode())
    }

    @Test
    fun `setAndApplyThemeMode updates AppCompatDelegate default night mode`() {
        store.setAndApplyThemeMode(ThemeMode.DARK)
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode())
        assertEquals(ThemeMode.DARK, store.getThemeMode())

        store.setAndApplyThemeMode(ThemeMode.LIGHT)
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.getDefaultNightMode())
        assertEquals(ThemeMode.LIGHT, store.getThemeMode())
    }
}
