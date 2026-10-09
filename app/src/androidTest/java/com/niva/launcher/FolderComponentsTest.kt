package com.niva.launcher

import android.content.ComponentName
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.niva.launcher.data.FolderPlacement
import com.niva.launcher.data.LauncherApp
import com.niva.launcher.data.LauncherFolder
import com.niva.launcher.ui.components.FolderRow
import com.niva.launcher.ui.overlays.FolderPopup
import com.niva.launcher.ui.overlays.ShortcutRevealState
import com.niva.launcher.ui.theme.NivaLauncherTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderComponentsTest {
    @get:Rule val compose = createComposeRule()

    private val browser = LauncherApp(ComponentName("test.browser", "Browser.Activity"), "Browser", null)
    private val mail = LauncherApp(ComponentName("test.mail", "Mail.Activity"), "Mail", null)
    private val folder = LauncherFolder(
        id = "essentials",
        name = "Essentials",
        appKeys = listOf(mail.key, "missing/Activity", browser.key, mail.key),
        placement = FolderPlacement.Favorites,
    )

    @Test
    fun folderRevealFinishesWithTheFingerStationaryAndReversingClosesIt() {
        showFolder()
        val row = compose.onNodeWithTag("folder:${folder.id}")
        val pixelsPerDp = density()
        row.performTouchInput {
            down(Offset(8f, centerY))
            moveTo(Offset(8f + 24f * pixelsPerDp, centerY), delayMillis = 120)
        }
        assertEquals("A short swipe starts an opening animation that finishes before UP", 1f, progress(), 0.001f)
        saveScreenshot("folder-popup-held.png")
        row.performTouchInput {
            moveTo(Offset(8f, centerY), delayMillis = 80)
            up()
        }
        compose.waitForIdle()
        compose.onNodeWithTag("folder_popup").assertDoesNotExist()
    }

    @Test
    fun folderTapKeepsMemberOrderSkipsMissingAppsAndLaunchesFromTheIconBounds() {
        var launched: LauncherApp? = null
        var source = Rect.Zero
        showFolder(onLaunch = { app, bounds -> launched = app; source = bounds })
        compose.onNodeWithTag("folder:${folder.id}").performClick()
        val mailRow = compose.onNodeWithTag("folder_app:${mail.key}").assertIsDisplayed()
        val browserRow = compose.onNodeWithTag("folder_app:${browser.key}").assertIsDisplayed()
        compose.onAllNodesWithTag("folder_app:${mail.key}").assertCountEquals(1)
        compose.onNodeWithTag("folder_app:missing/Activity").assertDoesNotExist()
        assertTrue("Member order follows the folder, not the app catalog", mailRow.fetchSemanticsNode().boundsInRoot.top < browserRow.fetchSemanticsNode().boundsInRoot.top)
        saveScreenshot("folder-popup-members.png")
        browserRow.performClick()
        compose.runOnIdle {
            assertEquals(browser, launched)
            assertTrue("Launch origin is an actual placed icon", source.left > 0f && source.top > 0f)
            assertEquals(36f * density(), source.width, 1f)
            assertEquals(36f * density(), source.height, 1f)
        }
        compose.onNodeWithTag("folder_popup").assertDoesNotExist()
    }

    @Test
    fun emptyFolderHasAnEditActionAndDoesNotInventMembers() {
        var edited = false
        showFolder(folder.copy(appKeys = emptyList()), onEdit = { edited = true })
        compose.onNodeWithTag("folder:${folder.id}").performClick()
        compose.onNodeWithTag("folder_empty").assertIsDisplayed()
        compose.onNodeWithTag("folder_app:${mail.key}").assertDoesNotExist()
        compose.onNodeWithTag("folder_edit").performClick()
        compose.runOnIdle { assertTrue(edited) }
    }

    @Test
    fun folderLongPressUsesTheDetailsCallbackWithoutOpeningContents() {
        var longPressed = false
        showFolder(onLongClick = { longPressed = true })
        compose.onNodeWithTag("folder:${folder.id}").performTouchInput { longClick() }
        compose.runOnIdle { assertTrue(longPressed) }
        compose.onNodeWithTag("folder_popup").assertDoesNotExist()
    }

    private fun showFolder(
        displayedFolder: LauncherFolder = folder,
        onLaunch: (LauncherApp, Rect) -> Unit = { _, _ -> },
        onEdit: () -> Unit = {},
        onLongClick: () -> Unit = {},
    ) {
        compose.setContent {
            NivaLauncherTheme(darkTheme = false, dynamicColor = false) {
                var anchor by remember { mutableStateOf(Rect.Zero) }
                var reveal by remember { mutableStateOf<ShortcutRevealState?>(null) }
                Box(Modifier.fillMaxSize().background(Color(0xFF283444)).testTag("folder_test_root")) {
                    FolderRow(
                        folder = displayedFolder,
                        onOpen = { anchor = it; reveal = ShortcutRevealState() },
                        onLongClick = onLongClick,
                        onDrag = { bounds, expanded ->
                            anchor = bounds
                            val state = reveal ?: ShortcutRevealState(expanded, dragging = true).also { reveal = it }
                            state.expanded = expanded
                            state.dragging = true
                        },
                        onDragEnd = { commit ->
                            reveal?.let { state -> state.expanded = commit; state.dragging = false }
                        },
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
                    )
                    reveal?.let { state ->
                        FolderPopup(
                            folder = displayedFolder,
                            apps = listOf(browser, mail),
                            anchor = anchor,
                            onDismiss = { reveal = null },
                            onLaunchApp = onLaunch,
                            onEdit = onEdit,
                            reveal = state,
                        )
                    }
                }
            }
        }
    }

    private fun progress(): Float = compose.onNodeWithTag("folder_popup")
        .fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current

    private fun density(): Float = InstrumentationRegistry.getInstrumentation()
        .targetContext.resources.displayMetrics.density

    private fun saveScreenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "ui-verification").apply { mkdirs() }
        val output = File(directory, name)
        val bitmap = compose.onNodeWithTag("folder_test_root").captureToImage().asAndroidBitmap()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Log.i("NivaUiVerification", output.absolutePath)
    }
}
