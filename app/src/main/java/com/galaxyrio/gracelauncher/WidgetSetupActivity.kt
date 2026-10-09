package com.galaxyrio.gracelauncher

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Bundle
import android.os.UserManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.galaxyrio.gracelauncher.data.LauncherDatabase
import com.galaxyrio.gracelauncher.data.LauncherSettingsRepository
import com.galaxyrio.gracelauncher.data.LauncherItemsRepository
import com.galaxyrio.gracelauncher.data.HomeLayout
import com.galaxyrio.gracelauncher.data.PopupItem
import com.galaxyrio.gracelauncher.platform.HomeWidgetHost
import com.galaxyrio.gracelauncher.ui.LauncherAppTheme
import com.galaxyrio.gracelauncher.ui.LauncherViewModel
import com.galaxyrio.gracelauncher.ui.widgets.WidgetPicker
import com.galaxyrio.gracelauncher.ui.theme.LauncherAppearance
import com.galaxyrio.gracelauncher.ui.theme.LocalLauncherAppearance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owns Android's binding/configuration round trips, including activity recreation. */
class WidgetSetupActivity : ComponentActivity() {
    private val viewModel: LauncherViewModel by viewModels()
    private val repository by lazy { LauncherSettingsRepository(LauncherDatabase.getInstance(this)) }
    private val itemsRepository by lazy { LauncherItemsRepository(LauncherDatabase.getInstance(this)) }
    private val popupOwner get() = intent.getStringExtra(EXTRA_POPUP_OWNER)
    private val manager by lazy { AppWidgetManager.getInstance(this) }
    private val host by lazy { HomeWidgetHost(this) }
    private var pendingId = -1
    private var expectedProvider: String? = null
    private var configuringExisting = false
    private var saving by mutableStateOf(false)
    private var busy by mutableStateOf(true)
    private var providers by mutableStateOf<List<AppWidgetProviderInfo>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingId = savedInstanceState?.getInt("pendingId", -1) ?: -1
        if (pendingId >= 0) host.trackPendingId(pendingId)
        expectedProvider = savedInstanceState?.getString("expectedProvider")
        configuringExisting = savedInstanceState?.getBoolean("configuringExisting") ?: false
        setContent {
            LauncherAppTheme(viewModel) {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                BackHandler(enabled = saving) { }
                CompositionLocalProvider(LocalLauncherAppearance provides LauncherAppearance(themedIcons = state.themedIcons,
                    iconSize = state.settings.iconDesign?.design?.iconSize ?: 100)) {
                    WidgetPicker(providers, busy, state, onBack = { if (!saving) cancelSetup() }, onSelect = ::select)
                }
            }
        }
        lifecycleScope.launch {
            try {
                val current = repository.snapshots.first().settings.homeLayout
                val popupWidgets = itemsRepository.snapshots.first().popups.values.flatten().mapNotNull { it.widget?.widgetId }.toSet()
                // Only discard this host's abandoned setup IDs; never touch the saved widget.
                host.appWidgetIds.filter { it != current.widgetId && it != pendingId && it !in popupWidgets && !host.isPendingId(it) }.forEach(host::deleteAppWidgetId)
                if (pendingId >= 0) {
                    if ((current.widgetId == pendingId || pendingId in popupWidgets) && !configuringExisting) { host.untrackPendingId(pendingId); pendingId = -1; finish() }
                    else if (savedInstanceState?.getBoolean("saving", false) == true) finishAdding()
                    // Otherwise the restored platform activity will deliver its result.
                    return@launch
                }
                val configureId = intent.getIntExtra(EXTRA_CONFIGURE_ID, -1)
                if (configureId >= 0) {
                    if (configureId != current.widgetId && configureId !in popupWidgets) { fail(R.string.widget_unavailable); return@launch }
                    pendingId = configureId
                    configuringExisting = true
                    configure()
                } else if (popupOwner == null && current.hasWidget) {
                    fail(R.string.widget_single_limit)
                } else {
                    providers = withContext(Dispatchers.IO) {
                        getSystemService(UserManager::class.java).userProfiles.filter { profile ->
                            if (android.os.Build.VERSION.SDK_INT < 35 || profile == android.os.Process.myUserHandle()) true
                            else runCatching {
                                val type = getSystemService(android.content.pm.LauncherApps::class.java).getLauncherUserInfo(profile)?.userType
                                type != null && type != UserManager.USER_TYPE_PROFILE_PRIVATE
                            }.getOrDefault(false)
                        }.flatMap { profile ->
                            runCatching { manager.getInstalledProvidersForProfile(profile) }.getOrDefault(emptyList())
                        }.distinctBy { it.provider.flattenToString() to it.profile }
                            .sortedBy { runCatching { it.loadLabel(packageManager) }.getOrDefault(it.provider.className).lowercase() }
                    }
                    busy = false
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                fail(R.string.widget_setup_error)
            }
        }
    }

