package com.galaxyrio.gracelauncher

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ClockStyleWallpaperTest {
    @get:Rule val compose = createAndroidComposeRule<SettingsActivity>()

    @Test fun standaloneEditorDisplaysTheSystemWallpaperInsideItsPreviewOnly() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("settings_category_themes").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("settings_category_themes").performClick()
        compose.onNodeWithTag("settings_clock_style").performScrollTo().performClick()
        compose.onNodeWithTag("clock_style_preview").assertIsDisplayed()
        SystemClock.sleep(350) // Wait for platform window animations, outside Compose's clock.
        compose.runOnIdle {
            assertTrue(compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER != 0)
        }
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = requireNotNull(context.getExternalFilesDir("ui-verification")).also { it.mkdirs() }
        File(directory, "clock-style-real-wallpaper.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // Exercise the same cutout with the taller two-line layout.
        compose.onNodeWithTag("clock_layout:TwoLines").performScrollTo().performClick()
        compose.onNodeWithTag("clock_style_preview").assertIsDisplayed()
        SystemClock.sleep(350)
        val stacked = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(directory, "clock-style-real-wallpaper-stacked.png").outputStream().use { stacked.compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.onNodeWithTag("settings_back").performClick()
        compose.onNodeWithTag("settings_themes").assertIsDisplayed()
    }
}
