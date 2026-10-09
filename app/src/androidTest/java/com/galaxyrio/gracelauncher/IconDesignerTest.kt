package com.galaxyrio.gracelauncher

import android.content.ComponentName
import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.data.IconShape
import com.galaxyrio.gracelauncher.data.IconColor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.galaxyrio.gracelauncher.data.ItemIcon
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.settings.LauncherSettingsScreen
import com.galaxyrio.gracelauncher.ui.settings.iconDesignerPreviewRows
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class IconDesignerTest {
    @get:Rule val compose = createComposeRule()
    private val apps = (0..8).map { index ->
        LauncherApp(ComponentName("test.app$index", "AppActivity"), "App $index", null,
            isSystemApp = index < 3, isAdaptiveIcon = index < 6,
            themeIconPackPackage = "test.icons".takeIf { index < 3 })
    }
    private var state by mutableStateOf(LauncherUiState(apps = apps, isLoadingApps = false))
    private var writes = 0
    private var specialSaved: ItemIcon? = null
    private var bulkSaved: ItemIcon? = null

    private fun show() {
        compose.setContent {
            GraceLauncherTheme(darkTheme = false, dynamicColor = false) {
                LauncherSettingsScreen(state, LauncherActions(updateSettings = { writes++ },
                    setItemIcon = { _, choice -> writes++; specialSaved = choice; true },
                    applyIconDesign = { choice -> writes++; bulkSaved = choice; true }), onBack = {}, initialPage = "IconPacks")
            }
        }
        compose.onNodeWithTag("icon_pack_designer").performClick()
    }

    @Test fun allIsTheDefaultAndSingleReusesAppSelection() {
        show()
        compose.onNodeWithTag("icon_designer_all").assertIsSelected()
        compose.onNodeWithTag("icon_designer_source").assertDoesNotExist()
        compose.onNodeWithTag("icon_designer_single").performClick().assertIsSelected()
        compose.onNodeWithTag("icon_designer_save").assertIsNotEnabled()
        compose.onNodeWithTag("icon_designer_choose_app").performClick()
        compose.onNodeWithTag("settings_icon_designer_page").assertIsDisplayed()
        compose.onNodeWithTag("icon_designer_app_query").performTextReplacement("App 4")
        compose.onNodeWithTag("icon_designer_app:${apps[4].key}").performClick()
        compose.onNodeWithTag("icon_designer_selected_app").assertIsDisplayed().assertWidthIsEqualTo(80.dp).assertHasNoClickAction()
        compose.onNodeWithTag("icon_designer_icon_size").assertDoesNotExist()
        compose.onNodeWithTag("icon_designer_selected_app").performTouchInput { click() }
        compose.onNodeWithTag("settings_icon_designer_page").assertDoesNotExist()
        compose.onNodeWithTag("icon_designer_all").performClick().assertIsSelected()
        repeat(3) { row ->
            compose.onNodeWithTag("icon_designer_row:$row").assertIsDisplayed()
            compose.onNodeWithTag("icon_designer_icon:$row:0", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithTag("icon_designer_icon:$row:1", useUnmergedTree = true).assertDoesNotExist()
        }
        compose.onNodeWithTag("icon_designer_single").performClick()
        compose.onNodeWithTag("icon_designer_selected_app").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, writes) }
    }

    @Test fun previewUsesPackMatchesAndEmptyDevicesGetThreeExamples() {
        assertEquals(listOf(listOf(apps[2]), listOf(apps[5]), listOf(apps[8])), iconDesignerPreviewRows(apps.reversed()))
        state = state.copy(apps = emptyList())
        show()
        repeat(3) { row -> compose.onNodeWithTag("icon_designer_icon:$row:0", useUnmergedTree = true).assertIsDisplayed() }
    }

    @Test fun previewUsesTheIconAlreadyCustomizedByTheDesktopEditor() {
        val customized = Bitmap.createBitmap(144, 144, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.MAGENTA) }
        state = state.copy(apps = apps.map { if (it.key == apps[4].key) it.copy(icon = customized.asImageBitmap()) else it },
            itemIcons = mapOf(apps[4].key to ItemIcon("image", "shared-designer-image.png")))
        show()
        compose.onNodeWithTag("icon_designer_single").performClick()
        compose.onNodeWithTag("icon_designer_choose_app").performClick()
        compose.onNodeWithTag("icon_designer_app_query").performTextReplacement("App 4")
        compose.onNodeWithTag("icon_designer_app:${apps[4].key}").performClick()
        val pixels = compose.onNodeWithTag("icon_designer_selected_app").captureToImage().toPixelMap()
        assertEquals(Color.Magenta, pixels[pixels.width / 2, pixels.height / 2])
        compose.runOnIdle { assertEquals(ItemIcon("image", "shared-designer-image.png"), state.itemIcons[apps[4].key]); assertEquals(0, writes) }
    }

    private fun chooseApp(index: Int = 4) {
        compose.onNodeWithTag("icon_designer_single").performClick()
        compose.onNodeWithTag("icon_designer_choose_app").performClick()
        compose.onNodeWithTag("icon_designer_app_query").performTextReplacement("App $index")
        compose.onNodeWithTag("icon_designer_app:${apps[index].key}").performClick()
    }

    @Test fun singleControlsStayDraftsUntilSaveAndColorPickerStaysBelowPreview() {
        show(); chooseApp()
        compose.onNodeWithTag("icon_designer_shape").performScrollTo()
        compose.onNodeWithTag("icon_designer_shapes").performScrollToIndex(5)
        compose.onNodeWithTag("icon_designer_shape:Cookie").performClick()
        compose.onNodeWithTag("icon_designer_cookie_slider").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(3f) }
        compose.onNodeWithTag("icon_designer_foreground").performScrollTo().performClick()
        val preview = compose.onNodeWithTag("icon_designer_preview").fetchSemanticsNode().boundsInRoot
        val picker = compose.onNodeWithTag("icon_designer_color_picker").fetchSemanticsNode().boundsInRoot
        assertTrue(preview.bottom <= picker.top)
        compose.onNodeWithTag("icon_designer_dynamic_color").performScrollTo().performClick()
        compose.onNodeWithTag("icon_designer_color_done").performScrollTo().performClick()
        compose.onNodeWithTag("icon_designer_x").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(20f) }
        compose.onNodeWithTag("icon_designer_size").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(150f) }
        compose.onNodeWithTag("icon_designer_size_reset").performClick()
        compose.runOnIdle { assertEquals(0, writes) }
        compose.onNodeWithTag("icon_designer_save").performClick()
        compose.onNodeWithTag("icon_designer").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, writes)
            assertEquals(IconShape.Cookie, specialSaved?.design?.shape)
            assertEquals(9, specialSaved?.design?.cookieSides)
            assertEquals(20f, specialSaved?.design?.x)
            assertEquals(100, specialSaved?.design?.size)
            assertEquals(true, specialSaved?.design?.foreground?.dynamic)
            assertNull(bulkSaved)
        }
    }

    @Test fun allSavesSeparatelyAndSwitchAppUsesTheExistingPicker() {
        show(); chooseApp()
        compose.onNodeWithTag("icon_designer_switch").performScrollTo().performClick()
        compose.onNodeWithTag("icon_designer_app_query").performTextReplacement("App 1")
        compose.onNodeWithTag("icon_designer_app:${apps[1].key}").performClick()
        compose.onNodeWithTag("icon_designer_all").performClick()
        compose.onNodeWithTag("icon_designer_shape:Circle").performScrollTo().performClick()
        compose.onNodeWithTag("icon_designer_save").performClick()
        compose.runOnIdle { assertNull(specialSaved); assertEquals(IconShape.Circle, bulkSaved?.design?.shape); assertEquals(1, writes) }
    }

    @Test fun sourcePickerAndColorResetKeepTheDraftUntilSave() {
        show(); chooseApp()
        compose.onNodeWithTag("icon_designer_source").performScrollTo().performClick()
        compose.onNodeWithTag("icon_designer_source_system").performClick()
        compose.onNodeWithTag("icon_designer_background").performScrollTo().performClick()
        compose.onNodeWithTag("icon_designer_hex").performScrollTo().performTextReplacement("ED5234")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("icon_designer_color_done").performScrollTo().performClick()
        compose.onNodeWithTag("icon_designer_foreground").performScrollTo().performClick()
        compose.onNodeWithTag("icon_designer_dynamic_color").performScrollTo().performClick()
        compose.onNodeWithTag("icon_designer_color_reset").performScrollTo().performClick()
        compose.onNodeWithTag("icon_designer_x").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(30f) }
        compose.onNodeWithTag("icon_designer_x_reset").performClick()
        compose.runOnIdle { assertEquals(0, writes) }
        compose.onNodeWithTag("icon_designer_save").performClick()
        compose.runOnIdle {
            assertEquals("system", specialSaved?.kind)
            assertEquals(0xFFED5234.toInt(), specialSaved?.design?.background?.argb)
            assertEquals(IconColor.Theme, specialSaved?.design?.foreground)
            assertEquals(0f, specialSaved?.design?.x)
        }
    }

}
