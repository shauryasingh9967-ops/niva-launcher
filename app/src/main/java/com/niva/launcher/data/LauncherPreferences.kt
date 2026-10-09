package com.niva.launcher.data

import android.content.Context
import androidx.core.content.edit

enum class WallpaperTextMode { Auto, Light, Dark }

/** Small, local-only preferences; app keys are flattened activity component names. */
class LauncherPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("launcher_appearance", Context.MODE_PRIVATE)

    val textMode: WallpaperTextMode
        get() = WallpaperTextMode.entries.firstOrNull { it.name == preferences.getString("text_mode", null) }
            ?: WallpaperTextMode.Auto

    val themedIcons: Boolean get() = preferences.getBoolean("themed_icons", true)

    /** Focus Mode: a launcher-only filter. Nothing is disabled or blocked in Android itself. */
    val focusActive: Boolean get() = preferences.getBoolean("focus_active", false)
    val focusAppKeys: Set<String> get() = preferences.getStringSet("focus_apps", emptySet())?.toSet().orEmpty()

    fun focusActive(enabled: Boolean) = preferences.edit { putBoolean("focus_active", enabled) }
    fun focusAppKeys(keys: Set<String>) = preferences.edit { putStringSet("focus_apps", keys.toSet()) }

    fun textMode(mode: WallpaperTextMode) = preferences.edit { putString("text_mode", mode.name) }
    fun themedIcons(enabled: Boolean) = preferences.edit { putBoolean("themed_icons", enabled) }

    fun renames(): Map<String, String> = values("label:")
    fun categories(): Map<String, String> = values("category:")

    fun rename(key: String, label: String) = preferences.edit {
        if (label.isBlank()) remove("label:$key") else putString("label:$key", label.trim())
    }

    fun categorize(key: String, category: String?) = preferences.edit {
        if (category.isNullOrBlank()) remove("category:$key") else putString("category:$key", category.trim())
    }

    private fun values(prefix: String): Map<String, String> = preferences.all.mapNotNull { (key, value) ->
        if (key.startsWith(prefix) && value is String) key.removePrefix(prefix) to value else null
    }.toMap()
}
