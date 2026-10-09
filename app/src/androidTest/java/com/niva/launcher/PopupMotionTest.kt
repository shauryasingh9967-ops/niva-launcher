package com.niva.launcher

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.niva.launcher.ui.overlays.ShortcutRevealState
import com.niva.launcher.ui.overlays.SwipeRevealPanel
import com.niva.launcher.ui.theme.NivaLauncherTheme
import java.io.File
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PopupMotionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun morphStartsAtTheIconGrowsOnBothAxesAndReversesWithoutAnAnimationReset() {
        var reveal by mutableStateOf<ShortcutRevealState?>(null)
        compose.setContent {
            NivaLauncherTheme(darkTheme = false, dynamicColor = false) {
                var anchor by remember { mutableStateOf(Rect.Zero) }
                Box(Modifier.fillMaxSize().background(Color(0xFFCAD6D6)).testTag("motion_root")) {
                    Box(Modifier.offset(x = 40.dp, y = 350.dp).size(width = 40.dp, height = 52.dp)
                        .background(Color.DarkGray).testTag("source_icon")
                        .onGloballyPositioned { anchor = it.boundsInWindow() })
                    reveal?.let { state ->
                        SwipeRevealPanel(anchor, state, "motion_panel", "Shortcuts", onDismiss = { reveal = null }) {
                            Text("Music", Modifier.padding(16.dp))
                            repeat(4) { Text("Shortcut ${it + 1}", Modifier.fillMaxWidth().height(56.dp).padding(16.dp)) }
                        }
                    }
                }
            }
        }
        val icon = bounds("source_icon")
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { reveal = ShortcutRevealState(dragging = true) }
        compose.mainClock.advanceTimeByFrame()
        val initial = bounds("motion_panel")
        assertEquals(icon.center.x, initial.center.x, 1f)
        assertEquals(icon.center.y, initial.center.y, 1f)
        assertEquals(icon.width, initial.width, 1f)
        assertEquals(icon.width, initial.height, 1f)
        compose.mainClock.advanceTimeBy(80)
        val partial = progress()
        val middle = bounds("motion_panel")
        assertTrue("The spring has an intermediate frame", partial > 0f && partial < 1f)
        assertTrue(middle.width > initial.width && middle.height > initial.height)
        saveScreenshot("popup-hero-intermediate.png")

        compose.runOnIdle { reveal!!.expanded = false }
        compose.mainClock.advanceTimeByFrame()
        assertTrue("Retargeting must not jump to an endpoint", abs(progress() - partial) < 0.3f)
        compose.mainClock.advanceTimeByFrame()
        assertTrue("The reversing spring must retain its forward velocity before slowing down", progress() > partial)
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(0f, progress(), 0.001f)
        assertNotNull("Closing during a held gesture must allow reopening", reveal)

        compose.runOnIdle { reveal!!.expanded = true }
        compose.mainClock.advanceTimeBy(1000)
        assertEquals("It finishes without another pointer move", 1f, progress(), 0.001f)
        val end = bounds("motion_panel")
        assertTrue(end.width > middle.width && end.height > middle.height)
        saveScreenshot("popup-hero-expanded.png")
        compose.runOnIdle { reveal!!.expanded = false; reveal!!.dragging = false }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("motion_panel").assertDoesNotExist()
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    private fun progress() = compose.onNodeWithTag("motion_panel").fetchSemanticsNode()
        .config[SemanticsProperties.ProgressBarRangeInfo].current

    private fun saveScreenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir("ui-verification"), name)
        file.parentFile!!.mkdirs()
        val bitmap = compose.onNodeWithTag("motion_root").captureToImage().asAndroidBitmap()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
