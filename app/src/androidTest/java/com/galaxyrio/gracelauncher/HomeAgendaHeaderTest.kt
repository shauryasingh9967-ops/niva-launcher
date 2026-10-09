package com.galaxyrio.gracelauncher

import android.content.res.Configuration
import android.content.ComponentName
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.LauncherSettings
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.ScheduleEvent
import com.galaxyrio.gracelauncher.data.weather.WeatherCondition
import com.galaxyrio.gracelauncher.data.weather.WeatherCurrent
import com.galaxyrio.gracelauncher.data.weather.WeatherLocation
import com.galaxyrio.gracelauncher.data.weather.WeatherSnapshot
import com.galaxyrio.gracelauncher.data.weather.WeatherState
import com.galaxyrio.gracelauncher.data.weather.WeatherStatus
import com.galaxyrio.gracelauncher.data.weather.WeatherTemperature
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.ScheduleStatus
import com.galaxyrio.gracelauncher.ui.home.HomeScreen
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeAgendaHeaderTest {
    @get:Rule val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val wallpaperColor = Color(0xFF17202A)
    private var agendaClicks = 0
    private var clockClicks = 0
    private var state by mutableStateOf(
        LauncherUiState(
            isLoadingApps = false,
            settings = LauncherSettings(calendarAgenda = true, weatherEnabled = true, showBatteryPercentage = false),
            scheduleStatus = ScheduleStatus.Ready,
            events = listOf(allDayEvent("国庆节")),
            weather = WeatherState(
                WeatherStatus.Ready,
                WeatherSnapshot(
                    location = WeatherLocation("shanghai", "Shanghai", ZoneId.systemDefault()),
                    current = WeatherCurrent(WeatherTemperature(23.0, "c"), WeatherCondition.Cloudy, true, "Cloudy"),
                    hourly = emptyList(),
                    daily = emptyList(),
                    updatedAt = Instant.now(),
                    attribution = "Open-Meteo",
                ),
            ),
        ),
    )

    @Test fun chineseAndMixedScriptTitlesShareTheEnglishMetadataBaseline() {
        showHome()
        node("schedule_remaining").assertTextEquals("· all day")
        assertBaselinesEqual("schedule_title", "schedule_remaining")
        screenshot("home-agenda-mixed-baseline.png")

        compose.runOnIdle { state = state.copy(events = listOf(allDayEvent("国庆节 National Day"))) }
        assertBaselinesEqual("schedule_title", "schedule_remaining")
        assertTrue("The title and metadata must not overlap", bounds("schedule_title").right < bounds("schedule_remaining").left)
    }

    @Test fun bothRowsUseTheSameSingleButtonAndClockKeepsItsOwnAction() {
        showHome()
        compose.onNodeWithTag("home_date").assertHasClickAction()
        compose.onAllNodes(
            hasClickAction() and hasAnyAncestor(hasTestTag("home_date")),
            useUnmergedTree = true,
        ).assertCountEquals(0)
        node("home_date_text").assertHasNoClickAction()
        node("schedule_line").assertHasNoClickAction()

        node("home_date_text").performTouchInput { click() }
        compose.runOnIdle { assertEquals("The first row opens the agenda once", 1, agendaClicks) }
        node("schedule_line").performTouchInput { click() }
        compose.runOnIdle { assertEquals("The second row opens the same agenda once", 2, agendaClicks) }
        compose.onNodeWithTag("home_clock").performClick()
        compose.runOnIdle {
            assertEquals(1, clockClicks)
            assertEquals("The clock does not open the agenda", 2, agendaClicks)
        }
    }

    @Test fun disablingCalendarRemovesTheSecondRowAndItsSpaceWithoutHidingWeather() {
        showHome()
        val twoLineHeight = bounds("home_date").height
        val eventHeight = bounds("schedule_line").height
        compose.runOnIdle { state = state.copy(settings = state.settings.copy(calendarAgenda = false)) }
        node("schedule_line").assertDoesNotExist()
        node("schedule_title").assertDoesNotExist()
        node("home_weather").assertIsDisplayed()
        val singleLineHeight = bounds("home_date").height
        assertTrue("A disabled agenda must not retain an empty second row", twoLineHeight - singleLineHeight >= eventHeight - 2f)
        node("home_date_text").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, agendaClicks) }
        screenshot("home-agenda-disabled-single-line.png")

        // No event also produces the same compact layout, even when the calendar is enabled.
        compose.runOnIdle { state = state.copy(settings = state.settings.copy(calendarAgenda = true), events = emptyList()) }
        node("schedule_line").assertDoesNotExist()
        assertEquals(singleLineHeight, bounds("home_date").height, 1f)
    }

    @Test fun chineseDateAndWeatherTemperatureShareABaseline() {
        showHome(locale = Locale.SIMPLIFIED_CHINESE)
        assertBaselinesEqual("home_date_text", "home_weather_temperature")
        assertTrue(bounds("home_date_text").right < bounds("home_weather").left)
        screenshot("home-agenda-chinese-date.png")
    }

    @Test fun dateAndAgendaMatchAppLabelSizeAndClockUsesMediumWeight() {
        val app = LauncherApp(ComponentName("test.clock", "Clock"), "Clock", null)
        state = state.copy(apps = listOf(app), favoriteKeys = setOf(app.key))
        showHome(fontScale = 0.85f)
        val label = layout("app:${app.key}:label").layoutInput.style
        listOf("home_date_text", "schedule_title", "schedule_remaining").forEach { tag ->
            assertEquals(label.fontSize, layout(tag).layoutInput.style.fontSize)
            assertEquals(label.lineHeight, layout(tag).layoutInput.style.lineHeight)
        }
        assertEquals(500, layout("home_clock").layoutInput.style.fontWeight!!.weight)
        val density = context.resources.displayMetrics.density
        assertEquals("Clock and date keep a compact gap", 2 * density, bounds("home_date").top - bounds("home_clock").bottom, 1f)
        assertEquals("Date and favorites keep a compact gap", 4 * density, bounds("app:${app.key}").top - bounds("home_date").bottom, 1f)
        assertBaselinesEqual("home_date_text", "home_weather_temperature")
        val temperature = layout("home_weather_temperature")
        val icon = requireNotNull(temperature.placeholderRects.single())
        assertTrue("Inline icon scales with the font", icon.height < 20 * context.resources.displayMetrics.density)
        screenshot("home-alignment-small-font.png")
    }

    @Test fun narrowLargeFontEllipsizesTheTitleAndKeepsMetadataAligned() {
        state = state.copy(events = listOf(allDayEvent("国庆节 National Day 和家人一起外出庆祝")))
        showHome(width = 320.dp, fontScale = 1.5f)
        val titleLayout = layout("schedule_title")
        val remainingLayout = layout("schedule_remaining")
        screenshot("home-agenda-mixed-large-font.png")
        assertTrue("A long title must truncate, rather than push the metadata away", titleLayout.isLineEllipsized(0))
        // GetTextLayoutResult can retain the paragraph's original maximum width
        // after this unweighted Text shrinks to its intrinsic width. Check visible
        // glyphs, not hasVisualOverflow (which compares those two container widths).
        assertEquals(remainingLayout.layoutInput.text.length, remainingLayout.getLineEnd(0, visibleEnd = true))
        assertTrue("The metadata must not be ellipsized", !remainingLayout.isLineEllipsized(0))
        assertTrue("The metadata must fit horizontally", remainingLayout.getLineRight(0) <= remainingLayout.size.width + 1f)
        assertTrue("The metadata must fit vertically", remainingLayout.getLineBottom(0) <= remainingLayout.size.height + 1f)
        assertBaselinesEqual("schedule_title", "schedule_remaining")
        assertTrue(bounds("schedule_title").right < bounds("schedule_remaining").left)
        assertTrue(bounds("schedule_remaining").right <= bounds("home_date").right)
    }

    @Test fun oneTransparentRoundedRippleSpansBothRows() {
        showHome()
        val button = compose.onNodeWithTag("home_date")
        val buttonBounds = bounds("home_date")
        val firstRowY = bounds("home_date_text").center.y - buttonBounds.top
        val secondRowY = bounds("schedule_line").center.y - buttonBounds.top
        val density = context.resources.displayMetrics.density
        val idle = button.captureToImage().asAndroidBitmap()
        val sampleX = (idle.width - 16f * density).roundToInt()
        val firstY = firstRowY.roundToInt()
        val secondY = secondRowY.roundToInt()
        assertEquals("The idle surface must not obscure the wallpaper", wallpaperColor.toArgb(), idle.getPixel(sampleX, firstY))
        assertEquals(wallpaperColor.toArgb(), idle.getPixel(sampleX, secondY))
        val previousAutoAdvance = compose.mainClock.autoAdvance
        compose.mainClock.autoAdvance = false
        try {
            button.performTouchInput { down(Offset(sampleX.toFloat(), firstRowY)) }
            compose.mainClock.advanceTimeBy(400)
            // The platform Material ripple animates on RenderThread, outside the Compose clock.
            SystemClock.sleep(450)
            val pressed = button.captureToImage().asAndroidBitmap()
            assertTrue("Pressing the date must ripple on its own row", pixelDifference(idle, pressed, sampleX, firstY) > 3)
            assertTrue("The same ripple must also reach the event row", pixelDifference(idle, pressed, sampleX, secondY) > 3)
            val corner = density.roundToInt().coerceAtLeast(1)
            assertTrue("The shared ripple must be clipped to a rounded rectangle", pixelDifference(idle, pressed, idle.width - corner - 1, corner) <= 2)
            screenshot("home-agenda-shared-ripple.png")
        } finally {
            button.performTouchInput { cancel() }
            compose.mainClock.autoAdvance = previousAutoAdvance
            compose.waitForIdle()
        }
        compose.runOnIdle { assertEquals("Cancelling the press must not open the agenda", 0, agendaClicks) }
    }

    private fun showHome(locale: Locale = Locale.US, width: Dp = 380.dp, fontScale: Float = 1f) {
        compose.setContent {
            val originalDensity = LocalDensity.current
            val configuration = Configuration(LocalConfiguration.current).apply {
                setLocale(locale)
                this.fontScale = fontScale
            }
            CompositionLocalProvider(
                LocalConfiguration provides configuration,
                LocalContext provides context.createConfigurationContext(configuration),
                LocalDensity provides Density(originalDensity.density, fontScale),
            ) {
                GraceLauncherTheme(dynamicColor = false) {
                    Box(Modifier.fillMaxSize().background(wallpaperColor)) {
                        HomeScreen(
                            uiState = state,
                            topSpace = 36.dp,
                            onLaunchApp = {},
                            onAppDetails = {},
                            onAppShortcuts = { _, _ -> },
                            onDateClick = { agendaClicks++ },
                            onClockClick = { clockClicks++ },
                            modifier = Modifier.width(width),
                        )
                    }
                }
            }
        }
    }

    private fun node(tag: String): SemanticsNodeInteraction = compose.onNodeWithTag(tag, useUnmergedTree = true)

    private fun bounds(tag: String) = node(tag).fetchSemanticsNode().boundsInRoot

    private fun layout(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        node(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private fun assertBaselinesEqual(first: String, second: String) {
        assertEquals(
            "Fallback fonts must share an absolute first baseline: $first / $second",
            bounds(first).top + layout(first).firstBaseline,
            bounds(second).top + layout(second).firstBaseline,
            1f,
        )
    }

    private fun allDayEvent(title: String): ScheduleEvent {
        val today = LocalDate.now()
        return ScheduleEvent(
            id = 1,
            title = title,
            startsAt = today.minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
            endsAt = today.plusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant(),
            isAllDay = true,
            location = null,
            calendarColor = null,
        )
    }

    private fun pixelDifference(before: Bitmap, after: Bitmap, x: Int, y: Int): Int =
        listOf(0, 8, 16).maxOf { shift ->
            abs(((before.getPixel(x, y) shr shift) and 255) - ((after.getPixel(x, y) shr shift) and 255))
        }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val directory = context.getExternalFilesDir("ui-verification")!!.apply { mkdirs() }
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
