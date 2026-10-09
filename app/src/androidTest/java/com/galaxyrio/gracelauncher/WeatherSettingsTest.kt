package com.galaxyrio.gracelauncher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.LauncherSettings
import com.galaxyrio.gracelauncher.data.weather.WeatherLocation
import com.galaxyrio.gracelauncher.data.weather.WeatherState
import com.galaxyrio.gracelauncher.data.weather.WeatherStatus
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.settings.LauncherSettingsScreen
import com.galaxyrio.gracelauncher.ui.settings.WeatherSettings
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeatherSettingsTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var state by mutableStateOf(LauncherUiState())
    private var accessRequests = 0
    private var installRequests = 0

    private fun showSettings(initial: LauncherUiState = LauncherUiState(), fullNavigation: Boolean = false) {
        state = initial
        compose.setContent {
            val actions = LauncherActions(
                updateSettings = { transform -> state = state.copy(settings = transform(state.settings)) },
                requestWeatherAccess = { accessRequests++ },
                installBreezyWeather = { installRequests++ },
            )
            GraceLauncherTheme(dynamicColor = false) {
                if (fullNavigation) LauncherSettingsScreen(state, actions, onBack = {})
                else WeatherSettings(state, actions, onBack = {})
            }
        }
    }

    @Test fun weatherIsOptionalAndAccessibleFromProductivity() {
        showSettings(fullNavigation = true)
        clickSetting("settings_category_productivity")
        clickSetting("settings_weather")
        compose.onNodeWithTag("settings_weather_page").assertIsDisplayed()
        compose.onNodeWithTag("weather_enabled").assertIsOff()
        compose.runOnIdle {
            assertFalse(state.settings.weatherEnabled)
            assertEquals(7, state.settings.weatherForecastDays)
            assertEquals(0, accessRequests)
        }
        compose.onNodeWithTag("weather_enabled").performClick().assertIsOn()
        compose.runOnIdle { assertEquals(1, accessRequests) }
        compose.onNodeWithTag("weather_enabled").performClick().assertIsOff()
        compose.runOnIdle { assertEquals("Disabling must not request access", 1, accessRequests) }
    }

    @Test fun missingBreezyExplainsRequirementAndOffersInstallation() {
        showSettings(LauncherUiState(
            settings = LauncherSettings(weatherEnabled = true),
            weather = WeatherState(status = WeatherStatus.NotInstalled),
        ))
        compose.onNodeWithTag("weather_connection_status")
            .assertTextEquals(context.getString(R.string.weather_settings_not_installed))
        clickSetting("weather_install")
        compose.runOnIdle { assertEquals(1, installRequests) }
        compose.onNodeWithTag("weather_allow_access").assertDoesNotExist()
        compose.onNodeWithTag("weather_location").assertDoesNotExist()
    }

    @Test fun deniedPermissionOffersOnlyExplicitAccessRequest() {
        showSettings(LauncherUiState(
            settings = LauncherSettings(weatherEnabled = true),
            weather = WeatherState(status = WeatherStatus.PermissionRequired),
        ))
        compose.onNodeWithTag("weather_connection_status")
            .assertTextEquals(context.getString(R.string.weather_settings_permission))
        compose.runOnIdle { assertEquals("Opening settings must not prompt automatically", 0, accessRequests) }
        clickSetting("weather_allow_access")
        compose.runOnIdle { assertEquals(1, accessRequests) }
        compose.onNodeWithTag("weather_install").assertDoesNotExist()
    }

    @Test fun locationAndDayChoicesUpdateIndependentPreferences() {
        showSettings(LauncherUiState(
            settings = LauncherSettings(weatherEnabled = true),
            weather = WeatherState(
                status = WeatherStatus.Ready,
                locations = listOf(
                    WeatherLocation("beijing&china", "Beijing", ZoneId.of("Asia/Shanghai"), false),
                    WeatherLocation("shanghai&china", "Shanghai", ZoneId.of("Asia/Shanghai"), false),
                ),
            ),
        ))
        compose.onNodeWithTag("weather_update_notifier_hint")
            .assertTextEquals(context.getString(R.string.weather_settings_update_notifier))
        clickSetting("weather_location")
        compose.onNodeWithTag("weather_location:shanghai&china").performClick()
        compose.runOnIdle { assertEquals("shanghai&china", state.settings.weatherLocationId) }
        clickSetting("weather_forecast_days")
        compose.onNodeWithTag("weather_days:10").performClick()
        compose.runOnIdle {
            assertEquals(10, state.settings.weatherForecastDays)
            assertEquals("shanghai&china", state.settings.weatherLocationId)
        }
        clickSetting("weather_location")
        compose.onNodeWithTag("weather_location:automatic").performClick()
        compose.runOnIdle { assertEquals(null, state.settings.weatherLocationId) }
    }

    private fun clickSetting(tag: String) {
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
    }
}
