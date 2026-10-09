package com.niva.launcher.ui

import android.Manifest
import androidx.activity.compose.LocalActivity
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.UserHandle
import android.provider.CalendarContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.net.toUri
import androidx.core.content.ContextCompat
import com.niva.launcher.R
import com.niva.launcher.MainActivity
import com.niva.launcher.WidgetSetupActivity
import com.niva.launcher.data.LauncherApp
import com.niva.launcher.data.icons.NivaButtonIcon
import com.niva.launcher.data.LauncherFolder
import com.niva.launcher.data.LauncherShortcut
import com.niva.launcher.data.PrivateSpaceDisplay
import com.niva.launcher.data.PrivateSpaceFolderId
import com.niva.launcher.data.WorkProfileFolderId
import com.niva.launcher.data.ScheduleEvent
import com.niva.launcher.ui.components.AlphabetRail
import com.niva.launcher.ui.components.AppRowGestures
import com.niva.launcher.ui.components.LocalLauncherInputEnabled
import com.niva.launcher.ui.components.LocalAppTransitions
import com.niva.launcher.ui.components.LocalHomeAnimationTarget
import com.niva.launcher.ui.components.LauncherLayout
import com.niva.launcher.ui.components.stableStatusBarInset
import com.niva.launcher.ui.components.rememberLauncherSystemBars
import com.niva.launcher.ui.components.statusBarContentFade
import com.niva.launcher.platform.AppLaunchTransition
import com.niva.launcher.platform.DefaultHome
import com.niva.launcher.platform.ClockLauncher
import com.niva.launcher.platform.ClockLaunchResult
import com.niva.launcher.platform.NivaSystemActions
import com.niva.launcher.data.media.MediaAccess
import com.niva.launcher.data.media.IdleMediaSessionId
import com.niva.launcher.data.NivaButtonAction
import com.niva.launcher.data.NivaButtonTarget
import com.niva.launcher.ui.components.NivaButton
import com.niva.launcher.ui.components.NivaButtonOrigin
import com.niva.launcher.ui.components.NivaButtonTransition
import com.niva.launcher.ui.components.NivaButtonTransitionOverlay
import com.niva.launcher.ui.components.NivaSystemAccessDialog
import com.niva.launcher.ui.components.nivaReveal
import com.niva.launcher.data.weather.BreezyWeatherRepository
import com.niva.launcher.ui.overlays.ShortcutRevealState
import com.niva.launcher.ui.components.LauncherIcon
import com.niva.launcher.ui.components.LauncherSymbol
import com.niva.launcher.ui.drawer.AppDrawerScreen
import com.niva.launcher.ui.drawer.AppListModel
import com.niva.launcher.ui.home.HomeScreen
import com.niva.launcher.ui.settings.LauncherSettingsScreen
import com.niva.launcher.ui.overlays.LauncherOverlay
import com.niva.launcher.ui.overlays.LauncherOverlays
import com.niva.launcher.ui.theme.NivaLauncherTheme
import com.niva.launcher.ui.theme.LocalLauncherAppearance
import com.niva.launcher.ui.theme.rememberLauncherAppearance
import com.niva.launcher.ui.theme.rememberLauncherHaptics
import com.niva.launcher.ui.theme.WallpaperBlur
import com.niva.launcher.ui.widgets.LocalWidgetHost
import com.niva.launcher.ui.widgets.rememberWidgetHost
import com.materialkolor.PaletteStyle
import com.materialkolor.ktx.toDynamicScheme
import com.materialkolor.ktx.toneColor
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun LauncherRoute(
    viewModel: LauncherViewModel,
    settingsOnly: Boolean = false,
    onCloseSettings: () -> Unit = {},
    widgetEditRequest: Int = 0,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val chooseAppStoreTitle = stringResource(R.string.choose_app_store)
    val launchView = LocalView.current
    val appTransitions = LocalAppTransitions.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.privateSpaceController.onResume()
                Lifecycle.Event.ON_PAUSE -> viewModel.privateSpaceController.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            activity?.let(viewModel.privateSpaceController::cancelAuthentication)
            viewModel.privateSpaceController.close()
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { viewModel.refreshSchedule() }
    val homeRoleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.refreshApps()
    }
    val mediaAccessLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.refreshApps()
    }
    val breezyRepository = remember(context) { BreezyWeatherRepository(context) }
    var weatherPermissionRequested by rememberSaveable { mutableStateOf(false) }
    val weatherPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refreshWeather()
    }
    LaunchedEffect(uiState.settingsSaveFailed, uiState.settingsLoadFailed) {
        val error = when {
            uiState.settingsLoadFailed -> R.string.settings_storage_load_error
            uiState.settingsSaveFailed -> R.string.settings_storage_save_error
            else -> null
        }
        if (error != null) Toast.makeText(context, error, Toast.LENGTH_LONG).show()
    }

    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(60_000)
                viewModel.refreshSchedule()
                viewModel.refreshWeather(force = false)
            }
        }
    }

    val openSystemApp: (Intent) -> Unit = { intent ->
        runCatching { context.startActivity(intent) }.onFailure {
            Toast.makeText(context, R.string.app_unavailable, Toast.LENGTH_SHORT).show()
        }
    }
    fun withPrivateProfile(user: UserHandle?, isPrivate: Boolean, ready: () -> Unit) {
        if (!isPrivate) { ready(); return }
        val controller = viewModel.privateSpaceController
        controller.refresh()
        val settings = viewModel.uiState.value.settings.privateSpace
        val state = controller.state.value
        if (!settings.enabled || user == null || user != state.user) return
        if (!state.locked && (state.accessible || settings.exposesApps)) { ready(); return }
        val currentActivity = activity ?: return
        controller.unlock(currentActivity, authenticate = settings.protectsApps) {
            lifecycleOwner.lifecycleScope.launch {
                yield()
                lifecycleOwner.lifecycle.withResumed {
                    if (controller.state.value.user == user && !controller.state.value.locked &&
                        viewModel.uiState.value.settings.privateSpace.enabled) ready()
                }
            }
        }
    }
    val openPrivateAppAction: (LauncherApp, () -> Unit) -> Unit = { app, open ->
        // App info may itself run in the private profile. Hide our contents now
        // and defer Android's profile lock until returning, as for an app launch.
        withPrivateProfile(app.user, app.isPrivateSpace) {
            if (!app.isPrivateSpace || viewModel.preparePrivateAppLaunch(app)) {
                val result = runCatching(open).onFailure {
                    Toast.makeText(context, R.string.app_unavailable, Toast.LENGTH_SHORT).show()
                }
                if (app.isPrivateSpace) viewModel.privateSpaceController.finishAppLaunch(result.isSuccess)
            }
        }
    }

    fun launchApp(app: LauncherApp, bounds: Rect?, fromSearch: Boolean = false) {
        val start: (AppLaunchTransition?) -> Unit = { transition ->
            if (!viewModel.launch(app, transition?.sourceBounds, transition?.options, fromSearch = fromSearch)) {
                Toast.makeText(context, R.string.app_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
        // Preserve the same icon launch animation and private-profile protection for every entry.
        withPrivateProfile(app.user, app.isPrivateSpace) {
            if (!app.isPrivateSpace || viewModel.preparePrivateAppLaunch(app)) {
                if (bounds == null) start(null)
                else if (appTransitions != null) appTransitions.launch(launchView, bounds.toAndroidRect(), start)
                else start(AppLaunchTransition.fromIcon(launchView, bounds.toAndroidRect()))
            }
        }
    }

    fun launchShortcut(shortcut: LauncherShortcut, bounds: Rect?) {
        withPrivateProfile(shortcut.user, shortcut.isPrivateSpace) {
            if (!shortcut.isPrivateSpace || viewModel.preparePrivateShortcutLaunch(shortcut)) {
                val start: (AppLaunchTransition?) -> Unit = { transition ->
                    if (!viewModel.launchShortcut(shortcut, transition?.sourceBounds, transition?.options)) {
                        Toast.makeText(context, R.string.shortcut_error, Toast.LENGTH_SHORT).show()
                    }
                }
                if (bounds == null) start(null)
                else if (appTransitions != null) appTransitions.launch(launchView, bounds.toAndroidRect(), start)
                else start(AppLaunchTransition.fromIcon(launchView, bounds.toAndroidRect()))
            }
        }
    }

    val actions = LauncherActions(
        requestPrivateSpace = { forceAuthentication, ready ->
            val settings = viewModel.uiState.value.settings.privateSpace
            if (settings.exposesApps && !forceAuthentication) ready()
            else activity?.let { a ->
                viewModel.privateSpaceController.unlock(a, authenticate = forceAuthentication || settings.protectsApps,
                    forceAuthentication = forceAuthentication) {
                    lifecycleOwner.lifecycleScope.launch {
                        // Availability can arrive while Android's credential Activity is still
                        // closing. Let NavHost receive RESUME before its guarded navigation.
                        yield()
                        lifecycleOwner.lifecycle.withResumed {
                            if (viewModel.privateSpaceController.state.value.accessible) ready()
                        }
                    }
                }
            }
        },
        closePrivateSpace = viewModel.privateSpaceController::close,
        openPrivateSpaceSettings = { activity?.let(viewModel.privateSpaceController::openSettings) },
        reorderPrivateApps = viewModel::reorderPrivateApps,
        resetPrivateSpaceAppearance = viewModel::resetPrivateSpaceAppearance,
        reorderWorkApps = viewModel::reorderWorkApps,
        resetWorkProfileAppearance = viewModel::resetWorkProfileAppearance,
        openWorkProfileSettings = {
            // Android exposes account/profile management through this public settings action.
            runCatching { context.startActivity(Intent(Settings.ACTION_SYNC_SETTINGS)) }
                .onFailure { openSystemApp(Intent(Settings.ACTION_SETTINGS)) }
        },
        requestCalendarAccess = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) },
        addWidget = {
            when {
                uiState.isLoadingSettings || uiState.settingsLoadFailed -> Toast.makeText(context, R.string.settings_storage_load_error, Toast.LENGTH_SHORT).show()
                uiState.settings.homeLayout.hasWidget -> Toast.makeText(context, R.string.widget_single_limit, Toast.LENGTH_LONG).show()
                else -> openSystemApp(Intent(context, WidgetSetupActivity::class.java))
            }
        },
        configureWidget = {
            if (uiState.settings.homeLayout.hasWidget) openSystemApp(Intent(context, WidgetSetupActivity::class.java)
                .putExtra(WidgetSetupActivity.EXTRA_CONFIGURE_ID, uiState.settings.homeLayout.widgetId))
        },
        removeWidget = viewModel::removeHomeWidget,
        moveWidget = {
            openSystemApp(Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_EDIT_WIDGET, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        },
        refreshWeather = { viewModel.refreshWeather() },
        requestWeatherAccess = {
            when {
                breezyRepository.installedPackage() == null -> viewModel.refreshWeather()
                ContextCompat.checkSelfPermission(context, BreezyWeatherRepository.READ_PERMISSION) == PackageManager.PERMISSION_GRANTED ->
                    viewModel.refreshWeather()
                weatherPermissionRequested && activity?.let {
                    ActivityCompat.shouldShowRequestPermissionRationale(it, BreezyWeatherRepository.READ_PERMISSION)
                } != true -> openSystemApp(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)))
                else -> {
                    weatherPermissionRequested = true
                    weatherPermissionLauncher.launch(BreezyWeatherRepository.READ_PERMISSION)
                }
            }
        },
        openBreezyWeather = {
            val intent = context.packageManager.getLaunchIntentForPackage(BreezyWeatherRepository.PACKAGE_NAME)
            if (intent != null) openSystemApp(intent)
            else Toast.makeText(context, R.string.app_unavailable, Toast.LENGTH_SHORT).show()
        },
        installBreezyWeather = {
            openSystemApp(Intent(Intent.ACTION_VIEW, "https://github.com/breezy-weather/breezy-weather/releases".toUri()))
        },
        dismissMedia = viewModel::dismissMedia,
        dismissNotification = viewModel::dismissNotification,
        openNotification = { key, revision ->
            viewModel.openNotification(key, revision).also { opened ->
                if (!opened) Toast.makeText(context, R.string.notification_unavailable, Toast.LENGTH_SHORT).show()
            }
        },
        requestMediaAccess = {
            runCatching { mediaAccessLauncher.launch(MediaAccess.settingsIntent(context)) }
                .onFailure { openSystemApp(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        },
        controlMedia = { sessionId, command ->
            if (!viewModel.controlMedia(sessionId, command)) {
                val fallback = if (sessionId == IdleMediaSessionId) uiState.settings.mediaAppKey?.let(uiState::findItem) else null
                if (fallback != null) launchApp(fallback, null)
                else Toast.makeText(context, R.string.media_control_unavailable, Toast.LENGTH_SHORT).show()
            }
        },
        requestDefaultHome = {
            runCatching { homeRoleLauncher.launch(DefaultHome.requestIntent(context)) }
                .onFailure { openSystemApp(Intent(Settings.ACTION_HOME_SETTINGS)) }
        },
        appInfo = { app ->
            if (app.user != null) openPrivateAppAction(app) {
                context.getSystemService(LauncherApps::class.java).startAppDetailsActivity(
                    app.componentName, requireNotNull(app.user), null, null)
            } else openSystemApp(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null)))
        },
        screenTime = { app ->
            if (Build.VERSION.SDK_INT >= 29) {
                val intent = Intent(Settings.ACTION_APP_USAGE_SETTINGS).putExtra(Intent.EXTRA_PACKAGE_NAME, app.packageName)
                if (app.user != null) openPrivateAppAction(app) {
                    context.startActivity(intent.putExtra(Intent.EXTRA_USER, requireNotNull(app.user)))
                } else openSystemApp(intent)
            } else Toast.makeText(context, R.string.action_unavailable, Toast.LENGTH_SHORT).show()
        },
        uninstall = { app ->
            val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
            if (app.user != null) openPrivateAppAction(app) {
                context.startActivity(intent.putExtra(Intent.EXTRA_USER, requireNotNull(app.user)))
            } else openSystemApp(intent)
        },
        rename = viewModel::renameApp,
        setItemIcon = viewModel::setItemIcon,
        importItemIcon = viewModel::importItemIcon,
        showShortcutInAppList = viewModel::showShortcutInAppList,
        rememberShortcut = viewModel::rememberShortcut,
        updatePopup = viewModel::updatePopup,
        addPopupWidget = { owner, defaults ->
            lifecycleOwner.lifecycleScope.launch {
                if (viewModel.ensurePopup(owner, defaults)) openSystemApp(Intent(context, WidgetSetupActivity::class.java)
                    .putExtra(WidgetSetupActivity.EXTRA_POPUP_OWNER, owner.key))
                else Toast.makeText(context, R.string.settings_storage_save_error, Toast.LENGTH_SHORT).show()
            }
        },
        categorize = viewModel::categorize,
        copyPackageName = { app ->
            context.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText(app.label, app.packageName))
            Toast.makeText(context, R.string.package_name_copied, Toast.LENGTH_SHORT).show()
        },
        storePage = { app ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.Builder().scheme("market").authority("details")
                .appendQueryParameter("id", app.packageName).build())
            // Leave the target implicit and always show Android's chooser, even
            // when a default store has been set. No Play Store/web fallback.
            if (intent.resolveActivity(context.packageManager) != null) {
                openSystemApp(Intent.createChooser(intent, chooseAppStoreTitle))
            } else Toast.makeText(context, R.string.app_store_unavailable, Toast.LENGTH_SHORT).show()
        },
        newEvent = {
            openSystemApp(Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, System.currentTimeMillis())
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, System.currentTimeMillis() + 3_600_000))
        },
        openEvent = {
            openSystemApp(Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, it.id))
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it.startsAt.toEpochMilli())
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it.endsAt.toEpochMilli()))
        },
        refreshAgenda = viewModel::refreshSchedule,
        textMode = viewModel::setTextMode,
        themedIcons = viewModel::setThemedIcons,
        refreshIconPacks = viewModel::refreshApps,
        refreshApps = viewModel::refreshApps,
        applyClockStyle = viewModel::applyClockStyle,
        applyIconDesign = viewModel::applyIconDesign,
        deleteIconDesigns = viewModel::deleteIconDesigns,
        updateSettings = { change ->
            val enableCalendar = change(uiState.settings).calendarAgenda && !uiState.settings.calendarAgenda
            viewModel.updateSettings(change)
            if (enableCalendar) {
                permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
            }
        },
        setHiddenApps = viewModel::setHiddenApps,
        setFocusActive = viewModel::setFocusActive,
        setFocusApps = viewModel::setFocusApps,
        exportBackup = viewModel::exportBackup,
        importBackup = viewModel::importBackup,
        savePreset = viewModel::savePreset,
        applyPreset = viewModel::applyPreset,
        deletePreset = viewModel::deletePreset,
        presets = viewModel.presets,
        saveFolder = viewModel::saveFolder,
        updateFolder = viewModel::updateFolder,
        setFolderFavorites = viewModel::setFolderFavorites,
        deleteFolder = viewModel::deleteFolder,
        shortcuts = viewModel::loadShortcuts,
        cachedShortcuts = viewModel::cachedShortcuts,
        prepareShortcuts = viewModel::prepareShortcuts,
        reorderFavorites = viewModel::reorderFavorites,
        launchAppAt = { app, bounds -> launchApp(app, bounds) },
        launchSearchApp = { app, bounds -> launchApp(app, bounds, fromSearch = true) },
        launchShortcutAt = { shortcut, bounds -> launchShortcut(shortcut, bounds) },
        launchShortcut = { launchShortcut(it, null) },
    )
    if (settingsOnly) {
        // Do not register a root back callback here: Android owns the predictive
        // cross-task/back-to-home animation of this regular settings Activity.
        val lightBars = MaterialTheme.colorScheme.surface.luminance() > 0.5f
        SideEffect {
            activity?.window?.let { window ->
                WindowInsetsControllerCompat(window, launchView).apply {
                    isAppearanceLightStatusBars = lightBars
                    isAppearanceLightNavigationBars = lightBars
                }
            }
        }
        LauncherSettingsScreen(uiState, actions, onCloseSettings, handleRootBack = false)
    } else {
        LauncherScreen(
            uiState = uiState,
            widgetEditRequest = widgetEditRequest,
            returnHomeRequests = viewModel.returnHomeRequests,
            onDateClick = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) },
            onClockClick = {
                val key = uiState.settings.clockAppKey
                if (key?.startsWith("profile:") == true) {
                    val app = uiState.allApps.firstOrNull { it.key == key }
                    if (app != null) launchApp(app, null)
                    else Toast.makeText(context, R.string.clock_app_unavailable, Toast.LENGTH_LONG).show()
                } else {
                    when (ClockLauncher.open(context, key)) {
                        ClockLaunchResult.Opened -> Unit
                        ClockLaunchResult.NoHandler -> {
                            Toast.makeText(context,
                                if (key == null) R.string.clock_default_unavailable else R.string.clock_app_unavailable,
                                Toast.LENGTH_LONG).show()
                        }
                        ClockLaunchResult.Failed -> Toast.makeText(context, R.string.clock_app_unavailable, Toast.LENGTH_LONG).show()
                    }
                }
            },
            onLaunchApp = { app -> launchApp(app, null) },
            onToggleFavorite = viewModel::toggleFavorite,
            actions = actions,
        )
    }
}

