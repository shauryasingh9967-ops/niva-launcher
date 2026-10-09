package com.niva.launcher

import android.content.ComponentName
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.niva.launcher.data.LauncherApp
import com.niva.launcher.ui.LauncherActions
import com.niva.launcher.ui.LauncherUiState
import com.niva.launcher.ui.settings.LauncherSettingsScreen
import com.niva.launcher.ui.theme.NivaLauncherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClockSettingsTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val clock = LauncherApp(ComponentName("test.clock", "ClockActivity"), "时钟", null)
    private val renamed = LauncherApp(ComponentName("test.alarm", "AlarmActivity"), "Morning", null, originalLabel = "Alarm")
    private val ownApp get() = LauncherApp(ComponentName(context.packageName, "SettingsActivity"), "Niva", null)
    private var state by mutableStateOf(LauncherUiState(isLoadingApps = false))
    private var retries = 0
    private var writes = 0

    private fun showSettings(failed: Boolean = false) {
        state = state.copy(apps = listOf(clock, renamed, ownApp), appLoadFailed = failed)
        compose.setContent {
            NivaLauncherTheme(darkTheme = false, dynamicColor = false) {
                LauncherSettingsScreen(state, LauncherActions(
                    updateSettings = { change -> writes++; state = state.copy(settings = change(state.settings)) },
                    refreshApps = { retries++ },
                ), onBack = {})
            }
        }
        compose.onNodeWithTag("settings_category_productivity").performClick()
        compose.onNodeWithTag("settings_clock").performScrollTo().performClick()
        compose.onNodeWithTag("settings_clock_page").assertIsDisplayed()
    }

    @Test fun defaultsToSystemAndSavesAChoiceWithoutOpeningAnApp() {
        showSettings()
        compose.onNodeWithTag("clock_default_app").assertIsSelected()
        compose.onNodeWithTag("clock_app:${ownApp.key}").assertDoesNotExist()
        compose.onNodeWithTag("clock_app:${clock.key}").performScrollTo().performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(clock.key, state.settings.clockAppKey); assertEquals(1, writes) }
        compose.onNodeWithTag("settings_back").performClick()
        compose.onNodeWithTag("settings_clock").performScrollTo().assertTextContains(clock.label).performClick()
        compose.onNodeWithTag("clock_default_app").performScrollTo().performClick().assertIsSelected()
        compose.runOnIdle { assertNull(state.settings.clockAppKey) }
    }

    @Test fun searchIncludesOriginalNamesAndDefaultStaysAvailable() {
        showSettings()
        compose.onNodeWithTag("clock_app_query").performTextReplacement("ALARM")
        compose.onNodeWithTag("clock_app:${renamed.key}").assertExists()
        compose.onNodeWithTag("clock_app:${clock.key}").assertDoesNotExist()
        compose.onNodeWithTag("clock_app_query").performTextReplacement("no-such-app")
        compose.onNodeWithText(context.getString(R.string.search_no_results)).assertExists()
        compose.onNodeWithTag("clock_default_app").assertExists()
        compose.runOnIdle { assertEquals(0, writes) }
    }

    @Test fun uninstallDoesNotOverwritePreferenceAndDefaultCanBeRestored() {
        showSettings()
        compose.onNodeWithTag("clock_app:${clock.key}").performScrollTo().performClick()
        compose.runOnIdle { state = state.copy(apps = listOf(renamed)) }
        compose.onNodeWithTag("clock_missing_app").assertExists()
        compose.runOnIdle { assertEquals(clock.key, state.settings.clockAppKey) }
        compose.onNodeWithTag("clock_default_app").performScrollTo().performClick()
        compose.onNodeWithTag("clock_missing_app").assertDoesNotExist()
        compose.runOnIdle { assertNull(state.settings.clockAppKey) }
    }

    @Test fun loadingFailureOffersRetry() {
        showSettings(failed = true)
        compose.onNodeWithText(context.getString(R.string.clock_apps_load_failed)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.retry)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, retries); assertEquals(0, writes) }
    }
}
