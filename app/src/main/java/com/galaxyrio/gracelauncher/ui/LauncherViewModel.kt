package com.galaxyrio.gracelauncher.ui

import android.Manifest
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.net.Uri
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.os.Process
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.galaxyrio.gracelauncher.data.AppRepository
import com.galaxyrio.gracelauncher.data.CalendarRepository
import com.galaxyrio.gracelauncher.data.FavoritesStore
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.LauncherAppOrder
import com.galaxyrio.gracelauncher.data.LauncherDatabase
import com.galaxyrio.gracelauncher.data.LauncherFolder
import com.galaxyrio.gracelauncher.data.FolderPlacement
import com.galaxyrio.gracelauncher.data.LauncherPreferences
import com.galaxyrio.gracelauncher.data.isFocusHidden
import com.galaxyrio.gracelauncher.data.LauncherSettings
import com.galaxyrio.gracelauncher.data.ClockStyle
import com.galaxyrio.gracelauncher.data.LauncherSettingsRepository
import com.galaxyrio.gracelauncher.data.LauncherShortcut
import com.galaxyrio.gracelauncher.data.ScheduleEvent
import com.galaxyrio.gracelauncher.data.ShortcutRepository
import com.galaxyrio.gracelauncher.data.WallpaperTextMode
import com.galaxyrio.gracelauncher.data.icons.IconPackInfo
import com.galaxyrio.gracelauncher.data.icons.IconPackRepository
import com.galaxyrio.gracelauncher.data.icons.IconPackStatus
import com.galaxyrio.gracelauncher.data.icons.ItemIconStore
import com.galaxyrio.gracelauncher.data.icons.GraceButtonIcon
import com.galaxyrio.gracelauncher.data.ItemIcon
import com.galaxyrio.gracelauncher.data.IconDesign
import com.galaxyrio.gracelauncher.data.PopupItem
import com.galaxyrio.gracelauncher.data.LauncherItemsRepository
import com.galaxyrio.gracelauncher.data.LauncherItemsSnapshot
import com.galaxyrio.gracelauncher.data.PrivateSpaceFolderId
import com.galaxyrio.gracelauncher.data.RecentlyInstalledFolderId
import com.galaxyrio.gracelauncher.data.PrivateSpaceFolderKey
import com.galaxyrio.gracelauncher.data.PrivateSpaceDefaultName
import com.galaxyrio.gracelauncher.data.PrivateSpaceDisplay
import com.galaxyrio.gracelauncher.data.WorkProfile
import com.galaxyrio.gracelauncher.data.WorkProfileFolderId
import com.galaxyrio.gracelauncher.data.WorkProfileFolderKey
import com.galaxyrio.gracelauncher.data.WorkProfileDefaultName
import com.galaxyrio.gracelauncher.data.PrivateAppCache
import com.galaxyrio.gracelauncher.platform.PrivateSpaceController
import com.galaxyrio.gracelauncher.platform.PrivateSpaceState
import com.galaxyrio.gracelauncher.platform.DefaultHome
import com.galaxyrio.gracelauncher.platform.BreezyWeatherUpdates
import com.galaxyrio.gracelauncher.data.media.MediaCommand
import com.galaxyrio.gracelauncher.data.media.MediaSessionRepository
import com.galaxyrio.gracelauncher.data.media.MediaSnapshot
import com.galaxyrio.gracelauncher.data.media.NowPlaying
import com.galaxyrio.gracelauncher.data.notifications.AppNotification
import com.galaxyrio.gracelauncher.data.notifications.appNotifications
import com.galaxyrio.gracelauncher.data.weather.BreezyWeatherRepository
import com.galaxyrio.gracelauncher.data.weather.WeatherState
import com.galaxyrio.gracelauncher.data.weather.WeatherStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ScheduleStatus {
    PermissionRequired,
    Loading,
    Ready,
    Error,
}

