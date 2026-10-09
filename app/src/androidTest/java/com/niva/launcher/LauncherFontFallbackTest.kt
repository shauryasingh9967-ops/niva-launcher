package com.niva.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.text.PositionedGlyphs
import android.graphics.text.TextRunShaper
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.niva.launcher.ui.theme.launcherTypeface
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Verify actual native glyph selection, not just the declared Compose family. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 31)
class LauncherFontFallbackTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val assetSizes = mutableMapOf<String, Int>()

    @Test
    fun mixedTextUsesBundledLatinAndGreekFontsWithSystemCjk() {
        val glyphs = shape("A中あ한ΩЖ")
        val system = shape("A中あ한ΩЖ", typeface = Typeface.create(Typeface.SANS_SERIF, 400, false))
        assertEquals(6, glyphs.glyphCount())
        assertEquals(6, system.glyphCount())
        assertFont(glyphs, 0, "josefin_sans")
        (1..3).forEach { index -> assertSystemFont(glyphs, system, index) }
        (4..5).forEach { index -> assertFont(glyphs, index, "noto_sans") }
    }

    @Test
    fun additionalScriptsUseTheirBundledNotoFamilies() {
        listOf(
            Triple("العربية", "ar", "noto_sans_arabic"),
            Triple("עברית", "he", "noto_sans_hebrew"),
            // The general Noto Sans file also contains these Devanagari glyphs.
            Triple("हिन्दी", "hi", "noto_sans"),
            Triple("ภาษาไทย", "th", "noto_sans_thai"),
        ).forEach { (text, language, asset) ->
            val glyphs = shape(text, locale = Locale.forLanguageTag(language), rtl = language in listOf("ar", "he"))
            assertTrue(glyphs.glyphCount() > 0)
            repeat(glyphs.glyphCount()) { index -> assertFont(glyphs, index, asset) }
        }
    }

    @Test
    fun allWeightsUseRealVariableFontInstancesForBundledFonts() {
        (100..700 step 100).forEach { weight ->
            val typeface = launcherTypeface(context, weight)
            assertEquals(weight, typeface.weight)
            assertSame(typeface, launcherTypeface(context, weight))
            val glyphs = shape("AΩ", weight)
            assertFont(glyphs, 0, "josefin_sans")
            assertFont(glyphs, 1, "noto_sans")
            repeat(glyphs.glyphCount()) { index ->
                val font = glyphs.getFont(index)
                assertEquals(weight, font.style.weight)
                val axis = font.axes.orEmpty().singleOrNull { it.tag == "wght" }
                // Android 15+ can carry the variable weight as a per-glyph
                // override rather than an axis on the shared backing Font.
                val override = if (Build.VERSION.SDK_INT >= 35) {
                    assertTrue("Weight must not be synthesized", !glyphs.getFakeBold(index))
                    glyphs.getWeightOverride(index).takeUnless { it == PositionedGlyphs.NO_OVERRIDE }
                } else null
                val defaultWeight = if (index == 0) 100f else 400f
                assertEquals("Weight $weight, glyph $index", weight.toFloat(), override ?: axis?.styleValue ?: defaultWeight, 0.01f)
            }
        }
        listOf("A", "Ω").forEach { text ->
            val thin = inkCoverage(text, 100)
            val normal = inkCoverage(text, 400)
            val bold = inkCoverage(text, 700)
            assertTrue("$text must render real weight differences", thin < normal && normal < bold)
        }
    }

    @Test
    fun cjkGlyphsAndWeightsMatchTheSystemForEachLocale() {
        listOf(
            Locale.SIMPLIFIED_CHINESE to "简体中文骨",
            Locale.TRADITIONAL_CHINESE to "繁體漢字骨",
            Locale.JAPANESE to "日本語ひらがなカタカナ骨",
            Locale.KOREAN to "한국어한글",
        ).forEach { (locale, text) ->
            listOf(100, 400, 700).forEach { weight ->
                val glyphs = shape(text, weight, locale)
                val system = shape(text, weight, locale, typeface = Typeface.create(Typeface.SANS_SERIF, weight, false))
                assertTrue(glyphs.glyphCount() > 0)
                assertEquals(system.glyphCount(), glyphs.glyphCount())
                repeat(glyphs.glyphCount()) { index -> assertSystemFont(glyphs, system, index) }
            }
        }
    }

    @Test
    fun emojiRemainsAvailableThroughTheFinalSystemFallback() {
        val glyphs = shape("😀")
        assertTrue(glyphs.glyphCount() > 0)
        repeat(glyphs.glyphCount()) { index -> assertTrue(glyphs.getGlyphId(index) != 0) }
    }

    private fun shape(
        text: String,
        weight: Int = 400,
        locale: Locale = Locale.ENGLISH,
        rtl: Boolean = false,
        typeface: Typeface = launcherTypeface(context, weight),
    ): PositionedGlyphs {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = 32f
            textLocale = locale
        }
        return TextRunShaper.shapeTextRun(text, 0, text.length, 0, text.length, 0f, 0f, rtl, paint)
    }

    private fun assertFont(glyphs: PositionedGlyphs, index: Int, asset: String) {
        assertTrue("Missing glyph at $index for $asset", glyphs.getGlyphId(index) != 0)
        val size = assetSizes.getOrPut(asset) {
            context.assets.open("fonts/$asset.ttf").use { input ->
                val buffer = ByteArray(8192)
                var size = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    size += count
                }
                size
            }
        }
        // Each bundled font has a distinct size; system fallback fonts cannot
        // silently satisfy this check.
        assertEquals("Unexpected native font for glyph $index; expected $asset", size, glyphs.getFont(index).buffer.capacity())
    }

    private fun assertSystemFont(glyphs: PositionedGlyphs, system: PositionedGlyphs, index: Int) {
        assertTrue("Missing CJK glyph at $index", glyphs.getGlyphId(index) != 0)
        assertEquals("CJK font must match system sans-serif", system.getFont(index), glyphs.getFont(index))
        assertEquals("Regional glyph must match the text locale", system.getGlyphId(index), glyphs.getGlyphId(index))
    }

    private fun inkCoverage(text: String, weight: Int): Long {
        val bitmap = Bitmap.createBitmap(256, 160, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = launcherTypeface(context, weight)
            textSize = 100f
        }
        Canvas(bitmap).drawText(text, 20f, 120f, paint)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        bitmap.recycle()
        return pixels.sumOf { (it ushr 24).toLong() }
    }
}
