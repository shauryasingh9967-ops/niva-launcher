package com.niva.launcher

import com.niva.launcher.data.CountdownUnit
import com.niva.launcher.data.ScheduleEvent
import com.niva.launcher.data.eventCountdown
import com.niva.launcher.data.nextVisibleEvent
import com.niva.launcher.data.agendaDays
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleTimingTest {
    private val now = Instant.parse("2026-09-22T12:00:00Z")
    private fun event(start: Instant, end: Instant, allDay: Boolean = false) = ScheduleEvent(
        id = start.epochSecond, title = "Movie night", startsAt = start, endsAt = end,
        isAllDay = allDay, location = null, calendarColor = null,
    )

    @Test
    fun expiredEventDoesNotHideTheNextAppointment() {
        val expired = event(now.minusSeconds(3600), now)
        val later = event(now.plusSeconds(7200), now.plusSeconds(10800))
        val next = event(now.plusSeconds(1680), now.plusSeconds(5280))
        assertEquals(next, nextVisibleEvent(listOf(expired, later, next), now))
        assertEquals(28, eventCountdown(next, now).value)
    }

    @Test
    fun countdownNeverShowsZeroMinutesBeforeAnEvent() {
        assertEquals(CountdownUnit.LessThanMinute,
            eventCountdown(event(now.plusSeconds(10), now.plusSeconds(3610)), now).unit)
        assertEquals(CountdownUnit.Ongoing,
            eventCountdown(event(now.minusSeconds(10), now.plusSeconds(3590)), now).unit)
    }

    @Test
    fun allDayDatesRespectTheUsersTimezone() {
        val allDay = event(
            Instant.parse("2026-09-23T00:00:00Z"),
            Instant.parse("2026-09-24T00:00:00Z"),
            allDay = true,
        )
        val shanghai = ZoneId.of("Asia/Shanghai")
        assertEquals(4, eventCountdown(allDay, now, shanghai).value)
        assertEquals(CountdownUnit.Hours, eventCountdown(allDay, now, shanghai).unit)
        assertEquals(CountdownUnit.AllDay,
            eventCountdown(allDay, Instant.parse("2026-09-22T16:00:00Z"), shanghai).unit)
        assertEquals(null,
            nextVisibleEvent(listOf(allDay), Instant.parse("2026-09-23T16:00:00Z"), shanghai))
    }

    @Test
    fun agendaExpandsAllDayEventsButNotTheirExclusiveEndDate() {
        val holiday = event(Instant.parse("2026-09-25T00:00:00Z"), Instant.parse("2026-09-28T00:00:00Z"), true)
        val today = LocalDate.parse("2026-09-22")
        val days = agendaDays(listOf(holiday), today, ZoneId.of("America/Los_Angeles"))
        assertEquals(listOf(today, today.plusDays(3), today.plusDays(4), today.plusDays(5)), days.keys.toList())
        assertEquals(emptyList<ScheduleEvent>(), days[today])
        assertEquals(listOf(holiday), days[today.plusDays(3)])
    }

    @Test
    fun agendaKeepsOnlyVisibleDaysAndSeparatesRecurringInstances() {
        val today = LocalDate.parse("2026-09-22")
        val old = event(Instant.parse("2026-09-20T08:00:00Z"), Instant.parse("2026-09-20T09:00:00Z"))
        val first = event(Instant.parse("2026-09-22T08:00:00Z"), Instant.parse("2026-09-22T09:00:00Z"))
        val second = first.copy(startsAt = first.startsAt.plusSeconds(86400), endsAt = first.endsAt.plusSeconds(86400))
        val distant = event(Instant.parse("2026-10-10T08:00:00Z"), Instant.parse("2026-10-10T09:00:00Z"))
        val days = agendaDays(listOf(old, first, second, distant), today, ZoneId.of("UTC"))
        assertEquals(listOf(today, today.plusDays(1)), days.keys.toList())
        assertEquals(listOf(first), days[today])
        assertEquals(listOf(second), days[today.plusDays(1)])
    }
}
