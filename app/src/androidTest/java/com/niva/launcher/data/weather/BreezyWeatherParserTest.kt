package com.niva.launcher.data.weather

import android.database.MatrixCursor
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.GZIPOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BreezyWeatherParserTest {
    private val location = WeatherLocation("31.2&121.5&openmeteo", "Shanghai", ZoneId.of("Asia/Shanghai"))
    private val now = Instant.parse("2026-09-26T16:30:00Z")

    @Test fun readsCompressedProtocolPreservingUnitsLocationDatesAndCredits() {
        val midnight = Instant.parse("2026-09-26T16:00:00Z").toEpochMilli()
        val later = midnight + 3_600_000
        val weather = requireNotNull(BreezyWeatherParser.weather(gzip("""
            {
              "refreshTime": $midnight,
              "futureProtocolField": true,
              "current": {"weatherCode":"clear","weatherText":"晴","temperature":{"temperature":{"value":75.2,"unit":"f"}}},
              "hourly": [
                {"date":$later,"isDaylight":false,"weatherCode":"partly_cloudy","temperature":{"temperature":{"value":74,"unit":"f"}}},
                {"date":$midnight,"isDaylight":false,"weatherCode":"clear","temperature":{"temperature":{"value":75.2,"unit":"f"}}}
              ],
              "daily": [{"date":$midnight,
                "day":{"weatherCode":"rain","weatherText":"Rain","temperature":{"temperature":{"value":81,"unit":"f"}}},
                "night":{"weatherCode":"cloudy","temperature":{"temperature":{"value":65,"unit":"f"}}}
              }],
              "sources": {
                "forecast":{"text":"Example Weather (CC BY 4.0)","links":{"CC BY 4.0":"https://creativecommons.org/licenses/by/4.0/"}},
                "current":{"text":"Example Weather (CC BY 4.0)"},
                "air_quality":{"text":"Unused AQ supplier"}
              }
            }
        """), location, now))
        val current = requireNotNull(weather.current)
        assertEquals(75.2, current.temperature!!.value, 0.0)
        assertEquals("75°F", current.temperature.format(showUnit = true))
        assertEquals("f", weather.daily.single().low!!.unit)
        assertEquals(LocalDate.of(2026, 9, 27), weather.daily.single().date)
        assertEquals(WeatherCondition.Rain, weather.daily.single().condition)
        assertEquals(Instant.ofEpochMilli(midnight), weather.hourly.first().at)
        assertEquals(Instant.ofEpochMilli(midnight), weather.updatedAt)
        assertFalse(current.isDaylight)
        assertEquals("Breezy Weather · Example Weather (CC BY 4.0)", weather.attribution)
        assertEquals("https://creativecommons.org/licenses/by/4.0/", weather.sourceLinks["CC BY 4.0"])
    }

    @Test fun absentFieldsAndUnknownUnitsNeverBecomeInventedZeroCelsius() {
        val weather = requireNotNull(BreezyWeatherParser.weather(gzip("""
            {
              "current":{"weatherCode":"cloudy","temperature":{"temperature":{"value":32,"unit":"new_scale"}}},
              "hourly":[{"date":null},{"weatherCode":"rain"}],
              "daily":[{"date":null}]
            }
        """), location, now))
        assertNull(weather.current!!.temperature)
        assertNull(weather.updatedAt)
        assertTrue(weather.hourly.isEmpty())
        assertTrue(weather.daily.isEmpty())
        assertEquals("Breezy Weather", weather.attribution)
        assertNull(BreezyWeatherParser.weather(gzip("{}"), location, now))
        assertNull(BreezyWeatherParser.weather(gzip("""{"current":{"temperature":{"temperature":{"value":0}}}}"""), location, now))
    }

    @Test fun customLocationNameAndTimeZoneUsePublicCursorColumns() {
        MatrixCursor(arrayOf("id", "city", "custom_name", "timezone", "is_current_position")).use { cursor ->
            cursor.addRow(arrayOf<Any>("current", "Shanghai", " Home ", "Asia/Shanghai", 1))
            cursor.moveToFirst()
            val parsed = BreezyWeatherParser.location(cursor)
            assertEquals("current", parsed.id)
            assertEquals("Home", parsed.name)
            assertEquals(ZoneId.of("Asia/Shanghai"), parsed.timeZone)
            assertTrue(parsed.isCurrentPosition)
        }
    }

    @Test fun weatherCodesAndUnknownValuesDegradeWithoutCrashing() {
        assertEquals(WeatherCondition.PartlyCloudy, BreezyWeatherParser.condition("partly_cloudy"))
        assertEquals(WeatherCondition.Thunderstorm, BreezyWeatherParser.condition("thunder"))
        assertEquals(WeatherCondition.Fog, BreezyWeatherParser.condition("haze"))
        assertEquals(WeatherCondition.Unknown, BreezyWeatherParser.condition("future_code"))
        assertEquals(WeatherCondition.Unknown, BreezyWeatherParser.condition(null))
        assertEquals("300 K", WeatherTemperature(300.1, "k").format())
        assertEquals("0°", WeatherTemperature(-0.1, "c").format())
    }

    private fun gzip(value: String): ByteArray = ByteArrayOutputStream().use { bytes ->
        GZIPOutputStream(bytes).use { it.write(value.toByteArray(Charsets.UTF_8)) }
        bytes.toByteArray()
    }
}
