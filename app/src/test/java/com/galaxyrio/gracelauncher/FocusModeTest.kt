package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.isFocusHidden
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusModeTest {
    private val chosen = setOf("a/.Main", "b/.Main")

    @Test
    fun chosenAppsAreHiddenOnlyWhileFocusIsActive() {
        assertTrue(isFocusHidden("a/.Main", true, chosen))
        assertFalse(isFocusHidden("a/.Main", false, chosen))
    }

    @Test
    fun unchosenAppsAreNeverHidden() {
        assertFalse(isFocusHidden("c/.Main", true, chosen))
    }

    @Test
    fun emptySelectionHidesNothing() {
        assertFalse(isFocusHidden("a/.Main", true, emptySet()))
    }
}
