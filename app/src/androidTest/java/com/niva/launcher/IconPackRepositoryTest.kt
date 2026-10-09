package com.niva.launcher

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Xml
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.niva.launcher.data.AppRepository
import com.niva.launcher.data.icons.IconPackRepository
import com.niva.launcher.data.icons.composeFallbackIcon
import com.niva.launcher.data.icons.parseIconPack
import java.io.StringReader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IconPackRepositoryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun parse(body: String) = parseIconPack(Xml.newPullParser().apply {
        setInput(StringReader("<resources>$body</resources>"))
    })

    @Test fun supportsShortComponentsPackageEntriesAndUnambiguousActivityAliases() {
        val definition = parse("""
            <item component="ComponentInfo{test.one/.Main}" drawable="one" />
            <item component="test.package" drawable="package_icon" />
            <item component="test.multi/test.multi.One" drawable="first" />
            <item component="test.multi/test.multi.Two" drawable="second" />
            <item component=":CALENDAR" drawable="ignore" />
            <item component="" drawable="ignore" />
        """)
        assertEquals(listOf("one"), definition.candidates("test.one/test.one.Main", 1))
        assertEquals(listOf("one"), definition.candidates("test.one/test.one.Renamed", 1))
        assertEquals(listOf("package_icon"), definition.candidates("test.package/.Main", 1))
        assertEquals(listOf("second"), definition.candidates("test.multi/.Two", 1))
        assertTrue(definition.candidates("test.multi/.NewActivity", 1).isEmpty())
        assertEquals(4, definition.icons.size)
    }

    @Test fun calendarsPreferTodaysDrawableAndRetainAStaticFallback() {
        val definition = parse("""
            <item component="test.calendar/.Main" drawable="calendar" />
            <calendar component="test.calendar/.Main" prefix="day_" />
        """)
        assertEquals(listOf("day_1", "calendar"), definition.candidates("test.calendar/.Main", 1))
        assertEquals(listOf("day_27", "calendar"), definition.candidates("test.calendar/.Main", 27))
        assertEquals(listOf("day_31", "calendar"), definition.candidates("test.calendar/.Main", 31))
    }

    @Test fun parsesMultipleFallbackLayersAndRejectsUnsafeScaleValues() {
        val definition = parse("""
            <iconback img1="back_one" img2="back_two" />
            <iconmask img="mask" /><iconupon img1="overlay" /><scale factor="1.15" />
        """)
        assertEquals(listOf("back_one", "back_two"), definition.backgrounds)
        assertEquals(listOf("mask"), definition.masks)
        assertEquals(listOf("overlay"), definition.overlays)
        assertEquals(1.15f, definition.scale, 0f)
        listOf("NaN", "Infinity", "-1", "0", "bad").forEach {
            assertEquals(1f, parse("<scale factor=\"$it\" />").scale, 0f)
        }
    }

    @Test fun masksEraseTheOriginalNotTheBackgroundAndOverlaysRemainOnTop() {
        fun solid(color: Int) = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        val original = solid(Color.RED)
        val back = solid(Color.BLUE)
        val mask = solid(Color.TRANSPARENT)
        Canvas(mask).drawRect(0f, 0f, 10f, 20f, Paint().apply { color = Color.BLACK })
        val upon = solid(Color.TRANSPARENT)
        Canvas(upon).drawRect(8f, 8f, 12f, 12f, Paint().apply { color = Color.GREEN })
        val rendered = composeFallbackIcon(original, back, mask, upon, 1f)
        assertEquals(Color.BLUE, rendered.getPixel(2, 2))
        assertEquals(Color.RED, rendered.getPixel(17, 2))
        assertEquals(Color.GREEN, rendered.getPixel(9, 9))
        val scaled = composeFallbackIcon(original, back, null, null, 0.5f)
        assertEquals(Color.BLUE, scaled.getPixel(1, 1))
        assertEquals(Color.RED, scaled.getPixel(10, 10))
    }

    @Test fun installedPurePackIsDeduplicatedAndLoadsActualSplitResources() = runBlocking {
        val repository = IconPackRepository(context)
        val packs = repository.installedPacks()
        assumeTrue("Install Pure Icon Pack for this integration test", packs.any { it.packageName == PurePackage })
        assertEquals(1, packs.count { it.packageName == PurePackage })
        val pack = requireNotNull(repository.load(PurePackage))
        assertTrue(pack.definition.icons.size > 1000)
        assertSame(pack, repository.load(PurePackage))
        val chrome = requireNotNull(pack.iconFor(ComponentName("com.android.chrome", "com.google.android.apps.chrome.Main"), null))
        assertNotNull(chrome.bitmap)
        assertNull("Pure must keep its own artwork, not the app's system monochrome icon", chrome.monochrome)
        val pixels = chrome.bitmap.asAndroidBitmap()
        assertTrue((0 until pixels.height).any { y -> (0 until pixels.width).any { x -> Color.alpha(pixels.getPixel(x, y)) > 0 } })
        val a = requireNotNull(pack.iconFor(ComponentName("niva.unmapped", "niva.unmapped.Main"), android.graphics.drawable.ColorDrawable(Color.RED)))
        val b = requireNotNull(pack.iconFor(ComponentName("niva.unmapped", "niva.unmapped.Main"), android.graphics.drawable.ColorDrawable(Color.RED)))
        assertTrue(a.bitmap.asAndroidBitmap().sameAs(b.bitmap.asAndroidBitmap()))
    }

    @Test fun applyingPureReplacesAppIconsAndMissingPackRestoresOriginals() = runBlocking {
        val packs = IconPackRepository(context)
        assumeTrue(packs.installedPacks().any { it.packageName == PurePackage })
        val repository = AppRepository(context, packs)
        val system = repository.loadApps()
        val pure = repository.loadApps(PurePackage)
        val missing = repository.loadApps("niva.missing.iconpack")
        assertEquals(system.map { it.key }, pure.map { it.key })
        assertEquals(system.map { it.key }, missing.map { it.key })
        val matched = pure.filter { it.iconPackPackage == PurePackage }
        assertTrue("The installed Pure pack must actually replace icons", matched.size > 5)
        val chrome = matched.first { it.packageName == "com.android.chrome" }
        assertFalse(chrome.icon!!.asAndroidBitmap().sameAs(system.first { it.key == chrome.key }.icon!!.asAndroidBitmap()))
        assertTrue(missing.all { it.iconPackPackage == null })
        system.zip(missing).forEach { (original, fallback) ->
            if (original.icon != null) assertTrue(original.icon.asAndroidBitmap().sameAs(fallback.icon!!.asAndroidBitmap()))
        }
    }

    @Test fun orderedPacksUseLaterMatchesAndReversingChangesSharedIcons() = runBlocking {
        val packs = IconPackRepository(context)
        val installed = packs.installedPacks().map { it.packageName }
        assumeTrue("Install two packs to verify priority with real artwork", installed.size >= 2)
        val first = installed[0]
        val second = installed[1]
        val repository = AppRepository(context, packs)
        val firstOnly = repository.loadApps(first).associateBy { it.key }
        val secondOnly = repository.loadApps(second).associateBy { it.key }
        val combined = repository.loadApps(listOf("niva.missing.pack", first, second))
        val reversed = repository.loadApps(listOf(second, first)).associateBy { it.key }
        combined.forEach { app ->
            val firstApp = firstOnly.getValue(app.key)
            val secondApp = secondOnly.getValue(app.key)
            val expected = if (firstApp.iconPackPackage != null) firstApp else secondApp
            assertEquals(expected.iconPackPackage, app.iconPackPackage)
            if (expected.icon != null) assertTrue(expected.icon.asAndroidBitmap().sameAs(app.icon!!.asAndroidBitmap()))
        }
        val shared = firstOnly.values.filter { it.iconPackPackage != null && secondOnly.getValue(it.key).iconPackPackage != null }
        assertTrue("Both packs should cover some of the same installed apps", shared.isNotEmpty())
        shared.forEach { app -> assertEquals(second, reversed.getValue(app.key).iconPackPackage) }
        val loadedFirst = requireNotNull(packs.load(first))
        packs.load(second)
        assertSame("Loading another selected pack must not evict the previous pack", loadedFirst, packs.load(first))
    }

    companion object { const val PurePackage = "me.morirain.dev.iconpack.pure" }
}
