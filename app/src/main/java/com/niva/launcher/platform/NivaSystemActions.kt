package com.niva.launcher.platform

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.niva.launcher.data.NivaButtonAction
import com.niva.launcher.data.NivaButtonTarget
import java.net.URI

/** Only explicit Niva-button actions; no scheduled or automatic system actions. */
internal object NivaSystemActions {
    val hasAccessibility: Boolean get() = NivaAccessibilityService.connected

    fun websiteUri(input: String): Uri? = runCatching {
        val text = input.trim()
        if (text.isEmpty() || text.length > 2048) return null
        val uri = URI(if ("://" in text) text else "https://$text")
        if (uri.scheme?.lowercase() !in setOf("https", "http") || uri.host.isNullOrBlank() ||
            uri.rawUserInfo != null || uri.port !in -1..65535) return null
        Uri.parse(uri.toASCIIString())
    }.getOrNull()

    fun perform(context: Context, target: NivaButtonTarget): Boolean = when (target.action) {
        NivaButtonAction.Website -> target.url?.let(::websiteUri)?.let { uri ->
            open(context, Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
        } ?: false
        NivaButtonAction.Assistant -> open(context, Intent(Intent.ACTION_ASSIST)) ||
            open(context, Intent(Intent.ACTION_VOICE_COMMAND))
        NivaButtonAction.LockScreen, NivaButtonAction.Notifications, NivaButtonAction.QuickSettings ->
            NivaAccessibilityService.perform(target.action)
        else -> false
    }

    fun openAccessibilitySettings(context: Context): Boolean = open(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    private fun open(context: Context, intent: Intent): Boolean = runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
}

/** No event subscriptions, window-content retrieval, key filtering or gesture injection. */
class NivaAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply { eventTypes = 0 }
        current = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        if (current === this) current = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (current === this) current = null
        super.onDestroy()
    }

    companion object {
        @Volatile private var current: NivaAccessibilityService? = null
        internal val connected: Boolean get() = current != null

        internal fun perform(action: NivaButtonAction): Boolean {
            val id = when (action) {
                NivaButtonAction.LockScreen -> GLOBAL_ACTION_LOCK_SCREEN // API 28, our minimum.
                NivaButtonAction.Notifications -> GLOBAL_ACTION_NOTIFICATIONS
                NivaButtonAction.QuickSettings -> GLOBAL_ACTION_QUICK_SETTINGS
                else -> return false
            }
            return runCatching { current?.performGlobalAction(id) == true }.getOrDefault(false)
        }
    }
}