data class LauncherUiState(
    val apps: List<LauncherApp> = emptyList(),
    val favoriteKeys: Set<String> = emptySet(),
    val isLoadingApps: Boolean = true,
    val appLoadFailed: Boolean = false,
    val events: List<ScheduleEvent> = emptyList(),
    val scheduleStatus: ScheduleStatus = ScheduleStatus.PermissionRequired,
    val hasShortcutAccess: Boolean = false,
    val categories: Map<String, String> = emptyMap(),
    val textMode: WallpaperTextMode = WallpaperTextMode.Auto,
    val themedIcons: Boolean = true,
    val settings: LauncherSettings = LauncherSettings(),
    val hiddenAppKeys: Set<String> = emptySet(),
    val focusActive: Boolean = false,
    val focusAppKeys: Set<String> = emptySet(),
    val folders: List<LauncherFolder> = emptyList(),
    val isLoadingSettings: Boolean = false,
    val settingsLoadFailed: Boolean = false,
    val settingsSaveFailed: Boolean = false,
    val iconPacks: List<IconPackInfo> = emptyList(),
    val isLoadingIconPacks: Boolean = false,
    val iconPacksLoadFailed: Boolean = false,
    val iconPackStatus: IconPackStatus = IconPackStatus.System,
    // Unknown until the first system query, avoiding a banner flash for an existing default.
    val isDefaultHome: Boolean? = null,
    val media: MediaSnapshot = MediaSnapshot(),
    val notifications: Map<String, List<AppNotification>> = emptyMap(),
    val favoriteOrder: List<String> = emptyList(),
    val weather: WeatherState = WeatherState(),
    val shortcutApps: List<LauncherApp> = emptyList(),
    val folderApps: List<LauncherApp> = emptyList(),
    val graceButtonApp: LauncherApp? = null,
    val popups: Map<String, List<PopupItem>> = emptyMap(),
    val itemIcons: Map<String, ItemIcon> = emptyMap(),
    val itemRevision: Int = 0,
    val privateSpace: PrivateSpaceState = PrivateSpaceState(),
    val privateApps: List<LauncherApp> = emptyList(),
    val privateAppsLoading: Boolean = false,
    val privateAppsFailed: Boolean = false,
    val privateFolderApp: LauncherApp? = null,
    val workProfiles: List<WorkProfile> = emptyList(),
    val workFolderApp: LauncherApp? = null,
    val shownShortcutKeys: Set<String> = emptySet(),
) {
    fun findItem(key: String): LauncherApp? = apps.firstOrNull { it.key == key }
        ?: shortcutApps.firstOrNull { it.key == key && (!it.isPrivateSpace || privateContentVisible) }
            ?.let { if (it.isPrivateSpace) it.copy(showPrivateIndicator = settings.privateSpace.showIndicator) else it }
        ?: privateSpaceApps.firstOrNull { it.key == key }
        ?: privateFolder.takeIf { it.key == key }?.let(::folderItem)
        ?: workFolder.takeIf { it.key == key }?.let(::folderItem)
        ?: folders.firstOrNull { it.key == key }?.let(::folderItem)
        ?: graceButtonApp?.takeIf { it.key == key }

    val privateFolder: LauncherFolder get() = settings.privateSpace.folder(privateSpaceApps)
    val workProfileApps: List<LauncherApp> get() = settings.workProfile.orderedApps(apps.filter { it.isWorkProfile && it.shortcut == null })
    val workFolder: LauncherFolder get() = settings.workProfile.folder(workProfileApps, WorkProfileFolderId)
    val privateContentVisible: Boolean get() = !isLoadingSettings && !settingsLoadFailed && settings.privateSpace.enabled &&
        privateSpace.user != null && (settings.privateSpace.exposesApps || (privateSpace.accessible && !privateSpace.locked))
    val privateSpaceApps: List<LauncherApp> get() = if (privateContentVisible)
        settings.privateSpace.orderedApps(privateApps).map { it.copy(showPrivateIndicator = settings.privateSpace.showIndicator) } else emptyList()
    private val publicPrivateShortcuts: List<LauncherApp> get() = if (settings.privateSpace.exposesApps && privateContentVisible)
        shortcutApps.filter { it.isPrivateSpace && it.key in shownShortcutKeys }.map { it.copy(showPrivateIndicator = settings.privateSpace.showIndicator) } else emptyList()
    /** Public surfaces include private apps only after the user opts out of launcher protection. */
    val allApps: List<LauncherApp> get() = (apps + (if (settings.privateSpace.exposesApps) privateSpaceApps else emptyList()) + publicPrivateShortcuts)
        .distinctBy(LauncherApp::key).sortedWith(LauncherAppOrder)

    // Hidden apps remain available in folders. Profile-protected apps and saved
    // shortcuts are not installations in this personal-profile collection.
    val recentlyInstalledApps: List<LauncherApp> get() = apps.asSequence()
        .filter { it.shortcut == null && it.folderId == null && !it.isPrivateSpace && !it.isLauncherSettings }
        .sortedWith(compareByDescending<LauncherApp> { it.firstInstallTime }.then(LauncherAppOrder))
        .distinctBy { it.userSerial to it.packageName }.take(8).toList()

    fun recentlyInstalledFolder(name: String) = LauncherFolder(
        RecentlyInstalledFolderId, name, recentlyInstalledApps.map(LauncherApp::key), FolderPlacement.AppList,
    )

    fun folderItem(folder: LauncherFolder): LauncherApp = ((if (folder.id == PrivateSpaceFolderId) privateFolderApp
        else if (folder.id == WorkProfileFolderId) workFolderApp
        else folderApps.firstOrNull { it.folderId == folder.id })
        ?: folder.asApp()).copy(label = folder.name, originalLabel = folder.name)

    fun popupItems(owner: LauncherApp, defaults: List<LauncherApp>, respectFocus: Boolean = true): List<PopupItem> {
        if (owner.folderId == RecentlyInstalledFolderId) return recentlyInstalledApps.map { PopupItem(it.key) }
            .filterNot { respectFocus && isFocusHidden(it.key, focusActive, focusAppKeys) }
        if (owner.folderId == WorkProfileFolderId) return workProfileApps.map { PopupItem(it.key) }
            .filterNot { respectFocus && isFocusHidden(it.key, focusActive, focusAppKeys) }
        val items = popups[owner.key].takeUnless { owner.folderId == PrivateSpaceFolderId }
            ?: (if (owner.folderId == PrivateSpaceFolderId) privateSpaceApps.map { it.key }
                else if (owner.folderId == null) defaults.map { it.key }
                else folders.firstOrNull { it.id == owner.folderId }?.appKeys.orEmpty()).distinct().map { PopupItem(it) }
        val filtered = if (owner.folderId != PrivateSpaceFolderId && !settings.privateSpace.exposesApps)
            items.filterNot { it.key.startsWith("profile:") && findItem(it.key)?.isWorkProfile != true } else items
        return if (respectFocus) filtered.filterNot { isFocusHidden(it.key, focusActive, focusAppKeys) } else filtered
    }
    val homeMedia: NowPlaying?
        get() = media.nowPlaying.takeIf {
            settings.mediaPlayer && media.hasAccess && !isLoadingSettings && !settingsLoadFailed
        }

    /** Hide Apps affects only the alphabetical app list, not other launcher surfaces. */
    val appListApps: List<LauncherApp>
        get() = if (isLoadingSettings || settingsLoadFailed) emptyList()
        else (if (settings.privateSpace.display == PrivateSpaceDisplay.NormalApp) allApps else apps + publicPrivateShortcuts)
            .filterNot { it.key in hiddenAppKeys }
            .filterNot { isFocusHidden(it.key, focusActive, focusAppKeys) }
            .filterNot { it.isWorkProfile && it.shortcut == null && settings.workProfile.enabled &&
                settings.workProfile.display != PrivateSpaceDisplay.NormalApp }

    val favoriteApps: List<LauncherApp>
        get() {
            if (isLoadingSettings || settingsLoadFailed) return emptyList()
            val favorites = (allApps + shortcutApps.filter { !it.isPrivateSpace || (settings.privateSpace.exposesApps && privateContentVisible) })
                .filter { it.key in favoriteKeys }.associateBy(LauncherApp::key)
            return (favoriteOrder + favorites.keys).distinct()
                .filterNot { isFocusHidden(it, focusActive, focusAppKeys) }.mapNotNull(favorites::get)
        }

    /** Folder placement owns membership; the shared order only determines position. */
    val favoriteItems: List<LauncherApp>
        get() {
            if (isLoadingSettings || settingsLoadFailed) return emptyList()
            val items = (favoriteApps + folders.filter { it.placement.inFavorites }.map(::folderItem))
                .associateBy(LauncherApp::key)
            // Existing folders keep their old position at the end until reordered.
            return (favoriteOrder + items.keys).distinct().mapNotNull(items::get)
        }
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val iconPacks = IconPackRepository(application)
    private val appRepository = AppRepository(application, iconPacks)
    private val privateAppCache = PrivateAppCache(application)
    private var privateRawApps: List<LauncherApp> = emptyList()
    private var privateRawSerial: Long = -1
    val privateSpaceController = PrivateSpaceController.get(application)
    private val calendarRepository = CalendarRepository(application)
    private val favoritesStore = FavoritesStore(application)
    private val preferences = LauncherPreferences(application)
    private val shortcutRepository = ShortcutRepository(application)
    private val settingsRepository = LauncherSettingsRepository(LauncherDatabase.getInstance(application))
    private val itemsRepository = LauncherItemsRepository(LauncherDatabase.getInstance(application))
    private val itemIcons = ItemIconStore(application, iconPacks)
    private var itemsSnapshot = LauncherItemsSnapshot()
    private var itemsReady = false
    private val mediaRepository = MediaSessionRepository(application)
    private val weatherRepository = BreezyWeatherRepository(application)
    private val settingsWriteMutex = Mutex()

    private val _uiState = MutableStateFlow(LauncherUiState(
        categories = preferences.categories(),
        textMode = preferences.textMode,
        themedIcons = preferences.themedIcons,
        focusActive = preferences.focusActive,
        focusAppKeys = preferences.focusAppKeys,
        graceButtonApp = GraceButtonIcon.item(application),
        isLoadingSettings = true,
    ))
    val uiState = _uiState.asStateFlow()

    private val _returnHomeRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val returnHomeRequests = _returnHomeRequests.asSharedFlow()

    private var appLoadJob: Job? = null
    private var privateAppLoadJob: Job? = null
    private var scheduleLoadJob: Job? = null
    private var weatherLoadJob: Job? = null
    private var lastWeatherRefresh = 0L
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_PACKAGE_REMOVED && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
            refreshApps()
        }
    }
    private val dateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { refreshApps() }
    }
    private val launcherApps = application.getSystemService(LauncherApps::class.java)
    private val profilePackages = object : LauncherApps.Callback() {
        private fun refresh(user: UserHandle) { if (user != Process.myUserHandle()) refreshApps() }
        override fun onPackageAdded(packageName: String, user: UserHandle) = refresh(user)
        override fun onPackageRemoved(packageName: String, user: UserHandle) = refresh(user)
        override fun onPackageChanged(packageName: String, user: UserHandle) = refresh(user)
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh(user)
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh(user)
    }

    init {
        launcherApps.registerCallback(profilePackages)
        viewModelScope.launch {
            privateSpaceController.state.collect { state ->
                val previous = _uiState.value.privateSpace
                val sameProfile = previous.user == state.user && previous.serial == state.serial
                _uiState.update { it.copy(privateSpace = state,
                    privateApps = if (sameProfile) it.privateApps else emptyList(),
                    shortcutApps = if (sameProfile) it.shortcutApps else it.shortcutApps.filterNot(LauncherApp::isPrivateSpace)) }
                if (!sameProfile || previous.accessible != state.accessible ||
                    previous.locked != state.locked || previous.revision != state.revision) refreshPrivateApps()
            }
        }
        viewModelScope.launch {
            itemsRepository.snapshots.catch { error ->
                if (error is CancellationException) throw error
                Log.e("LauncherViewModel", "Unable to read launcher items", error)
                _uiState.update { it.copy(settingsLoadFailed = true) }
            }.collect { snapshot ->
                val reload = !itemsReady || snapshot.icons != itemsSnapshot.icons || snapshot.shortcuts != itemsSnapshot.shortcuts
                itemsReady = true
                itemsSnapshot = snapshot
                _uiState.update { it.copy(popups = snapshot.popups, itemIcons = snapshot.icons,
                    shownShortcutKeys = snapshot.shortcuts.filter { saved -> saved.showInAppList }.map { saved -> saved.itemKey }.toSet(),
                    itemRevision = it.itemRevision + 1) }
                if (reload) refreshApps()
            }
        }
        viewModelScope.launch {
            BreezyWeatherUpdates.events.collectLatest {
                // Refresh after the final signal, so a forecast update following
                // a current-conditions update is never lost to a leading-edge throttle.
                delay(500)
                refreshWeather()
            }
        }
        viewModelScope.launch {
            appNotifications.state.collect { notifications -> _uiState.update { it.copy(notifications = notifications) } }
        }
        viewModelScope.launch {
            mediaRepository.state.collect { media -> _uiState.update { it.copy(media = media) } }
        }
        ContextCompat.registerReceiver(application, packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(application, dateReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_WALLPAPER_CHANGED)
            addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
            addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
            addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        viewModelScope.launch {
            settingsRepository.snapshots
                .catch { error ->
                    if (error is CancellationException) throw error
                    Log.e("LauncherViewModel", "Unable to read launcher settings", error)
                    _uiState.update { it.copy(isLoadingSettings = false, settingsLoadFailed = true) }
                    mediaRepository.setEnabled(false)
                    refreshWeather()
                }
                .collect { snapshot ->
                    val previous = _uiState.value
                    privateSpaceController.configure(snapshot.settings.privateSpace)
                    _uiState.update {
                        it.copy(
                            settings = snapshot.settings,
                            apps = it.apps.map { app -> if (app.isWorkProfile) app.copy(showWorkIndicator = snapshot.settings.workProfile.showIndicator) else app },
                            shortcutApps = it.shortcutApps.map { app -> if (app.isWorkProfile) app.copy(showWorkIndicator = snapshot.settings.workProfile.showIndicator) else app },
                            themedIcons = snapshot.settings.iconDesign?.design?.withThemeDefaults(IconDesign.defaults(preferences.themedIcons))?.themeIcons
                                ?: preferences.themedIcons,
                            hiddenAppKeys = snapshot.hiddenAppKeys,
                            folders = snapshot.folders,
                            isLoadingSettings = false,
                            settingsLoadFailed = false,
                        )
                    }
                    if (previous.isLoadingSettings ||
                        previous.settings.calendarAgenda != snapshot.settings.calendarAgenda
                    ) refreshSchedule()
                    if (previous.isLoadingSettings || previous.settings.mediaPlayer != snapshot.settings.mediaPlayer) {
                        mediaRepository.setEnabled(snapshot.settings.mediaPlayer)
                    }
                    if (previous.isLoadingSettings || previous.settings.enabledIconPackPackages != snapshot.settings.enabledIconPackPackages ||
                        previous.settings.iconDesign != snapshot.settings.iconDesign ||
                        previous.settings.darkMode != snapshot.settings.darkMode ||
                        previous.settings.useDynamicColors != snapshot.settings.useDynamicColors ||
                        previous.settings.themeColor != snapshot.settings.themeColor) refreshApps()
                    if (previous.settings.privateSpace != snapshot.settings.privateSpace) refreshPrivateApps()
                    if (previous.isLoadingSettings || previous.settings.weatherEnabled != snapshot.settings.weatherEnabled ||
                        previous.settings.weatherLocationId != snapshot.settings.weatherLocationId
                    ) refreshWeather()
                    if (previous.isLoadingSettings) {
                        val favorites = _uiState.value.favoriteApps
                        viewModelScope.launch { shortcutRepository.prefetch(favorites) }
                    }
                }
        }
        refreshApps()
    }

    fun refreshApps() {
        mediaRepository.refresh()
        refreshWeather()
        // Both HOME and the standalone settings Activity call this on resume;
        // also refresh after the role request, including cancellation.
        val isDefaultHome = DefaultHome.isDefault(getApplication())
        privateSpaceController.refresh()
        _uiState.update { it.copy(isDefaultHome = isDefaultHome) }
        // Wait for the stored pack choice: do not flash system icons on cold start.
        if (!itemsReady || _uiState.value.isLoadingSettings || _uiState.value.settingsLoadFailed) return
        appLoadJob?.cancel()
        appLoadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(isLoadingApps = true, appLoadFailed = false, isLoadingIconPacks = true, hasShortcutAccess = shortcutRepository.hasAccess())
            }
            try {
                val installed = iconPacks.installedPacks()
                _uiState.update { it.copy(iconPacks = installed, isLoadingIconPacks = false, iconPacksLoadFailed = false) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.update { it.copy(isLoadingIconPacks = false, iconPacksLoadFailed = true) }
            }
            val selectedPacks = _uiState.value.settings.enabledIconPackPackages
            val availablePacks = selectedPacks.mapNotNull { iconPacks.load(it) }
            val status = when {
                selectedPacks.isEmpty() -> IconPackStatus.System
                availablePacks.isNotEmpty() -> IconPackStatus.Ready
                else -> IconPackStatus.Unavailable
            }
            val workProfiles = runCatching { appRepository.workProfiles() }.getOrDefault(emptyList())
            runCatching { appRepository.loadApps(selectedPacks) + appRepository.loadWorkApps(workProfiles, selectedPacks) }
                .onSuccess { apps ->
                    val renames = preferences.renames()
                    val snapshot = itemsSnapshot
                    val iconSettings = _uiState.value.settings
                    suspend fun decorate(app: LauncherApp): LauncherApp = itemIcons.applyDesign(app, snapshot.icons[app.key], iconSettings.iconDesign, iconSettings, preferences.themedIcons)
                        .copy(label = renames[app.key] ?: app.originalLabel, showWorkIndicator = iconSettings.workProfile.showIndicator)
                    val shortcuts = snapshot.shortcuts.mapNotNull { saved ->
                        // Package names alone cannot distinguish personal and work installations.
                        val owners = apps.filter { app -> if (app.userSerial == null) !saved.itemKey.startsWith("profile:")
                            else saved.itemKey.startsWith("profile:${app.userSerial}:shortcut:") }
                        val owner = owners.firstOrNull { it.componentName.flattenToString() == saved.activity }
                            ?: owners.firstOrNull { it.packageName == saved.packageName } ?: return@mapNotNull null
                        val current = shortcutRepository.shortcutsFor(owner).shortcuts.firstOrNull { it.id == saved.shortcutId }
                            ?: LauncherShortcut(saved.shortcutId, saved.packageName, saved.label, owner.icon, owner.componentName)
                        decorate(current.asApp(owner))
                    }
                    val shown = snapshot.shortcuts.filter { it.showInAppList }.map { it.itemKey }.toSet()
                    val displayedApps = (apps.map { decorate(it) } + shortcuts.filter { it.key in shown })
                        .sortedWith(LauncherAppOrder)
                    val folderApps = _uiState.value.folders.map { folder ->
                        itemIcons.apply(folder.asApp(), snapshot.icons[folder.key], iconSettings)
                    }
                    val privateFolderApp = itemIcons.apply(_uiState.value.privateFolder.asApp(), snapshot.icons[PrivateSpaceFolderKey], iconSettings)
                    val workFolderApp = itemIcons.apply(_uiState.value.workFolder.asApp(), snapshot.icons[WorkProfileFolderKey], iconSettings)
                    val graceButtonApp = itemIcons.apply(GraceButtonIcon.item(getApplication()),
                        GraceButtonIcon.choice(snapshot.icons[GraceButtonIcon.key]), iconSettings)
                    val favorites = favoritesStore.favoritesFor(displayedApps)
                    _uiState.update {
                        it.copy(
                            apps = displayedApps,
                            shortcutApps = shortcuts + it.shortcutApps.filter(LauncherApp::isPrivateSpace),
                            folderApps = folderApps,
                            privateFolderApp = privateFolderApp,
                            workFolderApp = workFolderApp,
                            graceButtonApp = graceButtonApp,
                            workProfiles = workProfiles,
                            favoriteKeys = favorites.toSet(),
                            favoriteOrder = favorites,
                            // Settings also has an independent Activity/ViewModel.
                            // Refresh legacy preferences when returning to HOME.
                            categories = preferences.categories(),
                            textMode = preferences.textMode,
                            themedIcons = iconSettings.iconDesign?.design?.withThemeDefaults(IconDesign.defaults(preferences.themedIcons))?.themeIcons
                                ?: preferences.themedIcons,
                            iconPackStatus = status,
                            isLoadingApps = false,
                        )
                    }
                    shortcutRepository.prefetch(_uiState.value.favoriteApps)
                    refreshPrivateApps()
                }
                .onFailure {
                    if (it is CancellationException) throw it
                    _uiState.update { it.copy(isLoadingApps = false, appLoadFailed = true) }
                }
        }
    }

    private fun refreshPrivateApps() {
        privateAppLoadJob?.cancel()
        val state = _uiState.value
        val profile = state.privateSpace
        val user = profile.user
        if (user == null || !state.settings.privateSpace.enabled || !itemsReady || state.isLoadingSettings || state.settingsLoadFailed ||
            (state.settings.privateSpace.protectsApps && (!profile.accessible || profile.locked))) {
            _uiState.update { it.copy(privateAppsLoading = false, privateAppsFailed = false) }
            return
        }
        privateAppLoadJob = viewModelScope.launch {
            _uiState.update { it.copy(privateAppsLoading = true, privateAppsFailed = false) }
            try {
                val settings = state.settings
                if (privateRawSerial != profile.serial) {
                    privateRawApps = privateAppCache.load(user, profile.serial)
                    privateRawSerial = profile.serial
                }
                // This query never disables quiet mode or opens a credential UI.
                // Some systems suppress locked profiles; retain the last known inventory then.
                try {
                    val queried = appRepository.loadPrivateApps(user, profile.serial, emptyList())
                    if (queried.isNotEmpty() || !profile.locked) {
                        val previous = privateRawApps.associateBy(LauncherApp::key)
                        privateRawApps = queried.map { app ->
                            if (app.icon != null) app else app.copy(icon = previous[app.key]?.icon, monochromeIcon = previous[app.key]?.monochromeIcon)
                        }
                        try { privateAppCache.save(profile.serial, privateRawApps) }
                        catch (error: Exception) {
                            if (error is CancellationException) throw error
                            Log.w("LauncherViewModel", "Unable to cache private app inventory", error)
                        }
                    }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    if (privateRawApps.isEmpty()) throw error
                }
                val packed = appRepository.applyPrivateIconPacks(privateRawApps, settings.enabledIconPackPackages)
                val apps = packed.map { app ->
                    itemIcons.applyDesign(app, itemsSnapshot.icons[app.key], settings.iconDesign, settings, preferences.themedIcons)
                }
                val savedShortcuts = itemsSnapshot.shortcuts.filter { it.itemKey.startsWith("profile:${profile.serial}:shortcut:") }
                    .mapNotNull { saved ->
                        val owner = packed.firstOrNull { it.componentName.flattenToString() == saved.activity }
                            ?: packed.firstOrNull { it.packageName == saved.packageName } ?: return@mapNotNull null
                        val current = if (!profile.locked) shortcutRepository.shortcutsFor(owner).shortcuts.firstOrNull { it.id == saved.shortcutId } else null
                        val shortcut = current ?: LauncherShortcut(saved.shortcutId, saved.packageName, saved.label, owner.icon,
                            owner.componentName, user = user, userSerial = profile.serial, isPrivateSpace = true)
                        val app = shortcut.asApp(owner)
                        itemIcons.applyDesign(app, itemsSnapshot.icons[app.key], settings.iconDesign, settings, preferences.themedIcons)
                    }
                val renames = preferences.renames()
                // Cache visibility is determined by settings; never publish another profile's inventory.
                if (privateSpaceController.state.value.user == user && privateSpaceController.state.value.serial == profile.serial &&
                    _uiState.value.settings.privateSpace.enabled) {
                    _uiState.update { it.copy(privateApps = apps.map { app -> app.copy(label = renames[app.key] ?: app.originalLabel) },
                        shortcutApps = it.shortcutApps.filterNot(LauncherApp::isPrivateSpace) + savedShortcuts.map { app -> app.copy(label = renames[app.key] ?: app.originalLabel) },
                        privateAppsLoading = false) }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.update { it.copy(privateAppsLoading = false, privateAppsFailed = true) }
            }
        }
    }

    /** Only reads Breezy's local provider; never initiates a network weather request. */
    fun refreshWeather(force: Boolean = true) {
        val state = _uiState.value
        if (state.isLoadingSettings || state.settingsLoadFailed || !state.settings.weatherEnabled) {
            weatherLoadJob?.cancel()
            lastWeatherRefresh = 0L
            _uiState.update { it.copy(weather = WeatherState()) }
            return
        }
        if (!force && (weatherLoadJob?.isActive == true ||
                SystemClock.elapsedRealtime() - lastWeatherRefresh < 15 * 60_000L)) return
        weatherLoadJob?.cancel()
        val locationId = state.settings.weatherLocationId
        weatherLoadJob = viewModelScope.launch {
            val previous = _uiState.value.weather
            _uiState.update { it.copy(weather = previous.copy(status = WeatherStatus.Loading,
                snapshot = previous.snapshot.takeIf { previous.selectedLocationId == locationId || locationId == null })) }
            val weather = weatherRepository.load(locationId)
            lastWeatherRefresh = SystemClock.elapsedRealtime()
            _uiState.update { it.copy(weather = weather) }
        }
    }

    fun refreshSchedule() {
        if (_uiState.value.isLoadingSettings || _uiState.value.settingsLoadFailed ||
            !_uiState.value.settings.calendarAgenda
        ) {
            scheduleLoadJob?.cancel()
            _uiState.update { it.copy(events = emptyList(), scheduleStatus = ScheduleStatus.Ready) }
            return
        }
        val application = getApplication<Application>()
        val hasPermission = ContextCompat.checkSelfPermission(
            application,
            Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            scheduleLoadJob?.cancel()
            _uiState.update {
                it.copy(events = emptyList(), scheduleStatus = ScheduleStatus.PermissionRequired)
            }
            return
        }

        scheduleLoadJob?.cancel()
        scheduleLoadJob = viewModelScope.launch {
            _uiState.update { it.copy(scheduleStatus = ScheduleStatus.Loading) }
            runCatching { calendarRepository.upcomingEvents() }
                .onSuccess { events ->
                    _uiState.update { it.copy(events = events, scheduleStatus = ScheduleStatus.Ready) }
                }
                .onFailure {
                    if (it is CancellationException) throw it
                    _uiState.update { it.copy(events = emptyList(), scheduleStatus = ScheduleStatus.Error) }
                }
        }
    }

    fun launch(app: LauncherApp, bounds: Rect? = null, options: Bundle? = null, fromSearch: Boolean = false): Boolean {
        if (app.folderId != null) return false
        if (app.isPrivateSpace && !privateSpaceController.prepareAppLaunch(app.user, app.key)) return false
        if (!app.isPrivateSpace) privateSpaceController.close()
        val launched = app.shortcut?.let { shortcutRepository.launch(it, bounds, options) } ?: appRepository.launch(app, bounds, options)
        if (app.isPrivateSpace) privateSpaceController.finishAppLaunch(launched)
        if (launched && fromSearch && (!app.isPrivateSpace || _uiState.value.settings.privateSpace.exposesApps)) {
            updateSettings { it.copy(search = it.search.recordApp(app.key)) }
        }
        return launched
    }

    fun preparePrivateAppLaunch(app: LauncherApp): Boolean = privateSpaceController.prepareAppLaunch(app.user, app.key)

    suspend fun loadShortcuts(app: LauncherApp) = shortcutRepository.shortcutsFor(app)

    fun cachedShortcuts(app: LauncherApp) = shortcutRepository.peek(app)

    fun prepareShortcuts(app: LauncherApp) {
        viewModelScope.launch { shortcutRepository.prefetch(listOf(app)) }
    }

    fun preparePrivateShortcutLaunch(shortcut: LauncherShortcut): Boolean =
        privateSpaceController.prepareAppLaunch(shortcut.user, shortcut.key)

    fun launchShortcut(shortcut: LauncherShortcut, bounds: Rect? = null, options: Bundle? = null): Boolean {
        if (shortcut.isPrivateSpace && !preparePrivateShortcutLaunch(shortcut)) return false
        if (!shortcut.isPrivateSpace) privateSpaceController.close()
        val launched = shortcutRepository.launch(shortcut, bounds, options)
        if (shortcut.isPrivateSpace) privateSpaceController.finishAppLaunch(launched)
        return launched
    }

    override fun onCleared() {
        launcherApps.unregisterCallback(profilePackages)
        getApplication<Application>().unregisterReceiver(packageReceiver)
        getApplication<Application>().unregisterReceiver(dateReceiver)
        shortcutRepository.close()
        mediaRepository.close()
    }

    fun controlMedia(sessionId: String, command: MediaCommand): Boolean = mediaRepository.command(sessionId, command)
    fun dismissMedia(sessionId: String, revision: Long): Boolean = mediaRepository.dismiss(sessionId, revision)
    fun dismissNotification(key: String, revision: Long): Boolean = appNotifications.dismiss(key, revision)
    fun openNotification(key: String, revision: Long): Boolean = appNotifications.open(getApplication(), key, revision)

    fun renameApp(app: LauncherApp, label: String) {
        if (app.folderId == WorkProfileFolderId) {
            updateSettings { it.copy(workProfile = it.workProfile.copy(name = label.trim().ifBlank { WorkProfileDefaultName })) }
            return
        }
        if (app.folderId == PrivateSpaceFolderId) {
            updateSettings { it.copy(privateSpace = it.privateSpace.copy(name = label.trim().ifBlank { PrivateSpaceDefaultName })) }
            return
        }
        if (app.folderId != null) {
            updateFolder(app.folderId, label, null)
            return
        }
        preferences.rename(app.key, label)
        if (app.shortcut != null) persistSettings { itemsRepository.rememberShortcut(app) }
        _uiState.update { state ->
            state.copy(apps = state.apps.map {
                if (it.key == app.key) it.copy(label = label.trim().ifBlank { it.originalLabel }) else it
            }.sortedWith(LauncherAppOrder), shortcutApps = state.shortcutApps.map {
                if (it.key == app.key) it.copy(label = label.trim().ifBlank { it.originalLabel }) else it
            }, privateApps = state.privateApps.map {
                if (it.key == app.key) it.copy(label = label.trim().ifBlank { it.originalLabel }) else it
            }, itemRevision = state.itemRevision + 1)
        }
    }

    fun reorderPrivateApps(keys: List<String>) = updateSettings {
        it.copy(privateSpace = it.privateSpace.copy(appOrder = keys.distinct()))
    }

    fun reorderWorkApps(keys: List<String>) = updateSettings {
        it.copy(workProfile = it.workProfile.copy(appOrder = keys.distinct()))
    }

    suspend fun resetWorkProfileAppearance(): Boolean {
        if (!setItemIcon(_uiState.value.workFolder.asApp(), null)) return false
        updateSettings { it.copy(workProfile = it.workProfile.copy(name = WorkProfileDefaultName)) }
        return true
    }

    suspend fun resetPrivateSpaceAppearance(): Boolean {
        if (!setItemIcon(_uiState.value.privateFolder.asApp(), null)) return false
        updateSettings { it.copy(privateSpace = it.privateSpace.copy(name = PrivateSpaceDefaultName)) }
        return true
    }

    suspend fun setItemIcon(app: LauncherApp, choice: ItemIcon?): Boolean = settingsWriteMutex.withLock {
        try {
            val previous = itemsSnapshot.icons[app.key]
            itemsRepository.rememberShortcut(app)
            itemsRepository.saveIcon(app.key, choice)
            if (previous?.kind == "image" && (choice?.kind != "image" || previous.source != choice.source) &&
                !itemsRepository.isImageReferenced(previous.source)) itemIcons.deleteImage(previous)
            true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            false
        }
    }

    suspend fun importItemIcon(app: LauncherApp, uri: Uri): Boolean {
        var imported: ItemIcon? = null
        return try {
            imported = itemIcons.importImage(uri)
            setItemIcon(app, imported).also { if (!it) itemIcons.deleteImage(imported) }
        } catch (error: Exception) {
            itemIcons.deleteImage(imported)
            if (error is CancellationException) throw error
            false
        }
    }

    fun showShortcutInAppList(app: LauncherApp, show: Boolean) = persistSettings {
        app.shortcut?.let { shortcutRepository.pin(listOf(it)) }
        itemsRepository.rememberShortcut(app, show)
    }

    private suspend fun rememberPopupItems(apps: List<LauncherApp>) {
        apps.filter { it.shortcut != null }.forEach { itemsRepository.rememberShortcut(it) }
        shortcutRepository.pin(apps.mapNotNull { it.shortcut })
    }

    suspend fun rememberShortcut(app: LauncherApp): Boolean = settingsWriteMutex.withLock {
        try {
            rememberPopupItems(listOf(app))
            _uiState.update { state -> state.copy(shortcutApps = state.shortcutApps.filterNot { it.key == app.key } + app) }
            true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            _uiState.update { it.copy(settingsSaveFailed = true) }
            false
        }
    }

    fun updatePopup(owner: LauncherApp, defaults: List<LauncherApp>, transform: (List<PopupItem>) -> List<PopupItem>) = persistSettings {
        rememberPopupItems(defaults + listOf(owner))
        val removed = itemsRepository.updatePopup(owner.key, defaults.map { PopupItem(it.key) }, transform)
        val host = com.galaxyrio.gracelauncher.platform.HomeWidgetHost(getApplication())
        removed.forEach { runCatching { host.deleteAppWidgetId(it) } }
    }

    suspend fun ensurePopup(owner: LauncherApp, defaults: List<LauncherApp>): Boolean = settingsWriteMutex.withLock {
        try {
            rememberPopupItems(defaults + listOf(owner))
            itemsRepository.updatePopup(owner.key, defaults.map { PopupItem(it.key) }) { it }
            true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            false
        }
    }

    fun categorize(app: LauncherApp, category: String?) {
        preferences.categorize(app.key, category)
        _uiState.update { it.copy(categories = preferences.categories()) }
    }

    fun setTextMode(mode: WallpaperTextMode) {
        preferences.textMode(mode)
        _uiState.update { it.copy(textMode = mode) }
    }

    fun setFocusActive(enabled: Boolean) {
        preferences.focusActive(enabled)
        _uiState.update { it.copy(focusActive = enabled) }
    }

    fun setFocusApps(keys: Set<String>) {
        val snapshot = keys.toSet()
        preferences.focusAppKeys(snapshot)
        _uiState.update { it.copy(focusAppKeys = snapshot) }
    }

    fun setThemedIcons(enabled: Boolean) {
        preferences.themedIcons(enabled)
        _uiState.update { it.copy(themedIcons = enabled) }
    }

    fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) = persistSettings {
        settingsRepository.mutateSettings(transform)
    }

    suspend fun applyClockStyle(style: ClockStyle): Boolean = settingsWriteMutex.withLock {
        try {
            settingsRepository.mutateSettings { it.copy(clockStyle = style) }
            _uiState.update { it.copy(settingsSaveFailed = false) }
            true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.e("LauncherViewModel", "Unable to save clock style", error)
            _uiState.update { it.copy(settingsSaveFailed = true) }
            false
        }
    }

    suspend fun applyIconDesign(choice: ItemIcon): Boolean = settingsWriteMutex.withLock {
        try {
            var previous: ItemIcon? = null
            settingsRepository.mutateSettings { previous = it.iconDesign; it.copy(iconDesign = choice) }
            if (previous?.kind == "image" && (choice.kind != "image" || previous?.source != choice.source) &&
                !itemsRepository.isImageReferenced(previous!!.source)) itemIcons.deleteImage(previous)
            _uiState.update { it.copy(settingsSaveFailed = false) }
            true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.e("LauncherViewModel", "Unable to save icon design", error)
            _uiState.update { it.copy(settingsSaveFailed = true) }
            false
        }
    }

    suspend fun deleteIconDesigns(keys: Set<String>): Boolean = settingsWriteMutex.withLock {
        try {
            val previous = itemsRepository.resetIcons(keys)
            previous.filter { it.kind == "image" }.distinctBy { it.source }.forEach {
                if (!itemsRepository.isImageReferenced(it.source)) itemIcons.deleteImage(it)
            }
            _uiState.update { it.copy(settingsSaveFailed = false) }
            true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.e("LauncherViewModel", "Unable to delete icon designs", error)
            _uiState.update { it.copy(settingsSaveFailed = true) }
            false
        }
    }

    fun removeHomeWidget() = persistSettings {
        var removedId = -1
        settingsRepository.mutateSettings { current ->
            removedId = current.homeLayout.widgetId
            current.copy(homeLayout = current.homeLayout.withoutWidget())
        }
        // Deallocate only after Room commits; a storage failure must leave the widget intact.
        if (removedId >= 0) runCatching {
            com.galaxyrio.gracelauncher.platform.HomeWidgetHost(getApplication()).deleteAppWidgetId(removedId)
        }
    }

    private val presetStore = PresetStore(application)
    val presets = presetStore.presets

    /** Serializes the current configuration to a backup JSON document. */
    suspend fun exportBackup(): String {
        val state = _uiState.value
        return SettingsBackup.export(
            settings = state.settings,
            preferences = BackupPreferences(
                textMode = state.textMode,
                themedIcons = state.themedIcons,
                focusActive = state.focusActive,
                focusApps = state.focusAppKeys,
                renames = preferences.renames(),
                categories = preferences.categories(),
            ),
            favorites = state.favoriteOrder,
            hiddenApps = state.hiddenAppKeys,
            folders = state.folders,
        )
    }

    /**
     * Applies a validated backup. Settings are replaced atomically; if any
     * write fails the error is surfaced and current settings are preserved.
     */
    suspend fun importBackup(backup: ParsedBackup): Boolean = settingsWriteMutex.withLock {
        try {
            settingsRepository.updateSettings(backup.settings)
            settingsRepository.setHiddenApps(backup.hiddenApps)
            backup.textMode?.let { preferences.textMode(it) }
            preferences.themedIcons(backup.themedIcons)
            preferences.focusActive(backup.focusActive)
            preferences.focusAppKeys(backup.focusApps)
            backup.renames.forEach { (key, label) -> preferences.rename(key, label) }
            backup.categories.forEach { (key, category) -> preferences.categorize(key, category) }
            favoritesStore.restore(backup.favorites)
            // Replace user folders wholesale; reserved folders are never in a backup.
            val existingIds = settingsRepository.readFolders()
            existingIds.forEach { settingsRepository.deleteFolder(it.id) }
            backup.folders.forEach { settingsRepository.saveFolder(it) }
            _uiState.update {
                it.copy(
                    settings = backup.settings,
                    settingsSaveFailed = false,
                    textMode = backup.textMode ?: it.textMode,
                    themedIcons = backup.themedIcons,
                    focusActive = backup.focusActive,
                    focusAppKeys = backup.focusApps,
                    categories = preferences.categories(),
                    hiddenAppKeys = backup.hiddenApps,
                )
            }
            true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.e("LauncherViewModel", "Unable to restore backup", error)
            _uiState.update { it.copy(settingsSaveFailed = true) }
            false
        }
    }

    suspend fun savePreset(name: String): Boolean = try {
        val state = _uiState.value
        presetStore.save(name, state.settings, preferences)
        true
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        Log.e("LauncherViewModel", "Unable to save preset", error)
        false
    }

    suspend fun applyPreset(preset: AppearancePreset): Boolean = settingsWriteMutex.withLock {
        try {
            val merged = presetStore.applyTo(preset, _uiState.value.settings)
            settingsRepository.updateSettings(merged)
            val (textMode, themedIcons) = presetStore.applyPreferences(preset)
            textMode?.let { preferences.textMode(it) }
            preferences.themedIcons(themedIcons)
            _uiState.update {
                it.copy(
                    settings = merged,
                    settingsSaveFailed = false,
                    textMode = textMode ?: it.textMode,
                    themedIcons = themedIcons,
                )
            }
            true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.e("LauncherViewModel", "Unable to apply preset", error)
            _uiState.update { it.copy(settingsSaveFailed = true) }
            false
        }
    }

    fun deletePreset(id: String) {
        viewModelScope.launch { runCatching { presetStore.delete(id) } }
    }

    fun setHiddenApps(keys: Set<String>) {
        val snapshot = keys.toSet()
        persistSettings { settingsRepository.setHiddenApps(snapshot) }
    }

    fun saveFolder(folder: LauncherFolder) {
        val snapshot = folder.copy(appKeys = folder.appKeys.toList())
        persistSettings {
            rememberPopupItems(snapshot.appKeys.mapNotNull(_uiState.value::findItem))
            settingsRepository.saveFolder(snapshot)
        }
    }

    fun updateFolder(id: String, name: String?, placement: FolderPlacement?, appListAtBottom: Boolean? = null) = persistSettings {
        settingsRepository.updateFolder(id, name, placement, appListAtBottom)
    }

    suspend fun setFolderFavorites(selection: Map<String, Boolean>): Boolean = settingsWriteMutex.withLock {
        try {
            settingsRepository.setFolderFavorites(selection)
            _uiState.update { it.copy(settingsSaveFailed = false) }
            true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.e("LauncherViewModel", "Unable to save favorite folders", error)
            _uiState.update { it.copy(settingsSaveFailed = true) }
            false
        }
    }

    fun deleteFolder(id: String) = persistSettings {
        val previous = itemsSnapshot.icons["folder:$id"]
        val removed = settingsRepository.deleteFolder(id)
        val host = com.galaxyrio.gracelauncher.platform.HomeWidgetHost(getApplication())
        removed.forEach { runCatching { host.deleteAppWidgetId(it) } }
        if (previous?.kind == "image" && !itemsRepository.isImageReferenced(previous.source)) itemIcons.deleteImage(previous)
    }

    /** Room emissions update the UI only after the operation has committed. */
    private fun persistSettings(write: suspend () -> Unit) {
        viewModelScope.launch {
            settingsWriteMutex.withLock {
                try {
                    write()
                    _uiState.update { it.copy(settingsSaveFailed = false) }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    Log.e("LauncherViewModel", "Unable to save launcher settings", error)
                    _uiState.update { it.copy(settingsSaveFailed = true) }
                }
            }
        }
    }

    fun toggleFavorite(app: LauncherApp): Boolean {
        if (app.folderId != null) {
            val folder = _uiState.value.folders.firstOrNull { it.id == app.folderId } ?: return false
            val favorite = !folder.placement.inFavorites
            persistSettings { settingsRepository.setFolderFavorites(mapOf(folder.id to favorite)) }
            return favorite
        }
        if (app.shortcut != null && app.key !in _uiState.value.favoriteKeys) {
            viewModelScope.launch {
                if (rememberShortcut(app)) {
                    val updated = favoritesStore.toggle(app.key, currentFavoriteOrder())
                    _uiState.update { it.copy(favoriteKeys = updated.toSet(), favoriteOrder = updated) }
                }
            }
            return true
        }
        val updated = favoritesStore.toggle(app.key, currentFavoriteOrder())
        _uiState.update { it.copy(favoriteKeys = updated.toSet(), favoriteOrder = updated) }
        if (app.key in updated) prepareShortcuts(app)
        return app.key in updated
    }

    fun reorderFavorites(keys: List<String>) {
        val updated = favoritesStore.reorder(keys, currentFavoriteOrder())
        _uiState.update { it.copy(favoriteOrder = updated) }
    }

    private fun currentFavoriteOrder(): List<String> = _uiState.value.let { state ->
        (state.favoriteOrder + state.favoriteItems.map(LauncherApp::key)).distinct()
    }

    fun requestReturnHome() {
        _returnHomeRequests.tryEmit(Unit)
    }
}
