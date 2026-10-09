package com.niva.launcher.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray

class FavoritesStore internal constructor(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE))

    fun favoritesFor(apps: List<LauncherApp>): List<String> {
        preferences.getString(KEY_ORDER, null)?.let { encoded ->
            runCatching {
                val array = JSONArray(encoded)
                List(array.length()) { array.getString(it) }.filter(String::isNotBlank).distinct()
            }.getOrNull()?.let { return it }
        }
        if (preferences.contains(KEY_FAVORITES)) {
            val previous = preferences.getStringSet(KEY_FAVORITES, emptySet()).orEmpty()
            // Sets had no stored order. Preserve the old, name-sorted home layout
            // during migration, including hidden or temporarily unavailable apps.
            val installed = apps.map(LauncherApp::key).filter { it in previous }
            val migrated = installed + (previous - installed.toSet()).sorted()
            save(migrated)
            return migrated
        }

        val initial = apps.sortedWith(
            compareBy<LauncherApp> { preferredRank(it) }.thenBy { it.label.lowercase() },
        ).take(DEFAULT_FAVORITE_COUNT).map(LauncherApp::key)
        save(initial)
        return initial
    }

    fun toggle(appKey: String, current: List<String>): List<String> {
        val updated = if (appKey in current) current - appKey else current + appKey
        save(updated)
        return updated
    }

    /** External pin requests add an item; repeating a request must never toggle it off. */
    @Synchronized
    fun add(appKey: String, apps: List<LauncherApp>): List<String> {
        val updated = (favoritesFor(apps) + appKey).distinct()
        save(updated)
        return updated
    }

    fun reorder(requested: List<String>, current: List<String>): List<String> {
        // Only reorder existing members. The editor omits hidden/uninstalled apps;
        // leave their slots intact so unhiding or reinstalling restores them.
        val movable = requested.distinct().filter { it in current }
        val moving = movable.toSet()
        val replacements = movable.iterator()
        val updated = current.distinct().map { if (it in moving) replacements.next() else it }
        save(updated)
        return updated
    }

    /** Replaces the favorites order wholesale (used by backup restore). */
    fun restore(keys: List<String>) {
        save(keys.filter(String::isNotBlank).distinct())
    }

    private fun save(keys: List<String>) {
        preferences.edit {
            putString(KEY_ORDER, JSONArray(keys.distinct()).toString())
            putStringSet(KEY_FAVORITES, keys.toSet())
        }
    }

    private fun preferredRank(app: LauncherApp): Int {
        val searchable = "${app.packageName} ${app.label}".lowercase()
        val preferredTokens = listOf(
            "dialer", "phone", "telecom",
            "message", "sms",
            "camera",
            "chrome", "browser",
            "gmail", "mail",
            "calendar",
        )
        val firstMatch = preferredTokens.indexOfFirst(searchable::contains)
        return if (firstMatch >= 0) firstMatch else Int.MAX_VALUE
    }

    private companion object {
        const val FILE_NAME = "niva_launcher_preferences"
        const val KEY_FAVORITES = "favorite_components"
        const val KEY_ORDER = "favorite_component_order"
        const val DEFAULT_FAVORITE_COUNT = 6
    }
}
