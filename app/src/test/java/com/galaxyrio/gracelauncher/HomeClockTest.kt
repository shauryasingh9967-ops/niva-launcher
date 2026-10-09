package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.ui.home.formatHomeClock
import com.galaxyrio.gracelauncher.ui.home.clockFaceText
import com.galaxyrio.gracelauncher.data.ClockStyle
import com.galaxyrio.gracelauncher.data.ClockLayout
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeClockTest {
    @Test fun layoutsKeepLeadingZeroAndOnlySingleLineUsesColonPreference() {
        val single = ClockStyle()
        assertEquals("09 05", clockFaceText("09:05", single))
        val colon = single.withFace(single.face.copy(showColon = true))
        assertEquals("09:05", clockFaceText("09:05", colon))
        assertEquals("09\n05", clockFaceText("09:05", colon.copy(layout = ClockLayout.TwoLines)))
        assertEquals("٠٩\n٠٥", clockFaceText("٠٩:٠٥", colon.copy(layout = ClockLayout.TwoLines)))
    }

    private fun clock(time: String, use24Hour: Boolean = true, locale: Locale = Locale.US) =
        formatHomeClock(Instant.parse("2026-09-27T${time}:00Z"), use24Hour, locale, ZoneOffset.UTC)

    @Test fun earlyHoursKeepTheLeadingZeroIn24HourMode() {
        assertEquals("09:05", clock("09:05"))
        assertEquals("00:07", clock("00:07"))
        assertEquals("23:59", clock("23:59"))
    }

    @Test fun twelveHourModeDoesNotGainALeadingZero() {
        assertEquals("9:05", clock("09:05", false))
        assertEquals("12:07", clock("00:07", false))
        assertEquals("1:05", clock("13:05", false))
    }

    @Test fun chineseAndJapaneseUseTwoDigit24HourHoursToo() {
        assertEquals("08:04", clock("08:04", locale = Locale.SIMPLIFIED_CHINESE))
        assertEquals("08:04", clock("08:04", locale = Locale.JAPANESE))
    }

    @Test fun localizedDigitsRemainLocalized() {
        assertEquals("٠٨:٠٤", clock("08:04", locale = Locale.forLanguageTag("ar-u-nu-arab")))
    }
}
