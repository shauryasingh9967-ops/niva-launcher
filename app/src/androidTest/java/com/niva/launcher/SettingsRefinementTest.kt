package com.niva.launcher

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.niva.launcher.data.LauncherSettings
import com.niva.launcher.data.ThemeMode
import com.niva.launcher.data.licenses.LicensesRepository
import com.niva.launcher.ui.LauncherActions
import com.niva.launcher.ui.LauncherUiState
import com.niva.launcher.ui.settings.LauncherSettingsScreen
import com.niva.launcher.ui.settings.SettingsToggleItem
import com.niva.launcher.ui.theme.NivaLauncherTheme
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsRefinementTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var dialogColor = Color.Unspecified
    private var keyboard: SoftwareKeyboardController? = null
    private var edits = 0

    private fun showSettings(dark: Boolean = false) {
        compose.setContent {
            var state by remember { mutableStateOf(LauncherUiState(settings = LauncherSettings(useDynamicColors = false, darkMode = if (dark) ThemeMode.Dark else ThemeMode.Light))) }
            NivaLauncherTheme(darkTheme = state.settings.darkMode == ThemeMode.Dark, dynamicColor = false, seedColor = Color(state.settings.themeColor)) {
                val container = AlertDialogDefaults.containerColor
                val controller = LocalSoftwareKeyboardController.current
                SideEffect { dialogColor = container; keyboard = controller }
                LauncherSettingsScreen(state, LauncherActions(
                    updateSettings = { change -> edits++; state = state.copy(settings = change(state.settings)) },
                    textMode = { mode -> edits++; state = state.copy(textMode = mode) },
                ), onBack = {})
            }
        }
    }

    @Test fun togglingAnySegmentOnlyChangesTheSwitchInLightTheme() = assertStableToggleRows(false)
    @Test fun togglingAnySegmentOnlyChangesTheSwitchInDarkTheme() = assertStableToggleRows(true)

    private fun assertStableToggleRows(dark: Boolean) {
        compose.setContent {
            var states by remember { mutableStateOf(List(4) { false }) }
            NivaLauncherTheme(darkTheme = dark, dynamicColor = false) {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp)) {
                    repeat(4) { index ->
                        SettingsToggleItem("Setting $index", "A description", states[index], if (index == 3) 0 else index,
                            if (index == 3) 1 else 3, "toggle_$index",
                        ) { value -> states = states.toMutableList().also { it[index] = value } }
                        Spacer(Modifier.height(if (index == 2) 24.dp else 2.dp))
                    }
                }
            }
        }
        repeat(4) { index ->
            val row = compose.onNodeWithTag("toggle_$index")
            row.assertIsOff()
            val rowBounds = row.fetchSemanticsNode().boundsInRoot
            val switchBounds = compose.onNodeWithTag("toggle_${index}_switch", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.translate(-rowBounds.topLeft)
            val before = row.captureToImage().asAndroidBitmap()
            // Exercise the same toggle action through accessibility. This isolates
            // the saved checked state from RenderThread's wall-clock touch ripple;
            // actual touch toggles and ripple clipping are covered by integration tests.
            row.performSemanticsAction(SemanticsActions.OnClick) { it() }.assertIsOn()
            compose.waitForIdle()
            val after = row.captureToImage().asAndroidBitmap()
            if (index == 0) {
                val directory = requireNotNull(context.getExternalFilesDir("ui-verification"))
                directory.mkdirs()
                File(directory, "toggle-${if (dark) "dark" else "light"}-before.png").outputStream().use { before.compress(Bitmap.CompressFormat.PNG, 100, it) }
                File(directory, "toggle-${if (dark) "dark" else "light"}-after.png").outputStream().use { after.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            var switchChanges = 0
            for (y in 0 until before.height) for (x in 0 until before.width) {
                val isSwitch = x >= switchBounds.left - 2 && x <= switchBounds.right + 2 &&
                    y >= switchBounds.top - 2 && y <= switchBounds.bottom + 2
                if (isSwitch) {
                    if (before.getPixel(x, y) != after.getPixel(x, y)) switchChanges++
                } else {
                    // Hardware-rendered antialiased edges may dither by one channel value.
                    val beforeColor = before.getPixel(x, y)
                    val afterColor = after.getPixel(x, y)
                    val difference = listOf(0, 8, 16, 24).maxOf { shift ->
                        kotlin.math.abs(((beforeColor shr shift) and 255) - ((afterColor shr shift) and 255))
                    }
                    assertTrue("Segment $index changed outside the switch at $x,$y (difference=$difference)", difference <= 2)
                }
            }
            assertTrue("The switch itself must visibly change", switchChanges > 100)
            before.recycle(); after.recycle()
        }
        screenshot("settings-stable-switches-${if (dark) "dark" else "light"}.png")
    }

    @Test fun bothChoiceDialogsUsePlainAccessibleRadioRows() {
        showSettings()
        clickSetting("settings_category_themes")
        compose.onNodeWithText("Use wallpaper colors").assertExists()
        compose.onAllNodes(hasText("Android 12", substring = true)).assertCountEquals(0)
        clickSetting("settings_theme_mode")
        assertRadioRows("theme_mode", listOf("System", "Light", "Dark"))
        screenshot("settings-standard-dark-mode-dialog.png")
        compose.onNodeWithTag("theme_mode:Dark").performClick()
        compose.runOnIdle { assertEquals(1, edits) }
        compose.onNodeWithTag("theme_mode:Dark").assertDoesNotExist()
        clickSetting("settings_wallpaper_text")
        assertRadioRows("wallpaper_text", listOf("Auto", "Light", "Dark"))
        screenshot("settings-standard-text-color-dialog-dark.png")
        compose.onNodeWithText(context.getString(R.string.settings_cancel)).performClick()
        compose.runOnIdle { assertEquals("Cancel must not change a value", 1, edits) }
    }

    private fun assertRadioRows(prefix: String, keys: List<String>) {
        keys.forEach { key ->
            val row = compose.onNodeWithTag("$prefix:$key")
            row.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            val image = row.captureToImage().asAndroidBitmap()
            val inset = (8 * context.resources.displayMetrics.density).toInt()
            // Neither the selected nor unselected row gets its own colored card.
            listOf(inset, image.height / 2, image.height - inset).forEach { y ->
                assertEquals(dialogColor.toArgb(), image.getPixel(image.width - inset, y))
            }
            image.recycle()
        }
    }

    @Test fun changelogIsEmptyAndAboutGlyphIsSmallerWithoutShrinkingItsContainer() {
        showSettings()
        clickSetting("settings_category_about")
        val g = compose.onNodeWithTag("about_logo_g", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val container = compose.onNodeWithTag("about_logo_container", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(92f * 0.72f * context.resources.displayMetrics.density, g.width, 1f)
        assertEquals(container.center.x, g.center.x, 1f)
        assertEquals(container.center.y, g.center.y, 1f)
        assertTrue(container.width > 92f * context.resources.displayMetrics.density)
        screenshot("settings-about-smaller-g.png")
        clickSetting("settings_open_changelog")
        compose.onNodeWithTag("settings_changelog").assertIsDisplayed()
        val visibleText = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes().flatMap { it.config[SemanticsProperties.Text] }.map { it.text }.distinct()
        assertEquals(listOf(context.getString(R.string.settings_changelog)), visibleText)
        screenshot("settings-changelog-empty.png")
    }

    @Test fun advancedShowsComingSoonAndCanReturnToSettings() {
        showSettings()
        clickSetting("settings_category_advanced")
        compose.onNodeWithTag("settings_advanced_coming_soon")
            .assertIsDisplayed().assertTextEquals("Coming soon")
        screenshot("settings-advanced-coming-soon.png")
        compose.onNodeWithTag("settings_back").performClick()
        compose.onNodeWithTag("settings_root").assertIsDisplayed()
    }

    @Test fun licensesShowGplAndRealDependenciesWithoutFontOrIconLinks() {
        val libraries = runBlocking { LicensesRepository(context).getLibraries() }
        assertTrue(libraries.size > 10)
        assertTrue(libraries.any { it.artifactId.contains("androidx.room") })
        assertTrue(libraries.none { it.artifactId.contains("espresso") || it.artifactId.contains("junit") })
        val target = libraries.first { it.artifactId.contains("material-kolor") }
        showSettings()
        clickSetting("settings_category_about")
        compose.onNodeWithTag("settings_open_licenses").performScrollTo().assertTextContains("License")
        clickSetting("settings_open_licenses")
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("licenses_search").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("License").assertIsDisplayed()
        compose.onNodeWithTag("licenses_count").assertTextEquals(context.resources.getQuantityString(R.plurals.licenses_count, libraries.size, libraries.size))
        compose.onNodeWithTag("app_license_gpl3").assertTextEquals("GNU GPLv3")
        compose.onNodeWithTag("settings_font_licenses").assertDoesNotExist()
        compose.onNodeWithTag("settings_icon_licenses").assertDoesNotExist()
        screenshot("settings-licenses-list.png")
        compose.onNodeWithTag("licenses_search").performTextReplacement("not-a-real-library")
        compose.runOnIdle { keyboard?.hide() }
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("licenses_empty"))
        compose.onNodeWithTag("licenses_empty").assertIsDisplayed()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("licenses_search"))
        compose.onNodeWithTag("licenses_search").performTextReplacement(target.artifactId)
        compose.runOnIdle { keyboard?.hide() }
        clickSetting("license:${target.id}")
        compose.onNodeWithTag("license_details:${target.id}", useUnmergedTree = true).assertExists()
        compose.onNodeWithText(target.name, useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText(target.licenses.joinToString { it.name }, substring = true))
        screenshot("settings-licenses-expanded.png")
        compose.onNodeWithTag("settings_list").performScrollToIndex(0)
        compose.onNodeWithTag("settings_app_license").performClick()
        compose.onNodeWithTag("settings_license_text").assertIsDisplayed()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("GNU GENERAL PUBLIC LICENSE", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        screenshot("settings-gpl3-text.png")
        compose.onNodeWithTag("settings_back").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("settings_app_license").fetchSemanticsNodes().isNotEmpty() }
        // Hiding links must not remove the third-party notices from the APK.
        val fonts = context.assets.open("fonts/NOTICE.txt").bufferedReader().use { it.readText() }
        assertTrue(fonts.contains("SIL OPEN FONT LICENSE"))
        val icons = context.resources.openRawResource(R.raw.settings_material_symbols_license).bufferedReader().use { it.readText() }
        assertTrue(icons.contains("Apache License"))
    }

    private fun clickSetting(tag: String) {
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val directory = requireNotNull(context.getExternalFilesDir("ui-verification"))
        directory.mkdirs()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
