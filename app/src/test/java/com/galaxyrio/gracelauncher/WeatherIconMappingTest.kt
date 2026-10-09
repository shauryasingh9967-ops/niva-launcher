package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.weather.WeatherCondition
import com.galaxyrio.gracelauncher.ui.weather.weatherIconResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherIconMappingTest {
    @Test fun clearAndPartlyCloudyKeepDayAndNightDistinct() {
        for (condition in listOf(WeatherCondition.Clear, WeatherCondition.PartlyCloudy)) {
            assertNotEquals(weatherIconResource(condition, true), weatherIconResource(condition, false))
        }
        assertEquals(R.drawable.ms_sunny, weatherIconResource(WeatherCondition.Clear, true))
        assertEquals(R.drawable.ms_clear_night, weatherIconResource(WeatherCondition.Clear, false))
    }

    @Test fun everyKnownConditionIncludingFogHasArtwork() {
        val supported = WeatherCondition.entries - WeatherCondition.Unknown
        for (condition in supported) {
            for (daylight in listOf(false, true)) {
                assertNotNull(weatherIconResource(condition, daylight))
            }
        }
    }

    @Test fun nonCelestialConditionsRemainTheSameAtNightButHailAndSleetAreDistinct() {
        val conditions = WeatherCondition.entries - setOf(WeatherCondition.Clear, WeatherCondition.PartlyCloudy, WeatherCondition.Unknown)
        for (condition in conditions) {
            assertEquals(weatherIconResource(condition, true), weatherIconResource(condition, false))
        }
        assertNotEquals(weatherIconResource(WeatherCondition.Hail, true), weatherIconResource(WeatherCondition.Sleet, true))
    }

    @Test fun missingForecastNeverInventsAWeatherCondition() {
        for (daylight in listOf(false, true)) {
            assertNull(weatherIconResource(WeatherCondition.Unknown, daylight))
        }
    }
}
