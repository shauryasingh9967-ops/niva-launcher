package com.galaxyrio.gracelauncher

import android.content.ComponentName
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherScreen
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.ScheduleStatus
import com.galaxyrio.gracelauncher.ui.overlays.FavoritesScreen
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoritesEditorTest {
    @get:Rule val compose = createComposeRule()
    private val apps = listOf("Alpha", "Browser", "Camera", "Clock", "Mail", "Messages", "Music", "Phone", "Photos", "Reader", "Settings", "Zen").map {
        LauncherApp(ComponentName("test.${it.lowercase()}", "$it.Activity"), it, null)
    }
    private var state by mutableStateOf(LauncherUiState())
    private var commits = 0
    private var toggles = 0

    private fun show(selected: List<LauncherApp>, wholeLauncher: Boolean = false) {
        state = LauncherUiState(apps = apps, favoriteKeys = selected.map(LauncherApp::key).toSet(),
            favoriteOrder = selected.map(LauncherApp::key), isLoadingApps = false, scheduleStatus = ScheduleStatus.Ready)
        val toggle: (LauncherApp) -> Unit = { app ->
            toggles++
            val order = if (app.key in state.favoriteKeys) state.favoriteOrder - app.key else state.favoriteOrder + app.key
            state = state.copy(favoriteKeys = order.toSet(), favoriteOrder = order)
        }
        val reorder: (List<String>) -> Unit = { commits++; state = state.copy(favoriteOrder = it) }
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    if (wholeLauncher) LauncherScreen(state, onDateClick = {}, onClockClick = {}, onLaunchApp = {},
                        onToggleFavorite = toggle, actions = LauncherActions(reorderFavorites = reorder))
                    else Box(Modifier.fillMaxSize()) { FavoritesScreen(state, LauncherActions(), toggle, reorder, onDone = {}) }
                }
            }
        }
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun assertSelectedOrder(expected: List<LauncherApp>) {
        assertEquals(expected.map(LauncherApp::key), state.favoriteOrder)
        expected.zipWithNext().forEach { (first, second) ->
            assertTrue(bounds("favorite:${first.key}").top < bounds("favorite:${second.key}").top)
        }
    }

    @Test fun selectedAppsComeFirstAndAllAppsKeepTheirChecksAndAlphabeticOrder() {
        val selected = listOf(apps[3], apps[0], apps[2])
        show(selected)
        assertSelectedOrder(selected)
        compose.onNodeWithTag("favorites_all_apps").performScrollTo()
        assertTrue(bounds("favorite:${apps[2].key}").bottom <= bounds("favorites_all_apps").top)
        apps.take(3).forEach { app ->
            compose.onNodeWithTag("favorite_all:${app.key}").performScrollTo().let {
                if (app in selected) it.assertIsOn() else it.assertIsOff()
            }
        }
        compose.onNodeWithTag("favorites_list").performScrollToIndex(0)
        saveScreenshot("favorites-selected.png")
    }

    @Test fun selectionsAppendToTheSelectedSectionAndUncheckingDoesNotRemoveTheAppFromAllApps() {
        show(listOf(apps[3], apps[0]))
        compose.onNodeWithTag("favorite_all:${apps[2].key}").performScrollTo().performClick()
        compose.onNodeWithTag("favorites_list").performScrollToIndex(0)
        assertSelectedOrder(listOf(apps[3], apps[0], apps[2]))
        compose.onNodeWithTag("favorite:${apps[0].key}").performClick()
        compose.onNodeWithTag("favorite_all:${apps[0].key}").performScrollTo().assertIsOff()
        assertEquals(listOf(apps[3].key, apps[2].key), state.favoriteOrder)
        assertEquals(2, toggles)
        assertEquals(0, commits)
    }

    @Test fun handlesDragBothDirectionsWithoutTogglingAndOrderIsUsedOnHome() {
        show(apps.take(4), wholeLauncher = true)
        compose.onNodeWithTag("app:${apps[0].key}").performTouchInput { longClick() }
        compose.onNodeWithTag("edit_favorites").performClick()
        val rowDistance = bounds("favorite_drag:${apps[3].key}").center.y - bounds("favorite_drag:${apps[0].key}").center.y
        compose.onNodeWithTag("favorite_drag:${apps[0].key}").performTouchInput {
            swipe(center, center + Offset(0f, rowDistance + 50f), 650)
        }
        assertSelectedOrder(listOf(apps[1], apps[2], apps[3], apps[0]))
        compose.onNodeWithTag("favorite_drag:${apps[0].key}").performTouchInput {
            swipe(center, center - Offset(0f, rowDistance + 50f), 650)
        }
        assertSelectedOrder(apps.take(4))
        // Also expose precise move actions for keyboard and accessibility users.
        compose.onNodeWithTag("favorite_drag:${apps[0].key}").performClick()
        compose.onNodeWithText("Move down").performClick()
        assertSelectedOrder(listOf(apps[1], apps[0], apps[2], apps[3]))
        compose.onNodeWithTag("favorites_done").performClick()
        compose.onNodeWithTag("favorites_screen").assertDoesNotExist()
        assertTrue(bounds("app:${apps[1].key}").top < bounds("app:${apps[0].key}").top)
        assertEquals(0, toggles)
        assertEquals(3, commits)
    }

    @Test fun cancelledDragRestoresOrderAndDoesNotPersist() {
        show(apps.take(4))
        compose.onNodeWithTag("favorite_drag:${apps[0].key}").performTouchInput {
            down(center)
            moveBy(Offset(0f, 160f), 100)
            moveBy(Offset(0f, 160f), 100)
            cancel()
        }
        assertSelectedOrder(apps.take(4))
        assertEquals(0, commits)
        assertEquals(0, toggles)
    }

    @Test fun swappingRowsKeepsTheDraggedRowUnderTheFingerAndNeighborsMoveContinuously() {
        show(apps.take(4))
        val handle = compose.onNodeWithTag("favorite_drag:${apps[0].key}")
        val neighborTag = "favorite:${apps[1].key}"
        val initialTop = bounds(neighborTag).top
        val rowHeight = bounds(neighborTag).height
        compose.mainClock.autoAdvance = false
        handle.performTouchInput {
            down(center)
            moveBy(Offset(0f, rowHeight * 0.6f), 16)
        }
        compose.mainClock.advanceTimeBy(64)
        val draggedTop = bounds("favorite:${apps[0].key}").top
        val delta = rowHeight * 0.7f
        handle.performTouchInput { moveBy(Offset(0f, delta), 16) }
        val neighborPositions = mutableListOf(initialTop)
        val draggedPositions = mutableListOf<Float>()
        repeat(30) {
            compose.mainClock.advanceTimeByFrame()
            neighborPositions += bounds(neighborTag).top
            draggedPositions += bounds("favorite:${apps[0].key}").top
        }
        handle.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(initialTop - rowHeight, neighborPositions.last(), 1f)
        assertTrue("Neighbors must not jump a full slot before animating: $neighborPositions",
            neighborPositions.zipWithNext().all { (a, b) -> kotlin.math.abs(b - a) < rowHeight * 0.3f })
        assertTrue("The dragged row must not briefly fall into its new layout slot: $draggedPositions; expected ${draggedTop + delta}",
            draggedPositions.all { kotlin.math.abs(it - (draggedTop + delta)) < 2f })
        assertSelectedOrder(listOf(apps[1], apps[0], apps[2], apps[3]))
    }

    @Test fun ordinaryListScrollingNeverChangesFavoritesAndAnEmptySelectionCanBeRefilled() {
        show(emptyList())
        compose.onNodeWithText("Choose apps below to add them to your home screen.").assertIsDisplayed()
        compose.onNodeWithTag("favorites_list").performTouchInput { swipeUp() }
        compose.onNodeWithTag("favorite_all:${apps.last().key}").performScrollTo().performClick()
        compose.onNodeWithTag("favorites_list").performScrollToIndex(0)
        compose.onNodeWithTag("favorite:${apps.last().key}").assertIsOn()
        assertEquals(listOf(apps.last().key), state.favoriteOrder)
        assertEquals(0, commits)
    }

    @Test fun draggingAtTheBottomEdgeScrollsThroughALongSelectedList() {
        show(apps)
        val first = compose.onNodeWithTag("favorite_drag:${apps[0].key}")
        val distance = bounds("favorites_list").bottom - bounds("favorite_drag:${apps[0].key}").center.y - 12f
        compose.mainClock.autoAdvance = false
        first.performTouchInput {
            down(center)
            moveBy(Offset(0f, distance), 300)
        }
        compose.mainClock.advanceTimeBy(2400)
        first.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(apps[0].key, state.favoriteOrder.last())
        assertEquals(apps.map(LauncherApp::key).toSet(), state.favoriteKeys)
        assertEquals(1, commits)
        assertEquals(0, toggles)
        compose.onNodeWithTag("favorite:${apps[0].key}").performScrollTo()
        val upward = bounds("favorites_list").top - bounds("favorite_drag:${apps[0].key}").center.y + 12f
        compose.mainClock.autoAdvance = false
        first.performTouchInput {
            down(center)
            moveBy(Offset(0f, upward), 300)
        }
        compose.mainClock.advanceTimeBy(2400)
        first.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(apps.map(LauncherApp::key), state.favoriteOrder)
        assertEquals(2, commits)
        assertEquals(0, toggles)
    }

    private fun saveScreenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.getExternalFilesDir("ui-verification"), name)
        file.parentFile!!.mkdirs()
        val bitmap = compose.onNodeWithTag("favorites_screen").captureToImage().asAndroidBitmap()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
