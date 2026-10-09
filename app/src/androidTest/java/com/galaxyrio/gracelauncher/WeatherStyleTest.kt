package com.galaxyrio.gracelauncher

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.LinkAnnotation
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.LauncherSettings
import com.galaxyrio.gracelauncher.data.weather.WeatherCondition
import com.galaxyrio.gracelauncher.data.weather.WeatherCurrent
import com.galaxyrio.gracelauncher.data.weather.WeatherDay
import com.galaxyrio.gracelauncher.data.weather.WeatherHour
import com.galaxyrio.gracelauncher.data.weather.WeatherLocation
import com.galaxyrio.gracelauncher.data.weather.WeatherSnapshot
import com.galaxyrio.gracelauncher.data.weather.WeatherState
import com.galaxyrio.gracelauncher.data.weather.WeatherStatus
import com.galaxyrio.gracelauncher.data.weather.WeatherTemperature
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.ScheduleStatus
import com.galaxyrio.gracelauncher.ui.overlays.LauncherOverlay
import com.galaxyrio.gracelauncher.ui.overlays.LauncherOverlays
import com.galaxyrio.gracelauncher.ui.settings.WeatherSettings
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeatherStyleTest {
    @get:Rule val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val now = Instant.now()
    private val today = LocalDate.now()
    private val sources = linkedMapOf(
        "Open-Meteo" to "https://open-meteo.com/",
        "CC BY 4.0" to "https://creativecommons.org/licenses/by/4.0/",
    )
    private val snapshot = WeatherSnapshot(
        location = WeatherLocation("shanghai", "Shanghai", ZoneId.systemDefault()),
        current = WeatherCurrent(temperature(29), WeatherCondition.Rain, true, "Rain"),
        hourly = (1..24).map { hour ->
            WeatherHour(
                now.plusSeconds(hour * 3600L), temperature(29 - hour % 5),
                if (hour % 3 == 0) WeatherCondition.PartlyCloudy else WeatherCondition.Rain,
                hour < 7, null,
            )
        },
        daily = (0L..6L).map { day ->
            WeatherDay(
                today.plusDays(day), temperature(29 - day.toInt() % 4), temperature(24 - day.toInt() % 3),
                listOf(WeatherCondition.Rain, WeatherCondition.PartlyCloudy, WeatherCondition.Clear)[(day % 3).toInt()], null,
            )
        },
        updatedAt = now.minusSeconds(600),
        attribution = "Breezy Weather · Open-Meteo (CC BY 4.0)",
        sourceLinks = sources,
    )
    private val state = LauncherUiState(
        isLoadingApps = false,
        settings = LauncherSettings(weatherEnabled = true, weatherForecastDays = 7),
        weather = WeatherState(WeatherStatus.Ready, snapshot, listOf(snapshot.location)),
        scheduleStatus = ScheduleStatus.Ready,
    )
    private var expectedPage = Color.Unspecified
    private var expectedRow = Color.Unspecified

    @Test fun lightAgendaUsesSettingsColorsAndOmitsCredits() = assertAgendaStyle(dark = false)

    @Test fun darkAgendaUsesSettingsColorsAndOmitsCredits() = assertAgendaStyle(dark = true)

    @Test fun settingsKeepsProviderAndLicenseCreditsAwayFromAgenda() {
        showContent(dark = false, settings = true)
        assertPixelColor(
            "Settings page must use surfaceContainer", compose.onNodeWithTag("settings_weather_page"), expectedPage,
            horizontalFraction = 0.005f, verticalFraction = 0.5f,
        )
        assertPixelColor(
            "The enabled settings row must still use surfaceBright", compose.onNodeWithTag("weather_enabled"), expectedRow,
            horizontalFraction = 0.5f, verticalFraction = 0.025f,
        )
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("weather_credits"))
        val credits = compose.onNodeWithTag("weather_credits", useUnmergedTree = true).assertIsDisplayed()
            .fetchSemanticsNode().config[SemanticsProperties.Text].single()
        assertEquals(snapshot.attribution, credits.text)
        assertEquals(sources, credits.getLinkAnnotations(0, credits.length).associate {
            credits.text.substring(it.start, it.end) to (it.item as LinkAnnotation.Url).url
        })
        screenshot("weather-settings-credits-light.png")
    }

    private fun assertAgendaStyle(dark: Boolean) {
        showContent(dark = dark, settings = false)
        compose.onNodeWithTag("agenda_sheet").assertIsDisplayed()
        compose.onNodeWithTag("weather_hourly").assertIsDisplayed()
        compose.onNodeWithTag("weather_location").assertIsDisplayed()
        compose.onNodeWithTag("weather_credits", useUnmergedTree = true).assertDoesNotExist()
        assertTrue("The fixture must distinguish the page and item colors", expectedPage != expectedRow)
        assertPixelColor(
            "Agenda background must match the settings page surfaceContainer",
            compose.onNodeWithTag("launcher_sheet"), expectedPage,
            horizontalFraction = 0.005f, verticalFraction = 0.5f,
        )
        assertPixelColor(
            "The hourly card must match settings items surfaceBright",
            compose.onNodeWithTag("weather_hourly"), expectedRow,
            horizontalFraction = 0.5f, verticalFraction = 0.025f,
        )
        screenshot("weather-agenda-style-${if (dark) "dark" else "light"}.png")
    }

    private fun showContent(dark: Boolean, settings: Boolean) {
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply { setLocale(Locale.US) }
            val localizedContext = context.createConfigurationContext(configuration)
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalContext provides localizedContext) {
                GraceLauncherTheme(darkTheme = dark, dynamicColor = false, seedColor = Color(0xFF658C50)) {
                    expectedPage = MaterialTheme.colorScheme.surfaceContainer
                    expectedRow = MaterialTheme.colorScheme.surfaceBright
                    Box(Modifier.fillMaxSize().background(Color(0xFF20353D))) {
                        if (settings) {
                            WeatherSettings(state, LauncherActions(), onBack = {})
                        } else {
                            LauncherOverlays(
                                overlay = LauncherOverlay.Agenda,
                                uiState = state,
                                actions = LauncherActions(),
                                onChange = {},
                                onLaunchApp = {},
                                onToggleFavorite = {},
                                onRequestCalendar = {},
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertPixelColor(
        message: String,
        node: SemanticsNodeInteraction,
        expected: Color,
        horizontalFraction: Float,
        verticalFraction: Float,
    ) {
        val bitmap = node.captureToImage().asAndroidBitmap()
        val x = (bitmap.width * horizontalFraction).toInt().coerceIn(0, bitmap.width - 1)
        val y = (bitmap.height * verticalFraction).toInt().coerceIn(0, bitmap.height - 1)
        val actual = bitmap.getPixel(x, y)
        val target = expected.toArgb()
        // Allow only one channel value for device color-space rounding, not tonal-elevation changes.
        val matches = listOf(0, 8, 16, 24).all { shift ->
            abs(((actual ushr shift) and 0xFF) - ((target ushr shift) and 0xFF)) <= 1
        }
        assertTrue("$message: expected ${target.toUInt().toString(16)}, found ${actual.toUInt().toString(16)} at ($x, $y)", matches)
    }

    private fun temperature(value: Int) = WeatherTemperature(value.toDouble(), "c")

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val directory = context.getExternalFilesDir("ui-verification")!!.apply { mkdirs() }
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
