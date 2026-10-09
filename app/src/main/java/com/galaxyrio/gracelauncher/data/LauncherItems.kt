package com.galaxyrio.gracelauncher.data

import androidx.room.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import org.json.JSONArray
import org.json.JSONObject

/**
 * Shared Icon designer data, also written by the desktop's per-item editor.
 * Overrides precede the enabled pack order. Resource names survive pack updates.
 */
data class ItemIcon(val kind: String, val source: String = "", val name: String = "", val design: IconDesign? = null) {
    fun encode(): String = JSONObject().put("kind", kind).put("source", source).put("name", name)
        .apply { design?.let { put("design", it.json()) } }.toString()
    companion object {
        val System = ItemIcon("system")
        val Theme = ItemIcon("theme")
        fun decode(json: String): ItemIcon? = runCatching {
            val value = JSONObject(json)
            ItemIcon(value.getString("kind"), value.optString("source"), value.optString("name"),
                IconDesign.decode(value.optJSONObject("design")))
                .takeIf { it.kind in setOf("system", "theme", "pack", "image", "symbol") }
        }.getOrNull()
    }
}

/** Items reference the same app/shortcut identity everywhere; widgets own a separate host ID. */
data class PopupItem(val key: String, val widget: HomeLayout? = null) {
    companion object {
        fun encode(items: List<PopupItem>): String = JSONArray().apply {
            items.distinctBy { it.key }.forEach { item -> put(JSONObject().apply {
                put("key", item.key)
                item.widget?.let { put("widget", JSONObject(it.encode())) }
            }) }
        }.toString()
        fun decode(json: String): List<PopupItem> = runCatching {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { index ->
                val entry = array.getJSONObject(index)
                val key = entry.optString("key").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                PopupItem(key, entry.optJSONObject("widget")?.let { HomeLayout.decode(it.toString()) })
            }.distinctBy { it.key }
        }.getOrDefault(emptyList())
    }
}

@Entity(tableName = "item_icons")
data class ItemIconEntity(@PrimaryKey val itemKey: String, val iconJson: String)

@Entity(tableName = "saved_shortcuts")
data class SavedShortcutEntity(
    @PrimaryKey val itemKey: String, val packageName: String, val shortcutId: String,
    val activity: String, val label: String, val showInAppList: Boolean = false,
)

@Entity(tableName = "app_popups")
data class AppPopupEntity(@PrimaryKey val ownerKey: String, val itemsJson: String)

@Dao
abstract class LauncherItemsDao {
    @Query("SELECT * FROM item_icons") abstract fun icons(): kotlinx.coroutines.flow.Flow<List<ItemIconEntity>>
    @Query("SELECT * FROM item_icons") abstract suspend fun readIcons(): List<ItemIconEntity>
    @Query("SELECT * FROM saved_shortcuts") abstract fun shortcuts(): kotlinx.coroutines.flow.Flow<List<SavedShortcutEntity>>
    @Query("SELECT * FROM app_popups") abstract fun popups(): kotlinx.coroutines.flow.Flow<List<AppPopupEntity>>
    @Query("SELECT * FROM app_popups WHERE ownerKey = :key") abstract suspend fun popup(key: String): AppPopupEntity?
    @Query("SELECT * FROM saved_shortcuts WHERE itemKey = :key") abstract suspend fun shortcut(key: String): SavedShortcutEntity?
    @Upsert abstract suspend fun saveIcon(value: ItemIconEntity)
    @Query("DELETE FROM item_icons WHERE itemKey = :key") abstract suspend fun resetIcon(key: String)
    @Query("DELETE FROM item_icons WHERE itemKey IN (:keys)") abstract suspend fun resetIcons(keys: List<String>)
    @Upsert abstract suspend fun saveShortcut(value: SavedShortcutEntity)
    @Upsert abstract suspend fun savePopup(value: AppPopupEntity)
    @Query("DELETE FROM app_popups WHERE ownerKey = :key") abstract suspend fun deletePopup(key: String)
}

data class LauncherItemsSnapshot(
    val icons: Map<String, ItemIcon> = emptyMap(),
    val shortcuts: List<SavedShortcutEntity> = emptyList(),
    val popups: Map<String, List<PopupItem>> = emptyMap(),
)

class LauncherItemsRepository(private val database: LauncherDatabase) {
    private val dao = database.itemsDao()
    val snapshots = combine(dao.icons(), dao.shortcuts(), dao.popups()) { icons, shortcuts, popups ->
        LauncherItemsSnapshot(
            icons.mapNotNull { row -> ItemIcon.decode(row.iconJson)?.let { row.itemKey to it } }.toMap(),
            shortcuts, popups.associate { it.ownerKey to PopupItem.decode(it.itemsJson) },
        )
    }.distinctUntilChanged()

    suspend fun saveIcon(key: String, icon: ItemIcon?) = database.withTransaction {
        requireExistingFolder(key)
        if (icon == null) dao.resetIcon(key) else dao.saveIcon(ItemIconEntity(key, icon.encode()))
    }

    private suspend fun requireExistingFolder(key: String) {
        if (key.startsWith("folder:") && key != PrivateSpaceFolderKey) check(database.settingsDao().folder(key.removePrefix("folder:")) != null) {
            "Folder no longer exists"
        }
    }

    suspend fun resetIcons(keys: Set<String>): List<ItemIcon> = database.withTransaction {
        val previous = dao.readIcons().filter { it.itemKey in keys }.mapNotNull { ItemIcon.decode(it.iconJson) }
        keys.toList().chunked(900).forEach { dao.resetIcons(it) }
        previous
    }

    /** A special design can inherit the bulk image source; retain it until its final reference is gone. */
    suspend fun isImageReferenced(name: String): Boolean {
        fun ItemIcon?.matches() = this?.kind == "image" && source == name
        return dao.readIcons().any { ItemIcon.decode(it.iconJson).matches() } ||
            database.settingsDao().readSettings()?.iconDesignJson?.let(ItemIcon::decode).matches()
    }

    suspend fun rememberShortcut(app: LauncherApp, showInAppList: Boolean? = null) {
        val shortcut = app.shortcut ?: return
        database.withTransaction {
            val previous = dao.shortcut(app.key)
            dao.saveShortcut(SavedShortcutEntity(app.key, shortcut.packageName, shortcut.id,
                app.componentName.flattenToString(), app.originalLabel,
                showInAppList ?: previous?.showInAppList ?: false))
        }
    }

    /** Atomic edits also preserve widgets appended by the separate Android setup Activity. */
    suspend fun updatePopup(key: String, defaults: List<PopupItem>, transform: (List<PopupItem>) -> List<PopupItem>): List<Int> =
        database.withTransaction {
            requireExistingFolder(key)
            val previous = dao.popup(key)?.let { PopupItem.decode(it.itemsJson) } ?: defaults
            val next = transform(previous).distinctBy { it.key }
            dao.savePopup(AppPopupEntity(key, PopupItem.encode(next)))
            val retained = next.mapNotNull { it.widget?.widgetId }.toSet()
            previous.mapNotNull { it.widget?.widgetId }.filterNot { it in retained }
        }
}
