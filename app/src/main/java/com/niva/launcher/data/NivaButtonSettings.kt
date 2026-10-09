package com.niva.launcher.data

import org.json.JSONObject

enum class NivaButtonAction {
    App, Shortcut, Settings, Search, Website, LockScreen, AppList, Notifications, QuickSettings, Assistant, Agenda, Disabled;

    val requiresAccessibility: Boolean get() = this == LockScreen || this == Notifications || this == QuickSettings
}

enum class NivaButtonGesture(val storageKey: String) {
    Tap("tap"), LongPress("longPress"), DoubleTap("doubleTap"),
    SwipeUp("swipeUp"), SwipeDown("swipeDown"), SwipeLeft("swipeLeft"), SwipeRight("swipeRight");
}

data class NivaButtonTarget(
    val action: NivaButtonAction,
    val itemKey: String? = null,
    val enabled: Boolean = action != NivaButtonAction.Disabled,
    val url: String? = null,
) {
    val active: Boolean get() = enabled && action != NivaButtonAction.Disabled
}

data class NivaButtonSettings(
    val enabled: Boolean = true,
    val tap: NivaButtonTarget = NivaButtonTarget(NivaButtonAction.Search),
    val longPress: NivaButtonTarget = NivaButtonTarget(NivaButtonAction.Settings),
    val doubleTap: NivaButtonTarget = NivaButtonTarget(NivaButtonAction.Search, enabled = false),
    val swipeUp: NivaButtonTarget = NivaButtonTarget(NivaButtonAction.Search, enabled = false),
    val swipeDown: NivaButtonTarget = NivaButtonTarget(NivaButtonAction.Search, enabled = false),
    val swipeLeft: NivaButtonTarget = NivaButtonTarget(NivaButtonAction.Search, enabled = false),
    val swipeRight: NivaButtonTarget = NivaButtonTarget(NivaButtonAction.Search, enabled = false),
    val animationsEnabled: Boolean = true,
    val searchAnimationDurationMs: Int = 500,
    val appListAnimationDurationMs: Int = 500,
    /** 100 keeps the normal distance and velocity thresholds. Higher is easier. */
    val swipeSensitivity: Int = 100,
    /** null follows the system double-tap timeout. */
    val doubleTapIntervalMs: Int? = null,
) {
    fun target(gesture: NivaButtonGesture): NivaButtonTarget = when (gesture) {
        NivaButtonGesture.Tap -> tap
        NivaButtonGesture.LongPress -> longPress
        NivaButtonGesture.DoubleTap -> doubleTap
        NivaButtonGesture.SwipeUp -> swipeUp
        NivaButtonGesture.SwipeDown -> swipeDown
        NivaButtonGesture.SwipeLeft -> swipeLeft
        NivaButtonGesture.SwipeRight -> swipeRight
    }

    fun withTarget(gesture: NivaButtonGesture, target: NivaButtonTarget): NivaButtonSettings = when (gesture) {
        NivaButtonGesture.Tap -> copy(tap = target)
        NivaButtonGesture.LongPress -> copy(longPress = target)
        NivaButtonGesture.DoubleTap -> copy(doubleTap = target)
        NivaButtonGesture.SwipeUp -> copy(swipeUp = target)
        NivaButtonGesture.SwipeDown -> copy(swipeDown = target)
        NivaButtonGesture.SwipeLeft -> copy(swipeLeft = target)
        NivaButtonGesture.SwipeRight -> copy(swipeRight = target)
    }

    fun withEnabled(gesture: NivaButtonGesture, enabled: Boolean): NivaButtonSettings {
        val current = target(gesture)
        val action = if (current.action != NivaButtonAction.Disabled) current.action
            else if (gesture == NivaButtonGesture.LongPress) NivaButtonAction.Settings else NivaButtonAction.Search
        return withTarget(gesture, current.copy(action = action, enabled = enabled))
    }

    fun encode(): String = JSONObject().apply {
        put("enabled", enabled)
        put("animationsEnabled", animationsEnabled)
        put("searchAnimationDurationMs", searchAnimationDurationMs.coerceIn(100, 1500))
        put("appListAnimationDurationMs", appListAnimationDurationMs.coerceIn(100, 1500))
        put("swipeSensitivity", swipeSensitivity.coerceIn(50, 200))
        put("doubleTapIntervalMs", doubleTapIntervalMs?.coerceIn(150, 700))
        NivaButtonGesture.entries.forEach { gesture ->
            val target = target(gesture)
            put(gesture.storageKey, JSONObject().put("action", target.action.name).put("key", target.itemKey)
                .put("enabled", target.active).put("url", target.url))
        }
    }.toString()

    companion object {
        fun homeDefaults() = NivaButtonSettings(
            tap = NivaButtonTarget(NivaButtonAction.Disabled),
            longPress = NivaButtonTarget(NivaButtonAction.Disabled),
            swipeUp = NivaButtonTarget(NivaButtonAction.Search),
            swipeDown = NivaButtonTarget(NivaButtonAction.Notifications),
        )

        fun decode(json: String?, defaults: NivaButtonSettings = NivaButtonSettings()): NivaButtonSettings = runCatching {
            val data = JSONObject(json ?: "{}")
            var settings = defaults.copy(
                enabled = data.optBoolean("enabled", defaults.enabled),
                animationsEnabled = data.optBoolean("animationsEnabled", defaults.animationsEnabled),
                searchAnimationDurationMs = data.optInt("searchAnimationDurationMs", defaults.searchAnimationDurationMs).coerceIn(100, 1500),
                appListAnimationDurationMs = data.optInt("appListAnimationDurationMs", defaults.appListAnimationDurationMs).coerceIn(100, 1500),
                swipeSensitivity = data.optInt("swipeSensitivity", defaults.swipeSensitivity).coerceIn(50, 200),
                doubleTapIntervalMs = if (data.has("doubleTapIntervalMs") && !data.isNull("doubleTapIntervalMs"))
                    data.optInt("doubleTapIntervalMs", 300).coerceIn(150, 700) else defaults.doubleTapIntervalMs,
            )
            NivaButtonGesture.entries.forEach { gesture ->
                val saved = data.optJSONObject(gesture.storageKey) ?: return@forEach
                val fallback = settings.target(gesture)
                val action = NivaButtonAction.entries.firstOrNull { it.name == saved.optString("action") } ?: fallback.action
                settings = settings.withTarget(gesture, NivaButtonTarget(action,
                    saved.optString("key").takeIf { it.isNotBlank() && it != "null" },
                    // Old tap/long-press targets had no enabled field.
                    saved.optBoolean("enabled", action != NivaButtonAction.Disabled),
                    saved.optString("url").takeIf { it.isNotBlank() && it != "null" }))
            }
            settings
        }.getOrDefault(defaults)
    }
}