@Composable
internal fun LauncherScreen(
    uiState: LauncherUiState,
    returnHomeRequests: Flow<Unit> = emptyFlow(),
    onDateClick: () -> Unit,
    onClockClick: () -> Unit,
    onLaunchApp: (LauncherApp) -> Unit,
    onToggleFavorite: (LauncherApp) -> Unit,
    actions: LauncherActions = LauncherActions(),
    initialDrawerOpen: Boolean = false,
    widgetEditRequest: Int = 0,
) {
    var drawerOpen by rememberSaveable { mutableStateOf(initialDrawerOpen) }
    var selectedLetter by remember { mutableStateOf<String?>(null) }
    var overlay by remember { mutableStateOf<LauncherOverlay?>(null) }
    var systemActionAccess by rememberSaveable { mutableStateOf(false) }
    var buttonTransition by remember { mutableStateOf<NivaButtonTransition?>(null) }
    var instantButtonEntrance by remember { mutableStateOf(false) }
    val currentButtonTransition by rememberUpdatedState(buttonTransition)
    val searchTransition = buttonTransition?.takeIf { it.action == NivaButtonAction.Search }
    val drawerTransition = buttonTransition?.takeIf { it.action == NivaButtonAction.AppList }
    val searchQuery = rememberTextFieldState()
    var editingHome by rememberSaveable { mutableStateOf(false) }
    var privateListOpen by remember { mutableStateOf(false) }
    var workListOpen by remember { mutableStateOf(false) }
    var privateSwipeAnchor by remember { mutableStateOf<Rect?>(null) }
    var privateFolderWasOpen by remember { mutableStateOf(false) }
    val latestUiState by rememberUpdatedState(uiState)
    val privateExpanded = privateListOpen && uiState.privateContentVisible && uiState.settings.privateSpace.display == PrivateSpaceDisplay.List
    val privateFolder = uiState.privateFolder.takeIf {
        uiState.settings.privateSpace.enabled && uiState.settings.privateSpace.display != PrivateSpaceDisplay.NormalApp &&
            uiState.privateSpace.supported && !uiState.isLoadingSettings && !uiState.settingsLoadFailed
    }
    val appListApps = uiState.appListApps
    val workFolder = uiState.workFolder.takeIf {
        uiState.settings.workProfile.enabled && uiState.settings.workProfile.display != PrivateSpaceDisplay.NormalApp &&
            uiState.workProfiles.isNotEmpty() && !uiState.isLoadingSettings && !uiState.settingsLoadFailed
    }
    val workExpanded = workListOpen && workFolder != null && uiState.settings.workProfile.display == PrivateSpaceDisplay.List
    val workApps = uiState.workProfileApps.filterNot { it.key in uiState.hiddenAppKeys }
    val privateApps = uiState.privateSpaceApps
    val recentFolderName = stringResource(R.string.recently_installed)
    val recentFolder = remember(uiState.apps, recentFolderName) { uiState.recentlyInstalledFolder(recentFolderName) }
    val model = remember(appListApps, uiState.folders, privateFolder, privateExpanded, privateApps, recentFolder, workFolder, workExpanded, workApps) {
        AppListModel(appListApps, uiState.folders, privateFolder, privateExpanded, privateApps, recentFolder, workFolder, workExpanded, workApps)
    }
    val drawerState = rememberLazyListState()
    val appearance = rememberLauncherAppearance(uiState.textMode, uiState.themedIcons, uiState.settings.iconDesign?.design?.iconSize ?: 100)
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            val transition = currentButtonTransition ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    transition.leftForeground = true
                    // Never finish a pending lock after the user has switched away.
                    // A committed lock retains the black frame throughout screen-off.
                    if (!transition.lockCommitted) buttonTransition = null
                }
                Lifecycle.Event.ON_RESUME -> if (transition.leftForeground) buttonTransition = null
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(buttonTransition) {
        val transition = buttonTransition ?: return@LaunchedEffect
        transition.animate()
        if (transition.action != NivaButtonAction.LockScreen) {
            buttonTransition = null
        } else {
            // Let Compose submit the fully black endpoint before asking Android
            // to lock. Clearing it on animation completion exposes a bright HOME frame.
            withFrameNanos { }
            withFrameNanos { }
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                buttonTransition = null
                return@LaunchedEffect
            }
            transition.lockCommitted = true
            if (!NivaSystemActions.perform(context, NivaButtonTarget(NivaButtonAction.LockScreen))) {
                buttonTransition = null
                Toast.makeText(context, R.string.niva_action_unavailable, Toast.LENGTH_SHORT).show()
            } else {
                // Some OEMs accept the action but do not execute it. Do not leave
                // an interactive, unlocked phone permanently covered by our mask.
                delay(1_500)
                if (!transition.leftForeground && context.getSystemService(PowerManager::class.java)?.isInteractive == true &&
                    context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == false) {
                    buttonTransition = null
                    Toast.makeText(context, R.string.niva_action_unavailable, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    val haptics = rememberLauncherHaptics(uiState.settings.allowHapticFeedback)
    val searching = overlay == LauncherOverlay.Search
    val searchAlpha by animateFloatAsState(if (searching) 1f else 0f,
        if (instantButtonEntrance || searchTransition != null) androidx.compose.animation.core.snap() else tween(210), label = "searchFade")
    LaunchedEffect(searching, drawerOpen) { if (!searching && !drawerOpen) instantButtonEntrance = false }
    val isSettings = overlay == LauncherOverlay.Settings || overlay is LauncherOverlay.FolderSettings ||
        overlay is LauncherOverlay.SettingsDestination || overlay is LauncherOverlay.IconDesigner
    val fullScreen = isSettings || overlay == LauncherOverlay.Search || overlay == LauncherOverlay.Favorites ||
        overlay is LauncherOverlay.IconDesigner || overlay is LauncherOverlay.EditPopup
    val privateAppEditing = when (val current = overlay) {
        is LauncherOverlay.AppDetails -> current.app.isPrivateSpace
        is LauncherOverlay.IconDesigner -> current.app.isPrivateSpace
        is LauncherOverlay.EditPopup -> current.app.isPrivateSpace
        is LauncherOverlay.Shortcuts -> current.app.isPrivateSpace
        is LauncherOverlay.Categories -> current.app.isPrivateSpace
        else -> false
    }
    LaunchedEffect(uiState.privateContentVisible) {
        if (!uiState.privateContentVisible) {
            privateListOpen = false
            if ((overlay as? LauncherOverlay.Folder)?.folder?.id == PrivateSpaceFolderId || privateAppEditing) overlay = null
        }
    }
    var observedLockVersion by remember { mutableStateOf(uiState.privateSpace.lockVersion) }
    LaunchedEffect(uiState.privateSpace.lockVersion) {
        if (observedLockVersion != uiState.privateSpace.lockVersion) {
            privateListOpen = false
            // Public metadata remains available after a native lock, but its
            // folder/list must close and a details page must not reopen it.
            overlay = when (val current = overlay) {
                is LauncherOverlay.Folder -> current.takeUnless { it.folder.id == PrivateSpaceFolderId }
                is LauncherOverlay.AppDetails -> if (current.app.isPrivateSpace) current.copy(returnTo = null) else current
                is LauncherOverlay.IconDesigner -> if (current.app.isPrivateSpace) current.copy(returnTo = null) else current
                else -> current
            }
        }
        observedLockVersion = uiState.privateSpace.lockVersion
    }
    LaunchedEffect(drawerOpen, overlay, privateListOpen, uiState.settings.privateSpace.display, uiState.settings.privateSpace.locksOnExit) {
        val editingPrivate = (overlay as? LauncherOverlay.SettingsDestination)?.page == "PrivateSpaceEditor"
        val leavingList = !drawerOpen || (overlay != null && !privateAppEditing)
        if (privateListOpen && (uiState.settings.privateSpace.display != PrivateSpaceDisplay.List || editingPrivate ||
                (leavingList && uiState.settings.privateSpace.locksOnExit))) {
            privateListOpen = false
            if (!editingPrivate) actions.closePrivateSpace()
        }
        val folderOpen = (overlay as? LauncherOverlay.Folder)?.folder?.id == PrivateSpaceFolderId
        if (privateFolderWasOpen && !folderOpen && !editingPrivate && !privateAppEditing) actions.closePrivateSpace()
        privateFolderWasOpen = folderOpen || (privateFolderWasOpen && privateAppEditing)
        if (folderOpen && (!drawerOpen || uiState.settings.privateSpace.display != PrivateSpaceDisplay.Folder)) overlay = null
    }
    val screenActions = actions.copy(moveWidget = { drawerOpen = false; selectedLetter = null; overlay = null; editingHome = true })
    LaunchedEffect(drawerOpen, workFolder, uiState.settings.workProfile.display) {
        if (!drawerOpen || workFolder == null || uiState.settings.workProfile.display != PrivateSpaceDisplay.List) workListOpen = false
    }
    val backProgress = remember { Animatable(0f) }
    val darkSystemIcons = if (fullScreen && !searching) MaterialTheme.colorScheme.surface.luminance() > 0.5f else appearance.darkText
    val statusBarPull = rememberLauncherSystemBars(
        autoHide = uiState.settings.hideStatusBar && (!fullScreen || searching),
        darkIcons = darkSystemIcons,
        allowPullDown = !fullScreen && !editingHome && overlay == null && buttonTransition == null,
    )

    LaunchedEffect(returnHomeRequests) {
        returnHomeRequests.collect {
            drawerOpen = false; selectedLetter = null; overlay = null; editingHome = false
            if (buttonTransition?.lockCommitted != true) buttonTransition = null
        }
    }
    LaunchedEffect(widgetEditRequest) {
        if (widgetEditRequest > 0) { drawerOpen = false; selectedLetter = null; overlay = null; editingHome = true }
    }
    LaunchedEffect(model.letters) {
        if (selectedLetter !in model.letters) selectedLetter = null
    }
    BackHandler(enabled = buttonTransition == null && overlay != LauncherOverlay.Search && !(overlay == null && drawerOpen)) {
        when {
            overlay != null -> overlay = (overlay as? LauncherOverlay.AppDetails)?.returnTo
            editingHome -> editingHome = false
            selectedLetter != null -> selectedLetter = null
            else -> drawerOpen = false
        }
    }
    PredictiveBackHandler(enabled = buttonTransition == null && (overlay == LauncherOverlay.Search || (overlay == null && drawerOpen))) { events ->
        val fromSearch = overlay == LauncherOverlay.Search
        try {
            events.collect { backProgress.snapTo(it.progress) }
            backProgress.animateTo(1f, tween(180))
            if (fromSearch) overlay = null else drawerOpen = false
            selectedLetter = null
            backProgress.snapTo(0f)
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { backProgress.animateTo(0f, tween(180)) }
            throw cancelled
        }
    }
    BackHandler(enabled = buttonTransition != null) {
        if (buttonTransition?.lockCommitted != true) {
            buttonTransition = null
            overlay = null
            drawerOpen = false
        }
    }

    val drawerAlpha = animateFloatAsState(
        targetValue = if (drawerOpen) 1f else 0f,
        animationSpec = if (instantButtonEntrance || drawerTransition != null) androidx.compose.animation.core.snap() else tween(110),
        label = "appListFade",
    )
    val primary = MaterialTheme.colorScheme.primary
    val wallpaperTint = remember(primary, appearance.darkText) {
        // Adjust primary's tone, not a black scrim. Dark text gets the matching
        // light tint so either text mode gains contrast against the wallpaper.
        primary.toDynamicScheme(isDark = !appearance.darkText, style = PaletteStyle.TonalSpot)
            .primaryPalette.toneColor(if (appearance.darkText) 95 else 10)
    }

    val finishScrubbing: () -> Unit = {
        // The list is already positioned. Only remove the temporary mask;
        // scrolling here would cause a jump between the held and released views.
        selectedLetter = null
    }
    val rowGestures = AppRowGestures(
        onPrepare = actions.prepareShortcuts,
        onLaunchAt = actions.launchAppAt,
        onDrag = { app, bounds, expanded ->
            val current = overlay as? LauncherOverlay.Shortcuts
            if (current?.app?.key == app.key) {
                current.reveal.expanded = expanded
            } else {
                overlay = LauncherOverlay.Shortcuts(app, bounds, ShortcutRevealState(expanded, dragging = true))
            }
        },
        onDragEnd = { commit ->
            (overlay as? LauncherOverlay.Shortcuts)?.reveal?.let {
                it.expanded = commit
                it.dragging = false
            }
        },
    )
    val highlightedAppKey = (overlay as? LauncherOverlay.AppDetails)?.app?.key
    val openPrivateSpace: (Rect) -> Unit = { bounds ->
        when {
            uiState.isDefaultHome == false -> actions.requestDefaultHome()
            uiState.privateSpace.user == null -> actions.openPrivateSpaceSettings()
            else -> actions.requestPrivateSpace(false) {
                workListOpen = false
                drawerOpen = true
                selectedLetter = null
                if (latestUiState.settings.privateSpace.display == PrivateSpaceDisplay.List) privateListOpen = true
                else if (latestUiState.settings.privateSpace.display == PrivateSpaceDisplay.Folder)
                    overlay = LauncherOverlay.Folder(latestUiState.privateFolder, bounds)
            }
        }
    }
    val editPrivateSpace: () -> Unit = {
        if (uiState.privateSpace.user == null) actions.openPrivateSpaceSettings()
        else actions.requestPrivateSpace(true) {
            privateListOpen = false
            overlay = LauncherOverlay.SettingsDestination("PrivateSpaceEditor")
        }
    }
    val openFolder: (LauncherFolder, Rect) -> Unit = { folder, bounds ->
        when {
            folder.id == PrivateSpaceFolderId -> openPrivateSpace(bounds)
            folder.id == WorkProfileFolderId && uiState.settings.workProfile.display == PrivateSpaceDisplay.List -> {
                privateListOpen = false
                actions.closePrivateSpace()
                workListOpen = !workListOpen
                selectedLetter = null
            }
            else -> overlay = LauncherOverlay.Folder(folder, bounds)
        }
    }
    val editFolder: (LauncherFolder) -> Unit = {
        when (it.id) {
            PrivateSpaceFolderId -> editPrivateSpace()
            WorkProfileFolderId -> overlay = LauncherOverlay.SettingsDestination("WorkProfileEditor")
            else -> overlay = LauncherOverlay.AppDetails(uiState.folderItem(it))
        }
    }
    val dragFolder: (LauncherFolder, Rect, Boolean) -> Unit = { folder, bounds, expanded ->
        val canRevealPrivateFolder = uiState.settings.privateSpace.display == PrivateSpaceDisplay.Folder &&
            uiState.privateContentVisible && uiState.isDefaultHome != false
        // Once its contents are available, a private folder uses the same live
        // reveal/reversal as ordinary folders. Defer only the credential UI or
        // inline-list opening until the row's pointer gesture has finished.
        if (folder.id == PrivateSpaceFolderId && !canRevealPrivateFolder) privateSwipeAnchor = bounds
        else {
            val current = overlay as? LauncherOverlay.Folder
            if (current?.folder?.id == folder.id) current.reveal.expanded = expanded
            else overlay = LauncherOverlay.Folder(folder, bounds, ShortcutRevealState(expanded, dragging = true))
        }
    }
    val endFolderDrag: (Boolean) -> Unit = { commit ->
        val privateAnchor = privateSwipeAnchor
        privateSwipeAnchor = null
        if (privateAnchor != null) { if (commit) openPrivateSpace(privateAnchor) }
        else (overlay as? LauncherOverlay.Folder)?.reveal?.let { it.expanded = commit; it.dragging = false }
    }
    val popupReveal = when (val current = overlay) {
        is LauncherOverlay.Shortcuts -> current.reveal
        is LauncherOverlay.Folder -> current.reveal
        else -> null
    }
    // Once a predictive pop commits, keep HOME fully visible instead of fading
    // it in a second time while the old drawer's exit alpha finishes settling.
    val drawerVisibility = if (drawerOpen) {
        drawerAlpha.value * if (overlay == null) (1f - backProgress.value) else 1f
    } else 0f
    val wallpaperEffectVisibility = when {
        searchTransition != null -> searchTransition.searchBackground
        drawerTransition != null -> drawerTransition.fraction
        searching -> searchAlpha * (1f - backProgress.value)
        fullScreen -> 0f
        else -> drawerVisibility
    }
    WallpaperBlur(if (uiState.settings.blurWallpaper) uiState.settings.wallpaperBlurRadius.dp * wallpaperEffectVisibility else 0.dp)
    val dimAlpha = if (uiState.settings.dimWallpaper) {
        uiState.settings.wallpaperDimAmount.coerceIn(0, 100) / 100f * wallpaperEffectVisibility
    } else 0f
    // Preserve the existing subtle contrast treatment on HOME. The app list
    // gets only the optional primary-tinted overlay, never this black scrim.
    val homeScrimOpacity = if (appearance.darkText) 0f else 0.08f * (if (drawerTransition != null) 1f else 1f - drawerVisibility)
    val homeScrim = remember(homeScrimOpacity) {
        Brush.verticalGradient(listOf(
            Color.Black.copy(alpha = homeScrimOpacity),
            Color.Black.copy(alpha = homeScrimOpacity * 0.6f),
            Color.Black.copy(alpha = homeScrimOpacity),
        ))
    }
    // Both gesture surfaces use this dispatcher and the same target model.
    // Only a real button gesture morphs the button into search/the app list.
    fun performGestureAction(target: NivaButtonTarget, origin: NivaButtonOrigin, fromButton: Boolean = true) {
        if (!target.active || buttonTransition != null || systemActionAccess) return
        val buttonSettings = uiState.settings.nivaButton
        instantButtonEntrance = fromButton && !buttonSettings.animationsEnabled &&
            target.action in listOf(NivaButtonAction.Search, NivaButtonAction.AppList)
        when (target.action) {
            NivaButtonAction.Search -> {
                searchQuery.edit { replace(0, length, "") }
                if (fromButton && buttonSettings.animationsEnabled && uiState.settings.search.enabled)
                    buttonTransition = NivaButtonTransition(target.action, origin, durationMillis = buttonSettings.searchAnimationDurationMs)
                overlay = LauncherOverlay.search(uiState.settings.search.enabled)
            }
            NivaButtonAction.Settings -> overlay = LauncherOverlay.Settings
            NivaButtonAction.App, NivaButtonAction.Shortcut -> {
                val app = target.itemKey?.let(uiState::findItem)
                if (app != null) onLaunchApp(app)
                else Toast.makeText(context, R.string.app_unavailable, Toast.LENGTH_SHORT).show()
            }
            NivaButtonAction.AppList -> {
                if (fromButton && buttonSettings.animationsEnabled)
                    buttonTransition = NivaButtonTransition(target.action, origin, durationMillis = buttonSettings.appListAnimationDurationMs)
                selectedLetter = null
                overlay = null
                drawerState.requestScrollToItem(0)
                drawerOpen = true
            }
            NivaButtonAction.Agenda -> {
                actions.refreshWeather()
                actions.refreshAgenda()
                overlay = LauncherOverlay.Agenda
            }
            NivaButtonAction.LockScreen -> {
                if (!NivaSystemActions.hasAccessibility) systemActionAccess = true
                else buttonTransition = NivaButtonTransition(target.action, origin, originIsButton = fromButton,
                    animationsEnabled = !fromButton || buttonSettings.animationsEnabled)
            }
            NivaButtonAction.Website, NivaButtonAction.Assistant,
            NivaButtonAction.Notifications, NivaButtonAction.QuickSettings -> {
                if (target.action.requiresAccessibility && !NivaSystemActions.hasAccessibility) systemActionAccess = true
                else if (!NivaSystemActions.perform(context, target))
                    Toast.makeText(context, R.string.niva_action_unavailable, Toast.LENGTH_SHORT).show()
            }
            NivaButtonAction.Disabled -> Unit
        }
    }
    val homeGestureRadius = with(LocalDensity.current) { 24.dp.toPx() }
    val widgetHost = rememberWidgetHost()
    CompositionLocalProvider(LocalLauncherAppearance provides appearance, LocalHapticFeedback provides haptics, LocalWidgetHost provides widgetHost) {
      // One wallpaper treatment serves both transparent search and the alphabetical app list.
      val revealDimAlpha = if (drawerTransition != null && uiState.settings.dimWallpaper)
          uiState.settings.wallpaperDimAmount.coerceIn(0, 100) / 100f else dimAlpha
      Box(Modifier.fillMaxSize().nivaReveal(drawerTransition, reveal = true).background(wallpaperTint.copy(alpha = revealDimAlpha)))
      CompositionLocalProvider(LocalLauncherInputEnabled provides (!fullScreen && !editingHome && backProgress.value == 0f && buttonTransition == null)) {
      BoxWithConstraints(
        // Settings can reveal this retained page during a predictive root back.
        // It remains non-interactive and absent from accessibility while covered.
        modifier = Modifier.fillMaxSize()
            .nestedScroll(statusBarPull)
            .retainedPage(visible = !fullScreen || isSettings || backProgress.value > 0f || searchTransition != null)
            .then(if (fullScreen || backProgress.value > 0f || buttonTransition != null) Modifier.clearAndSetSemantics {} else Modifier)
            .graphicsLayer { alpha = if (searching) searchTransition?.let { 1f - it.searchBackground } ?: backProgress.value else 1f }
            .then(if (drawerTransition == null) Modifier.background(homeScrim) else Modifier)
            // The lists extend behind both system bars. Insets belong to their
            // scrollable content, not a parent that clips the whole viewport.
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .then(if (popupReveal?.dragging == false) Modifier.clearAndSetSemantics {} else Modifier),
    ) {
        val statusBarHeight = stableStatusBarInset()
        val bottomInset = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
        val safeHeight = (maxHeight - statusBarHeight - bottomInset).coerceAtLeast(0.dp)
        val regularHomeTop = (safeHeight * if (safeHeight < 600.dp) 0.12f else 0.32f).coerceIn(24.dp, 320.dp)
        // Variable sections extend below the clock; they never reposition the home anchor.
        val homeTop = statusBarHeight + regularHomeTop
        val drawerTop = statusBarHeight + safeHeight * 0.28f
        val railHeight = ((model.letters.size + 1) * 18).dp.coerceAtMost(safeHeight * 0.65f)
        val railTop = statusBarHeight + (safeHeight * 0.39f).coerceAtMost(safeHeight - railHeight - 72.dp).coerceAtLeast(0.dp)

        CompositionLocalProvider(LocalHomeAnimationTarget provides true) {
            HomeScreen(
                uiState = uiState,
                topSpace = homeTop,
                onLaunchApp = onLaunchApp,
                onAppDetails = { overlay = LauncherOverlay.AppDetails(it) },
                onAppShortcuts = { app, bounds -> overlay = LauncherOverlay.Shortcuts(app, bounds) },
                onDateClick = {
                    actions.refreshWeather()
                    actions.refreshAgenda()
                    overlay = LauncherOverlay.Agenda
                },
                onClockClick = onClockClick,
                onWidgetMenu = { overlay = LauncherOverlay.HomeWidgetMenu },
                onCustomWidgetMenu = { overlay = LauncherOverlay.CustomWidgetMenu },
                editingLayout = editingHome,
                widgetInputEnabled = !drawerOpen && overlay == null && buttonTransition == null && !systemActionAccess,
                onHomeGesture = { gesture, position ->
                    if (!drawerOpen && overlay == null && !editingHome) {
                        val origin = NivaButtonOrigin(Rect(position.x - homeGestureRadius, position.y - homeGestureRadius,
                            position.x + homeGestureRadius, position.y + homeGestureRadius))
                        performGestureAction(uiState.settings.homeGestures.target(gesture), origin, fromButton = false)
                    }
                },
                onTopOffsetChange = { offset -> actions.updateSettings { it.copy(homeLayout = it.homeLayout.copy(topOffsetDp = offset)) } },
                onWidgetHeightChange = { height -> actions.updateSettings { it.copy(homeLayout = it.homeLayout.copy(widgetHeightDp = height)) } },
                rowGestures = rowGestures,
                highlightedAppKey = highlightedAppKey,
                onOpenFolder = openFolder,
                onEditFolder = editFolder,
                onFolderDrag = dragFolder,
                onFolderDragEnd = endFolderDrag,
                onMediaCommand = actions.controlMedia,
                onDismissMedia = actions.dismissMedia,
                modifier = Modifier.retainedPage(visible = !drawerOpen || drawerTransition != null || (overlay == null && backProgress.value > 0f))
                    .graphicsLayer { alpha = if (drawerTransition != null) 1f else 1f - drawerVisibility }
                    .nivaReveal(drawerTransition, reveal = false)
                    .then(if (drawerTransition != null) Modifier.background(homeScrim) else Modifier).statusBarContentFade(),
            )
        }

        // One complete list and one scroll state for both held and released
        // views. Content padding anchors headings without reserving a viewport.
        AppDrawerScreen(
            model = model,
            folderApps = (uiState.folders + listOfNotNull(workFolder, privateFolder)).associate { it.id to uiState.folderItem(it) },
            notifications = uiState.notifications,
            listState = drawerState,
            selectedLetter = selectedLetter,
            topSpace = drawerTop,
            onLaunchApp = onLaunchApp,
            onAppDetails = { overlay = LauncherOverlay.AppDetails(it) },
            onAppShortcuts = { app, bounds -> overlay = LauncherOverlay.Shortcuts(app, bounds) },
            rowGestures = rowGestures,
            highlightedAppKey = highlightedAppKey,
            onOpenFolder = openFolder,
            onEditFolder = editFolder,
            onFolderDrag = dragFolder,
            onFolderDragEnd = endFolderDrag,
            privateExpanded = privateExpanded,
            privateAppsPublic = uiState.settings.privateSpace.exposesApps,
            privateLoading = uiState.privateAppsLoading,
            privateFailed = uiState.privateAppsFailed,
            onPrivateSpaceSettings = actions.openPrivateSpaceSettings,
            onRetryPrivateSpace = { actions.requestPrivateSpace(true, actions.refreshApps) },
            workExpanded = workExpanded,
            onWorkProfileSettings = actions.openWorkProfileSettings,
            modifier = Modifier.retainedPage(visible = drawerOpen)
                .graphicsLayer { alpha = if (drawerTransition != null) 1f else drawerVisibility }
                .nivaReveal(drawerTransition, reveal = true).statusBarContentFade(),
        )

        if (!editingHome) AlphabetRail(
            letters = model.letters,
            selectedLetter = if (drawerOpen) selectedLetter else null,
            height = railHeight,
            onLetterSelected = { letter ->
                if (letter == null) {
                    drawerOpen = false
                    selectedLetter = null
                } else {
                    selectedLetter = letter
                    // Offset zero uses the list's leading content padding.
                    // LazyColumn clamps this request when the remaining apps
                    // cannot fill the viewport; do not append phantom space.
                    drawerState.requestScrollToItem(model.indexOfSection(letter))
                    drawerOpen = true
                }
            },
            modifier = Modifier.align(Alignment.TopEnd).offset(y = railTop)
                .nivaReveal(drawerTransition.takeIf { uiState.settings.hideAlphabet }, reveal = true),
            onScrubFinished = finishScrubbing,
            autoHide = uiState.settings.hideAlphabet && !drawerOpen,
        )
        if (!drawerOpen && (editingHome || uiState.settings.nivaButton.enabled)) {
            NivaButton(settings = uiState.settings.nivaButton, editing = editingHome,
                artwork = uiState.nivaButtonApp?.icon.takeIf { NivaButtonIcon.key in uiState.itemIcons },
                design = NivaButtonIcon.choice(uiState.itemIcons[NivaButtonIcon.key]).design ?: NivaButtonIcon.defaults,
                enabled = overlay == null && !systemActionAccess && buttonTransition == null,
                hidden = searchTransition != null,
                heldOrigin = buttonTransition?.takeIf { it.action == NivaButtonAction.LockScreen && it.originIsButton }?.origin,
                onGesture = { gesture, origin -> performGestureAction(uiState.settings.nivaButton.target(gesture), origin) },
                onFinishEditing = { editingHome = false },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = LauncherLayout.End, bottom = 22.dp + bottomInset))
        }
      }
      }
      LauncherOverlays(
          overlay = overlay, uiState = uiState, actions = screenActions, onChange = { overlay = it },
          onLaunchApp = onLaunchApp, onToggleFavorite = onToggleFavorite, onRequestCalendar = onDateClick,
          searchBackProgress = backProgress.value,
          searchEnterAlpha = searchAlpha, searchQuery = searchQuery,
          searchTransition = searchTransition,
      )
      if (systemActionAccess) NivaSystemAccessDialog { systemActionAccess = false }
      buttonTransition?.let { NivaButtonTransitionOverlay(it) }
    }
}

private fun Rect.toAndroidRect() = android.graphics.Rect(
    left.toInt(), top.toInt(), right.toInt(), bottom.toInt(),
)

private fun Modifier.retainedPage(visible: Boolean): Modifier =
    // A premeasured page must not leak hidden apps into TalkBack's tree.
    then(if (visible) Modifier else Modifier.clearAndSetSemantics {}).layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        if (visible) placeable.placeRelative(0, 0)
    }
}

