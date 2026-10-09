package com.niva.launcher.ui.drawer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.niva.launcher.data.LauncherApp
import com.niva.launcher.data.LauncherFolder
import com.niva.launcher.data.PrivateSpaceFolderId
import com.niva.launcher.data.RecentlyInstalledFolderId
import com.niva.launcher.data.notifications.AppNotification
import com.niva.launcher.R
import com.niva.launcher.ui.components.AppRowGestures
import com.niva.launcher.ui.components.LauncherAppRow
import com.niva.launcher.ui.components.FolderRow
import com.niva.launcher.ui.components.LauncherLayout
import com.niva.launcher.ui.components.stableStatusBarInset
import com.niva.launcher.ui.theme.LocalLauncherAppearance
import kotlinx.coroutines.flow.first

@Composable
fun AppDrawerScreen(
    model: AppListModel,
    listState: LazyListState,
    selectedLetter: String?,
    topSpace: Dp,
    onLaunchApp: (LauncherApp) -> Unit,
    onAppDetails: (LauncherApp) -> Unit,
    onAppShortcuts: (LauncherApp, Rect) -> Unit,
    modifier: Modifier = Modifier,
    rowGestures: AppRowGestures = AppRowGestures(),
    highlightedAppKey: String? = null,
    onOpenFolder: (LauncherFolder, Rect) -> Unit = { _, _ -> },
    onEditFolder: (LauncherFolder) -> Unit = {},
    onFolderDrag: (LauncherFolder, Rect, Boolean) -> Unit = { _, _, _ -> },
    onFolderDragEnd: (Boolean) -> Unit = {},
    notifications: Map<String, List<AppNotification>> = emptyMap(),
    folderApps: Map<String, LauncherApp> = emptyMap(),
    privateExpanded: Boolean = false,
    privateAppsPublic: Boolean = false,
    privateLoading: Boolean = false,
    privateFailed: Boolean = false,
    onPrivateSpaceSettings: () -> Unit = {},
    onRetryPrivateSpace: () -> Unit = {},
    workExpanded: Boolean = false,
    onWorkProfileSettings: () -> Unit = {},
) {
    val appearance = LocalLauncherAppearance.current
    val density = LocalDensity.current
    val bottomInset = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
    val safeTop = with(density) { (stableStatusBarInset() + 12.dp)
        .coerceAtLeast(LauncherLayout.TopFadeHeight).roundToPx() }
    val currentModel by rememberUpdatedState(model)
    val currentPrivateLoading by rememberUpdatedState(privateLoading)
    LaunchedEffect(privateExpanded, workExpanded) {
        if (!privateExpanded && !workExpanded) return@LaunchedEffect
        val expandedId = if (workExpanded) com.niva.launcher.data.WorkProfileFolderId else PrivateSpaceFolderId
        withFrameNanos { }
        // Position this opening once, after its initial contents have been laid
        // out. Later package/icon refreshes must leave the user's scroll alone.
        snapshotFlow {
            (workExpanded || !currentPrivateLoading || currentModel.items.any { it is DrawerItem.PrivateApp }) &&
                listState.layoutInfo.totalItemsCount == currentModel.items.size &&
                listState.layoutInfo.viewportEndOffset > listState.layoutInfo.viewportStartOffset
        }.first { it }
        val expandedModel = currentModel
        val index = expandedModel.items.indexOfFirst { it is DrawerItem.Folder && it.folder.id == expandedId }
        if (index < 0) return@LaunchedEffect
        val viewport = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
        val rowHeight = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == expandedModel.items[index].key }?.size
            ?: with(density) { LauncherLayout.RowMinHeight.roundToPx() }
        val blockSize = 1 + expandedModel.items.drop(index + 1).takeWhile { it.section == FolderSection && it !is DrawerItem.Folder }.size
        val blockHeight = rowHeight * blockSize
        val desiredTop = (viewport - blockHeight - with(density) { (24.dp + bottomInset).roundToPx() }).coerceAtLeast(safeTop)
        // Short lists settle against the real bottom inset; tall lists start with
        // the Private row in view and continue below it in this same LazyColumn.
        listState.animateScrollToItem(index, with(density) { topSpace.roundToPx() } - desiredTop)
    }
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag("app_drawer"),
        // Scrollable leading space, not padding on the viewport: earlier groups
        // may occupy this area after jumping to a later letter. The small, real
        // bottom inset lets LazyColumn naturally clamp sections near the end
        // without clipping scrolling rows above the navigation bar.
        contentPadding = PaddingValues(start = LauncherLayout.Start, end = LauncherLayout.End, top = topSpace, bottom = 24.dp + bottomInset),
        userScrollEnabled = selectedLetter == null,
    ) {
        items(
            items = model.items,
            key = DrawerItem::key,
            contentType = { when (it) { is DrawerItem.Header -> "header"; is DrawerItem.App, is DrawerItem.PrivateApp -> "app"; is DrawerItem.Folder -> "folder"; DrawerItem.PrivateStatus, DrawerItem.WorkStatus -> "status" } },
        ) { item ->
            // Keep every item's key and measured height. Hiding a group must
            // not change scroll bounds or move the selected header on release.
            Box(Modifier.retainItemSpace(selectedLetter == null || item.section == selectedLetter)) {
                when (item) {
                    is DrawerItem.Header -> Text(
                        text = when (item.section) {
                            FolderSection -> stringResource(R.string.drawer_folders)
                            NivaSection -> stringResource(R.string.app_name)
                            else -> item.section
                        },
                        modifier = Modifier
                            .height(44.dp)
                            .padding(start = LauncherLayout.ContentInset, end = LauncherLayout.ContentInset, top = 12.dp)
                            .testTag("section:${item.section}"),
                        color = appearance.text,
                        style = TextStyle(
                            fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
                            fontSize = 18.sp,
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.Normal,
                            shadow = appearance.textShadow,
                        ),
                    )
                    is DrawerItem.App -> LauncherAppRow(
                        app = item.app,
                        onClick = { onLaunchApp(item.app) },
                        onLongClick = { onAppDetails(item.app) },
                        onSwipeRight = { onAppShortcuts(item.app, it) },
                        gestures = rowGestures,
                        highlighted = highlightedAppKey == item.app.key,
                        notification = notifications[item.app.packageName]?.firstOrNull().takeUnless { item.app.user != null },
                    )
                    is DrawerItem.Folder -> FolderRow(
                        folder = item.folder,
                        app = folderApps[item.folder.id] ?: item.folder.asApp(),
                        highlighted = highlightedAppKey == item.folder.key,
                        onOpen = { onOpenFolder(item.folder, it) },
                        onLongClick = if (item.folder.id == RecentlyInstalledFolderId) null else ({ onEditFolder(item.folder) }),
                        onDrag = { bounds, expanded -> onFolderDrag(item.folder, bounds, expanded) },
                        onDragEnd = onFolderDragEnd,
                    )
                    is DrawerItem.PrivateApp -> LauncherAppRow(
                        app = item.app, onClick = { onLaunchApp(item.app) }, onLongClick = { onAppDetails(item.app) },
                        onSwipeRight = { if (privateAppsPublic) onAppShortcuts(item.app, it) },
                        gestures = if (privateAppsPublic) rowGestures else AppRowGestures(onLaunchAt = rowGestures.onLaunchAt),
                        highlighted = highlightedAppKey == item.app.key,
                        modifier = Modifier.animateItem(fadeOutSpec = null),
                    )
                    DrawerItem.WorkStatus -> Column(Modifier.padding(start = LauncherLayout.ContentInset, bottom = 12.dp)) {
                        Text(stringResource(R.string.work_profile_no_apps), Modifier.padding(vertical = 12.dp), color = appearance.text,
                            style = MaterialTheme.typography.bodyMedium.copy(shadow = appearance.textShadow))
                        TextButton(onClick = onWorkProfileSettings) { Text(stringResource(R.string.work_profile_setup), color = appearance.text) }
                    }
                    DrawerItem.PrivateStatus -> Column(Modifier.padding(start = LauncherLayout.ContentInset, bottom = 12.dp)) {
                        Text(stringResource(when { privateLoading -> R.string.private_space_loading
                            privateFailed -> R.string.private_space_unavailable; else -> R.string.private_space_empty }),
                            Modifier.padding(vertical = 12.dp), color = appearance.text,
                            style = MaterialTheme.typography.bodyMedium.copy(shadow = appearance.textShadow))
                        if (!privateLoading) TextButton(onClick = if (privateFailed) onRetryPrivateSpace else onPrivateSpaceSettings) {
                            Text(stringResource(if (privateFailed) R.string.retry else R.string.private_space_setup), color = appearance.text)
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.retainItemSpace(visible: Boolean): Modifier =
    // Unlike alpha=0, not placing a child also removes its invisible hit targets.
    // Clear descendants from TalkBack while retaining the complete list geometry.
    then(if (visible) Modifier else Modifier.clearAndSetSemantics {}).layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            if (visible) placeable.placeRelative(0, 0)
        }
    }
