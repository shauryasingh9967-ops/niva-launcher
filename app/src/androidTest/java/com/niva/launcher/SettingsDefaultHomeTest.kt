package com.niva.launcher

import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.niva.launcher.platform.DefaultHome
import com.niva.launcher.ui.LauncherActions
import com.niva.launcher.ui.LauncherUiState
import com.niva.launcher.ui.settings.LauncherSettingsScreen
import com.niva.launcher.ui.theme.NivaLauncherTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsDefaultHomeTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var state by mutableStateOf(LauncherUiState())
    private var requests = 0
    private var highlight = Color.Unspecified

    private fun show(defaultHome: Boolean?, dark: Boolean = false) {
        state = LauncherUiState(isDefaultHome = defaultHome)
        compose.setContent {
            NivaLauncherTheme(darkTheme = dark, dynamicColor = false, seedColor = Color(0xFFE27C33)) {
                val primaryContainer = MaterialTheme.colorScheme.primaryContainer
                SideEffect { highlight = primaryContainer }
                LauncherSettingsScreen(state, LauncherActions(requestDefaultHome = { requests++ }), onBack = {})
            }
        }
    }

    @Test fun bannerIsAnIndependentHighlightedPillAboveCategoriesInLightTheme() = checkBanner(false)
    @Test fun bannerUsesDarkThemeColorsToo() = checkBanner(true)

    private fun checkBanner(dark: Boolean) {
        show(defaultHome = false, dark = dark)
        val banner = compose.onNodeWithTag("settings_default_home").assertIsDisplayed().assertHasClickAction()
        banner.assertTextContains(context.getString(R.string.set_default_launcher))
        // Opening a system chooser is an action, not an enabled switch/selected preference.
        banner.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ToggleableState))
        banner.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
        val bounds = banner.fetchSemanticsNode().boundsInRoot
        val category = compose.onNodeWithTag("settings_category_productivity").fetchSemanticsNode().boundsInRoot
        val density = context.resources.displayMetrics.density
        assertEquals(category.left, bounds.left, 1f)
        assertEquals(category.right, bounds.right, 1f)
        assertTrue("The banner must have a comfortable touch target", bounds.height >= 72f * density - 1)
        assertTrue("Keep the banner separate from the category group", category.top - bounds.bottom >= 32f * density)
        val bitmap = banner.captureToImage().asAndroidBitmap()
        assertEquals(highlight.toArgb(), bitmap.getPixel(bitmap.width / 2, (6 * density).toInt()))
        assertNotEquals("The banner's corners must be rounded", highlight.toArgb(), bitmap.getPixel(1, 1))
        bitmap.recycle()
        screenshot("default-home-banner-${if (dark) "dark" else "light"}.png")
        banner.performClick()
        compose.runOnIdle { assertEquals(1, requests) }
        // Cancellation or a dismissed chooser must not hide it optimistically.
        banner.assertIsDisplayed()
    }

    @Test fun realStatusChangesHideBannerAndRestoreOriginalSpacingWithoutFlashingUnknownState() {
        show(defaultHome = null)
        compose.onNodeWithTag("settings_default_home").assertDoesNotExist()
        val originalTop = compose.onNodeWithTag("settings_category_productivity").fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle { state = state.copy(isDefaultHome = false, hasShortcutAccess = true) }
        compose.onNodeWithTag("settings_default_home").assertIsDisplayed()
        assertTrue(compose.onNodeWithTag("settings_category_productivity").fetchSemanticsNode().boundsInRoot.top > originalTop)
        compose.runOnIdle { state = state.copy(isDefaultHome = true, hasShortcutAccess = false) }
        compose.onNodeWithTag("settings_default_home").assertDoesNotExist()
        assertEquals(originalTop, compose.onNodeWithTag("settings_category_productivity").fetchSemanticsNode().boundsInRoot.top, 1f)
        screenshot("default-home-already-default.png")
        compose.runOnIdle { state = state.copy(isDefaultHome = false) }
        compose.onNodeWithTag("settings_default_home").assertIsDisplayed()
    }

    @Test fun bannerOnlyBelongsToSettingsRootAndStatusIsFreshWhenReturningFromSubpage() {
        show(defaultHome = false)
        compose.onNodeWithTag("settings_category_productivity").performClick()
        compose.onNodeWithTag("settings_default_home").assertDoesNotExist()
        compose.runOnIdle { state = state.copy(isDefaultHome = true) }
        compose.onNodeWithTag("settings_back").performClick()
        compose.onNodeWithTag("settings_root").assertIsDisplayed()
        compose.onNodeWithTag("settings_default_home").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, requests) }
    }

    @Test fun platformCheckMatchesSystemHomeRoleAndRequestUsesResolvableSystemUi() {
        val role = if (Build.VERSION.SDK_INT >= 29) context.getSystemService(RoleManager::class.java) else null
        if (Build.VERSION.SDK_INT >= 29 && role != null && role.isRoleAvailable(RoleManager.ROLE_HOME)) {
            val held = role.isRoleHeld(RoleManager.ROLE_HOME)
            assertEquals(held, DefaultHome.isDefault(context))
            val expected = if (held) Intent(Settings.ACTION_HOME_SETTINGS) else role.createRequestRoleIntent(RoleManager.ROLE_HOME)
            assertTrue(expected.filterEquals(DefaultHome.requestIntent(context)))
        } else {
            @Suppress("DEPRECATION")
            val home = context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            assertEquals(home?.activityInfo?.packageName == context.packageName, DefaultHome.isDefault(context))
            assertEquals(Settings.ACTION_HOME_SETTINGS, DefaultHome.requestIntent(context).action)
        }
        assertNotNull(DefaultHome.requestIntent(context).resolveActivity(context.packageManager))
    }

    private fun screenshot(name: String) {
        val directory = requireNotNull(context.getExternalFilesDir("ui-verification"))
        directory.mkdirs()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
