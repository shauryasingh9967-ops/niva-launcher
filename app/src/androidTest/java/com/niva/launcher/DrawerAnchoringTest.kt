package com.niva.launcher

import android.content.ComponentName
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.niva.launcher.data.LauncherApp
import com.niva.launcher.data.WallpaperTextMode
import com.niva.launcher.ui.LauncherScreen
import com.niva.launcher.ui.LauncherUiState
import com.niva.launcher.ui.ScheduleStatus
import com.niva.launcher.ui.theme.NivaLauncherTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DrawerAnchoringTest {
    @get:Rule val compose = createComposeRule()

    // Normal groups fit above the middle anchor. M overflows a viewport, while
    // the three single-app tail groups are too short to reach the same anchor.
    private val apps = ('A'..'Z').flatMap { letter ->
        val count = when (letter) {
            'M' -> 14
            in 'X'..'Z' -> 1
            else -> 2
        }
        (1..count).map { index ->
            val packageName = "test.anchor.${letter.lowercaseChar()}$index"
            LauncherApp(ComponentName(packageName, "$packageName.MainActivity"), "$letter app $index", null)
        }
    }

    @Test
    fun middleLetterUsesTheInitialAnchorAndReleaseOnlyRevealsItsNeighbours() {
        showDrawer()
        val viewport = drawer().fetchSemanticsNode().boundsInRoot
        val firstHeader = bounds(section('A'))
        val blankFraction = (firstHeader.top - viewport.top) / viewport.height
        assertTrue("The initial group should start after roughly 30% top whitespace", blankFraction in 0.26f..0.36f)
        saveScreenshot("drawer-anchor-start.png")

        withHeldLetter('D') { _, finish ->
            section('D').assertIsDisplayed()
            val heldHeader = bounds(section('D'))
            val heldApp = bounds(app('D'))
            assertEquals("Middle letters must use the original A anchor", firstHeader.top, heldHeader.top, 1f)
            section('C').assertIsNotDisplayed()
            section('E').assertIsNotDisplayed()
            app('C', 2).assertIsNotDisplayed()
            saveScreenshot("drawer-held-middle.png")

            finish(false)
            assertPositionUnchanged(heldHeader, bounds(section('D')), "D header after UP")
            assertPositionUnchanged(heldApp, bounds(app('D')), "D app after UP")
            section('C').assertIsDisplayed()
            app('C', 2).assertIsDisplayed()
            section('E').assertIsDisplayed()
            assertTrue("Previous C apps should appear above D without moving it", bounds(app('C', 2)).bottom <= heldHeader.top + 1f)
            saveScreenshot("drawer-released-middle.png")
        }
    }

    @Test
    fun shortTailClampsNaturallyAndReleaseDoesNotJumpToAnArtificialAnchor() {
        showDrawer()
        val firstHeader = bounds(section('A'))
        val viewport = drawer().fetchSemanticsNode().boundsInRoot
        withHeldLetter('X') { _, finish ->
            val heldHeader = bounds(section('X'))
            val heldApp = bounds(app('X'))
            assertTrue("A short tail must remain lower than the middle-list anchor", heldHeader.top > firstHeader.top + 48f * density())
            section('W').assertIsNotDisplayed()
            section('Y').assertIsNotDisplayed()
            section('Z').assertIsNotDisplayed()
            saveScreenshot("drawer-held-end.png")

            finish(false)
            assertPositionUnchanged(heldHeader, bounds(section('X')), "X header after UP")
            assertPositionUnchanged(heldApp, bounds(app('X')), "X app after UP")
            section('W').assertIsDisplayed()
            section('Y').assertIsDisplayed()
            section('Z').assertIsDisplayed()
            app('Z').assertIsDisplayed()
            assertEquals(
                "The final app should leave only the normal 24dp bottom inset",
                24f * density(), viewport.bottom - bounds(app('Z')).bottom, 2f,
            )
            saveScreenshot("drawer-released-end.png")
        }
    }

    @Test
    fun scrollingTheCompleteListAwayAndBackRestoresTheInitialAnchor() {
        showDrawer()
        val firstHeader = bounds(section('A'))
        val firstD = apps.first { it.section == "D" }
        val dHeaderIndex = apps.indexOf(firstD) + ('D' - 'A')
        drawer().performScrollToIndex(dHeaderIndex)
        assertEquals(firstHeader.top, bounds(section('D')).top, 1f)
        app('C', 2).assertIsDisplayed()
        assertTrue(
            "Ordinary scrolling must allow earlier apps into the initial top whitespace",
            bounds(app('C', 2)).top < firstHeader.top - 32f * density(),
        )
        drawer().performScrollToIndex(0)
        assertPositionUnchanged(firstHeader, bounds(section('A')), "A header after returning to the beginning")
    }

    @Test
    fun catalogueThatAlreadyFitsNeverMovesItsSelectedGroupOnHoldOrRelease() {
        val shortCatalogue = apps.filter { it.section in listOf("A", "B", "C") && it.label.endsWith(" 1") }
        showDrawer(shortCatalogue)
        app('A').assertIsDisplayed()
        app('B').assertIsDisplayed()
        app('C').assertIsDisplayed()
        val originalHeader = bounds(section('C'))
        val originalApp = bounds(app('C'))
        withHeldLetter('C', listOf('A', 'B', 'C')) { _, finish ->
            assertPositionUnchanged(originalHeader, bounds(section('C')), "Short catalogue C header while held")
            assertPositionUnchanged(originalApp, bounds(app('C')), "Short catalogue C app while held")
            app('A').assertIsNotDisplayed()
            app('B').assertIsNotDisplayed()
            finish(false)
            assertPositionUnchanged(originalHeader, bounds(section('C')), "Short catalogue C header after UP")
            assertPositionUnchanged(originalApp, bounds(app('C')), "Short catalogue C app after UP")
            app('A').assertIsDisplayed()
            app('B').assertIsDisplayed()
        }
    }

    @Test
    fun rapidScrubbingAndCancellationDoNotChangeTheSelectedGroupsPosition() {
        showDrawer()
        val firstHeader = bounds(section('A'))
        withHeldLetter('D') { rail, finish ->
            listOf('X', 'B', 'M', 'D').forEach { letter ->
                rail.performTouchInput {
                    moveTo(Offset(centerX, height * railFraction(letter)), delayMillis = 32)
                }
                section(letter).assertIsDisplayed()
                if (letter != 'X') {
                    assertEquals("$letter should reuse the stable middle anchor", firstHeader.top, bounds(section(letter)).top, 1f)
                }
            }
            val heldHeader = bounds(section('D'))
            val heldApp = bounds(app('D'))
            finish(true)
            assertPositionUnchanged(heldHeader, bounds(section('D')), "D header after CANCEL")
            assertPositionUnchanged(heldApp, bounds(app('D')), "D app after CANCEL")
            section('C').assertIsDisplayed()
            section('E').assertIsDisplayed()
        }
    }

    @Test
    fun overflowingGroupKeepsItsAnchorAndRemainsPartOfTheCompleteScrollableList() {
        showDrawer()
        val firstHeader = bounds(section('A'))
        withHeldLetter('M') { _, finish ->
            val heldHeader = bounds(section('M'))
            val heldApp = bounds(app('M'))
            assertEquals(firstHeader.top, heldHeader.top, 1f)
            section('L').assertIsNotDisplayed()
            app('M', 14).assertIsNotDisplayed()
            finish(false)
            assertPositionUnchanged(heldHeader, bounds(section('M')), "Long M header after UP")
            assertPositionUnchanged(heldApp, bounds(app('M')), "Long M app after UP")
            section('L').assertIsDisplayed()
        }
        val lastM = apps.last { it.section == "M" }
        // One header for every letter up to and including this app's section.
        val indexInFullList = apps.indexOf(lastM) + ('M' - 'A' + 1)
        drawer().performScrollToIndex(indexInFullList)
        app('M', 14).assertIsDisplayed()
        section('N').assertIsDisplayed()
        app('N').assertIsDisplayed()
    }

    private fun showDrawer(catalogue: List<LauncherApp> = apps) {
        compose.setContent {
            NivaLauncherTheme(dynamicColor = false) {
                Box(Modifier.fillMaxSize().background(Color(0xFF152431))) {
                    LauncherScreen(
                        uiState = LauncherUiState(
                            apps = catalogue,
                            favoriteKeys = catalogue.take(2).mapTo(linkedSetOf(), LauncherApp::key),
                            isLoadingApps = false,
                            scheduleStatus = ScheduleStatus.Ready,
                            textMode = WallpaperTextMode.Light,
                        ),
                        onDateClick = {}, onClockClick = {}, onLaunchApp = {}, onToggleFavorite = {},
                        initialDrawerOpen = true,
                    )
                }
            }
        }
    }

    private fun drawer() = compose.onNodeWithTag("app_drawer")

    private fun section(letter: Char) = drawerChild("section:$letter")

    private fun app(letter: Char, number: Int = 1): SemanticsNodeInteraction {
        val app = apps.single { it.label == "$letter app $number" }
        return drawerChild("app:${app.key}")
    }

    private fun drawerChild(tag: String) = compose.onNode(
        hasTestTag(tag) and hasAnyAncestor(hasTestTag("app_drawer")),
    )

    private fun bounds(node: SemanticsNodeInteraction) = node.fetchSemanticsNode().boundsInRoot

    private fun assertPositionUnchanged(before: Rect, after: Rect, label: String) {
        assertEquals("$label left", before.left, after.left, 1f)
        assertEquals("$label top", before.top, after.top, 1f)
        assertEquals("$label bottom", before.bottom, after.bottom, 1f)
    }

    private fun railFraction(letter: Char, letters: List<Char> = ('A'..'Z').toList()) =
        (letters.indexOf(letter) + 1.5f) / (letters.size + 1f)

    private fun withHeldLetter(
        letter: Char,
        letters: List<Char> = ('A'..'Z').toList(),
        block: (rail: SemanticsNodeInteraction, finish: (cancel: Boolean) -> Unit) -> Unit,
    ) {
        val rail = compose.onNodeWithTag("alphabet_rail")
        var held = true
        rail.performTouchInput { down(Offset(centerX, height * railFraction(letter, letters))) }
        try {
            block(rail) { cancelGesture ->
                rail.performTouchInput { if (cancelGesture) cancel() else up() }
                held = false
            }
        } finally {
            if (held) rail.performTouchInput { cancel() }
        }
    }

    private fun density() = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density

    private fun saveScreenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = instrumentation.targetContext.getExternalFilesDir("ui-verification")!!
        directory.mkdirs()
        compose.waitForIdle()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val output = File(directory, name)
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Log.i("NivaUiVerification", "Screenshot: ${output.absolutePath}")
    }
}
