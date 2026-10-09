package com.niva.launcher.ui.overlays

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.niva.launcher.R
import com.niva.launcher.data.LauncherApp
import com.niva.launcher.data.LauncherFolder
import com.niva.launcher.data.PrivateSpaceFolderId
import com.niva.launcher.data.ShortcutResult
import com.niva.launcher.data.ShortcutStatus
import com.niva.launcher.data.notifications.AppNotification
import com.niva.launcher.ui.LauncherActions
import com.niva.launcher.ui.LauncherUiState
import com.niva.launcher.ui.components.AppIcon
import com.niva.launcher.ui.widgets.HomeWidget
import com.niva.launcher.ui.widgets.rememberHostedWidget
import com.niva.launcher.ui.components.LauncherIcon
import com.niva.launcher.ui.components.LauncherSymbol
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Stable
class ShortcutRevealState(expanded: Boolean = true, dragging: Boolean = false) {
    var dragging by mutableStateOf(dragging)
    var expanded by mutableStateOf(expanded)
}

/** Folders differ only in their owner and initial contents, not their popup UI. */
@Composable
fun FolderPopup(
    folder: LauncherFolder, apps: List<LauncherApp>, anchor: Rect, onDismiss: () -> Unit,
    onLaunchApp: (LauncherApp, Rect) -> Unit, onEdit: (() -> Unit)?,
    reveal: ShortcutRevealState = remember { ShortcutRevealState() },
    uiState: LauncherUiState = LauncherUiState(apps = apps, folders = listOf(folder)),
    actions: LauncherActions = LauncherActions(), onDetails: (LauncherApp) -> Unit = {},
) = ShortcutPopup(
    app = uiState.folderItem(folder), anchor = anchor, hasAccess = uiState.hasShortcutAccess,
    actions = actions, onLaunchApp = {}, onDismiss = onDismiss, reveal = reveal,
    uiState = uiState, onEdit = onEdit, onDetails = onDetails, onLaunchItem = onLaunchApp,
)

