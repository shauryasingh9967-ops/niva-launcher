package com.niva.launcher.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.niva.launcher.R
import com.niva.launcher.data.AppFont
import com.niva.launcher.data.ClockFontStore
import com.niva.launcher.data.ClockPresetFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.fonts.Font as PlatformFont
import android.graphics.fonts.FontFamily as PlatformFontFamily
import android.graphics.fonts.FontStyle as PlatformFontStyle

/**
 * Josefin Sans first, with bundled Noto fallbacks on Android 10+ and system CJK fonts.
 * Separate weight descriptors preserve real variable-font weights: wrapping a single
 * Android Typeface in Compose FontFamily would ignore subsequent TextStyle weights.
 */
val LauncherFontFamily: FontFamily = FontFamily(
    (100..700 step 100).map { weight -> LauncherFont(FontWeight(weight)) },
)

// The app and clock share the same bundled font families, not separate font buffers.
private val presetFamilies = mapOf(
    ClockPresetFont.Sacramento to FontFamily(Font(R.font.sacramento)),
    ClockPresetFont.Bokor to FontFamily(Font(R.font.bokor)),
    ClockPresetFont.Plaster to FontFamily(Font(R.font.plaster)),
    ClockPresetFont.Monoton to FontFamily(Font(R.font.monoton)),
    ClockPresetFont.LuckiestGuy to FontFamily(Font(R.font.luckiest_guy)),
)

internal fun ClockPresetFont.family(): FontFamily = presetFamilies.getValue(this)

@Composable
internal fun rememberAppFontFamily(id: String?): FontFamily = remember(id) {
    when (id) {
        null -> LauncherFontFamily
        AppFont.System.id -> FontFamily.Default
        AppFont.Sacramento.id -> ClockPresetFont.Sacramento.family()
        AppFont.Bokor.id -> ClockPresetFont.Bokor.family()
        AppFont.Plaster.id -> ClockPresetFont.Plaster.family()
        AppFont.Monoton.id -> ClockPresetFont.Monoton.family()
        AppFont.LuckiestGuy.id -> ClockPresetFont.LuckiestGuy.family()
        else -> if (id == AppFont.NotoSans.id || ClockFontStore.displayName(id) != null) {
            FontFamily((100..900 step 100).map { SelectedFont(id, FontWeight(it)) })
        } else LauncherFontFamily
    }
}

/** Async per-weight faces keep imported variable fonts responsive without losing bold roles. */
private data class SelectedFont(val id: String, override val weight: FontWeight) : AndroidFont(
    loadingStrategy = FontLoadingStrategy.Async,
    typefaceLoader = SelectedTypefaceLoader,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
) {
    override val style: FontStyle = FontStyle.Normal
}

private object SelectedTypefaceLoader : AndroidFont.TypefaceLoader {
    private val notoFaces = mutableMapOf<Int, Typeface>()

    override fun loadBlocking(context: Context, font: AndroidFont): Typeface? = null

    override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface = withContext(Dispatchers.IO) {
        val choice = font as SelectedFont
        val weight = choice.weight.weight
        if (choice.id == AppFont.NotoSans.id) synchronized(notoFaces) {
            notoFaces.getOrPut(weight) {
                Typeface.Builder(context.assets, "fonts/noto_sans.ttf")
                    .setFontVariationSettings("'wght' $weight").setWeight(weight)
                    .setItalic(false).setFallback("sans-serif").build()
                    ?: launcherTypeface(context, weight.coerceIn(100, 700))
            }
        } else ClockFontStore(context).typeface(choice.id, weight)
            ?: launcherTypeface(context, weight.coerceIn(100, 700))
    }
}

private data class LauncherFont(
    override val weight: FontWeight,
) : AndroidFont(
    loadingStrategy = FontLoadingStrategy.Blocking,
    typefaceLoader = LauncherTypefaceLoader,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
) {
    override val style: FontStyle = FontStyle.Normal
}

private object LauncherTypefaceLoader : AndroidFont.TypefaceLoader {
    override fun loadBlocking(context: Context, font: AndroidFont): Typeface =
        launcherTypeface(context, font.weight.weight)

    override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface =
        loadBlocking(context, font)
}

private const val PRIMARY_FONT_ASSET = "fonts/josefin_sans.ttf"

private val typefaces = mutableMapOf<Int, Typeface>()

/**
 * Returns a cached real weight, shared by every launcher text surface.
 *
 * API 28 has no public custom per-glyph fallback-chain builder. It keeps the same
 * Josefin Sans variable weight and the device's sans-serif glyph fallback, which is not
 * guaranteed to be our bundled Noto files on OEM devices. No hidden APIs are used.
 *
 * Chinese, Japanese and Korean use Android's system sans-serif fallback.
 * Keep the text locale supplied by Compose so Android selects regional glyphs.
 */
internal fun launcherTypeface(context: Context, weight: Int): Typeface {
    require(weight in 100..700) { "Josefin Sans supports weights between 100 and 700" }
    return synchronized(typefaces) {
        typefaces.getOrPut(weight) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                NotoFallbackApi29.build(context, weight)
            } else {
                checkNotNull(
                    Typeface.Builder(context.assets, PRIMARY_FONT_ASSET)
                        .setFontVariationSettings("'wght' $weight")
                        .setWeight(weight)
                        .setItalic(false)
                        .setFallback("sans-serif")
                        .build(),
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
private object NotoFallbackApi29 {
    private val fallbackAssets = listOf(
        "fonts/noto_sans.ttf",
        "fonts/noto_sans_arabic.ttf",
        "fonts/noto_sans_hebrew.ttf",
        "fonts/noto_sans_devanagari.ttf",
        "fonts/noto_sans_thai.ttf",
    )

    // Retain each bundled font's native backing buffer once. All weight
    // instances reuse that buffer instead of re-reading files.
    // Access is serialized by the outer typeface cache lock.
    private val baseFonts = mutableMapOf<String, PlatformFont>()

    private fun family(context: Context, asset: String, weight: Int): PlatformFontFamily {
        val base = baseFonts.getOrPut(asset) {
            PlatformFont.Builder(context.assets, asset).build()
        }
        // The Font copy-builder needs API 31. The public buffer constructor also
        // shares font data and works on every API level supporting custom fallback.
        val font = PlatformFont.Builder(base.buffer)
            .setTtcIndex(base.ttcIndex)
            .setFontVariationSettings("'wght' $weight")
            .setWeight(weight)
            .setSlant(PlatformFontStyle.FONT_SLANT_UPRIGHT)
            .build()
        return PlatformFontFamily.Builder(font).build()
    }

    fun build(context: Context, weight: Int): Typeface {
        val builder = Typeface.CustomFallbackBuilder(family(context, PRIMARY_FONT_ASSET, weight))
        fallbackAssets.forEach { asset ->
            builder.addCustomFallback(family(context, asset, weight))
        }
        return builder
            .setStyle(PlatformFontStyle(weight, PlatformFontStyle.FONT_SLANT_UPRIGHT))
            // CJK, emoji and scripts beyond the packaged Noto families use the OS.
            .setSystemFallback("sans-serif")
            .build()
    }
}
