package com.galaxyrio.gracelauncher.platform

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.Process
import android.os.UserManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.AppRepository
import com.galaxyrio.gracelauncher.data.FavoritesStore
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.LauncherDatabase
import com.galaxyrio.gracelauncher.data.LauncherItemsRepository
import com.galaxyrio.gracelauncher.data.LauncherSettingsRepository
import com.galaxyrio.gracelauncher.data.LauncherShortcut
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Keeps acceptance and persistence alive across activity recreation. Only saved IDs transfer ownership. */
class PinItemViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application
    private val launcher = context.getSystemService(LauncherApps::class.java)
    private val manager = AppWidgetManager.getInstance(context)
    private val host = HomeWidgetHost(context)
    private val database = LauncherDatabase.getInstance(context)
    private val settings = LauncherSettingsRepository(database)
    private var request: LauncherApps.PinItemRequest? = null
    private var initialized = false
    private var committingWidget = false
    var pendingWidgetId = -1
        private set
    var ready by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var finished by mutableStateOf(false)
        private set
    var added = false
        private set
    var error: Int? = null
        private set
    var isWidget = false
        private set
    var label = ""
        private set

    fun initialize(intent: Intent, restoredWidgetId: Int) {
        if (initialized) return
        initialized = true
        pendingWidgetId = restoredWidgetId
        request = runCatching { launcher.getPinItemRequest(intent) }.getOrNull()
        val pin = request
        val expectedType = when (intent.action) {
            LauncherApps.ACTION_CONFIRM_PIN_SHORTCUT -> LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT
            LauncherApps.ACTION_CONFIRM_PIN_APPWIDGET -> LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET
            else -> -1
        }
        isWidget = expectedType == LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET
        busy = true
        if (pendingWidgetId >= 0) host.trackPendingId(pendingWidgetId)
        viewModelScope.launch {
            try {
                val current = settings.snapshots.first().settings.homeLayout
                // Process recreation may happen just after the database took ownership.
                if (pendingWidgetId >= 0 && current.widgetId == pendingWidgetId) {
                    host.untrackPendingId(pendingWidgetId)
                    pendingWidgetId = -1
                    complete()
                    return@launch
                }
                if (pin == null || pin.requestType != expectedType || !valid(pin)) {
                    fail(R.string.pin_item_expired)
                    return@launch
                }
                label = if (isWidget) pin.getAppWidgetProviderInfo(context)?.loadLabel(context.packageManager).orEmpty()
                    else pin.shortcutInfo?.shortLabel?.toString().orEmpty()
                if (isWidget && current.hasWidget) {
                    fail(R.string.widget_single_limit)
                } else {
                    busy = pendingWidgetId >= 0 // A restored Android bind dialog will deliver its result.
                    ready = true
                }
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                fail(R.string.settings_storage_save_error)
            }
        }
    }

    private fun valid(pin: LauncherApps.PinItemRequest): Boolean =
        DefaultHome.isDefault(context) && runCatching { pin.isValid }.getOrDefault(false)

    fun confirm(startBind: (Intent) -> Unit) {
        if (!ready || busy || finished) return
        val pin = request ?: return fail(R.string.pin_item_expired)
        if (!valid(pin)) return fail(R.string.pin_item_expired)
        busy = true
        viewModelScope.launch {
            try {
                if (!isWidget) {
                    addShortcut(pin)
                } else {
                    if (settings.snapshots.first().settings.homeLayout.hasWidget) {
                        fail(R.string.widget_single_limit)
                        return@launch
                    }
                    val info = pin.getAppWidgetProviderInfo(context) ?: error("Missing widget provider")
                    pendingWidgetId = host.allocateAppWidgetId().also(host::trackPendingId)
                    val options = widgetOptions()
                    if (manager.bindAppWidgetIdIfAllowed(pendingWidgetId, info.profile, info.provider, options)) {
                        addWidget(pin, info)
                    } else startBind(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options)
                    })
                }
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                fail(if (isWidget) R.string.widget_setup_error else R.string.pin_item_error)
            }
        }
    }

    private suspend fun addShortcut(pin: LauncherApps.PinItemRequest) = withContext(Dispatchers.IO) {
        val info = pin.shortcutInfo ?: error("Missing shortcut")
        val user = info.userHandle
        val serial = if (user == Process.myUserHandle()) null else
            context.getSystemService(UserManager::class.java).getSerialNumberForUser(user).takeIf { it >= 0 }
                ?: error("Unknown profile")
        val activity = info.activity ?: launcher.getActivityList(info.`package`, user).firstOrNull()?.componentName
            ?: error("Missing shortcut activity")
        val shortcut = LauncherShortcut(info.id, info.`package`, info.shortLabel.toString(), null,
            activity, user, serial)
        val app = LauncherApp(activity, shortcut.label, null, shortcut = shortcut, user = user, userSerial = serial)
        // Initialize the existing favorite order before adding the requested item.
        val apps = AppRepository(context).loadApps()
        // Once Android acknowledges the request, finish saving even if the caller opens HOME.
        withContext(NonCancellable) {
            check(valid(pin) && pin.accept()) { "Request no longer valid" }
            LauncherItemsRepository(database).rememberShortcut(app, showInAppList = true)
            FavoritesStore(context).add(app.key, apps)
            withContext(Dispatchers.Main) { complete() }
        }
    }

    fun onBindResult(accepted: Boolean) {
        if (finished || pendingWidgetId < 0) return
        if (!accepted) { cancel(); return }
        val pin = request ?: return fail(R.string.pin_item_expired)
        viewModelScope.launch {
            try {
                val info = pin.getAppWidgetProviderInfo(context) ?: error("Missing widget provider")
                addWidget(pin, info)
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                fail(R.string.widget_setup_error)
            }
        }
    }

    private suspend fun addWidget(pin: LauncherApps.PinItemRequest, expected: AppWidgetProviderInfo) = withContext(NonCancellable) {
        committingWidget = true
        try {
            val id = pendingWidgetId
            val bound = manager.getAppWidgetInfo(id)
            check(bound != null && bound.provider == expected.provider && bound.profile == expected.profile)
            settings.mutateSettings { current ->
                check(!current.homeLayout.hasWidget) { "Home widget slot already occupied" }
                check(valid(pin) && pin.accept(widgetOptions().apply { putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, id) }))
                // Pin requests are already configured by the requesting app. Do not launch configure again.
                current.copy(homeLayout = current.homeLayout.copy(widgetId = id,
                    widgetProvider = expected.provider.flattenToString(), widgetLabel = label, widgetHeightDp = 0))
            }
            host.untrackPendingId(id)
            pendingWidgetId = -1
            complete()
        } finally { committingWidget = false }
    }

    private fun widgetOptions() = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
    }

    private fun complete() { added = true; busy = false; finished = true }
    fun fail(message: Int) { error = message; cancel() }
    fun cancel() {
        if (pendingWidgetId >= 0) {
            host.untrackPendingId(pendingWidgetId)
            runCatching { host.deleteAppWidgetId(pendingWidgetId) }
        }
        pendingWidgetId = -1
        busy = false
        finished = true
    }

    override fun onCleared() {
        if (!committingWidget && pendingWidgetId >= 0) {
            host.untrackPendingId(pendingWidgetId)
            runCatching { host.deleteAppWidgetId(pendingWidgetId) }
        }
        super.onCleared()
    }
}