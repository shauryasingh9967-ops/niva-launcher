package com.niva.launcher.ui.drawer

import com.niva.launcher.data.LauncherAlphabet
import com.niva.launcher.data.LauncherApp
import com.niva.launcher.data.LauncherAppOrder
import com.niva.launcher.data.LauncherFolder

const val FolderSection = "◇"
const val NivaSection = "niva-launcher"

sealed interface DrawerItem {
    val key: String
    val section: String

    data class Header(override val section: String) : DrawerItem {
        override val key = "header:$section"
    }

    data class App(val app: LauncherApp, override val section: String = app.section) : DrawerItem {
        override val key = app.key
    }
    data class Folder(val folder: LauncherFolder, override val section: String = FolderSection) : DrawerItem {
        override val key = "folder:${folder.id}"
    }
    data class PrivateApp(val app: LauncherApp) : DrawerItem {
        override val key = app.key
        override val section = FolderSection
    }
    data object PrivateStatus : DrawerItem {
        override val key = "private-space-status"
        override val section = FolderSection
    }
    data object WorkStatus : DrawerItem {
        override val key = "work-profile-status"
        override val section = FolderSection
    }
}

class AppListModel(apps: List<LauncherApp>, folders: List<LauncherFolder> = emptyList(),
    privateFolder: LauncherFolder? = null, privateExpanded: Boolean = false, privateApps: List<LauncherApp> = emptyList(),
    recentlyInstalledFolder: LauncherFolder? = null,
    workFolder: LauncherFolder? = null, workExpanded: Boolean = false, workApps: List<LauncherApp> = emptyList()) {
    private val settingsApp = apps.firstOrNull(LauncherApp::isLauncherSettings)
    private val inlineFolders = folders.filter { it.placement.inAppList && !it.appListAtBottom }.associateBy(LauncherFolder::key)
    // Use exactly the same name/pinyin ordering and alphabet sections as apps.
    private val grouped = (apps.filterNot(LauncherApp::isLauncherSettings) + inlineFolders.values.map(LauncherFolder::asApp))
        .sortedWith(LauncherAppOrder).groupBy(LauncherApp::section)
    private val appLetters = LauncherAlphabet.filter { it != "#" && it in grouped } +
        grouped.keys.filter { it !in LauncherAlphabet }.sorted() +
        if ("#" in grouped) listOf("#") else emptyList()
    private val drawerFolders = folders.filter { it.placement.inAppList && it.appListAtBottom }
    val items: List<DrawerItem> = buildList {
        appLetters.forEach { letter ->
            add(DrawerItem.Header(letter))
            grouped.getValue(letter).forEach { app ->
                val folder = inlineFolders[app.key]
                add(if (folder == null) DrawerItem.App(app) else DrawerItem.Folder(folder, letter))
            }
        }
        if (drawerFolders.isNotEmpty() || privateFolder != null || workFolder != null) {
            add(DrawerItem.Header(FolderSection))
            drawerFolders.forEach { add(DrawerItem.Folder(it)) }
            if (workFolder != null) {
                add(DrawerItem.Folder(workFolder))
                if (workExpanded) {
                    workApps.forEach { add(DrawerItem.App(it, FolderSection)) }
                    if (workApps.isEmpty()) add(DrawerItem.WorkStatus)
                }
            }
            // Private Space follows user folders and never enters the stored folder table.
            if (privateFolder != null) {
                add(DrawerItem.Folder(privateFolder))
                if (privateExpanded) {
                    privateApps.forEach { add(DrawerItem.PrivateApp(it)) }
                    if (privateApps.isEmpty()) add(DrawerItem.PrivateStatus)
                }
            }
        }
        // Launcher utilities are a footer, not another alphabetical G section.
        if (recentlyInstalledFolder != null || settingsApp != null) {
            add(DrawerItem.Header(NivaSection))
            recentlyInstalledFolder?.let { add(DrawerItem.Folder(it, NivaSection)) }
            settingsApp?.let { add(DrawerItem.App(it, NivaSection)) }
        }
    }
    // Keep the rail in sync with every section, including launcher utilities.
    val letters: List<String> = items.filterIsInstance<DrawerItem.Header>().map { it.section }
    private val sectionIndices = items.mapIndexedNotNull { index, item ->
        (item as? DrawerItem.Header)?.let { it.section to index }
    }.toMap()

    /** Every index refers to the same complete list, even while other groups are hidden. */
    fun indexOfSection(letter: String): Int =
        sectionIndices[letter] ?: 0
}