/** Same-window overlay: adding a popup window during DOWN would cancel the row's drag. */
@Composable
fun ShortcutPopup(
    app: LauncherApp,
    anchor: Rect,
    hasAccess: Boolean,
    actions: LauncherActions,
    onLaunchApp: () -> Unit,
    onDismiss: () -> Unit,
    reveal: ShortcutRevealState = remember { ShortcutRevealState() },
    notifications: List<AppNotification> = emptyList(),
    uiState: LauncherUiState = LauncherUiState(),
    onEdit: (() -> Unit)? = {},
    onDetails: (LauncherApp) -> Unit = {},
    onLaunchItem: (LauncherApp, Rect) -> Unit = { item, bounds ->
        item.shortcut?.let { actions.launchShortcutAt?.invoke(it, bounds) ?: actions.launchShortcut(it) }
    },
) {
    val isFolder = app.folderId != null
    val isPrivateFolder = app.folderId == PrivateSpaceFolderId
    var retry by remember { mutableIntStateOf(0) }
    val loadShortcuts by rememberUpdatedState(actions.shortcuts)
    val initialResult = remember(app.key, hasAccess) {
        if (isFolder) ShortcutResult(ShortcutStatus.Ready) else actions.cachedShortcuts(app) ?: ShortcutResult(ShortcutStatus.Loading)
    }
    val result by produceState(initialResult, app.key, hasAccess, retry, uiState.itemRevision) {
        // Keep cached content while refreshing; never insert a progress indicator.
        if (!isFolder) value = loadShortcuts(app)
    }
    val shortcuts = result.shortcuts.map { uiState.findItem(it.key) ?: it.asApp(app) }
    val customItems = uiState.popups[app.key]
    val entries = uiState.popupItems(app, shortcuts)
    SwipeRevealPanel(
        anchor = anchor,
        reveal = reveal,
        panelTag = if (isFolder) "folder_popup" else "shortcut_popup",
        title = if (isFolder) app.label else stringResource(R.string.app_shortcuts),
        onDismiss = onDismiss,
    ) { maxListHeight ->
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("shortcut_header")
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(enabled = !isFolder || onEdit != null,
                    onLongClick = { onDetails(app) }, onClick = { if (isFolder) onDetails(app) else onLaunchApp() })
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LauncherIcon(LauncherSymbol.Outbound, Modifier.size(19.dp))
            Spacer(Modifier.width(10.dp))
            Text(app.label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LazyColumn(Modifier.heightIn(max = maxListHeight).testTag(if (isFolder) "folder_members" else "shortcut_list")) {
            items(notifications, key = { "notification:${it.key}" }) { notification ->
                NotificationPopupItem(
                    app, notification,
                    onOpen = { if (actions.openNotification(notification.key, notification.revision)) onDismiss() },
                    onDismiss = { actions.dismissNotification(notification.key, notification.revision) },
                    modifier = Modifier.animateItem(),
                    gesturesEnabled = !reveal.dragging && reveal.expanded,
                )
            }
        if (isFolder && entries.isEmpty()) item {
            if (isPrivateFolder && uiState.privateAppsLoading) {
                Text(stringResource(R.string.private_space_loading), Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
            } else if (isPrivateFolder) {
                Text(stringResource(if (uiState.privateAppsFailed) R.string.private_space_unavailable else R.string.private_space_empty),
                    Modifier.testTag("private_space_empty").padding(horizontal = 12.dp, vertical = 18.dp), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = if (uiState.privateAppsFailed) ({ actions.requestPrivateSpace(true, actions.refreshApps) }) else actions.openPrivateSpaceSettings) {
                    Text(stringResource(if (uiState.privateAppsFailed) R.string.retry else R.string.private_space_setup))
                }
            } else if (app.folderId == com.niva.launcher.data.WorkProfileFolderId) {
                Text(stringResource(R.string.work_profile_no_apps), Modifier.padding(horizontal = 12.dp, vertical = 18.dp), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = actions.openWorkProfileSettings) { Text(stringResource(R.string.work_profile_setup)) }
            } else {
                Text(stringResource(R.string.folder_contents_empty), Modifier.testTag("folder_empty").padding(horizontal = 12.dp, vertical = 18.dp), style = MaterialTheme.typography.bodyMedium)
                if (onEdit != null) TextButton(onClick = onEdit, modifier = Modifier.testTag("folder_edit")) { Text(stringResource(R.string.settings_folder_edit)) }
            }
        }
        if (!isFolder && customItems == null) when (result.status) {
            // Cold apps may need one binder query; keep the layout quiet.
            ShortcutStatus.Loading -> if (notifications.isEmpty()) item { Spacer(Modifier.height(56.dp)) }
            ShortcutStatus.DefaultLauncherRequired -> item {
                Column {
                Text(stringResource(R.string.shortcut_permission), Modifier.padding(horizontal = 12.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = actions.requestDefaultHome) { Text(stringResource(R.string.set_default_launcher)) }
                }
            }
            ShortcutStatus.Error -> item {
                Column {
                Text(stringResource(R.string.shortcut_error), Modifier.padding(horizontal = 12.dp, vertical = 12.dp))
                TextButton(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
                }
            }
            ShortcutStatus.Ready -> {
                if (result.shortcuts.isEmpty() && notifications.isEmpty()) item {
                    Text(stringResource(R.string.no_shortcuts), Modifier.padding(horizontal = 12.dp, vertical = 18.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        items(entries, key = { "entry:${it.key}" }) { entry ->
            val widget = entry.widget
            val itemApp = uiState.findItem(entry.key) ?: shortcuts.firstOrNull { it.key == entry.key }
            when {
                widget != null -> {
                    val hosted = rememberHostedWidget(widget)
                    Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        HomeWidget(widget, hosted, hosted.defaultHeight, editing = false,
                            enabled = !reveal.dragging && reveal.expanded, hapticsEnabled = uiState.settings.allowHapticFeedback, onLongPress = { onEdit?.invoke() })
                    }
                }
                itemApp != null -> {
                        var iconBounds by remember { mutableStateOf(Rect.Zero) }
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(if (isFolder) "folder_app:${itemApp.key}" else "shortcut:${itemApp.shortcut?.id ?: itemApp.key}")
                                .clip(RoundedCornerShape(16.dp))
                                .combinedClickable(enabled = !reveal.dragging && reveal.expanded, onLongClick = { onDetails(itemApp) }, onClick = {
                                    onLaunchItem(itemApp, iconBounds)
                                    if (!isPrivateFolder) onDismiss()
                                }).padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(itemApp, Modifier.onGloballyPositioned { iconBounds = it.boundsInWindow() }, size = 36.dp)
                            Spacer(Modifier.width(22.dp))
                            Text(itemApp.label, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                }
                else -> Text(stringResource(R.string.popup_item_unavailable), Modifier.fillMaxWidth().clickable(enabled = onEdit != null, onClick = { onEdit?.invoke() }).padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        }
    }
}

/** One same-window reveal/positioning implementation for app shortcuts and folders. */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun SwipeRevealPanel(
    anchor: Rect,
    reveal: ShortcutRevealState,
    panelTag: String,
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(maxListHeight: Dp) -> Unit,
) {
    val windowSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val margin = with(density) { 32.dp.roundToPx() }
    val maxListHeight = with(density) { windowSize.height.toDp() } * 0.55f
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val spatial = remember(reveal) { Animatable(0f) }
    val effects = remember(reveal) { Animatable(0f) }
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    LaunchedEffect(reveal, spatialSpec, effectsSpec) {
        snapshotFlow { reveal.expanded }.collect { expanded ->
            val destination = if (expanded) 1f else 0f
            // Let Animatable interrupt its previous animateTo. Cancelling the
            // owning effect on every target change would reset its velocity.
            launch { effects.animateTo(destination, effectsSpec) }
            launch { spatial.animateTo(destination, spatialSpec) }
        }
    }
    LaunchedEffect(reveal.dragging, reveal.expanded, spatial.isRunning) {
        // Keep the original gesture alive at zero so a rightward reversal can
        // reopen the same panel without lifting the finger.
        if (!reveal.dragging && !reveal.expanded && !spatial.isRunning && spatial.value == 0f) currentOnDismiss()
    }
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.boundsInWindow().topLeft }) {
        Box(Modifier.matchParentSize().clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null,
            onClick = { reveal.expanded = false },
        ).clearAndSetSemantics {})
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                LauncherPanelTheme {
                    Surface(
                        modifier = Modifier.testTag(panelTag).semantics {
                            paneTitle = title
                            dismiss { reveal.expanded = false; true }
                            progressBarRangeInfo = ProgressBarRangeInfo(spatial.value.coerceIn(0f, 1f), 0f..1f)
                        }.graphicsLayer { alpha = (spatial.value * 12f).coerceIn(0f, 1f) },
                        shape = PopupContainerShape(anchor, spatial.value),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 16.dp * spatial.value.coerceIn(0f, 1f),
                    ) {}
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                        Column(Modifier.padding(horizontal = 6.dp, vertical = 12.dp)) {
                            content(maxListHeight)
                        }
                    }
                }
            },
        ) { measurables, constraints ->
            val panelWidth = (constraints.maxWidth - margin * 2).coerceIn(1, 440.dp.roundToPx())
            // Measure content at its final size: only the container/clip morphs,
            // so neither text nor icons are stretched during the hero transition.
            val panel = measurables[1].measure(Constraints(
                minWidth = panelWidth, maxWidth = panelWidth,
                maxHeight = (constraints.maxHeight - margin * 2).coerceAtLeast(1),
            ))
            val left = (constraints.maxWidth - panel.width) / 2
            val top = (anchor.center.y - origin.y - panel.height / 2f).roundToInt()
                .coerceIn(margin, (constraints.maxHeight - panel.height - margin).coerceAtLeast(margin))
            val target = Rect(left.toFloat(), top.toFloat(), (left + panel.width).toFloat(), (top + panel.height).toFloat())
            val frame = popupMorphFrame(anchor.translate(-origin), target, spatial.value, 24.dp.toPx())
            val container = measurables[0].measure(Constraints.fixed(
                frame.bounds.width.roundToInt().coerceAtLeast(1), frame.bounds.height.roundToInt().coerceAtLeast(1),
            ))
            layout(constraints.maxWidth, constraints.maxHeight) {
                container.place(frame.bounds.left.roundToInt(), frame.bounds.top.roundToInt())
                panel.placeWithLayer(left, top) {
                    shape = PopupContentClip(frame.bounds.translate(-target.topLeft), frame.radius)
                    clip = true
                    alpha = effects.value * ((spatial.value - 0.15f) / 0.85f).coerceIn(0f, 1f)
                }
            }
        }
    }
}

/** The source is always circular, regardless of the actual icon pack's outline. */
private class PopupContainerShape(private val anchor: Rect, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val start = minOf(anchor.width, anchor.height).coerceAtLeast(1f) / 2f
        val radius = androidx.compose.ui.util.lerp(start, with(density) { 24.dp.toPx() }, progress.coerceAtLeast(0f))
            .coerceIn(0f, size.minDimension / 2f)
        return Outline.Rounded(RoundRect(Rect(Offset.Zero, size), CornerRadius(radius)))
    }
}

private class PopupContentClip(private val bounds: Rect, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(bounds, CornerRadius(radius)))
}
