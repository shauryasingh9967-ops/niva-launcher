package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.sectionForLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherSectionTest {
    @Test
    fun latinLabelsAreGroupedCaseInsensitively() {
        assertEquals("C", sectionForLabel("chrome"))
        assertEquals("N", sectionForLabel("  Notes"))
    }

    @Test
    fun accentedLabelsUseTheirLatinSection() {
        assertEquals("E", sectionForLabel("Éditeur"))
    }

    @Test
    fun numericAndEmptyLabelsUseFallbackSection() {
        assertEquals("#", sectionForLabel("1Password"))
        assertEquals("#", sectionForLabel(""))
    }

    @Test
    fun fullWidthLatinLabelsUseTheirLatinSection() {
        assertEquals("C", sectionForLabel("Ｃａｍｅｒａ"))
    }
}
