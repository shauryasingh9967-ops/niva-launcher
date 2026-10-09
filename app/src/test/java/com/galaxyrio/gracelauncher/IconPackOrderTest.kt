package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.firstMatchingPackIcon
import com.galaxyrio.gracelauncher.data.normalizeIconPackOrder
import org.junit.Assert.*
import org.junit.Test

class IconPackOrderTest {
    @Test fun firstMatchWinsAndLaterPacksAreNotRead() {
        val visited = mutableListOf<String>()
        val match = firstMatchingPackIcon(listOf("first", "second", "third")) { pack ->
            visited += pack
            if (pack == "first") null else "$pack-icon"
        }
        assertEquals("second" to "second-icon", match)
        assertEquals(listOf("first", "second"), visited)
    }

    @Test fun changingTheOrderChangesPriorityAndNoMatchAllowsSystemFallback() {
        val icons = mapOf("first" to "first-icon", "second" to "second-icon")
        assertEquals("first" to "first-icon", firstMatchingPackIcon(listOf("first", "second"), icons::get))
        assertEquals("second" to "second-icon", firstMatchingPackIcon(listOf("second", "first"), icons::get))
        assertNull(firstMatchingPackIcon(listOf("missing", "unmapped"), icons::get))
    }

    @Test fun blanksAndDuplicatesCannotAffectTheOrder() {
        assertEquals(listOf("second", "first"), normalizeIconPackOrder(listOf("", "second", " ", "first", "second")))
    }
}
