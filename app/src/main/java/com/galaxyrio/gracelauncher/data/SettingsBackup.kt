package com.galaxyrio.gracelauncher.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Settings backup/restore: serializes the launcher's user configuration to a
 * versioned JSON document and validates documents on import.
 *
 * Included: [LauncherSettings] (all fields), [LauncherPreferences] (appearance
 * prefs, Focus Mode selection, renames, categories), favorites order, hidden
 * apps, and folders (definitions only — widget instances are re-created by the
 * user because widget IDs are assigned by Android and cannot be restored).
 *
 * Deliberately excluded: icon bitmaps/caches, notification text, media state,
 * private-space authentication state, and any credential material.
 */
object SettingsBackup {
    const val FORMAT = "niva-backup"
    const val VERSION = 1

    /** Thrown when an imported document is not a usable backup. */
    class InvalidBackupException(message: String) : Exception(message)

    fun export(
        settings: LauncherSettings,
        preferences: BackupPreferences,
        favorites: List<String>,
        hiddenApps: Set<String>,
        folders: List<LauncherFolder>,
    ): String = JSONObject().apply {
        put("format", FORMAT)
        put("version", VERSION)
        put("exportedAt", System.currentTimeMillis())
        put("appId", "com.niva.launcher")
        put("settings", settingsToJson(settings))
        put("preferences", JSONObject().apply {
            put("text_mode", preferences.textMode?.name)
            put("themed_icons", preferences.themedIcons)
            put("focus_active", preferences.focusActive)
            put("focus_apps", JSONArray(preferences.focusApps.toList()))
            put("renames", JSONObject(preferences.renames))
            put("categories", JSONObject(preferences.categories))
        })
        put("favorites", JSONArray(favorites.filter(String::isNotBlank).distinct()))
        put("hiddenApps", JSONArray(hiddenApps.filter(String::isNotBlank).distinct().sorted()))
        put("folders", JSONArray(folders.map { folder ->
            JSONObject().apply {
                put("id", folder.id)
                put("name", folder.name)
                put("appKeys", JSONArray(folder.appKeys.filter(String::isNotBlank).distinct()))
                put("placement", folder.placement.name)
                put("appListAtBottom", folder.appListAtBottom)
            }
        }))
    }.toString()

    /** Parses and validates a backup document. Never throws anything but [InvalidBackupException]. */
    fun parse(raw: String): ParsedBackup = try {
        parseUnsafe(raw)
    } catch (e: InvalidBackupException) {
        throw e
    } catch (e: Exception) {
        throw InvalidBackupException("Backup file is not valid JSON: ${e.message}")
    }

    private fun parseUnsafe(raw: String): ParsedBackup {
        if (raw.length > 4 * 1024 * 1024) throw InvalidBackupException("Backup file is too large (max 4 MB).")
        val root = try {
            JSONObject(raw)
        } catch (e: Exception) {
            throw InvalidBackupException("Backup file is not valid JSON.")
        }
        if (root.optString("format") != FORMAT) throw InvalidBackupException("Not a Niva Launcher backup file.")
        val version = root.optInt("version", -1)
        if (version != VERSION) throw InvalidBackupException("Unsupported backup version: $version.")
        val settingsJson = root.optJSONObject("settings")
            ?: throw InvalidBackupException("Backup is missing its settings section.")
        val settings = settingsFromJson(settingsJson)
        val prefsJson = root.optJSONObject("preferences") ?: JSONObject()
        val favorites = root.optJSONArray("favorites")?.stringList().orEmpty()
        val hiddenApps = root.optJSONArray("hiddenApps")?.stringList().orEmpty().toSet()
        val foldersJson = root.optJSONArray("folders") ?: JSONArray()
        val folders = (0 until foldersJson.length()).mapNotNull { i ->
            val o = foldersJson.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            // Never allow reserved/system folder ids to be overwritten by a backup.
            if (id == RecentlyInstalledFolderId) return@mapNotNull null
            LauncherFolder(
                id = id.take(64),
                name = o.optString("name").takeIf { it.isNotBlank() }?.take(64) ?: id,
                appKeys = o.optJSONArray("appKeys")?.stringList().orEmpty().take(500),
                placement = FolderPlacement.entries.firstOrNull { it.name == o.optString("placement") }
                    ?: FolderPlacement.AppList,
                appListAtBottom = o.optBoolean("appListAtBottom", true),
            )
        }.take(100)
        return ParsedBackup(
            settings = settings,
            textMode = WallpaperTextMode.entries.firstOrNull { it.name == prefsJson.optString("text_mode") },
            themedIcons = prefsJson.optBoolean("themed_icons", true),
            focusActive = prefsJson.optBoolean("focus_active", false),
            focusApps = prefsJson.optJSONArray("focus_apps")?.stringList().orEmpty().toSet().take(500).toSet(),
            renames = prefsJson.optJSONObject("renames")?.stringMap().orEmpty(),
            categories = prefsJson.optJSONObject("categories")?.stringMap().orEmpty(),
            favorites = favorites.take(2000),
            hiddenApps = hiddenApps.take(2000).toSet(),
            folders = folders,
        )
    }

