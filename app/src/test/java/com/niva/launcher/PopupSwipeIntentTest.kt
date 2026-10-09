package com.niva.launcher

import com.niva.launcher.ui.components.PopupSwipeIntent
import org.junit.Assert.*
import org.junit.Test

class PopupSwipeIntentTest {
    @Test fun firstRightMovementAfterTouchSlopTriggersOpening() {
        val swipe = PopupSwipeIntent(8f)
        assertNull(swipe.drag(-20f))
        assertEquals(true, swipe.drag(1f))
        assertTrue(swipe.expanded)
        assertNull(swipe.drag(0f))
        assertTrue(swipe.expanded)
    }

    @Test fun directionReversalDoesNotNeedToUndoALongDrag() {
        val swipe = PopupSwipeIntent(8f)
        swipe.drag(200f)
        assertNull(swipe.drag(-3f))
        assertEquals(false, swipe.drag(-5f))
        assertNull(swipe.drag(3f))
        assertEquals(true, swipe.drag(5f))
    }

    @Test fun smallJitterDoesNotRetargetAndNewGestureResetsIntent() {
        val swipe = PopupSwipeIntent(8f)
        swipe.drag(1f)
        repeat(20) { assertNull(swipe.drag(-2f)); assertNull(swipe.drag(2f)) }
        assertTrue(swipe.expanded)
        swipe.reset()
        assertFalse(swipe.revealed)
        assertFalse(swipe.expanded)
    }
}
