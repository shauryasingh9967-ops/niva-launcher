package com.niva.launcher

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.niva.launcher.ui.components.AlphabetRail
import com.niva.launcher.ui.theme.NivaLauncherTheme
import java.io.File
import kotlin.math.ceil
import kotlin.math.floor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlphabetIndicatorTest {
    @get:Rule val compose = createComposeRule()

    private val letters = listOf("C", "G", "I", "W", "#")
    private var indicatorColor = 0
    private var indicatorInkColor = 0
    private val themeSeed = mutableStateOf(Color(0xFF6750A4))
    private val darkTheme = mutableStateOf(true)

    @Test
    fun indicatorCentersTheActualInkOfNarrowWideAndNonAlphabeticGlyphs() {
        showRail()
        withHeldC { rail ->
            letters.forEach { letter ->
                rail.performTouchInput {
                    moveTo(Offset(centerX, height * fraction(letter)), delayMillis = 32)
                }
                val pixels = inspect(letter)
                assertCenteredInk(pixels, letter)
                assertEquals("Indicator keeps its 46dp diameter", 46f * density(), pixels.circle.width.toFloat(), 2f)
                saveScreenshot("alphabet-indicator-${if (letter == "#") "hash" else letter}.png")
            }
        }
    }

    @Test
    fun indicatorKeepsAVisibleGapFromActualRailInkDuringHorizontalPulls() {
        showRail()
        withHeldC { rail ->
            var firstGap: Float? = null
            var firstCircleX: Float? = null
            listOf(44f, 24f, 0f, -24f, -80f).forEach { fingerX ->
                rail.performTouchInput {
                    moveTo(Offset(fingerX * density(), height * fraction("C")), delayMillis = 32)
                }
                val pixels = inspect("C")
                assertCenteredInk(pixels, "C at finger x=$fingerX")
                val gap = assertRailGap(pixels, "C at finger x=$fingerX")
                val expectedGap = firstGap
                if (expectedGap == null) {
                    firstGap = gap
                    firstCircleX = pixels.circle.centerX
                } else {
                    assertEquals("Horizontal pulling should preserve the visible separation", expectedGap, gap, 2f)
                    assertTrue("The bubble should follow the wave leftwards", pixels.circle.centerX <= checkNotNull(firstCircleX))
                }
            }
            saveScreenshot("alphabet-indicator-horizontal-pull.png")
        }
    }

    @Test
    fun indicatorStaysCenteredAndSeparatedAcrossLetterBoundariesAndTheRailEnd() {
        showRail()
        withHeldC { rail ->
            // Cell zero is Home. Include both sides of a boundary and the lower clamp.
            val samples = listOf(
                "C" to 1.5f, "C" to 1.92f, "G" to 2.08f, "G" to 2.5f,
                "I" to 3.5f, "W" to 4.5f, "#" to 5.5f, "#" to 5.95f,
                "G" to 2.2f, "C" to 1.5f,
            )
            samples.forEach { (letter, cell) ->
                rail.performTouchInput {
                    moveTo(Offset(centerX, height * cell / (letters.size + 1)), delayMillis = 32)
                }
                val pixels = inspect(letter)
                assertCenteredInk(pixels, "$letter at cell $cell")
                assertRailGap(pixels, "$letter at cell $cell")
                if (cell == 5.95f) saveScreenshot("alphabet-indicator-bottom-crossing.png")
            }
        }
    }

    @Test
    fun favoriteCellShowsARealStarBubbleAndKeepsItWhileScrubbingBackFromLetters() {
        showRail()
        val rail = compose.onNodeWithTag("alphabet_rail")
        val homeFraction = 0.5f / (letters.size + 1)
        var held = false
        try {
            rail.performTouchInput { down(Offset(centerX, height * homeFraction)) }
            held = true
            assertVisibleFavoriteIndicator()

            rail.performTouchInput { moveTo(Offset(centerX, height * fraction("C")), delayMillis = 32) }
            compose.onNodeWithTag("alphabet_indicator_star", useUnmergedTree = true).assertDoesNotExist()
            val letterPixels = inspect("C")
            assertCenteredInk(letterPixels, "C after the favorites cell")
            assertEquals("The same bubble remains visible over C", 46f * density(), letterPixels.circle.width.toFloat(), 2f)

            rail.performTouchInput { moveTo(Offset(centerX, height * homeFraction), delayMillis = 32) }
            assertVisibleFavoriteIndicator()
            saveScreenshot("alphabet-indicator-favorite.png")
            rail.performTouchInput { up() }
            held = false
            assertIndicatorHidden()

            rail.performTouchInput { down(Offset(centerX, height * homeFraction)) }
            held = true
            assertVisibleFavoriteIndicator()
            rail.performTouchInput { cancel() }
            held = false
            assertIndicatorHidden()
        } finally {
            if (held) rail.performTouchInput { cancel() }
        }
    }

    private fun showRail() {
        compose.setContent {
            var selected by remember { mutableStateOf<String?>(null) }
            NivaLauncherTheme(dynamicColor = false, seedColor = themeSeed.value, darkTheme = darkTheme.value) {
                val colors = MaterialTheme.colorScheme
                SideEffect { indicatorColor = colors.primary.toArgb(); indicatorInkColor = colors.onPrimary.toArgb() }
                Box(
                    Modifier.fillMaxSize().background(Color(0xFF142333)).testTag("alphabet_test_canvas"),
                ) {
                    AlphabetRail(
                        letters = letters,
                        selectedLetter = selected,
                        height = ((letters.size + 1) * 18).dp,
                        onLetterSelected = { selected = it },
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 32.dp),
                        onScrubFinished = { selected = null },
                    )
                }
            }
        }
    }

    private fun withHeldC(block: (SemanticsNodeInteraction) -> Unit) {
        val rail = compose.onNodeWithTag("alphabet_rail")
        rail.performTouchInput { down(Offset(centerX, height * fraction("C"))) }
        try {
            block(rail)
        } finally {
            rail.performTouchInput { cancel() }
        }
    }

    private fun fraction(letter: String) = (letters.indexOf(letter) + 1.5f) / (letters.size + 1f)

    private fun assertVisibleFavoriteIndicator() {
        compose.onNodeWithTag("alphabet_indicator_star", useUnmergedTree = true).assertExists()
        compose.waitForIdle()
        val bitmap = compose.onNodeWithTag("alphabet_indicator", useUnmergedTree = true)
            .captureToImage().asAndroidBitmap()
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val circle = findPixels(PixelBounds(0, 0, bitmap.width - 1, bitmap.height - 1), bitmap.width, pixels) { _, _, color ->
                isIndicator(color)
            }
            assertEquals("Favorites uses the same theme-colored 46dp circle", 46f * density(), circle.width.toFloat(), 2f)
            val radius = minOf(circle.width, circle.height) / 2f - 2f
            val star = findPixels(circle, bitmap.width, pixels) { x, y, color ->
                val dx = x - circle.centerX
                val dy = y - circle.centerY
                dx * dx + dy * dy < radius * radius &&
                    matchesColor(color, indicatorInkColor)
            }
            assertTrue("The star must actually render inside the circle", star.width > 12f * density() && star.height > 12f * density())
        } finally {
            bitmap.recycle()
        }
    }

    private fun assertIndicatorHidden() {
        compose.waitForIdle()
        val bitmap = compose.onNodeWithTag("alphabet_indicator", useUnmergedTree = true)
            .captureToImage().asAndroidBitmap()
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            assertEquals("Releasing or cancelling must fade the themed indicator completely", 0, pixels.count(::isIndicator))
        } finally {
            bitmap.recycle()
        }
    }

    private data class PixelBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left + 1
        val height get() = bottom - top + 1
        val centerX get() = (left + right) / 2f
        val centerY get() = (top + bottom) / 2f
    }

    private data class IndicatorPixels(val circle: PixelBounds, val glyph: PixelBounds, val railInk: PixelBounds)

    /** All coordinates come from one rendered root image, including layer transforms. */
    private fun inspect(letter: String): IndicatorPixels {
        compose.waitForIdle()
        val canvas = compose.onNodeWithTag("alphabet_test_canvas")
        val origin = canvas.fetchSemanticsNode().boundsInRoot.topLeft
        val bitmap = canvas.captureToImage().asAndroidBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        fun region(node: SemanticsNodeInteraction): PixelBounds {
            val bounds = node.fetchSemanticsNode().boundsInRoot
            return PixelBounds(
                floor(bounds.left - origin.x).toInt().coerceIn(0, bitmap.width - 1),
                floor(bounds.top - origin.y).toInt().coerceIn(0, bitmap.height - 1),
                (ceil(bounds.right - origin.x).toInt() - 1).coerceIn(0, bitmap.width - 1),
                (ceil(bounds.bottom - origin.y).toInt() - 1).coerceIn(0, bitmap.height - 1),
            )
        }
        // Child Canvas bounds include the ancestor layer's wave transform.
        val circle = findPixels(
            region(compose.onNodeWithTag("alphabet_indicator", useUnmergedTree = true)), bitmap.width, pixels,
        ) { _, _, color -> isIndicator(color) }
        val radius = minOf(circle.width, circle.height) / 2f - 2f
        val glyph = findPixels(circle, bitmap.width, pixels) { x, y, color ->
            val dx = x - circle.centerX
            val dy = y - circle.centerY
            dx * dx + dy * dy < radius * radius &&
                matchesColor(color, indicatorInkColor)
        }
        val railInk = findPixels(
            region(compose.onNodeWithText(letter, useUnmergedTree = true)), bitmap.width, pixels,
        ) { x, y, color ->
            // The child Text's bounds include the ancestor wave transform.
            (x < circle.left || x > circle.right || y < circle.top || y > circle.bottom) && isBright(color)
        }
        bitmap.recycle()
        return IndicatorPixels(circle, glyph, railInk)
    }

    private fun findPixels(
        region: PixelBounds,
        rowWidth: Int,
        pixels: IntArray,
        matches: (x: Int, y: Int, color: Int) -> Boolean,
    ): PixelBounds {
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = -1
        var bottom = -1
        for (y in region.top..region.bottom) for (x in region.left..region.right) {
            if (matches(x, y, pixels[y * rowWidth + x])) {
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x)
                bottom = maxOf(bottom, y)
            }
        }
        assertTrue("Expected rendered pixels inside $region", right >= left && bottom >= top)
        return PixelBounds(left, top, right, bottom)
    }

    private fun isBright(color: Int) =
        ((color shr 16) and 255) >= 235 && ((color shr 8) and 255) >= 235 && (color and 255) >= 235

    private fun isIndicator(color: Int) = matchesColor(color, indicatorColor)

    private fun matchesColor(actual: Int, expected: Int) = listOf(0, 8, 16).all { shift ->
        kotlin.math.abs(((actual shr shift) and 255) - ((expected shr shift) and 255)) <= 12
    }

    @Test
    fun letterAndFavoriteIndicatorsFollowLiveAccentAndDarkModeChanges() {
        showRail()
        withHeldC { rail ->
            listOf(false, true).forEach { dark ->
                listOf(Color(0xFFB3261E), Color(0xFF009688)).forEach { seed ->
                    compose.runOnIdle { darkTheme.value = dark; themeSeed.value = seed }
                    compose.waitForIdle()
                    val letter = inspect("C")
                    assertCenteredInk(letter, "C with $seed / dark=$dark")
                    assertRailGap(letter, "Themed C")
                    rail.performTouchInput { moveTo(Offset(centerX, height * 0.5f / (letters.size + 1)), delayMillis = 32) }
                    assertVisibleFavoriteIndicator()
                    rail.performTouchInput { moveTo(Offset(centerX, height * fraction("C")), delayMillis = 32) }
                }
            }
            saveScreenshot("alphabet-indicator-themed.png")
        }
    }

    private fun assertCenteredInk(pixels: IndicatorPixels, label: String) {
        assertEquals("$label glyph is horizontally centered by ink", pixels.circle.centerX, pixels.glyph.centerX, 2f)
        assertEquals("$label glyph is vertically centered by ink", pixels.circle.centerY, pixels.glyph.centerY, 2f)
    }

    private fun assertRailGap(pixels: IndicatorPixels, label: String): Float {
        val gap = (pixels.railInk.left - pixels.circle.right - 1).toFloat()
        assertTrue("$label needs at least 12dp clear space; actual=${gap / density()}dp", gap >= 12f * density() - 2f)
        return gap
    }

    private fun density() = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density

    private fun saveScreenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = instrumentation.targetContext.getExternalFilesDir("ui-verification")!!
        directory.mkdirs()
        compose.waitForIdle()
        val output = File(directory, name)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        Log.i("NivaUiVerification", "Screenshot: ${output.absolutePath}")
    }
}