    private fun select(info: AppWidgetProviderInfo) {
        if (busy) return
        busy = true
        try {
            pendingId = host.allocateAppWidgetId().also(host::trackPendingId)
            expectedProvider = info.provider.flattenToString()
            val options = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
            }
            if (manager.bindAppWidgetIdIfAllowed(pendingId, info.profile, info.provider, options)) configure()
            else startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options)
            }, REQUEST_BIND)
        } catch (_: Exception) { fail(R.string.widget_setup_error) }
    }

    private fun configure() {
        val info = runCatching { manager.getAppWidgetInfo(pendingId) }.getOrNull()
        if (info == null || (expectedProvider != null && info.provider.flattenToString() != expectedProvider)) {
            fail(R.string.widget_unavailable); return
        }
        if (info.configure == null) {
            if (configuringExisting) fail(R.string.widget_no_configuration) else finishAdding()
            return
        }
        try {
            // This API also handles providers in a managed/work profile.
            host.startAppWidgetConfigureActivityForResult(this, pendingId, 0, REQUEST_CONFIGURE, null)
        } catch (_: Exception) { fail(R.string.widget_no_configuration) }
    }

    @Deprecated("Platform AppWidgetHost configuration uses request codes")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_BIND && requestCode != REQUEST_CONFIGURE) return
        if (resultCode != Activity.RESULT_OK) { cancelSetup(); return }
        if (pendingId < 0) { fail(R.string.widget_setup_error); return }
        if (requestCode == REQUEST_BIND) configure()
        else if (configuringExisting) { host.untrackPendingId(pendingId); pendingId = -1; finish() }
        else finishAdding()
    }

    private fun finishAdding() {
        if (saving) return
        val info = runCatching { manager.getAppWidgetInfo(pendingId) }.getOrNull() ?: return fail(R.string.widget_unavailable)
        val id = pendingId
        saving = true
        lifecycleScope.launch {
            try {
                val layout = HomeLayout(widgetId = id, widgetProvider = info.provider.flattenToString(),
                    widgetLabel = runCatching { info.loadLabel(packageManager) }.getOrDefault(info.provider.className))
                val owner = popupOwner
                if (owner != null) {
                    itemsRepository.updatePopup(owner, emptyList()) { entries ->
                        if (entries.any { it.widget?.widgetId == id }) entries else entries + PopupItem("widget:$id", layout)
                    }
                } else repository.mutateSettings { current ->
                    // A recreated setup activity can resume just after the old
                    // transaction committed. Keep ownership transfer idempotent.
                    if (current.homeLayout.widgetId == id) return@mutateSettings current
                    check(!current.homeLayout.hasWidget) { "Only one home widget is supported" }
                    current.copy(homeLayout = current.homeLayout.copy(
                        widgetId = id, widgetProvider = info.provider.flattenToString(),
                        widgetLabel = runCatching { info.loadLabel(packageManager) }.getOrDefault(info.provider.className), widgetHeightDp = 0,
                    ))
                }
                host.untrackPendingId(pendingId)
                pendingId = -1 // The database owns it now; cancellation must not delete it.
                finish()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                fail(R.string.settings_storage_save_error)
            } finally { saving = false }
        }
    }

    private fun fail(message: Int) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        cancelSetup()
    }

    private fun cancelSetup() {
        if (!configuringExisting && pendingId >= 0) runCatching { host.deleteAppWidgetId(pendingId) }
        host.untrackPendingId(pendingId)
        pendingId = -1
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("pendingId", pendingId)
        outState.putString("expectedProvider", expectedProvider)
        outState.putBoolean("configuringExisting", configuringExisting)
        outState.putBoolean("saving", saving)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (isFinishing && !saving) host.untrackPendingId(pendingId)
        if (isFinishing && !configuringExisting && !saving && pendingId >= 0) runCatching { host.deleteAppWidgetId(pendingId) }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_POPUP_OWNER = "popupOwner"
        const val EXTRA_CONFIGURE_ID = "configureWidgetId"
        private const val REQUEST_BIND = 4001
        private const val REQUEST_CONFIGURE = 4002
    }
}