private fun previewState(): LauncherUiState {
    val labels = listOf("Clock", "Chrome", "Calendar", "Camera", "Gmail", "Signal", "Discord", "Todoist", "Reddit")
    val apps = labels.map { label ->
        LauncherApp(ComponentName("preview.${label.lowercase()}", "$label.Activity"), label, null)
    }
    return LauncherUiState(
        apps = apps,
        favoriteKeys = apps.drop(4).mapTo(linkedSetOf(), LauncherApp::key),
        isLoadingApps = false,
        events = listOf(
            ScheduleEvent(
                id = 1L,
                title = "Movie night",
                startsAt = Instant.now().plus(Duration.ofMinutes(28)),
                endsAt = Instant.now().plus(Duration.ofHours(3)),
                isAllDay = false,
                location = null,
                calendarColor = null,
            ),
        ),
        scheduleStatus = ScheduleStatus.Ready,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF102430, widthDp = 390, heightDp = 844)
@Composable
private fun HomePreview() {
    NivaLauncherTheme(dynamicColor = false) {
        LauncherScreen(
            uiState = previewState(),
            onDateClick = {},
            onClockClick = {},
            onLaunchApp = {},
            onToggleFavorite = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF102430, widthDp = 390, heightDp = 844)
@Composable
private fun DrawerPreview() {
    NivaLauncherTheme(dynamicColor = false) {
        LauncherScreen(
            uiState = previewState(),
            onDateClick = {},
            onClockClick = {},
            onLaunchApp = {},
            onToggleFavorite = {},
            initialDrawerOpen = true,
        )
    }
}