    private fun JSONArray.stringList(): List<String> =
        (0 until length()).mapNotNull { (opt(it) as? String)?.takeIf(String::isNotBlank)?.take(512) }

    private fun JSONObject.stringMap(): Map<String, String> {
        val out = mutableMapOf<String, String>()
        val it = keys()
        var count = 0
        while (it.hasNext() && count < 2000) {
            val k = it.next()
            (opt(k) as? String)?.takeIf(String::isNotBlank)?.let { v -> out[k.take(512)] = v.take(256) }
            count++
        }
        return out
    }

    internal fun settingsToJson(s: LauncherSettings): JSONObject = JSONObject().apply {
        put("clockEnabled", s.clockEnabled)
        put("calendarAgenda", s.calendarAgenda)
        put("calendarAboveClock", s.calendarAboveClock)
        put("showBatteryPercentage", s.showBatteryPercentage)
        put("allowHapticFeedback", s.allowHapticFeedback)
        put("useDynamicColors", s.useDynamicColors)
        put("themeColor", s.themeColor)
        put("darkMode", s.darkMode.name)
        put("amoledMode", s.amoledMode)
        put("iconPackPackage", s.iconPackPackage)
        put("iconPackPackages", JSONArray(s.iconPackPackages))
        put("iconDesign", s.iconDesign?.encode())
        put("mediaPlayer", s.mediaPlayer)
        put("mediaAlwaysVisible", s.mediaAlwaysVisible)
        put("mediaAppKey", s.mediaAppKey)
        put("weatherEnabled", s.weatherEnabled)
        put("weatherForecastDays", s.weatherForecastDays)
        put("weatherLocationId", s.weatherLocationId)
        put("clockAppKey", s.clockAppKey)
        put("clockStyle", s.clockStyle.encode())
        put("homeLayout", s.homeLayout.encode())
        put("hideStatusBar", s.hideStatusBar)
        put("hideAlphabet", s.hideAlphabet)
        put("hideFavoriteNames", s.hideFavoriteNames)
        put("dimWallpaper", s.dimWallpaper)
        put("wallpaperDimAmount", s.wallpaperDimAmount)
        put("blurWallpaper", s.blurWallpaper)
        put("wallpaperBlurRadius", s.wallpaperBlurRadius)
        put("appFontId", s.appFontId)
        put("applyFontToSettings", s.applyFontToSettings)
        put("privateSpace", s.privateSpace.encode())
        put("workProfile", s.workProfile.encode())
        put("search", s.search.encode())
        put("graceButton", s.graceButton.encode())
        put("homeGestures", s.homeGestures.encode())
    }

