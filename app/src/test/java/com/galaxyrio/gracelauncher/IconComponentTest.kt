package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.data.icons.normalizeIconComponent
import org.junit.Assert.*
import org.junit.Test

class IconComponentTest {
    @Test fun normalizesStandardAndShortComponentNames() {
        assertEquals("com.example/com.example.Main", normalizeIconComponent("ComponentInfo{com.example/.Main}"))
        assertEquals("com.example/com.example.Main", normalizeIconComponent(" com.example/com.example.Main "))
        assertEquals("com.example", normalizeIconComponent("com.example"))
    }

    @Test fun rejectsMalformedAndSpecialShortcutMappings() {
        listOf("", ":CALENDAR", "ComponentInfo{broken/.Main", "pkg/", "pkg/a/b", "bad name/.Main").forEach {
            assertNull(it, normalizeIconComponent(it))
        }
    }
}
