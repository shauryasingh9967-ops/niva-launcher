package com.niva.launcher

import com.niva.launcher.data.BackupPreferences
import com.niva.launcher.data.FolderPlacement
import com.niva.launcher.data.LauncherFolder
import com.niva.launcher.data.LauncherSettings
import com.niva.launcher.data.SettingsBackup
import com.niva.launcher.data.ThemeMode
import com.niva.launcher.data.WallpaperTextMode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SettingsBackupTest {
    private fun sampleSettings() = LauncherSettings(
        darkMode = ThemeMode.Dark,
        themeColor = 0xFF112233.toInt(),
        weatherForecastDays = 10,
        wallpaperDimAmount = 42,
    )

    private fun samplePrefs() = BackupPreferences(
        textMode = WallpaperTextMode.Auto,
        themedIcons = true,
        focusActive = false,
        focusApps = setOf("d/.Main"),
        renames = mapOf("a/.Main" to "My App"),
        categories = mapOf("a/.Main" to "Work"),
    )

    private fun exportJson(): String = SettingsBackup.export(
        settings = sampleSettings(),
        preferences = samplePrefs(),
        favorites = listOf("a/.Main", "b/.Main"),
        hiddenApps = setOf("c/.Main"),
        folders = listOf(LauncherFolder("f1", "Work", listOf("a/.Main"), FolderPlacement.Both, true)),
    )

    @Test
    fun exportProducesVersionedDocument() {
        val root = JSONObject(exportJson())
        assertEquals("niva-backup", root.getString("format"))
        assertEquals(1, root.getInt("version"))
        assertEquals("com.niva.launcher", root.getString("appId"))
    }

    @Test
    fun roundTripPreservesSettings() {
        val parsed = SettingsBackup.parse(exportJson())
        assertEquals(ThemeMode.Dark, parsed.settings.darkMode)
        assertEquals(0xFF112233.toInt(), parsed.settings.themeColor)
        assertEquals(10, parsed.settings.weatherForecastDays)
        assertEquals(42, parsed.settings.wallpaperDimAmount)
        assertEquals(listOf("a/.Main", "b/.Main"), parsed.favorites)
        assertEquals(setOf("c/.Main"), parsed.hiddenApps)
        assertEquals(1, parsed.folders.size)
        assertEquals("f1", parsed.folders[0].id)
        assertEquals(FolderPlacement.Both, parsed.folders[0].placement)
        assertEquals(WallpaperTextMode.Auto, parsed.textMode)
        assertEquals(setOf("d/.Main"), parsed.focusApps)
        assertEquals(mapOf("a/.Main" to "My App"), parsed.renames)
    }

    @Test
    fun rejectsNonJson() {
        try {
            SettingsBackup.parse("this is not json{{{")
            fail("expected InvalidBackupException")
        } catch (e: SettingsBackup.InvalidBackupException) {
            // expected
        }
    }

    @Test
    fun rejectsWrongFormat() {
        try {
            SettingsBackup.parse(
                JSONObject().put("format", "other").put("version", 1)
                    .put("settings", JSONObject()).toString()
            )
            fail("expected InvalidBackupException")
        } catch (e: SettingsBackup.InvalidBackupException) {
            // expected
        }
    }

    @Test
    fun rejectsUnsupportedVersion() {
        try {
            SettingsBackup.parse(
                JSONObject().put("format", "niva-backup").put("version", 99)
                    .put("settings", JSONObject()).toString()
            )
            fail("expected InvalidBackupException")
        } catch (e: SettingsBackup.InvalidBackupException) {
            // expected
        }
    }

    @Test
    fun rejectsMissingSettings() {
        try {
            SettingsBackup.parse(JSONObject().put("format", "niva-backup").put("version", 1).toString())
            fail("expected InvalidBackupException")
        } catch (e: SettingsBackup.InvalidBackupException) {
            // expected
        }
    }

    @Test
    fun malformedFieldsFallBackToDefaults() {
        val root = JSONObject(exportJson())
        val settings = root.getJSONObject("settings")
        settings.put("darkMode", "NotAMode")
        settings.put("weatherForecastDays", 999)
        settings.put("wallpaperDimAmount", -5)
        val parsed = SettingsBackup.parse(root.toString())
        assertEquals(ThemeMode.System, parsed.settings.darkMode)
        assertEquals(14, parsed.settings.weatherForecastDays)
        assertEquals(0, parsed.settings.wallpaperDimAmount)
    }

    @Test
    fun oversizedDocumentRejected() {
        val big = "x".repeat(5 * 1024 * 1024)
        try {
            SettingsBackup.parse(big)
            fail("expected InvalidBackupException")
        } catch (e: SettingsBackup.InvalidBackupException) {
            // expected
        }
    }

    @Test
    fun reservedFolderIdsDropped() {
        val root = JSONObject(exportJson())
        val folders = root.getJSONArray("folders")
        folders.put(
            JSONObject().put("id", "recently-installed").put("name", "evil")
                .put("appKeys", org.json.JSONArray())
        )
        val parsed = SettingsBackup.parse(root.toString())
        assertTrue(parsed.folders.none { it.id == "recently-installed" })
    }
}
