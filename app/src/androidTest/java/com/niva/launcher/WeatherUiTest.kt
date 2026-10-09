package com.niva.launcher

import android.content.ComponentName
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.niva.launcher.data.LauncherApp
import com.niva.launcher.data.LauncherSettings
import com.niva.launcher.data.ScheduleEvent
import com.niva.launcher.data.WallpaperTextMode
import com.niva.launcher.data.weather.WeatherCondition
import com.niva.launcher.data.weather.WeatherCurrent
import com.niva.launcher.data.weather.WeatherDay
import com.niva.launcher.data.weather.WeatherHour
import com.niva.launcher.data.weather.WeatherLocation
import com.niva.launcher.data.weather.WeatherSnapshot
import com.niva.launcher.data.weather.WeatherState
import com.niva.launcher.data.weather.WeatherStatus
import com.niva.launcher.data.weather.WeatherTemperature
import com.niva.launcher.ui.LauncherActions
import com.niva.launcher.ui.LauncherScreen
import com.niva.launcher.ui.LauncherUiState
import com.niva.launcher.ui.ScheduleStatus
import com.niva.launcher.ui.overlays.AgendaSheet
import com.niva.launcher.ui.theme.NivaLauncherTheme
import com.niva.launcher.ui.weather.WeatherSourceNote
import com.niva.launcher.ui.weather.WeatherAttribution
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeatherUiTest {
    @get:Rule val compose = createComposeRule()
    private val now = Instant.now()
    private val today = LocalDate.now()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val snapshot = WeatherSnapshot(
        location = WeatherLocation("shanghai", "Shanghai", ZoneId.systemDefault()),
        current = WeatherCurrent(temperature(32), WeatherCondition.Clear, true, "Sunny"),
        hourly = (1..24).map { hour ->
            WeatherHour(now.plusSeconds(hour * 3600L), temperature(32 - hour % 8),
                if (hour < 6) WeatherCondition.Clear else WeatherCondition.PartlyCloudy,
                hour < 8, null)
        },
        daily = (0L..10L).map { day ->
            WeatherDay(today.plusDays(day), temperature(32 - day.toInt() % 4), temperature(24 - day.toInt() % 3),
                listOf(WeatherCondition.Clear, WeatherCondition.PartlyCloudy, WeatherCondition.Rain)[(day % 3).toInt()], null)
        },
        updatedAt = now.minusSeconds(600),
        attribution = "Open-Meteo",
    )
    private val apps = listOf("Calendar", "Camera", "Chrome", "Messages").map { label ->
        LauncherApp(ComponentName("test.${label.lowercase()}", "$label.Activity"), label, null)
    }
    private var state by mutableStateOf(LauncherUiState(
        apps = apps, favoriteKeys = apps.mapTo(linkedSetOf()) { it.key }, isLoadingApps = false,
        textMode = WallpaperTextMode.Light,
        settings = LauncherSettings(weatherEnabled = true, weatherForecastDays = 7),
        weather = WeatherState(WeatherStatus.Ready, snapshot),
        scheduleStatus = ScheduleStatus.Ready,
        events = listOf(event(1, 0, "Coffee with Alex"), event(2, 2, "Design review"), event(3, 10, "Later appointment")),
    ))

    @Test fun homeWeatherSharesDateActionAndAgendaMergesForecastWithEvents() {
        showHome()
        val date = compose.onNodeWithTag("home_date_text", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val weather = compose.onNodeWithTag("home_weather", useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(weather.left > date.right)
        assertEquals(date.center.y, weather.center.y, 2f)
        screenshot("weather-home-en.png")
        compose.onNodeWithTag("home_date").performClick()
        compose.onNodeWithTag("agenda_sheet").assertIsDisplayed()
        assertForecastAligned(today)
        compose.onNodeWithTag("weather_hourly").assertIsDisplayed()
        compose.onNodeWithTag("weather_credits", useUnmergedTree = true).assertDoesNotExist()
        screenshot("weather-agenda-en.png")

        compose.onNodeWithTag("weather_hourly").performScrollToIndex(12)
        compose.onNodeWithTag("weather_hour:${snapshot.hourly[11].at}", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("agenda_list").performScrollToNode(hasTestTag("agenda_date:${today.plusDays(6)}"))
        compose.onNodeWithTag("agenda_weather:${today.plusDays(6)}", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("agenda_date:${today.plusDays(7)}", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("agenda_list").performScrollToNode(hasTestTag("agenda_event:3"))
        compose.onNodeWithTag("agenda_event:3").assertIsDisplayed()
    }

    @Test fun disabledOrUninitializedSettingsNeverShowWeatherOnHome() {
        showHome()
        compose.onNodeWithTag("home_weather", useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle { state = state.copy(settings = state.settings.copy(weatherEnabled = false)) }
        compose.onNodeWithTag("home_weather", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { state = state.copy(settings = state.settings.copy(weatherEnabled = true), isLoadingSettings = true) }
        compose.onNodeWithTag("home_weather", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { state = state.copy(isLoadingSettings = false, weather = WeatherState(WeatherStatus.NotInstalled)) }
        compose.onNodeWithTag("home_weather", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("home_date").assertIsDisplayed()
    }

    @Test fun calendarPermissionAndCalendarErrorsKeepTheWeatherAvailable() {
        state = state.copy(scheduleStatus = ScheduleStatus.PermissionRequired, events = emptyList())
        showHome()
        compose.onNodeWithTag("home_date").performClick()
        compose.onNodeWithTag("weather_hourly").assertIsDisplayed()
        assertForecastAligned(today)
        compose.onNodeWithTag("agenda_list").performScrollToNode(hasTestTag("new_event"))
        compose.onNodeWithText(context.getString(R.string.connect_calendar)).assertIsDisplayed()
        compose.runOnIdle { state = state.copy(scheduleStatus = ScheduleStatus.Error) }
        compose.onNodeWithTag("agenda_list").performScrollToIndex(0)
        compose.onNodeWithTag("weather_hourly").assertIsDisplayed()
        compose.onNodeWithTag("agenda_list").performScrollToNode(hasTestTag("agenda_date:${today.plusDays(1)}"))
        compose.onNodeWithTag("agenda_weather:${today.plusDays(1)}", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun narrowLargeFontKeepsForecastOnDateRowWithoutOverlap() {
        showNarrowAgenda(Locale.US)
        assertForecastAligned(today)
        assertDateFits(today)
        compose.onNodeWithTag("weather_hourly").assertIsDisplayed()
        screenshot("weather-agenda-narrow-large-font.png")
    }

    @Test fun chineseNarrowAgendaKeepsLocalizedDatesAndWeatherReadable() {
        state = state.copy(
            weather = state.weather.copy(snapshot = snapshot.copy(location = snapshot.location.copy(name = "上海"))),
            events = listOf(event(1, 0, "和小林喝咖啡"), event(2, 2, "设计评审")),
        )
        showNarrowAgenda(Locale.SIMPLIFIED_CHINESE)
        assertForecastAligned(today)
        assertDateFits(today)
        compose.onNodeWithTag("weather_hourly").assertIsDisplayed()
        compose.onNodeWithText("你的日程").assertIsDisplayed()
        screenshot("weather-agenda-zh-narrow-large-font.png")
    }

    private fun showNarrowAgenda(locale: Locale) {
        compose.setContent {
            val density = LocalDensity.current
            val configuration = Configuration(LocalConfiguration.current).apply { setLocale(locale); fontScale = 1.5f }
            val localizedContext = context.createConfigurationContext(configuration)
            CompositionLocalProvider(LocalConfiguration provides configuration,
                LocalContext provides localizedContext,
                LocalDensity provides Density(density.density, 1.5f)) {
                NivaLauncherTheme(dynamicColor = false) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
                        Surface(Modifier.width(320.dp), color = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface) {
                            AgendaSheet(state, LauncherActions(), onRequestCalendar = {})
                        }
                    }
                }
            }
        }
    }

    @Test fun unavailableWeatherOffersAnActionThatMatchesTheConnectionProblem() {
        val invoked = mutableListOf<String>()
        state = state.copy(weather = WeatherState(WeatherStatus.NotInstalled))
        showHome(LauncherActions(
            installBreezyWeather = { invoked += "install" },
            requestWeatherAccess = { invoked += "permission" },
            openBreezyWeather = { invoked += "open" },
            refreshWeather = { invoked += "retry" },
        ))
        compose.onNodeWithTag("home_date").performClick()
        compose.runOnIdle {
            assertEquals("Opening the agenda refreshes the shared weather cache", listOf("retry"), invoked)
            invoked.clear()
        }
        compose.onNodeWithTag("weather_connection_action").performClick()
        compose.runOnIdle { state = state.copy(weather = WeatherState(WeatherStatus.PermissionRequired)) }
        compose.onNodeWithTag("weather_connection_action").performClick()
        compose.runOnIdle { state = state.copy(weather = WeatherState(WeatherStatus.NoLocations)) }
        compose.onNodeWithTag("weather_connection_action").performClick()
        compose.runOnIdle { state = state.copy(weather = WeatherState(WeatherStatus.Error)) }
        compose.onNodeWithTag("weather_connection_action").performClick()
        compose.runOnIdle { assertEquals(listOf("install", "permission", "open", "retry"), invoked) }
    }

    @Test fun sourceAndLicenseCreditsWrapAndOpenTheirOwnLinks() {
        val attribution = "Breezy Weather · Open-Meteo · Licensed under CC BY 4.0"
        val urls = linkedMapOf(
            "Open-Meteo" to "https://open-meteo.com/",
            "CC BY 4.0" to "https://creativecommons.org/licenses/by/4.0/",
            "Terms" to "https://open-meteo.com/en/terms",
        )
        val openedLinks = mutableListOf<String>()
        val recordingUriHandler = object : UriHandler {
            override fun openUri(uri: String) { openedLinks += uri }
        }
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides recordingUriHandler) {
                NivaLauncherTheme(dynamicColor = false) {
                    Surface(Modifier.width(220.dp)) {
                        WeatherAttribution(snapshot.copy(attribution = attribution, sourceLinks = urls))
                    }
                }
            }
        }
        val creditsNode = compose.onNodeWithTag("weather_credits", useUnmergedTree = true)
        val annotated = creditsNode.fetchSemanticsNode().config[SemanticsProperties.Text].single()
        assertEquals("The complete credit remains visible, with unmatched link labels appended", "$attribution · Terms", annotated.text)
        val annotations = annotated.getLinkAnnotations(0, annotated.length)
        assertEquals(urls, annotations.associate { annotated.text.substring(it.start, it.end) to (it.item as LinkAnnotation.Url).url })
        val layouts = mutableListOf<TextLayoutResult>()
        creditsNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("All credit and license text must wrap without truncation", layouts.single().lineCount > 1 && !layouts.single().hasVisualOverflow)
        val sourceLink = annotations.single { (it.item as LinkAnnotation.Url).url == urls.getValue("Open-Meteo") }
        val linkPosition = layouts.single().getBoundingBox(sourceLink.start + 2).center
        creditsNode.performTouchInput { click(linkPosition) }
        compose.runOnIdle { assertEquals(listOf(urls.getValue("Open-Meteo")), openedLinks) }
    }

    @Test fun agendaMetadataKeepsItsLocationActionWithoutSourceCredits() {
        var breezyOpens = 0
        compose.setContent {
            NivaLauncherTheme(dynamicColor = false) {
                Surface { WeatherSourceNote(snapshot, now) { breezyOpens++ } }
            }
        }
        compose.onNodeWithTag("weather_source").assertHasNoClickAction()
        compose.onNodeWithTag("weather_credits", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("weather_location").performClick()
        compose.runOnIdle { assertEquals(1, breezyOpens) }
    }

    private fun showHome(actions: LauncherActions = LauncherActions()) {
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply { setLocale(Locale.US) }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                NivaLauncherTheme(dynamicColor = false) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF20353D))) {
                        LauncherScreen(state, onDateClick = {}, onClockClick = {}, onLaunchApp = {},
                            onToggleFavorite = {}, actions = actions)
                    }
                }
            }
        }
    }

    private fun assertForecastAligned(date: LocalDate) {
        val dateBounds = compose.onNodeWithTag("agenda_date:$date", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val forecastBounds = compose.onNodeWithTag("agenda_weather:$date", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val rowBounds = compose.onNodeWithTag("agenda_day:$date", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("The weather belongs to the date's right side", dateBounds.right < forecastBounds.left)
        assertEquals("Date and forecast should share a center line", dateBounds.center.y, forecastBounds.center.y, 2f)
        assertEquals("Weather should align with the row's trailing edge", rowBounds.right, forecastBounds.right, 2f)
    }

    private fun assertDateFits(date: LocalDate) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("agenda_date:$date", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("The compact month and day must fit without an ellipsis", layouts.isNotEmpty() && layouts.none { it.isLineEllipsized(0) })
    }

    private fun event(id: Long, day: Long, title: String): ScheduleEvent {
        val start = today.plusDays(day).atTime(16, 0).atZone(ZoneId.systemDefault()).toInstant()
        return ScheduleEvent(id, title, start, start.plusSeconds(3600), false, null, 0xFFB7A2EE.toInt())
    }

    private fun temperature(value: Int) = WeatherTemperature(value.toDouble(), "c")

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val directory = context.getExternalFilesDir("ui-verification")!!.apply { mkdirs() }
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
