package com.niva.launcher

import android.content.ComponentName
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.niva.launcher.data.FolderPlacement
import com.niva.launcher.data.LauncherApp
import com.niva.launcher.data.LauncherFolder
import com.niva.launcher.data.LauncherSettings
import com.niva.launcher.data.ScheduleEvent
import com.niva.launcher.data.ThemeMode
import com.niva.launcher.data.WallpaperTextMode
import com.niva.launcher.ui.LauncherActions
import com.niva.launcher.ui.LauncherScreen
import com.niva.launcher.ui.LauncherUiState
import com.niva.launcher.ui.ScheduleStatus
import com.niva.launcher.ui.theme.NivaLauncherTheme
import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise the actual launcher entry points; only platform persistence/launch is faked. */
@RunWith(AndroidJUnit4::class)
class LauncherSettingsIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private val apps = listOf("Calendar", "Camera", "Chrome", "Gmail", "Vault").map {
        LauncherApp(ComponentName("settings.test.${it.lowercase()}", "$it.Activity"), it, null)
    }
    private lateinit var state: MutableState<LauncherUiState>
    private val launched = mutableListOf<String>()
    private var keyboard: SoftwareKeyboardController? = null
    private var themePrimary = Color.Unspecified
    private var themeSurface = Color.Unspecified

    private fun fixture() = LauncherUiState(
        apps = apps, favoriteKeys = setOf(apps.first().key), isLoadingApps = false,
        scheduleStatus = ScheduleStatus.Ready, textMode = WallpaperTextMode.Light,
        settings = LauncherSettings(useDynamicColors = false, darkMode = ThemeMode.Light),
    )

    private fun showLauncher(initial: LauncherUiState = fixture()) {
        state = mutableStateOf(initial)
        compose.setContent {
            val current = state.value
            val keyboardController = LocalSoftwareKeyboardController.current
            NivaLauncherTheme(
                darkTheme = current.settings.darkMode == ThemeMode.Dark,
                dynamicColor = current.settings.useDynamicColors,
                seedColor = Color(current.settings.themeColor),
            ) {
                val colors = MaterialTheme.colorScheme
                SideEffect { keyboard = keyboardController; themePrimary = colors.primary; themeSurface = colors.surface }
                Box(Modifier.fillMaxSize().background(Color(0xFF152431))) {
                    LauncherScreen(
                        uiState = current, onDateClick = {}, onClockClick = {},
                        onLaunchApp = { launched += it.key },
                        onToggleFavorite = { app ->
                            val keys = state.value.favoriteKeys
                            state.value = state.value.copy(favoriteKeys = if (app.key in keys) keys - app.key else keys + app.key)
                        },
                        actions = LauncherActions(
                            updateSettings = { transform -> state.value = state.value.copy(settings = transform(state.value.settings)) },
                            setHiddenApps = { state.value = state.value.copy(hiddenAppKeys = it) },
                            saveFolder = { folder -> state.value = state.value.copy(folders = state.value.folders.filterNot { it.id == folder.id } + folder) },
                            updateFolder = { id, name, placement, atBottom -> state.value = state.value.copy(folders = state.value.folders.map {
                                if (it.id == id) it.copy(name = name ?: it.name, placement = placement ?: it.placement,
                                    appListAtBottom = atBottom ?: it.appListAtBottom) else it
                            }) },
                            updatePopup = { owner, defaults, transform ->
                                val contents = transform(state.value.popupItems(owner, defaults))
                                state.value = state.value.copy(popups = state.value.popups + (owner.key to contents),
                                    folders = state.value.folders.map { if (it.id == owner.folderId)
                                        it.copy(appKeys = contents.filter { item -> item.widget == null }.map { item -> item.key }) else it })
                            },
                            deleteFolder = { id -> state.value = state.value.copy(folders = state.value.folders.filterNot { it.id == id }) },
                            textMode = { state.value = state.value.copy(textMode = it) },
                            themedIcons = { state.value = state.value.copy(themedIcons = it) },
                        ),
                    )
                }
            }
        }
    }

    @Test
    fun fabSearchIncludesHiddenAppsAndLaunchesTheFilteredResult() {
        showLauncher(fixture().copy(hiddenAppKeys = setOf(apps.last().key)))
        compose.onNodeWithTag("launcher_fab").performClick()
        compose.onNodeWithTag("app_search").assertIsDisplayed()
        compose.onNodeWithTag("settings_root").assertDoesNotExist()
        compose.onNodeWithTag("launcher_sheet").assertDoesNotExist()
        compose.onNodeWithTag("app_search_query").performTextReplacement("Vault")
        compose.onNodeWithTag("app:${apps.last().key}").assertIsDisplayed()
        compose.onNodeWithTag("app_search_query").performTextReplacement("cAm")
        compose.onNodeWithTag("app:${apps[1].key}").assertIsDisplayed()
        listOf(apps[0], apps[2], apps[3], apps[4]).forEach {
            compose.onNodeWithTag("app:${it.key}").assertDoesNotExist()
        }
        saveScreenshot("settings-search-filtered.png")
        compose.onNodeWithTag("app:${apps[1].key}").performClick()
        compose.onNodeWithTag("app_search").assertDoesNotExist()
        compose.onNodeWithTag("home_clock").assertIsDisplayed()
        assertEquals(listOf(apps[1].key), launched)
    }

    @Test
    fun fabLongPressOpensFullScreenSettingsAndEachCategoryNavigatesBack() {
        showLauncher()
        val viewport = compose.onRoot().fetchSemanticsNode().boundsInRoot
        openSettings()
        val settingsBounds = compose.onNodeWithTag("settings_root").fetchSemanticsNode().boundsInRoot
        assertEquals(viewport.width, settingsBounds.width, 1f)
        assertEquals(viewport.height, settingsBounds.height, 1f)
        compose.onNodeWithTag("launcher_sheet").assertDoesNotExist()
        compose.onNodeWithTag("home_clock").assertDoesNotExist()
        saveScreenshot("settings-root.png")
        listOf("productivity", "themes", "advanced", "about").forEach { category ->
            clickSetting("settings_root", "settings_category_$category")
            compose.onNodeWithTag("settings_$category").assertIsDisplayed()
            compose.onNodeWithTag("launcher_sheet").assertDoesNotExist()
            if (category != "advanced") saveScreenshot("settings-$category.png")
            settingsBack()
            compose.onNodeWithTag("settings_root").assertIsDisplayed()
        }
        settingsBack()
        compose.onNodeWithTag("settings_root").assertDoesNotExist()
        compose.onNodeWithTag("home_clock").assertIsDisplayed()
    }

    @Test
    fun fabSearchStillRespondsToTheFirstTapAfterLeavingLongPressSettings() {
        showLauncher()
        openSettings()
        settingsBack()
        compose.onNodeWithTag("home_clock").assertIsDisplayed()
        compose.onNodeWithTag("launcher_fab").performClick()
        compose.onNodeWithTag("app_search").assertIsDisplayed()
    }

    @Test
    fun folderStillRespondsToTheFirstTapAfterLeavingLongPressDetails() {
        val folder = LauncherFolder("retained", "Everyday", listOf(apps[1].key), FolderPlacement.Favorites)
        showLauncher(fixture().copy(folders = listOf(folder)))
        compose.onNodeWithTag("folder:${folder.id}").performTouchInput { longClick() }
        compose.onNodeWithTag("app_details").assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("folder:${folder.id}").assertIsDisplayed().performClick()
        compose.onNodeWithTag("folder_popup").assertIsDisplayed()
        compose.onNodeWithTag("folder_app:${apps[1].key}").assertIsDisplayed()
    }

    @Test
    fun productivityTogglesRemoveAgendaAndBatteryFromTheHomeHeader() {
        val now = Instant.now()
        showLauncher(fixture().copy(events = listOf(
            ScheduleEvent(1, "Movie night", now.plusSeconds(1800), now.plusSeconds(7200), false, null, null),
        )))
        compose.onNodeWithTag("schedule_line", useUnmergedTree = true).assertIsDisplayed()
        openSettings()
        clickSetting("settings_root", "settings_category_productivity")
        clickSetting("settings_productivity", "calendar_agenda")
        clickSetting("settings_productivity", "show_battery")
        compose.runOnIdle {
            assertFalse(state.value.settings.calendarAgenda)
            assertFalse(state.value.settings.showBatteryPercentage)
        }
        settingsBack()
        settingsBack()
        compose.onNodeWithTag("schedule_line").assertDoesNotExist()
        val date = compose.onNodeWithTag("home_date").fetchSemanticsNode()
            .config[SemanticsProperties.Text].joinToString { it.text }
        assertFalse("Battery percentage must disappear without removing the date", date.contains('%'))
        assertTrue(date.isNotBlank())
        saveScreenshot("settings-home-agenda-battery-disabled.png")
    }

    @Test
    fun foldersRespectPlacementAndHiddenMembersRemainInPopupAndSearch() {
        val homeFolder = LauncherFolder("home", "Everyday", listOf(apps[1].key, apps[3].key), FolderPlacement.Favorites)
        val drawerFolder = LauncherFolder("drawer", "Tools", listOf(apps[2].key), FolderPlacement.AppList)
        showLauncher(fixture().copy(folders = listOf(homeFolder, drawerFolder)))
        compose.onNodeWithTag("folder:home").assertIsDisplayed()
        compose.onNodeWithTag("folder:drawer").assertDoesNotExist()
        compose.onNodeWithTag("folder:home").performClick()
        compose.onNodeWithTag("folder_app:${apps[3].key}").assertIsDisplayed()
        compose.onNodeWithTag("folder_app:${apps[1].key}").performClick()
        compose.onNodeWithTag("folder_popup").assertDoesNotExist()
        assertEquals(listOf(apps[1].key), launched)

        openSettings()
        clickSetting("settings_root", "settings_category_productivity")
        clickSetting("settings_productivity", "settings_open_hidden_apps")
        clickSetting("settings_hidden_apps", "hidden_app:${apps[3].key}")
        compose.onNodeWithTag("hidden_apps_save").performClick()
        settingsBack()
        settingsBack()
        compose.onNodeWithTag("folder:home").performClick()
        compose.onNodeWithTag("folder_app:${apps[1].key}").assertIsDisplayed()
        compose.onNodeWithTag("folder_app:${apps[3].key}").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(apps[3].key in state.value.hiddenAppKeys)
            assertTrue(apps[3].key in state.value.folders.first { it.id == "home" }.appKeys)
        }
        saveScreenshot("settings-folder-hidden-member.png")
        compose.onNodeWithTag("folder_app:${apps[1].key}").performClick()
        compose.onNodeWithTag("folder_popup").assertDoesNotExist()
        compose.onNodeWithTag("settings_root").assertDoesNotExist()
        compose.onNodeWithTag("home_clock").assertIsDisplayed()
        assertEquals(listOf(apps[1].key, apps[1].key), launched)
        val fab = compose.onNodeWithTag("launcher_fab").assertIsDisplayed()
        Log.i("NivaSettingsDiagnostics", "After folder close; FAB=${fab.fetchSemanticsNode().boundsInRoot}")
        fab.performClick()
        try {
            compose.onNodeWithTag("app_search").assertIsDisplayed()
        } catch (failure: AssertionError) {
            Log.e("NivaSettingsDiagnostics", compose.onRoot(useUnmergedTree = true).printToString())
            saveScreenshot("settings-search-after-folder-failure.png")
            throw failure
        }
        compose.onNodeWithTag("app_search_query").performTextReplacement("Gmail")
        compose.onNodeWithTag("app:${apps[3].key}").assertIsDisplayed()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithText(context.getString(R.string.cancel)).performClick()
        compose.onNodeWithTag("alphabet:◇").performClick()
        compose.onNodeWithTag("folder:drawer").assertIsDisplayed()
        compose.onNodeWithTag("folder:home").assertDoesNotExist()
        val folderY = compose.onNodeWithTag("folder:drawer").fetchSemanticsNode().boundsInRoot.top
        val lastAppY = compose.onNodeWithTag("app:${apps.last().key}").fetchSemanticsNode().boundsInRoot.top
        assertTrue("App-list folders belong after the final app group", folderY > lastAppY)
        saveScreenshot("settings-drawer-folder-tail.png")
    }

    @Test
    fun folderEditorCreatesMovesRemovesMembersAndDeletesWithConfirmation() {
        showLauncher()
        openSettings()
        clickSetting("settings_root", "settings_category_productivity")
        clickSetting("settings_productivity", "settings_open_folders")
        clickSetting("settings_folders", "folder_create")
        compose.onNodeWithTag("text_entry").performTextReplacement("Everyday")
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.save)).performClick()
        compose.runOnIdle { keyboard?.hide() }
        compose.onNodeWithTag("popup_editor_list").performScrollToNode(hasTestTag("favorite_all:${apps[1].key}"))
        compose.onNodeWithTag("favorite_all:${apps[1].key}").performClick()
        compose.onNodeWithTag("popup_editor_done").performClick()
        val folder = compose.runOnIdle { state.value.folders.single() }
        assertEquals("Everyday", folder.name)
        assertEquals(FolderPlacement.Favorites, folder.placement)
        assertEquals(listOf(apps[1].key), folder.appKeys)
        repeat(3) { settingsBack() }
        compose.onNodeWithTag("folder:${folder.id}").assertIsDisplayed()

        compose.onNodeWithTag("folder:${folder.id}").performTouchInput { longClick() }
        compose.onNodeWithTag("advanced").performClick()
        compose.onNodeWithTag("edit_popup").performClick()
        compose.onNodeWithTag("folder_placement").performClick()
        compose.onNodeWithTag("folder_placement:Favorites").performClick()
        compose.onNodeWithTag("folder_placement:AppList").performClick()
        compose.onNodeWithTag("folder_placement_done").performClick()
        compose.onNodeWithTag("popup_editor_list").performScrollToNode(hasTestTag("favorite:${apps[1].key}"))
        compose.onNodeWithTag("favorite:${apps[1].key}").performClick()
        compose.onNodeWithTag("popup_editor_done").performClick()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("folder:${folder.id}").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(FolderPlacement.AppList, state.value.folders.single().placement)
            assertTrue(state.value.folders.single().appKeys.isEmpty())
        }
        compose.onNodeWithTag("alphabet:◇").performClick()
        compose.onNodeWithTag("folder:${folder.id}").performClick()
        compose.onNodeWithTag("folder_empty").assertIsDisplayed()
        compose.onNodeWithTag("folder_edit").performClick()
        compose.onNodeWithTag("folder_delete").performClick()
        compose.runOnIdle { assertEquals(1, state.value.folders.size) }
        compose.onNodeWithTag("folder_confirm_delete").performClick()
        compose.runOnIdle { assertTrue(state.value.folders.isEmpty()) }
        compose.onNodeWithTag("folder:${folder.id}").assertDoesNotExist()
    }

    @Test
    fun paletteDynamicColorAndDarkModeImmediatelyUpdateTheLauncherTheme() {
        showLauncher()
        openSettings()
        clickSetting("settings_root", "settings_category_themes")
        val originalPrimary = compose.runOnIdle { themePrimary }
        compose.onNodeWithTag("settings_palette_1").performClick()
        compose.runOnIdle {
            assertEquals(0xFFB3261E.toInt(), state.value.settings.themeColor)
            assertFalse(state.value.settings.useDynamicColors)
            assertNotEquals(originalPrimary, themePrimary)
        }
        clickSetting("settings_themes", "settings_dynamic_colors")
        compose.runOnIdle { assertTrue(state.value.settings.useDynamicColors) }
        clickSetting("settings_themes", "settings_dynamic_colors")
        compose.runOnIdle {
            assertFalse(state.value.settings.useDynamicColors)
            assertEquals(0xFFB3261E.toInt(), state.value.settings.themeColor)
        }
        clickSetting("settings_themes", "settings_theme_mode")
        compose.onNodeWithTag("theme_mode:Dark").performClick()
        compose.runOnIdle {
            assertEquals(ThemeMode.Dark, state.value.settings.darkMode)
            assertTrue("The active Material theme must also become dark", themeSurface.luminance() < 0.5f)
        }
        compose.onNodeWithTag("settings_list").performScrollToIndex(0)
        saveScreenshot("settings-themes-dark.png")
        clickSetting("settings_themes", "settings_theme_mode")
        compose.onNodeWithTag("theme_mode:Light").performClick()
        compose.runOnIdle {
            assertEquals(ThemeMode.Light, state.value.settings.darkMode)
            assertTrue(themeSurface.luminance() > 0.5f)
        }
    }

    private fun openSettings() {
        compose.onNodeWithTag("launcher_fab").performTouchInput { longClick() }
        compose.onNodeWithTag("settings_root").assertIsDisplayed()
    }

    private fun settingsBack() = compose.onNodeWithTag("settings_back").performClick()

    private fun clickSetting(page: String, tag: String) {
        compose.onNodeWithTag(page).assertIsDisplayed()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
    }

    private fun saveScreenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = checkNotNull(instrumentation.targetContext.getExternalFilesDir("ui-verification"))
        directory.mkdirs()
        val output = File(directory, name)
        output.outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
        Log.i("NivaUiVerification", "Screenshot: ${output.absolutePath}")
    }
}