    internal fun settingsFromJson(o: JSONObject): LauncherSettings = LauncherSettings(
        clockEnabled = o.optBoolean("clockEnabled", true),
        calendarAgenda = o.optBoolean("calendarAgenda", true),
        calendarAboveClock = o.optBoolean("calendarAboveClock", false),
        showBatteryPercentage = o.optBoolean("showBatteryPercentage", true),
        allowHapticFeedback = o.optBoolean("allowHapticFeedback", true),
        useDynamicColors = o.optBoolean("useDynamicColors", true),
        themeColor = o.optInt("themeColor", 0xFF6750A4.toInt()),
        darkMode = ThemeMode.entries.firstOrNull { it.name == o.optString("darkMode") } ?: ThemeMode.System,
        amoledMode = o.optBoolean("amoledMode", false),
        iconPackPackage = o.optString("iconPackPackage").takeIf { it.isNotBlank() },
        iconPackPackages = o.optJSONArray("iconPackPackages")?.stringList().orEmpty(),
        iconDesign = o.optString("iconDesign").takeIf { it.isNotBlank() }?.let(ItemIcon::decode),
        mediaPlayer = o.optBoolean("mediaPlayer", true),
        mediaAlwaysVisible = o.optBoolean("mediaAlwaysVisible", false),
        mediaAppKey = o.optString("mediaAppKey").takeIf { it.isNotBlank() },
        weatherEnabled = o.optBoolean("weatherEnabled", false),
        weatherForecastDays = o.optInt("weatherForecastDays", 7).coerceIn(1, 14),
        weatherLocationId = o.optString("weatherLocationId").takeIf { it.isNotBlank() },
        clockAppKey = o.optString("clockAppKey").takeIf { it.isNotBlank() },
        clockStyle = ClockStyle.decode(o.optString("clockStyle").takeIf { it.isNotBlank() }),
        homeLayout = HomeLayout.decode(o.optString("homeLayout").takeIf { it.isNotBlank() }),
        hideStatusBar = o.optBoolean("hideStatusBar", true),
        hideAlphabet = o.optBoolean("hideAlphabet", false),
        hideFavoriteNames = o.optBoolean("hideFavoriteNames", false),
        dimWallpaper = o.optBoolean("dimWallpaper", false),
        wallpaperDimAmount = o.optInt("wallpaperDimAmount", 20).coerceIn(0, 100),
        blurWallpaper = o.optBoolean("blurWallpaper", true),
        wallpaperBlurRadius = o.optInt("wallpaperBlurRadius", 16).coerceIn(0, 48),
        appFontId = o.optString("appFontId").takeIf { it.isNotBlank() },
        applyFontToSettings = o.optBoolean("applyFontToSettings", true),
        privateSpace = ProfileSettings.decode(o.optString("privateSpace").takeIf { it.isNotBlank() }),
        workProfile = ProfileSettings.decode(
            o.optString("workProfile").takeIf { it.isNotBlank() }, ProfileSettings.workDefaults()),
        search = SearchSettings.decode(o.optString("search").takeIf { it.isNotBlank() }),
        graceButton = GraceButtonSettings.decode(o.optString("graceButton").takeIf { it.isNotBlank() }),
        homeGestures = GraceButtonSettings.decode(
            o.optString("homeGestures").takeIf { it.isNotBlank() }, GraceButtonSettings.homeDefaults()),
    )
}

/** A validated backup, ready to be applied to the live stores. */
data class ParsedBackup(
    val settings: LauncherSettings,
    val textMode: WallpaperTextMode?,
    val themedIcons: Boolean,
    val focusActive: Boolean,
    val focusApps: Set<String>,
    val renames: Map<String, String>,
    val categories: Map<String, String>,
    val favorites: List<String>,
    val hiddenApps: Set<String>,
    val folders: List<LauncherFolder>,
)

/** Plain preference values captured for backup export (testable without Android). */
data class BackupPreferences(
    val textMode: WallpaperTextMode?,
    val themedIcons: Boolean,
    val focusActive: Boolean,
    val focusApps: Set<String>,
    val renames: Map<String, String>,
    val categories: Map<String, String>,
)
