package com.galaxyrio.gracelauncher.ui.home

import android.text.format.DateFormat
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.LauncherFolder
import com.galaxyrio.gracelauncher.data.ScheduleEvent
import com.galaxyrio.gracelauncher.data.ClockStyle
import com.galaxyrio.gracelauncher.data.GraceButtonGesture
import com.galaxyrio.gracelauncher.data.nextVisibleEvent
import com.galaxyrio.gracelauncher.data.media.MediaCommand
import com.galaxyrio.gracelauncher.data.media.NowPlaying
import com.galaxyrio.gracelauncher.data.media.IdleMediaSessionId
import com.galaxyrio.gracelauncher.data.weather.WeatherCurrent
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.AppRowGestures
import com.galaxyrio.gracelauncher.ui.components.LauncherAppRow
import com.galaxyrio.gracelauncher.ui.components.FolderRow
import com.galaxyrio.gracelauncher.ui.components.LauncherLayout
import com.galaxyrio.gracelauncher.ui.components.stableStatusBarInset
import com.galaxyrio.gracelauncher.ui.components.eventRemainingText
import com.galaxyrio.gracelauncher.ui.theme.LocalLauncherAppearance
import com.galaxyrio.gracelauncher.ui.theme.rememberBatteryPercent
import com.galaxyrio.gracelauncher.ui.weather.HomeWeather
import com.galaxyrio.gracelauncher.ui.widgets.HomeWidget
import com.galaxyrio.gracelauncher.ui.widgets.HomeEditHandle
import com.galaxyrio.gracelauncher.ui.widgets.rememberHostedWidget
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    uiState: LauncherUiState,
    topSpace: Dp,
    onLaunchApp: (LauncherApp) -> Unit,
    onAppDetails: (LauncherApp) -> Unit,
    onAppShortcuts: (LauncherApp, Rect) -> Unit,
    onDateClick: () -> Unit,
    onClockClick: () -> Unit,
    modifier: Modifier = Modifier,
    rowGestures: AppRowGestures = AppRowGestures(),
    highlightedAppKey: String? = null,
    onOpenFolder: (LauncherFolder, Rect) -> Unit = { _, _ -> },
    onEditFolder: (LauncherFolder) -> Unit = {},
    onFolderDrag: (LauncherFolder, Rect, Boolean) -> Unit = { _, _, _ -> },
    onFolderDragEnd: (Boolean) -> Unit = {},
    onMediaCommand: (String, MediaCommand) -> Unit = { _, _ -> },
    onDismissMedia: (String, Long) -> Boolean = { _, _ -> false },
    onWidgetMenu: () -> Unit = {},
    onCustomWidgetMenu: () -> Unit = {},
    editingLayout: Boolean = false,
    widgetInputEnabled: Boolean = true,
    onTopOffsetChange: (Float) -> Unit = {},
    onWidgetHeightChange: (Int) -> Unit = {},
    onHomeGesture: (GraceButtonGesture, Offset) -> Unit = { _, _ -> },
) {
    val now by produceState(initialValue = Instant.now()) {
        while (true) {
            value = Instant.now()
            delay(60_000 - System.currentTimeMillis() % 60_000)
        }
    }
    val event = if (uiState.settings.calendarAgenda) nextVisibleEvent(uiState.events, now) else null
    val favorites = uiState.favoriteItems
    var idleMediaDismissed by rememberSaveable(uiState.settings.mediaAlwaysVisible, uiState.settings.mediaPlayer) { mutableStateOf(false) }
    LaunchedEffect(uiState.homeMedia?.sessionId, uiState.homeMedia?.revision) {
        if (uiState.homeMedia != null) idleMediaDismissed = false
    }
    val media = uiState.homeMedia ?: if (uiState.settings.mediaPlayer && uiState.settings.mediaAlwaysVisible &&
        !idleMediaDismissed && !uiState.isLoadingSettings && !uiState.settingsLoadFailed) NowPlaying(
        sessionId = IdleMediaSessionId, playerName = stringResource(R.string.settings_media_player),
        title = stringResource(R.string.media_idle_title), artist = stringResource(R.string.media_idle_summary),
        playing = false, canToggle = true, canPrevious = false, canNext = false,
    ) else null
    val listState = rememberLazyListState()
    val homeLayout = uiState.settings.homeLayout
    val hostedWidget = if (homeLayout.hasWidget && !uiState.isLoadingSettings && !uiState.settingsLoadFailed) rememberHostedWidget(homeLayout) else null
    var topOffset by remember(homeLayout.topOffsetDp) { mutableFloatStateOf(homeLayout.topOffsetDp) }
    var widgetHeight by remember(homeLayout.widgetId, homeLayout.widgetHeightDp) { mutableFloatStateOf(homeLayout.widgetHeightDp.toFloat()) }
    var headerHeightPx by remember { mutableIntStateOf(0) }
    var anchorOffsetPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val context = LocalContext.current
    val doubleTapSlop = remember(context, density) { android.view.ViewConfiguration.get(context).scaledDoubleTapSlop.toFloat() }
    val minimumTop = stableStatusBarInset() + 8.dp
    val bottomInset = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
    LaunchedEffect(editingLayout) { if (editingLayout) listState.scrollToItem(0) }
    val overscroll = rememberOverscrollEffect()
    val gesturesEnabled = widgetInputEnabled && !editingLayout && uiState.settings.homeGestures.enabled &&
        !uiState.isLoadingSettings && !uiState.settingsLoadFailed
    val latestGestures by rememberUpdatedState(uiState.settings.homeGestures)
    val latestEnabled by rememberUpdatedState(gesturesEnabled)
    val latestOnGesture by rememberUpdatedState(onHomeGesture)
    val swipeSensitivity = uiState.settings.homeGestures.swipeSensitivity.coerceIn(50, 200) / 100f
    var windowOffset by remember { mutableStateOf(Offset.Zero) }
    var pointerPosition by remember { mutableStateOf(Offset.Zero) }
    val scrollConnection = remember(overscroll, listState, density, swipeSensitivity) {
        HomeGestureConnection(overscroll, listState,
            pullThreshold = with(density) { 96.dp.toPx() } / swipeSensitivity,
            flingThreshold = with(density) { 3000.dp.toPx() } / swipeSensitivity,
            canTrigger = { latestEnabled && latestGestures.target(it).active },
            onGesture = { latestOnGesture(it, windowOffset + pointerPosition) })
    }
    LaunchedEffect(gesturesEnabled) { if (!gesturesEnabled) scrollConnection.cancelGesture() }

    BoxWithConstraints(modifier.fillMaxSize()
        .onGloballyPositioned { windowOffset = it.positionInWindow() }
        .pointerInput(scrollConnection) {
            // Observe without consuming: app/widget interactions and list scrolling
            // continue to own their pointer stream.
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                pointerPosition = down.position
                scrollConnection.beginGesture()
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.size > 1) scrollConnection.cancelGesture()
                    event.changes.firstOrNull { it.id == down.id }?.let { pointerPosition = it.position }
                } while (event.changes.any { it.pressed })
            }
        }
        .then(if (gesturesEnabled && latestGestures.doubleTap.active) Modifier.pointerInput(doubleTapSlop, latestGestures.doubleTapIntervalMs) {
            observeBlankDoubleTaps(doubleTapSlop, latestGestures.doubleTapIntervalMs) { position ->
                if (latestEnabled && latestGestures.doubleTap.active) latestOnGesture(GraceButtonGesture.DoubleTap, windowOffset + position)
            }
        } else Modifier)) {
    val availableHeight = (maxHeight - bottomInset).coerceAtLeast(0.dp)
    val headerHeight = with(density) { headerHeightPx.toDp() }
    // Only content ABOVE the calendar anchor constrains its position. Agenda,
    // media, widgets and favorites grow downwards without moving this anchor.
    // Measure the boundary, not a hard-coded clock height, so it can be optional later.
    val anchorOffset = with(density) { anchorOffsetPx.toDp() }
    val maximumTop = (availableHeight - anchorOffset - 56.dp).coerceAtLeast(minimumTop)
    val resolvedTop = (topSpace + topOffset.dp).coerceIn(minimumTop, maximumTop)
    val minWidgetHeight = hostedWidget?.minHeight ?: 0
    val maximumWidgetHeight = minOf(hostedWidget?.maxHeight ?: 0,
        (availableHeight - minimumTop - headerHeight - 56.dp).value.toInt()).coerceAtLeast(minWidgetHeight)
    val resolvedWidgetHeight = if (hostedWidget == null) 0 else
        (if (widgetHeight == 0f) hostedWidget.defaultHeight else widgetHeight.roundToInt()).coerceIn(minWidgetHeight, maximumWidgetHeight)
    val resizeLimit = (availableHeight - resolvedTop - headerHeight - 56.dp).value.toInt()
        .coerceIn(minWidgetHeight, maximumWidgetHeight)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
            .overscroll(overscroll)
            .nestedScroll(scrollConnection)
            .testTag("home_content"),
        // The row's 8dp inset keeps icons aligned at 44dp while giving its
        // rounded touch surface breathing room around the icon.
        contentPadding = PaddingValues(start = LauncherLayout.Start, end = LauncherLayout.End, top = resolvedTop, bottom = 72.dp + bottomInset),
        userScrollEnabled = !editingLayout,
        // Feed the native effect at both boundaries even when every row fits.
        // LazyColumn's own effect is disabled to avoid stretching twice.
        overscrollEffect = null,
    ) {
        item(key = "date", contentType = "date") {
            HomeClockHeader(
                now = now,
                event = event,
                onDateClick = onDateClick,
                onClockClick = onClockClick,
                onLongClick = onWidgetMenu,
                interactive = !editingLayout,
                onAnchorOffsetChange = { anchorOffsetPx = it },
                showClock = uiState.settings.clockEnabled,
                showCalendar = uiState.settings.calendarAgenda,
                calendarAboveClock = uiState.settings.calendarAboveClock,
                clockStyle = uiState.settings.clockStyle,
                showBattery = uiState.settings.showBatteryPercentage,
                weather = uiState.weather.snapshot?.current.takeIf {
                    uiState.settings.weatherEnabled && !uiState.isLoadingSettings && !uiState.settingsLoadFailed
                },
                modifier = Modifier.fillMaxWidth().onSizeChanged { headerHeightPx = it.height },
            )
            Spacer(Modifier.height(2.dp))
        }
        if (hostedWidget != null) item(key = "widget:${homeLayout.widgetId}", contentType = "widget") {
            HomeWidget(homeLayout, hostedWidget, resolvedWidgetHeight, editingLayout,
                enabled = widgetInputEnabled, hapticsEnabled = uiState.settings.allowHapticFeedback,
                onLongPress = onCustomWidgetMenu)
            Spacer(Modifier.height(2.dp))
        }
        if (media != null) item(key = "media", contentType = "media") {
            HomeMediaPlayer(media, onMediaCommand, onDismiss = { id, revision ->
                if (id == IdleMediaSessionId) { idleMediaDismissed = true; true }
                else onDismissMedia(id, revision).also { if (it) idleMediaDismissed = true }
            })
            Spacer(Modifier.height(2.dp))
        }
        items(favorites, key = LauncherApp::key, contentType = { if (it.folderId == null) "app" else "folder" }) { app ->
            val folder = app.folderId?.let { id -> uiState.folders.firstOrNull { it.id == id } }
            if (folder != null) FolderRow(
                folder = folder,
                app = app,
                highlighted = highlightedAppKey == folder.key,
                onOpen = { onOpenFolder(folder, it) },
                onLongClick = { onEditFolder(folder) },
                onDrag = { bounds, expanded -> onFolderDrag(folder, bounds, expanded) },
                onDragEnd = onFolderDragEnd,
                showLabel = !uiState.settings.hideFavoriteNames,
            ) else LauncherAppRow(
                app = app,
                onClick = { onLaunchApp(app) },
                onLongClick = { onAppDetails(app) },
                onSwipeRight = { onAppShortcuts(app, it) },
                gestures = rowGestures,
                highlighted = highlightedAppKey == app.key,
                notification = uiState.notifications[app.packageName]?.firstOrNull().takeUnless { app.user != null },
                showLabel = !uiState.settings.hideFavoriteNames,
            )
        }
    }
    // The visual move handle stays BELOW the agenda, independently of the
    // actual anchor above the calendar. Its position never drives the layout.
    // Overlay the handles in the viewport, not outside a lazy item's bounds:
    // both halves of each circular control must remain inside its hit-test area.
    if (editingLayout) {
        val handleModifier = Modifier.padding(start = LauncherLayout.Start, end = LauncherLayout.End)
        HomeEditHandle(
            label = stringResource(R.string.widget_move_home), changed = topOffset != 0f, tag = "home_position_handle",
            onReset = { topOffset = 0f; onTopOffsetChange(0f) },
            onDelta = { delta ->
                val currentTop = (topSpace.value + topOffset).coerceIn(minimumTop.value, maximumTop.value)
                topOffset = (currentTop + delta).coerceIn(minimumTop.value, maximumTop.value) - topSpace.value
            },
            onFinished = { onTopOffsetChange(topOffset) },
            modifier = handleModifier.offset(y = resolvedTop + headerHeight - 20.dp),
        )
        if (hostedWidget != null) HomeEditHandle(
            label = stringResource(R.string.widget_resize), changed = widgetHeight != 0f && widgetHeight.roundToInt() != hostedWidget.defaultHeight,
            tag = "home_widget_size_handle",
            onReset = { widgetHeight = 0f; onWidgetHeightChange(0) },
            onDelta = { delta -> widgetHeight = ((if (widgetHeight == 0f) resolvedWidgetHeight.toFloat() else widgetHeight) + delta)
                .coerceIn(minWidgetHeight.toFloat(), resizeLimit.toFloat()) },
            onFinished = { onWidgetHeightChange(if (widgetHeight.roundToInt() == hostedWidget.defaultHeight) 0 else widgetHeight.roundToInt()) },
            modifier = handleModifier.offset(y = resolvedTop + headerHeight + 2.dp + resolvedWidgetHeight.dp - 20.dp),
        )
    }
    }
}

