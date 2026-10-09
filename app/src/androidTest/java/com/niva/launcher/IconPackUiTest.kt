package com.niva.launcher

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.niva.launcher.data.LauncherSettings
import com.niva.launcher.data.icons.IconPackInfo
import com.niva.launcher.ui.LauncherActions
import com.niva.launcher.ui.LauncherUiState
import com.niva.launcher.ui.settings.LauncherSettingsScreen
import com.niva.launcher.ui.theme.NivaLauncherTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class IconPackUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val packs = listOf(IconPackInfo("test.alpha", "Alpha icons", null), IconPackInfo("test.beta", "Beta icons", null))
    private var state by mutableStateOf(LauncherUiState(isLoadingApps = false, iconPacks = packs))
    private var writes = 0
    private var refreshes = 0

    private fun show(initialPage: String = "Themes") {
        compose.setContent {
            NivaLauncherTheme(darkTheme = false, dynamicColor = false) {
                LauncherSettingsScreen(state, LauncherActions(
                    updateSettings = { change -> writes++; state = state.copy(settings = change(state.settings)) },
                    refreshIconPacks = { refreshes++ },
                ), onBack = {}, initialPage = initialPage)
            }
        }
        if (initialPage == "Themes") compose.onNodeWithTag("settings_icon_pack").performScrollTo().performClick()
        compose.onNodeWithTag("icon_pack_settings").assertIsDisplayed()
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    private fun assertOrder(order: List<String>) {
        compose.runOnIdle { assertEquals(order, state.settings.enabledIconPackPackages) }
        order.zipWithNext().forEach { (first, second) ->
            assertTrue(bounds("icon_pack_selected:$first").top < bounds("icon_pack_selected:$second").top)
        }
        assertTrue(bounds("icon_pack_selected:${order.last()}").bottom <= bounds("icon_pack:system").top)
    }

    @Test fun themeOpensFullScreenAndMultiplePacksCanBeReorderedAndRemoved() {
        show()
        compose.onNodeWithTag("icon_pack_picker").assertDoesNotExist()
        compose.onNodeWithTag("icon_pack:system").assertIsOn().assertIsNotEnabled()
        packs.forEach { compose.onNodeWithTag("icon_pack:${it.packageName}").performScrollTo().performClick() }
        compose.onNodeWithTag("icon_pack_list").performScrollToIndex(0)
        assertOrder(packs.map { it.packageName })
        val distance = bounds("icon_pack_drag:test.beta").center.y - bounds("icon_pack_drag:test.alpha").center.y
        compose.onNodeWithTag("icon_pack_drag:test.alpha").performTouchInput {
            swipe(center, center + Offset(0f, distance + 40f), 650)
        }
        assertOrder(listOf("test.beta", "test.alpha"))
        compose.onNodeWithTag("icon_pack_drag:test.alpha").performClick()
        compose.onNodeWithText(context.getString(R.string.favorites_move_up)).performClick()
        assertOrder(listOf("test.alpha", "test.beta"))
        screenshot("icon-pack-order.png")
        compose.onNodeWithTag("icon_pack_selected:test.alpha").performClick()
        compose.runOnIdle { assertEquals(listOf("test.beta"), state.settings.enabledIconPackPackages) }
        compose.onNodeWithTag("icon_pack:system").assertIsOn().assertIsNotEnabled()
        compose.onNodeWithTag("icon_pack:test.alpha").performScrollTo().assertIsOff()
    }

    @Test fun emptyListStillOffersTheAlwaysSelectedDesignerAndSystemIcons() {
        state = state.copy(iconPacks = emptyList())
        show("IconPacks")
        compose.onNodeWithTag("icon_pack:system").assertIsOn().assertIsNotEnabled()
        compose.onNodeWithTag("icon_pack_designer").assertIsSelected()
        compose.onNodeWithTag("icon_pack_empty").assertIsDisplayed()
        compose.onNodeWithTag("icon_pack_designer").performClick()
        compose.onNodeWithTag("icon_designer").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, writes) }
    }

    @Test fun unavailableSelectionSurvivesScanFailuresAndCanBeRemovedExplicitly() {
        state = state.copy(settings = LauncherSettings(iconPackPackage = "missing.icons"), iconPacksLoadFailed = true)
        show("IconPacks")
        compose.onNodeWithTag("icon_pack_selected:missing.icons").assertIsOn()
        compose.onNodeWithTag("icon_pack:system").assertIsOn().assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.retry)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, refreshes); assertEquals(listOf("missing.icons"), state.settings.enabledIconPackPackages) }
        compose.onNodeWithTag("icon_pack_selected:missing.icons").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(state.settings.enabledIconPackPackages.isEmpty()); assertEquals(1, writes) }
    }

    @Test fun searchFiltersOnlyAllAndKeepsSelectedPacksVisible() {
        state = state.copy(settings = LauncherSettings().withIconPacks(listOf("test.alpha")))
        show("IconPacks")
        compose.onNodeWithTag("icon_pack_query").performScrollTo().performTextReplacement("Beta")
        compose.onNodeWithTag("icon_pack:test.alpha").assertDoesNotExist()
        compose.onNodeWithTag("icon_pack:test.beta").assertExists()
        compose.onNodeWithTag("icon_pack_selected:test.alpha").assertExists()
        compose.runOnIdle { assertEquals(0, writes) }
    }

    private fun screenshot(name: String) {
        val directory = requireNotNull(context.getExternalFilesDir("ui-verification"))
        directory.mkdirs()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
