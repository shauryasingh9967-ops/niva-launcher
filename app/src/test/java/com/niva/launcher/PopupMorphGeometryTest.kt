package com.niva.launcher

import androidx.compose.ui.geometry.Rect
import com.niva.launcher.ui.overlays.popupMorphFrame
import org.junit.Assert.*
import org.junit.Test

class PopupMorphGeometryTest {
    private val source = Rect(20f, 300f, 60f, 360f)
    private val target = Rect(16f, 180f, 376f, 620f)

    @Test fun startsAtACircleCenteredOnTheIconEvenForNonSquareBounds() {
        val frame = popupMorphFrame(source, target, 0f, 24f)
        assertEquals(source.center, frame.bounds.center)
        assertEquals(40f, frame.bounds.width, 0f)
        assertEquals(frame.bounds.width, frame.bounds.height, 0f)
        assertEquals(20f, frame.radius, 0f)
    }

    @Test fun growsBothAxesToTheFinalRoundedRectangle() {
        val middle = popupMorphFrame(source, target, 0.5f, 24f)
        assertEquals(200f, middle.bounds.width, 0f)
        assertEquals(240f, middle.bounds.height, 0f)
        assertEquals(22f, middle.radius, 0f)
        val end = popupMorphFrame(source, target, 1f, 24f)
        assertEquals(target, end.bounds)
        assertEquals(24f, end.radius, 0f)
    }

    @Test fun spatialSpringOvershootIsNotClampedAway() {
        assertTrue(popupMorphFrame(source, target, 1.02f, 24f).bounds.width > target.width)
        assertEquals(popupMorphFrame(source, target, 0f, 24f), popupMorphFrame(source, target, -0.01f, 24f))
    }
}
