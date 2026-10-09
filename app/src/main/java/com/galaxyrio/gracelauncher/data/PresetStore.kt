package com.galaxyrio.gracelauncher.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Named appearance presets, stored as JSON in the app's private files dir.
 * A preset captures the appearance subset of [LauncherSettings] plus the
 * appearance-related [LauncherPreferences]; applying a preset only replaces
 * those appearance fields, never favorites, folders, hidden apps or Focus data.
 */
class PresetStore(context: Context) {
    private val file = File(context.filesDir, "appearance_presets.json")
    private val _presets = MutableStateFlow<List<AppearancePreset>>(emptyList())
    val presets: StateFlow<List<AppearancePreset>> = _presets.asStateFlow()

    init {
        _presets.value = runCatching { readAll() }.getOrDefault(emptyList())
    }

    suspend fun save(name: String, settings: LauncherSettings, preferences: LauncherPreferences): AppearancePreset =
        withContext(Dispatchers.IO) {
            val cleanName = name.trim().take(48).ifBlank { throw IllegalArgumentException("Preset name is blank") }
            val preset = AppearancePreset(
                id = UUID.randomUUID().toString(),
                name = cleanName,
                createdAt = System.currentTimeMillis(),
                payload = presetPayload(settings, preferences),
            )
            val updated = (_presets.value + preset).takeLast(50)
            writeAll(updated)
            _presets.value = updated
            preset
        }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val updated = _presets.value.filterNot { it.id == id }
        writeAll(updated)
        _presets.value = updated
    }

    /** Applies the preset's appearance fields onto [current], returning the merged settings. */
    fun applyTo(preset: AppearancePreset, current: LauncherSettings): LauncherSettings {
        val p = preset.payload
        return current.copy(
            useDynamicColors = p.useDynamicColors,
            themeColor = p.themeColor,
            darkMode = p.darkMode,
            amoledMode = p.amoledMode,
            iconPackPackages = p.iconPackPackages,
            iconDesign = p.iconDesign,
            clockStyle = p.clockStyle,
            clockEnabled = p.clockEnabled,
            appFontId = p.appFontId,
            applyFontToSettings = p.applyFontToSettings,
            homeLayout = p.homeLayout,
            hideStatusBar = p.hideStatusBar,
            hideAlphabet = p.hideAlphabet,
            hideFavoriteNames = p.hideFavoriteNames,
            dimWallpaper = p.dimWallpaper,
            wallpaperDimAmount = p.wallpaperDimAmount,
            blurWallpaper = p.blurWallpaper,
            wallpaperBlurRadius = p.wallpaperBlurRadius,
            calendarAgenda = p.calendarAgenda,
            calendarAboveClock = p.calendarAboveClock,
            showBatteryPercentage = p.showBatteryPercentage,
        )
    }

    /** Applies the preset's preference fields. Returns (textMode, themedIcons). */
    fun applyPreferences(preset: AppearancePreset): Pair<WallpaperTextMode?, Boolean> =
        preset.payload.textMode to preset.payload.themedIcons

    private fun presetPayload(settings: LauncherSettings, preferences: LauncherPreferences): PresetPayload =
        PresetPayload(
            useDynamicColors = settings.useDynamicColors,
            themeColor = settings.themeColor,
            darkMode = settings.darkMode,
            amoledMode = settings.amoledMode,
            iconPackPackages = settings.iconPackPackages,
            iconDesign = settings.iconDesign,
            clockStyle = settings.clockStyle,
            clockEnabled = settings.clockEnabled,
            appFontId = settings.appFontId,
            applyFontToSettings = settings.applyFontToSettings,
            homeLayout = settings.homeLayout,
            hideStatusBar = settings.hideStatusBar,
            hideAlphabet = settings.hideAlphabet,
            hideFavoriteNames = settings.hideFavoriteNames,
            dimWallpaper = settings.dimWallpaper,
            wallpaperDimAmount = settings.wallpaperDimAmount,
            blurWallpaper = settings.blurWallpaper,
            wallpaperBlurRadius = settings.wallpaperBlurRadius,
            calendarAgenda = settings.calendarAgenda,
            calendarAboveClock = settings.calendarAboveClock,
            showBatteryPercentage = settings.showBatteryPercentage,
            textMode = preferences.textMode,
            themedIcons = preferences.themedIcons,
        )

    private fun readAll(): List<AppearancePreset> {
        if (!file.exists()) return emptyList()
        val text = file.readText().take(1024 * 1024)
        val array = JSONArray(text)
        return (0 until array.length()).mapNotNull { i ->
            runCatching { parsePreset(array.getJSONObject(i)) }.getOrNull()
        }.take(50)
    }

    private fun writeAll(presets: List<AppearancePreset>) {
        val array = JSONArray()
        presets.forEach { array.put(presetToJson(it)) }
        file.writeText(array.toString())
    }

    private fun presetToJson(p: AppearancePreset): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("createdAt", p.createdAt)
        put("payload", payloadToJson(p.payload))
    }

    private fun parsePreset(o: JSONObject): AppearancePreset {
        val id = o.optString("id").takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("missing id")
        val name = o.optString("name").takeIf { it.isNotBlank() }?.take(48)
            ?: throw IllegalArgumentException("missing name")
        return AppearancePreset(
            id = id.take(64),
            name = name,
            createdAt = o.optLong("createdAt", 0L),
            payload = parsePayload(o.optJSONObject("payload") ?: JSONObject()),
        )
    }

    private fun payloadToJson(p: PresetPayload): JSONObject = JSONObject().apply {
        put("useDynamicColors", p.useDynamicColors)
        put("themeColor", p.themeColor)
        put("darkMode", p.darkMode.name)
        put("amoledMode", p.amoledMode)
        put("iconPackPackages", JSONArray(p.iconPackPackages))
        put("iconDesign", p.iconDesign?.encode())
        put("clockStyle", p.clockStyle.encode())
        put("clockEnabled", p.clockEnabled)
        put("appFontId", p.appFontId)
        put("applyFontToSettings", p.applyFontToSettings)
        put("homeLayout", p.homeLayout.encode())
        put("hideStatusBar", p.hideStatusBar)
        put("hideAlphabet", p.hideAlphabet)
        put("hideFavoriteNames", p.hideFavoriteNames)
        put("dimWallpaper", p.dimWallpaper)
        put("wallpaperDimAmount", p.wallpaperDimAmount)
        put("blurWallpaper", p.blurWallpaper)
        put("wallpaperBlurRadius", p.wallpaperBlurRadius)
        put("calendarAgenda", p.calendarAgenda)
        put("calendarAboveClock", p.calendarAboveClock)
        put("showBatteryPercentage", p.showBatteryPercentage)
        put("textMode", p.textMode.name)
        put("themedIcons", p.themedIcons)
    }

    private fun parsePayload(o: JSONObject): PresetPayload = PresetPayload(
        useDynamicColors = o.optBoolean("useDynamicColors", true),
        themeColor = o.optInt("themeColor", 0xFF6750A4.toInt()),
        darkMode = ThemeMode.entries.firstOrNull { it.name == o.optString("darkMode") } ?: ThemeMode.System,
        amoledMode = o.optBoolean("amoledMode", false),
        iconPackPackages = (0 until (o.optJSONArray("iconPackPackages")?.length() ?: 0))
            .mapNotNull { (o.optJSONArray("iconPackPackages")?.opt(it) as? String)?.takeIf(String::isNotBlank) },
        iconDesign = o.optString("iconDesign").takeIf { it.isNotBlank() }?.let(ItemIcon::decode),
        clockStyle = ClockStyle.decode(o.optString("clockStyle").takeIf { it.isNotBlank() }),
        clockEnabled = o.optBoolean("clockEnabled", true),
        appFontId = o.optString("appFontId").takeIf { it.isNotBlank() },
        applyFontToSettings = o.optBoolean("applyFontToSettings", true),
        homeLayout = HomeLayout.decode(o.optString("homeLayout").takeIf { it.isNotBlank() }),
        hideStatusBar = o.optBoolean("hideStatusBar", true),
        hideAlphabet = o.optBoolean("hideAlphabet", false),
        hideFavoriteNames = o.optBoolean("hideFavoriteNames", false),
        dimWallpaper = o.optBoolean("dimWallpaper", false),
        wallpaperDimAmount = o.optInt("wallpaperDimAmount", 20).coerceIn(0, 100),
        blurWallpaper = o.optBoolean("blurWallpaper", true),
        wallpaperBlurRadius = o.optInt("wallpaperBlurRadius", 16).coerceIn(0, 48),
        calendarAgenda = o.optBoolean("calendarAgenda", true),
        calendarAboveClock = o.optBoolean("calendarAboveClock", false),
        showBatteryPercentage = o.optBoolean("showBatteryPercentage", true),
        textMode = WallpaperTextMode.entries.firstOrNull { it.name == o.optString("textMode") }
            ?: WallpaperTextMode.Auto,
        themedIcons = o.optBoolean("themedIcons", true),
    )
}

data class AppearancePreset(
    val id: String,
    val name: String,
    val createdAt: Long,
    val payload: PresetPayload,
)

data class PresetPayload(
    val useDynamicColors: Boolean,
    val themeColor: Int,
    val darkMode: ThemeMode,
    val amoledMode: Boolean,
    val iconPackPackages: List<String>,
    val iconDesign: ItemIcon?,
    val clockStyle: ClockStyle,
    val clockEnabled: Boolean,
    val appFontId: String?,
    val applyFontToSettings: Boolean,
    val homeLayout: HomeLayout,
    val hideStatusBar: Boolean,
    val hideAlphabet: Boolean,
    val hideFavoriteNames: Boolean,
    val dimWallpaper: Boolean,
    val wallpaperDimAmount: Int,
    val blurWallpaper: Boolean,
    val wallpaperBlurRadius: Int,
    val calendarAgenda: Boolean,
    val calendarAboveClock: Boolean,
    val showBatteryPercentage: Boolean,
    val textMode: WallpaperTextMode,
    val themedIcons: Boolean,
)
