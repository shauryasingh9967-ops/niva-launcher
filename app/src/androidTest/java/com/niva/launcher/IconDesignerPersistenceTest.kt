package com.niva.launcher

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.niva.launcher.data.*
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class IconDesignerPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "icon-designer-${UUID.randomUUID()}.db"
    private var database: LauncherDatabase? = null

    private fun open(): LauncherDatabase {
        database?.close()
        return Room.databaseBuilder(context, LauncherDatabase::class.java, databaseName)
            .addMigrations(LauncherDatabase.Migration10To11, LauncherDatabase.Migration11To12).build().also { database = it }
    }

    @After fun close() { database?.close(); context.deleteDatabase(databaseName) }

    @Test fun migrationPreservesTheOldPackAndDesktopOverridesWhileAddingPersistentPriority() = runBlocking {
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.niva.launcher.data.LauncherDatabase/10.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val appKey = "test.app/test.app.Activity"
        val override = ItemIcon("pack", "custom.icons", "alternate")
        context.openOrCreateDatabase(databaseName, 0, null).use { legacy ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                fun execute(sql: String) = legacy.execSQL(sql.replace('$' + "{TABLE_NAME}", table))
                execute(entity.getString("createSql"))
                entity.optJSONArray("indices")?.let { indices ->
                    for (i in 0 until indices.length()) execute(indices.getJSONObject(i).getString("createSql"))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) legacy.execSQL(setup.getString(index))
            legacy.execSQL("INSERT INTO launcher_settings (id, calendarAgenda, showBatteryPercentage, allowHapticFeedback, useDynamicColors, themeColor, darkMode, iconPackPackage) VALUES (0, 1, 1, 1, 1, -1, 'System', 'old.icons')")
            legacy.execSQL("INSERT INTO item_icons VALUES (?, ?)", arrayOf(appKey, override.encode()))
            legacy.version = 10
        }
        var db = open()
        var settings = LauncherSettingsRepository(db)
        assertEquals(listOf("old.icons"), withTimeout(10_000) { settings.snapshots.first().settings.enabledIconPackPackages })
        assertEquals(override, withTimeout(10_000) { LauncherItemsRepository(db).snapshots.first().icons[appKey] })
        settings.mutateSettings { it.withIconPacks(listOf("second.icons", "first.icons", "second.icons")) }
        db = open()
        settings = LauncherSettingsRepository(db)
        assertEquals(listOf("second.icons", "first.icons"), withTimeout(10_000) { settings.snapshots.first().settings.enabledIconPackPackages })
        assertEquals(override, withTimeout(10_000) { LauncherItemsRepository(db).snapshots.first().icons[appKey] })
        settings.mutateSettings { it.withIconPacks(emptyList()) }
        db = open()
        assertTrue(withTimeout(10_000) { LauncherSettingsRepository(db).snapshots.first().settings.enabledIconPackPackages }.isEmpty())
        assertEquals(override, withTimeout(10_000) { LauncherItemsRepository(db).snapshots.first().icons[appKey] })
        val bulk = ItemIcon.Theme.copy(design = IconDesign(shape = IconShape.Gem, size = 125,
            background = IconColor(0xFF6750A4.toInt(), true), x = 15f))
        val special = override.copy(design = IconDesign(shape = IconShape.Cookie, cookieSides = 12, y = -10f))
        LauncherSettingsRepository(db).mutateSettings { it.copy(iconDesign = bulk) }
        LauncherItemsRepository(db).saveIcon(appKey, special)
        db = open()
        assertEquals(bulk, withTimeout(10_000) { LauncherSettingsRepository(db).snapshots.first().settings.iconDesign })
        assertEquals(special, withTimeout(10_000) { LauncherItemsRepository(db).snapshots.first().icons[appKey] })
    }
}
