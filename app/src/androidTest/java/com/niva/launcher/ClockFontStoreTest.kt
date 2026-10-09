package com.niva.launcher

import androidx.test.platform.app.InstrumentationRegistry
import com.niva.launcher.data.ClockFontStore
import com.niva.launcher.data.ClockLayout
import com.niva.launcher.data.ClockStyle
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ClockFontStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun importedFontSurvivesReopeningAndSupportsRealVariableWeights() = runBlocking {
        val store = ClockFontStore(context)
        val imported = context.assets.open("fonts/josefin_sans.ttf").use { store.importStream(it, "Test clock.ttf") }
        try {
            val reopened = ClockFontStore(context)
            assertTrue(reopened.list().any { it == imported })
            val regular = requireNotNull(reopened.typeface(imported.id, 400))
            val bold = requireNotNull(reopened.typeface(imported.id, 700))
            assertEquals(400, regular.weight)
            assertEquals(700, bold.weight)
            assertNull(reopened.typeface("../../outside.ttf", 400))
            assertNull(reopened.typeface("missing.ttf", 400))
        } finally { File(context.filesDir, "clock_fonts/${imported.id}").delete() }
    }

    @Test fun invalidAndOversizedImportsDoNotLeaveFiles() = runBlocking {
        val store = ClockFontStore(context)
        val before = store.list()
        listOf("not a font".toByteArray(), ByteArray(20 * 1024 * 1024 + 1)).forEach { bytes ->
            assertTrue(runCatching { store.importStream(ByteArrayInputStream(bytes), "bad.ttf") }.isFailure)
        }
        assertEquals(before, store.list())
    }

    @Test fun styleCodecPreservesBothLayoutsAndRecoversFromMalformedValues() {
        val original = ClockStyle(layout = ClockLayout.TwoLines,
            singleLine = ClockStyle.defaults(ClockLayout.SingleLine).copy(weight = 700, size = 98, showColon = true),
            twoLines = ClockStyle.defaults(ClockLayout.TwoLines).copy(fontId = "chosen.ttf", weight = 900, letterSpacing = 5))
        assertEquals(original, ClockStyle.decode(original.encode()))
        assertEquals(ClockStyle(), ClockStyle.decode("not json"))
        assertEquals(ClockStyle(), ClockStyle.decode(null))
        val invalid = ClockStyle.decode("{\"layout\":\"unknown\",\"singleLine\":{\"weight\":9999,\"size\":-5,\"letterSpacing\":999}}")
        assertEquals(ClockLayout.SingleLine, invalid.layout)
        assertEquals(700, invalid.face.weight)
        assertEquals(32, invalid.face.size)
        assertEquals(16, invalid.face.letterSpacing)
    }
}
