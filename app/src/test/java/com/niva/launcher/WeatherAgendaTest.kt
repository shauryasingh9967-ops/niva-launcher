package com.niva.launcher

import com.niva.launcher.data.ScheduleEvent
import com.niva.launcher.ui.weather.weatherAgendaDays
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WeatherAgendaTest {
    private val today = LocalDate.of(2026, 9, 27)

    @Test fun fillsWeatherOnlyDaysWithoutRemovingLaterEvents() {
        val start = today.plusDays(10).atTime(9, 0).toInstant(ZoneOffset.UTC)
        val event = ScheduleEvent(1, "Later appointment", start, start.plusSeconds(3600), false, null, null)
        val forecasts = (0L..12L).map(today::plusDays)
        val days = weatherAgendaDays(listOf(event), forecasts, today, 7, ZoneOffset.UTC)

        assertEquals((0L..6L).map(today::plusDays) + today.plusDays(10), days.keys.toList())
        assertEquals(listOf(event), days[today.plusDays(10)])
        assertEquals(emptyList<ScheduleEvent>(), days[today.plusDays(1)])
    }

    @Test fun ignoresPastForecastsAndNeverInventsUnavailableForecastDates() {
        val days = weatherAgendaDays(emptyList(), listOf(today.minusDays(1), today.plusDays(2)), today, 7)

        assertEquals(listOf(today, today.plusDays(2)), days.keys.toList())
        assertFalse(days.containsKey(today.plusDays(1)))
    }

    @Test fun emptyWeatherKeepsTheExistingTodaySection() {
        assertEquals(listOf(today), weatherAgendaDays(emptyList(), emptyList(), today, 7).keys.toList())
    }
}
