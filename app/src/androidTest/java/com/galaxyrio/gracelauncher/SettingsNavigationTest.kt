package com.galaxyrio.gracelauncher

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.AppRepository
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherScreen
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.settings.LauncherSettingsScreen
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var dispatcher: OnBackPressedDispatcher
    private var closes = 0
    private var keyboard: SoftwareKeyboardController? = null

    private fun showSettings(embedded: Boolean = true, folderId: String? = null) {
        compose.setContent {
            val owner = requireNotNull(LocalOnBackPressedDispatcherOwner.current)
            SideEffect { dispatcher = owner.onBackPressedDispatcher }
            GraceLauncherTheme(dynamicColor = false) {
                LauncherSettingsScreen(
                    LauncherUiState(apps = listOf(LauncherApp(ComponentName("test.calendar", "Calendar"), "Calendar", null))),
                    LauncherActions(), onBack = { closes++ },
                    initialFolderId = folderId, handleRootBack = embedded,
                )
            }
        }
        compose.waitForIdle()
    }

    @Test fun appListExposesOnlySettingsAndHomeRemainsSeparate() {
        val entries = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName), 0,
        )
        assertEquals(listOf(SettingsActivity::class.java.name), entries.map { it.activityInfo.name })
        assertEquals(context.getString(R.string.grace_settings), entries.single().loadLabel(context.packageManager))
        val home = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(context.packageName), 0,
        )
        assertEquals(listOf(MainActivity::class.java.name), home.map { it.activityInfo.name })
        val ownApps = runBlocking { AppRepository(context).loadApps() }.filter { it.packageName == context.packageName }
        assertEquals(listOf(SettingsActivity::class.java.name), ownApps.map { it.componentName.className })
        assertEquals(context.getString(R.string.grace_settings), ownApps.single().label)
    }

    @Test fun standaloneSettingsRootLeavesBackToTheSystem() {
        showSettings(embedded = false)
        compose.runOnIdle { assertFalse(dispatcher.hasEnabledCallbacks()) }
        compose.onNodeWithTag("settings_category_themes").performClick()
        compose.onNodeWithTag("settings_themes").assertIsDisplayed()
        compose.runOnIdle { assertTrue(dispatcher.hasEnabledCallbacks()) }
        completeGesture()
        compose.onNodeWithTag("settings_root").assertIsDisplayed()
        compose.runOnIdle { assertFalse(dispatcher.hasEnabledCallbacks()); assertEquals(0, closes) }
    }

    @Test fun pageTransitionsAnimateAndPredictiveCancelKeepsTheCurrentPage() {
        showSettings()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("settings_category_themes").performClick()
        compose.mainClock.advanceTimeBy(100)
        // Both destinations participate during the same 300 ms hierarchy transition.
        compose.onNodeWithTag("settings_root").assertExists()
        compose.onNodeWithTag("settings_themes").assertExists()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("settings_root").assertDoesNotExist()
        beginGesture(0.65f)
        compose.onNodeWithTag("settings_root").assertExists()
        compose.onNodeWithTag("settings_themes").assertExists()
        screenshot("settings-predictive-back-progress.png")
        cancelGesture()
        compose.onNodeWithTag("settings_themes").assertIsDisplayed()
        compose.onNodeWithTag("settings_root").assertDoesNotExist()
        completeGesture()
        compose.onNodeWithTag("settings_root").assertIsDisplayed()
        compose.onNodeWithTag("settings_themes").assertDoesNotExist()
        assertEquals(0, closes)
    }

    @Test fun embeddedRootTracksGestureAndOnlyClosesOnCommit() {
        showSettings()
        val originalLeft = compose.onNodeWithTag("settings_navigation").fetchSemanticsNode().boundsInRoot.left
        beginGesture(0.5f)
        assertTrue(compose.onNodeWithTag("settings_root").fetchSemanticsNode().boundsInRoot.left > originalLeft)
        assertEquals(0, closes)
        cancelGesture()
        assertEquals(originalLeft, compose.onNodeWithTag("settings_root").fetchSemanticsNode().boundsInRoot.left, 1f)
        assertEquals(0, closes)
        completeGesture()
        assertEquals(1, closes)
    }

    @Test fun cancellingBackKeepsFolderDraftAndDirectEditorExitsItsOwnFlow() {
        showSettings(folderId = "direct-folder")
        compose.onNodeWithTag("folder_name").performTextReplacement("Draft name")
        beginGesture(0.6f)
        cancelGesture()
        compose.onNodeWithTag("folder_name").assertTextContains("Draft name")
        compose.onNodeWithTag("settings_folder_editor").assertIsDisplayed()
        assertEquals(0, closes)
        compose.onNodeWithTag("settings_back").performClick()
        compose.waitForIdle()
        assertEquals(1, closes)
    }

    @Test fun searchBackCancelKeepsQueryAndCommitReturnsToHome() {
        showLauncher(drawer = false)
        compose.onNodeWithTag("launcher_fab").performClick()
        compose.onNodeWithTag("app_search_query").performTextReplacement("cal")
        compose.runOnIdle { keyboard?.hide() }
        beginGesture(0.5f)
        cancelGesture()
        compose.onNodeWithTag("app_search_query").assertTextContains("cal")
        completeGesture()
        compose.onNodeWithTag("app_search").assertDoesNotExist()
        compose.onNodeWithTag("home_clock").assertIsDisplayed()
    }

    @Test fun drawerBackCancelKeepsDrawerAndCommitReturnsToHome() {
        showLauncher(drawer = true)
        compose.onNodeWithTag("home_clock").assertDoesNotExist()
        beginGesture(0.5f)
        cancelGesture()
        compose.onNodeWithTag("home_clock").assertDoesNotExist()
        completeGesture()
        compose.onNodeWithTag("home_clock").assertIsDisplayed()
    }

    private fun showLauncher(drawer: Boolean) {
        compose.setContent {
            val owner = requireNotNull(LocalOnBackPressedDispatcherOwner.current)
            val controller = LocalSoftwareKeyboardController.current
            SideEffect { dispatcher = owner.onBackPressedDispatcher; keyboard = controller }
            GraceLauncherTheme(dynamicColor = false) {
                LauncherScreen(
                    LauncherUiState(apps = listOf(LauncherApp(ComponentName("test.calendar", "Calendar"), "Calendar", null))),
                    onDateClick = {}, onClockClick = {}, onLaunchApp = {}, onToggleFavorite = {},
                    initialDrawerOpen = drawer,
                )
            }
        }
        compose.waitForIdle()
    }

    private fun event(progress: Float) = BackEventCompat(0f, 300f, progress, BackEventCompat.EDGE_LEFT)

    private fun beginGesture(progress: Float) {
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { dispatcher.dispatchOnBackStarted(event(0f)) }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(event(progress)) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(32)
    }

    private fun cancelGesture() {
        compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    private fun completeGesture() {
        beginGesture(0.8f)
        compose.runOnUiThread { dispatcher.onBackPressed() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    private fun screenshot(name: String) {
        val directory = requireNotNull(context.getExternalFilesDir("ui-verification"))
        directory.mkdirs()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