@Composable
internal fun HomeClockHeader(
    now: Instant,
    event: ScheduleEvent?,
    onDateClick: () -> Unit,
    onClockClick: () -> Unit,
    showBattery: Boolean,
    clockStyle: ClockStyle,
    weather: WeatherCurrent?,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    clockTag: String = "home_clock",
    dateTag: String = "home_date",
    onLongClick: () -> Unit = {},
    onAnchorOffsetChange: (Int) -> Unit = {},
    showClock: Boolean = true,
    showCalendar: Boolean = true,
    calendarAboveClock: Boolean = false,
) {
    val appearance = LocalLauncherAppearance.current
    val battery = if (showCalendar && showBattery) rememberBatteryPercent() else null
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val clockText = formatHomeClock(now, DateFormat.is24HourFormat(context), locale)
    val week = showClock && clockStyle.layout.week
    val weekday = SimpleDateFormat("EEE", locale).format(Date.from(now))
    val datePattern = DateFormat.getBestDateTimePattern(locale, if (week) "MMMd" else "MMMEd")
    val dateText = SimpleDateFormat(datePattern, locale).format(Date.from(now))
    val dateDescription = stringResource(if (weather != null) R.string.weather_agenda_action else R.string.date_calendar_action, dateText)
    val clockDescription = stringResource(R.string.clock_action, clockText)

    val clockContent: @Composable () -> Unit = {
        if (showClock) ClockFace(
            time = if (week) weekday else clockText,
            style = clockStyle,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(clockTag)
                .semantics { contentDescription = if (week) dateDescription else clockDescription }
                .then(if (interactive) Modifier.combinedClickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                    onClick = if (week) onDateClick else onClockClick,
                    onLongClick = onLongClick,
                ) else Modifier)
                .padding(horizontal = LauncherLayout.ContentInset),
            color = appearance.text,
        )
    }
    val calendarContent: @Composable () -> Unit = {
        val shape = LauncherLayout.RowShape
        if (showCalendar || weather?.temperature != null) Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(dateTag)
                .semantics { contentDescription = dateDescription }
                .clip(shape)
                .then(if (interactive) Modifier.combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = appearance.text),
                    role = Role.Button,
                    onClick = onDateClick,
                    onLongClick = onLongClick,
                ) else Modifier),
            shape = shape,
            color = Color.Transparent,
            contentColor = appearance.text,
        ) {
            val dateStyle = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Normal,
                shadow = appearance.textShadow,
            )
            Column(Modifier.padding(vertical = LauncherLayout.ContentInset)) {
                Row(Modifier.padding(horizontal = LauncherLayout.ContentInset, vertical = 2.dp)) {
                    if (showCalendar && week) {
                        ClockTimeLabel(
                            time = clockText, style = clockStyle, textStyle = dateStyle,
                            modifier = Modifier.alignByBaseline()
                                .semantics { contentDescription = clockDescription }
                                .then(if (interactive) Modifier.combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                                    onClick = onClockClick,
                                    onLongClick = onLongClick,
                                ) else Modifier),
                            color = appearance.text,
                        )
                        Text(" · ", modifier = Modifier.alignByBaseline(), color = appearance.text, style = dateStyle)
                    }
                    if (showCalendar) Text(
                        text = dateText + (battery?.let { "  $it%" } ?: ""),
                        modifier = Modifier.weight(1f, fill = false).alignByBaseline().testTag("${dateTag}_text"),
                        color = appearance.text,
                        style = dateStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (weather?.temperature != null) {
                        if (showCalendar) Spacer(Modifier.width(10.dp))
                        HomeWeather(weather, appearance.text, dateStyle, Modifier.alignByBaseline())
                    }
                }
                if (showCalendar && event != null) {
                    ScheduleLine(event = event, now = now)
                }
            }
        }
    }
    Column(modifier) {
        if (calendarAboveClock) calendarContent() else clockContent()
        Spacer(Modifier.height(if (showClock && (showCalendar || weather != null)) 2.dp else 0.dp)
            .onGloballyPositioned { onAnchorOffsetChange(it.positionInParent().y.roundToInt()) })
        if (calendarAboveClock) clockContent() else calendarContent()
    }
}

@Composable
private fun ScheduleLine(event: ScheduleEvent, now: Instant) {
    val appearance = LocalLauncherAppearance.current
    val remaining = eventRemainingText(event, now)
    val style = MaterialTheme.typography.bodyLarge.copy(
        fontWeight = FontWeight.Normal,
        shadow = appearance.textShadow,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("schedule_line")
            .padding(horizontal = LauncherLayout.ContentInset, vertical = 2.dp),
    ) {
        Text(
            text = event.title,
            // CJK fallback and Latin fonts can have different ascents at the same size.
            modifier = Modifier.weight(1f, fill = false).alignByBaseline().testTag("schedule_title"),
            style = style,
            color = appearance.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = "· $remaining",
            modifier = Modifier.alignByBaseline().testTag("schedule_remaining"),
            style = style,
            color = appearance.text.copy(alpha = 0.93f),
            maxLines = 1,
        )
    }
}
