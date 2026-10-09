package com.niva.launcher

import com.niva.launcher.ui.home.ClockInkBox
import com.niva.launcher.ui.home.clockDigitPositions
import com.niva.launcher.ui.home.clockGroupPositions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockSpacingTest {
    @Test fun digitAndGroupGapsAreIndependentAcrossBothSliderRanges() {
        for (digitGap in -16..16) for (groupGap in -16..64) {
            val hourDigits = clockDigitPositions(listOf(38f, 54f), digitGap.toFloat())
            val minuteDigits = clockDigitPositions(listOf(66f, 42f), digitGap.toFloat())
            val hours = ClockInkBox(hourDigits.last() + 54f, -78f, 2f)
            val minutes = ClockInkBox(minuteDigits.last() + 42f, -80f, 0f)
            val positions = clockGroupPositions(hours, minutes, groupGap.toFloat(), stacked = false)
            assertEquals(digitGap.toFloat(), hourDigits[1] - hourDigits[0] - 38f, 0.001f)
            assertEquals(digitGap.toFloat(), minuteDigits[1] - minuteDigits[0] - 66f, 0.001f)
            assertEquals(groupGap.toFloat(), positions.minuteX - hours.width, 0.001f)
        }
    }

    @Test fun colonSplitsOnlyTheGroupGapAndDoesNotChangeDigitPositions() {
        val hours = ClockInkBox(96f, -80f, 0f)
        val minutes = ClockInkBox(104f, -80f, 0f)
        for (gap in -16..64) {
            val positions = clockGroupPositions(hours, minutes, gap.toFloat(), stacked = false, colonWidth = 9f)
            val colon = requireNotNull(positions.colonX)
            assertEquals(gap / 2f, colon - hours.width, 0.001f)
            assertEquals(gap / 2f, positions.minuteX - (colon + 9f), 0.001f)
        }
    }

    @Test fun stackedGapUsesInkEdgesInsteadOfFontDescentOrDigitWidths() {
        for (width in listOf(10f, 70f, 160f)) for (gap in -16..64) {
            val hours = ClockInkBox(width, -91f, 3f)
            val minutes = ClockInkBox(98f, -82f, 1f)
            val positions = clockGroupPositions(hours, minutes, gap.toFloat(), stacked = true, centerLines = true)
            assertEquals(gap.toFloat(), positions.minuteBaseline + minutes.top - hours.bottom, 0.001f)
        }
    }

    @Test fun largeNegativeDigitSpacingStillProducesFiniteNormalizedPositions() {
        val positions = clockDigitPositions(listOf(3f, 2f), -16f)
        assertTrue(positions.all { it.isFinite() && it >= 0f })
        assertEquals(-16f, positions[1] - positions[0] - 3f, 0.001f)
        assertTrue(clockDigitPositions(emptyList(), -16f).isEmpty())
    }
}
