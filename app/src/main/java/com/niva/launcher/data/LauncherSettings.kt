package com.niva.launcher.data

enum class ThemeMode { System, Light, Dark }

enum class AppFont(val id: String?) {
    Default(null), System("system"), NotoSans("noto_sans"), Sacramento("sacramento"),
    Bokor("bokor"), Plaster("plaster"), Monoton("monoton"), LuckiestGuy("luckiest_guy"),
}

data class LauncherSettings(
    val clockEnabled: Boolean = true,
    val calendarAgenda: Boolean = true,
    val calendarAboveClock: Boolean = false,
    val showBatteryPercentage: Boolean = true,
    val allowHapticFeedback: Boolean = true,
    val useDynamicColors: Boolean = true,
    val themeColor: Int = 0xFF6750A4.toInt(),
    val darkMode: ThemeMode = ThemeMode.System,
    val amoledMode: Boolean = false,
    /** Retained for installations that used the original single-pack setting. */
    val iconPackPackage: String? = null,
    val iconPackPackages: List<String> = emptyList(),
    val iconDesign: ItemIcon? = null,
    val mediaPlayer: Boolean = true,
    val mediaAlwaysVisible: Boolean = false,
    val mediaAppKey: String? = null,
    val weatherEnabled: Boolean = false,
    val weatherForecastDays: Int = 7,
    val weatherLocationId: String? = null,
    /** null delegates to Android's standard SHOW_ALARMS action. */
    val clockAppKey: String? = null,
    val clockStyle: ClockStyle = ClockStyle(),
    val homeLayout: HomeLayout = HomeLayout(),
    val hideStatusBar: Boolean = true,
    val hideAlphabet: Boolean = false,
    val hideFavoriteNames: Boolean = false,
    val dimWallpaper: Boolean = false,
    val wallpaperDimAmount: Int = 20,
    val blurWallpaper: Boolean = true,
    val wallpaperBlurRadius: Int = 16,
    /** null keeps the original Josefin Sans; imported fonts use their private file id. */
    val appFontId: String? = null,
    val applyFontToSettings: Boolean = true,
    val privateSpace: ProfileSettings = ProfileSettings(),
    val workProfile: ProfileSettings = ProfileSettings.workDefaults(),
    val search: SearchSettings = SearchSettings(),
    val nivaButton: NivaButtonSettings = NivaButtonSettings(),
    val homeGestures: NivaButtonSettings = NivaButtonSettings.homeDefaults(),
) {
    /** Icon designer overrides precede this order; system icons always follow it. */
    val enabledIconPackPackages: List<String>
        get() = normalizeIconPackOrder(iconPackPackages.ifEmpty { listOfNotNull(iconPackPackage) })

    fun withIconPacks(packages: List<String>): LauncherSettings = copy(
        iconPackPackage = null,
        iconPackPackages = normalizeIconPackOrder(packages),
    )

    fun gestureSettings(home: Boolean): NivaButtonSettings = if (home) homeGestures else nivaButton
    fun withGestureSettings(home: Boolean, transform: (NivaButtonSettings) -> NivaButtonSettings): LauncherSettings =
        if (home) copy(homeGestures = transform(homeGestures)) else copy(nivaButton = transform(nivaButton))
}

enum class FolderPlacement(val inFavorites: Boolean, val inAppList: Boolean) {
    Favorites(true, false), AppList(false, true), Both(true, true), None(false, false);

    fun withFavorites(enabled: Boolean): FolderPlacement = of(enabled, inAppList)
    fun withAppList(enabled: Boolean): FolderPlacement = of(inFavorites, enabled)

    companion object {
        fun of(inFavorites: Boolean, inAppList: Boolean): FolderPlacement =
            entries.first { it.inFavorites == inFavorites && it.inAppList == inAppList }

        // Preserve the existing stored Favorites/AppList values on upgrade.
        fun decode(value: String): FolderPlacement = entries.firstOrNull { it.name == value } ?: AppList
    }
}

const val RecentlyInstalledFolderId = "recently-installed"

data class LauncherFolder(
    val id: String,
    val name: String,
    val appKeys: List<String>,
    val placement: FolderPlacement,
    /** Only affects the app list; favorites always use their shared manual order. */
    val appListAtBottom: Boolean = true,
) {
    val key: String get() = "folder:$id"

    /** Local identity only: folders must never be sent to Android as activities. */
    fun asApp() = LauncherApp(
        componentName = android.content.ComponentName("com.niva.launcher", key),
        label = name, icon = null, folderId = id,
    )
}

/** A complete first emission is available only after all Room tables have loaded. */
data class LauncherStorageSnapshot(
    val settings: LauncherSettings = LauncherSettings(),
    val hiddenAppKeys: Set<String> = emptySet(),
    val folders: List<LauncherFolder> = emptyList(),
)
