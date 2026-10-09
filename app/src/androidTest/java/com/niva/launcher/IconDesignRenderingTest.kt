package com.niva.launcher

import android.content.ComponentName
import android.graphics.*
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.niva.launcher.data.*
import com.niva.launcher.data.icons.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class IconDesignRenderingTest {
    private fun solid(color: Int, side: Int = 100) = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
    private fun layers(): IconLayers {
        val back = solid(Color.BLUE)
        val symbol = solid(Color.TRANSPARENT)
        Canvas(symbol).drawRect(40f, 40f, 60f, 60f, Paint().apply { color = Color.RED })
        val mono = solid(Color.TRANSPARENT)
        Canvas(mono).drawRect(40f, 40f, 60f, 60f, Paint().apply { color = Color.WHITE })
        val original = back.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(original).drawBitmap(symbol, 0f, 0f, null)
        return IconLayers(original, back, symbol, mono)
    }
    private fun render(layers: IconLayers, style: IconDesign) = renderDesignedIcon(layers, style, Color.GREEN, Color.YELLOW)

    @Test fun adaptiveIconsWithMissingLayersKeepTheirArtworkWithoutCrashing() {
        val drawables = listOf(
            AdaptiveIconDrawable(ColorDrawable(Color.BLUE), null),
            AdaptiveIconDrawable(null, ColorDrawable(Color.BLUE)),
        )
        for (drawable in drawables) {
            val source = iconLayers(drawable, 100)
            assertFalse(source.layered)
            assertEquals(Color.BLUE, source.original.getPixel(50, 50))
            assertSame(source.original, render(source, IconDesign()))
            val themed = render(source, IconDesign(shape = IconShape.Circle, foreground = IconColor(Color.RED),
                themeIcons = true, themeUnsupportedIcons = true))
            assertEquals(Color.RED, themed.getPixel(50, 50))
        }
    }

    @Test fun foregroundMovesAndScalesWithoutMovingTheTrayAndResetRestoresOriginal() {
        val source = layers()
        val result = render(source, IconDesign(x = 25f, size = 50))
        assertEquals(Color.BLUE, result.getPixel(50, 50))
        assertEquals(Color.RED, result.getPixel(75, 50))
        assertEquals(Color.BLUE, result.getPixel(82, 50))
        assertEquals(Color.BLUE, result.getPixel(0, 0))
        assertSame(source.original, render(source, IconDesign()))
        assertEquals(Color.RED, source.original.getPixel(50, 50))
    }

    @Test fun adaptiveColorsUseSeparateLayersAndDynamicRoles() {
        val source = layers()
        val result = render(source, IconDesign(background = IconColor(Color.RED, dynamic = true),
            foreground = IconColor(Color.BLUE, dynamic = true), themeIcons = true))
        assertEquals(Color.GREEN, result.getPixel(10, 10))
        assertEquals(Color.YELLOW, result.getPixel(50, 50))
        val resetForeground = render(source, IconDesign(background = IconColor(Color.GREEN), themeIcons = true))
        assertEquals(Color.RED, resetForeground.getPixel(50, 50))
    }

    @Test fun grayscaleNormalizesVisiblePixelsAndPreservesAlpha() {
        val source = Bitmap.createBitmap(intArrayOf(Color.TRANSPARENT, Color.rgb(64, 64, 64),
            Color.argb(128, 128, 128, 128), Color.rgb(192, 192, 192)), 4, 1, Bitmap.Config.ARGB_8888)
        val result = recolorGrayscale(source, Color.RED, Color.BLUE)
        assertEquals(Color.TRANSPARENT, result.getPixel(0, 0))
        assertEquals(Color.RED, result.getPixel(1, 0))
        assertEquals(Color.BLUE, result.getPixel(3, 0))
        assertEquals(128, Color.alpha(result.getPixel(2, 0)))
        assertTrue(Color.red(result.getPixel(2, 0)) in 126..129)
        assertTrue(Color.blue(result.getPixel(2, 0)) in 126..129)
        val mono = normalizedMonochrome(source)
        assertEquals(0, Color.alpha(mono.getPixel(1, 0)))
        assertEquals(255, Color.alpha(mono.getPixel(3, 0)))
        assertTrue(Color.alpha(mono.getPixel(2, 0)) in 63..65)
        val inverted = normalizedMonochrome(source, invert = true)
        assertEquals(0, Color.alpha(inverted.getPixel(0, 0)))
        assertEquals(255, Color.alpha(inverted.getPixel(1, 0)))
        assertEquals(0, Color.alpha(inverted.getPixel(3, 0)))
        assertTrue(Color.alpha(inverted.getPixel(2, 0)) in 63..65)
    }

    @Test fun allCropChoicesRenderAndCookieCountsProduceDistinctMasks() {
        val source = layers()
        val square = render(source, IconDesign(shape = IconShape.Square))
        assertEquals(Color.BLUE, square.getPixel(0, 0))
        for (shape in listOf(IconShape.Circle, IconShape.Pebble, IconShape.Gem, IconShape.Cookie)) {
            val result = render(source, IconDesign(shape = shape))
            assertEquals("$shape should crop corners", 0, Color.alpha(result.getPixel(0, 0)))
            assertEquals("$shape should retain its center", Color.RED, result.getPixel(50, 50))
        }
        val masks = IconDesign.CookieSides.map { sides ->
            val bitmap = render(source, IconDesign(shape = IconShape.Cookie, cookieSides = sides))
            IntArray(10_000).also { bitmap.getPixels(it, 0, 100, 0, 0, 100, 100) }.contentHashCode()
        }
        assertEquals(5, masks.distinct().size)
    }

    @Test fun allStylesPackMatchesAndSingleParametersTakePriority() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = ItemIconStore(context, IconPackRepository(context))
        val original = solid(Color.GREEN).asImageBitmap()
        val app = LauncherApp(ComponentName("test.legacy", "Main"), "Legacy", original)
        val bulk = ItemIcon.Theme.copy(design = IconDesign(shape = IconShape.Circle, foreground = IconColor(Color.RED),
            themeIcons = true, themeUnsupportedIcons = true))
        val settings = LauncherSettings(iconDesign = bulk)
        val mapped = app.copy(iconPackPackage = "adapted.icons", themeIconPackPackage = "adapted.icons")
        val adapted = store.applyDesign(mapped, null, bulk, settings).icon!!.asAndroidBitmap()
        assertEquals(Color.RED, adapted.getPixel(50, 50))
        assertEquals(0, Color.alpha(adapted.getPixel(0, 0)))
        assertEquals(Color.RED, store.applyDesign(app, ItemIcon.System, bulk, settings).icon!!.asAndroidBitmap().getPixel(50, 50))
        val styled = store.applyDesign(app, null, bulk, settings).icon!!.asAndroidBitmap()
        assertEquals(Color.RED, styled.getPixel(50, 50))
        assertEquals(Color.GREEN, original.asAndroidBitmap().getPixel(50, 50))
        val special = ItemIcon.System.copy(design = IconDesign(foreground = IconColor(Color.BLUE), themeIcons = true,
            themeUnsupportedIcons = true))
        assertEquals(Color.BLUE, store.applyDesign(app, special, bulk, settings).icon!!.asAndroidBitmap().getPixel(50, 50))
    }

    @Test fun itemIconReadsDesktopDataAndRoundTripsNonDestructiveParameters() {
        val desktop = """{"kind":"pack","source":"example.icons","name":"alternate"}"""
        assertEquals(ItemIcon("pack", "example.icons", "alternate"), ItemIcon.decode(desktop))
        val design = ItemIcon("image", "example.png", design = IconDesign(shape = IconShape.Cookie, cookieSides = 9,
            background = IconColor.Theme, foreground = IconColor(Color.BLUE), x = 12.5f, y = -10f, size = 175,
            iconSize = 150, themeIcons = true, themeUnsupportedIcons = true, invertBackgroundDetection = true))
        assertEquals(design, ItemIcon.decode(design.encode()))
    }
}
