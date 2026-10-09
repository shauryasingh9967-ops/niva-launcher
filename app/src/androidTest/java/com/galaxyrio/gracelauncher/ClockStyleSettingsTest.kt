package com.galaxyrio.gracelauncher

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.ClockLayout
import com.galaxyrio.gracelauncher.data.ClockFontStore
import com.galaxyrio.gracelauncher.data.ClockStyle
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.settings.LauncherSettingsScreen
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ClockStyleSettingsTest {
    @get:Rule val compose = createComposeRule()
    private var state by mutableStateOf(LauncherUiState(isLoadingApps = false))
    private var writes = 0
    private var failSave = false

    private fun show() {
        compose.setContent {
            GraceLauncherTheme(darkTheme = false, dynamicColor = false) {
                LauncherSettingsScreen(state, LauncherActions(applyClockStyle = {
                    writes++
                    if (!failSave) state = state.copy(settings = state.settings.copy(clockStyle = it))
                    !failSave
                }), onBack = {})
            }
        }
        compose.onNodeWithTag("settings_category_themes").performClick()
        openEditor()
    }
    private fun openEditor() = compose.onNodeWithTag("settings_clock_style").performScrollTo().performClick()
    private fun choose(layout: ClockLayout) {
        compose.onNodeWithTag("clock_layout:${layout.name}").performScrollTo().performClick()
    }
    private fun set(tag: String, value: Float) {
        compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
    }
    private fun assertSliderValue(tag: String, value: Float) {
        val node = compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo().fetchSemanticsNode()
        assertEquals(value, node.config[SemanticsProperties.ProgressBarRangeInfo].current, 0.01f)
    }
    private fun previewText(): String = compose.onNodeWithTag("clock_style_preview_text", useUnmergedTree = true)
        .fetchSemanticsNode().config[SemanticsProperties.Text].single().text

    @Test fun previewsAreLiveAndLayoutsKeepIndependentValuesUntilApply() {
        show()
        assertEquals(1, previewText().lines().size)
        set("clock_weight", 600f)
        assertSliderValue("clock_weight", 600f)
        set("clock_size", 80f)
        choose(ClockLayout.TwoLines)
        assertEquals(2, previewText().lines().size)
        set("clock_size", 104f)
        compose.onNodeWithTag("clock_show_colon").assertDoesNotExist()
        screenshot("clock-style-two-lines.png")
        choose(ClockLayout.SingleLine)
        assertSliderValue("clock_size", 80f)
        compose.onNodeWithTag("clock_show_colon").performScrollTo().performClick()
        assertTrue(previewText().contains(':'))
        compose.runOnIdle { assertEquals(0, writes); assertEquals(ClockStyle(), state.settings.clockStyle) }
        screenshot("clock-style-single-line.png")
        compose.onNodeWithTag("clock_style_apply").performClick()
        compose.onNodeWithTag("settings_themes").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, writes)
            assertEquals(600, state.settings.clockStyle.singleLine.weight)
            assertEquals(80, state.settings.clockStyle.singleLine.size)
            assertEquals(104, state.settings.clockStyle.twoLines.size)
            assertTrue(state.settings.clockStyle.singleLine.showColon)
        }
        openEditor()
        assertTrue(previewText().contains(':'))
        choose(ClockLayout.TwoLines)
        assertSliderValue("clock_size", 104f)
    }

    @Test fun resetButtonsResetOnlyTheirOwnSliderAndBackDiscardsTheDraft() {
        show()
        set("clock_weight", 700f)
        set("clock_size", 96f)
        set("clock_letter_spacing", 4f)
        compose.onNodeWithTag("clock_size_reset", useUnmergedTree = true).performScrollTo().performClick().assertIsNotEnabled()
        assertSliderValue("clock_size", 72f)
        assertSliderValue("clock_weight", 700f)
        assertSliderValue("clock_letter_spacing", 4f)
        compose.onNodeWithTag("clock_letter_spacing_reset", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("clock_weight_reset", useUnmergedTree = true).performScrollTo().performClick()
        assertSliderValue("clock_weight", 400f)
        choose(ClockLayout.TwoLines)
        set("clock_size", 112f)
        compose.onNodeWithTag("clock_size_reset", useUnmergedTree = true).performClick()
        assertSliderValue("clock_size", 88f)
        compose.onNodeWithTag("settings_back").performClick()
        openEditor()
        assertEquals(1, previewText().lines().size)
        compose.runOnIdle { assertEquals(0, writes) }
    }

    @Test fun failedApplyKeepsTheEditorAndCanBeRetried() {
        failSave = true
        show()
        choose(ClockLayout.TwoLines)
        compose.onNodeWithTag("clock_style_apply").performClick()
        compose.onNodeWithTag("settings_clock_style_editor").assertIsDisplayed()
        compose.runOnIdle { assertEquals(ClockLayout.SingleLine, state.settings.clockStyle.layout); failSave = false }
        compose.onNodeWithTag("clock_style_apply").performClick()
        compose.onNodeWithTag("settings_themes").assertIsDisplayed()
        compose.runOnIdle { assertEquals(ClockLayout.TwoLines, state.settings.clockStyle.layout); assertEquals(2, writes) }
    }

    @Test fun importedFontsCanBeSelectedAndReplacedWithTheDefault() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = ClockFontStore(context)
        val imported = context.assets.open("fonts/noto_sans.ttf").use { store.importStream(it, "Preview font.ttf") }
        try {
            show()
            compose.onNodeWithTag("clock_font_selector").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("clock_font:${imported.id}").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("clock_font:${imported.id}").performClick()
            compose.onNodeWithTag("clock_font_selector").assertTextContains(imported.name)
            compose.onNodeWithTag("clock_style_apply").performClick()
            compose.runOnIdle { assertEquals(imported.id, state.settings.clockStyle.face.fontId) }
            openEditor()
            compose.onNodeWithTag("clock_font_selector").performClick()
            compose.onNodeWithTag("clock_font:default").performClick()
            compose.onNodeWithTag("clock_style_apply").performClick()
            compose.runOnIdle { assertNull(state.settings.clockStyle.face.fontId) }
        } finally { File(context.filesDir, "clock_fonts/${imported.id}").delete() }
    }

    private fun screenshot(name: String) {
        val directory = requireNotNull(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("ui-verification"))
        directory.mkdirs()
        File(directory, name).outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
