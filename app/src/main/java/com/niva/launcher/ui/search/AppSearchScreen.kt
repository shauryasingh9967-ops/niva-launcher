package com.niva.launcher.ui.search

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.niva.launcher.R
import com.niva.launcher.data.*
import com.niva.launcher.platform.SearchLauncher
import com.niva.launcher.ui.LauncherActions
import com.niva.launcher.ui.LauncherUiState
import com.niva.launcher.ui.components.AppIcon
import com.niva.launcher.ui.components.LauncherLayout
import com.niva.launcher.ui.components.stableStatusBarInset
import com.niva.launcher.ui.components.LauncherSearchBar
import com.niva.launcher.ui.components.NivaButtonTransition
import com.niva.launcher.ui.theme.LocalLauncherAppearance
import kotlinx.coroutines.delay

private sealed interface SearchResult {
    val key: String
    data class App(val app: LauncherApp) : SearchResult { override val key = "app:" + app.key }
    data class Contact(val contact: SearchContact) : SearchResult { override val key = "contact:" + contact.id }
    data class Action(val action: LauncherSearchAction) : SearchResult { override val key = "action:" + action.id }
    data object Internet : SearchResult { override val key = "search_internet_result" }
}

/** Executable launcher actions searchable by name. */
private data class LauncherSearchAction(
    val id: String,
    val titleRes: Int,
    val summaryRes: Int,
    val keywords: List<String>,
    val run: () -> Boolean,
)

@Composable
internal fun AppSearchScreen(
    uiState: LauncherUiState,
    actions: LauncherActions,
    onLaunch: (LauncherApp) -> Unit,
    onDetails: (LauncherApp) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    backProgress: Float = 0f,
    enterAlpha: Float = 1f,
    onSettings: () -> Unit = {},
    queryState: TextFieldState = rememberTextFieldState(),
    transition: NivaButtonTransition? = null,
) {
    val settings = uiState.settings.search
    val query = queryState.text.toString().trim()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var requestKeyboard by remember { mutableStateOf(transition == null) }
    LaunchedEffect(transition) {
        // Give the hero a short head start, then let the IME enter alongside it.
        // Keep this true when the transition ends so focus is not requested twice.
        if (transition != null) delay(100)
        requestKeyboard = true
    }
    val appearance = LocalLauncherAppearance.current
    val contactsSource = remember(context) { SearchContacts(context) }
    var hasContactsAccess by remember { mutableStateOf(contactsSource.hasAccess()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, contactsSource) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasContactsAccess = contactsSource.hasAccess()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val contacts by produceState(emptyList<SearchContact>(), settings.enabled, settings.contacts, hasContactsAccess) {
        value = emptyList()
        if (settings.enabled && settings.contacts && hasContactsAccess) contactsSource.observe().collect { value = it }
    }
    val apps = remember(uiState.allApps, uiState.hiddenAppKeys, settings.hiddenApps, uiState.focusActive, uiState.focusAppKeys) {
        uiState.allApps.filter {
            it.folderId == null && (settings.hiddenApps || it.key !in uiState.hiddenAppKeys) &&
                !isFocusHidden(it.key, uiState.focusActive, uiState.focusAppKeys)
        }
    }
    val names = remember(apps) { apps.associate {
        val label = SearchName(it.label)
        it.key to (label to if (it.originalLabel == it.label) label else SearchName(it.originalLabel))
    } }
    val searchActions = remember(uiState.focusActive) {
        listOf(
            LauncherSearchAction(
                id = "settings", titleRes = R.string.search_action_settings,
                summaryRes = R.string.search_action_settings_summary,
                keywords = listOf("settings", "preferences", "options", "niva"),
                run = { onSettings(); true },
            ),
            LauncherSearchAction(
                id = "default_home", titleRes = R.string.search_action_default_home,
                summaryRes = R.string.search_action_default_home_summary,
                keywords = listOf("default", "home", "launcher"),
                run = { actions.requestDefaultHome(); true },
            ),
            LauncherSearchAction(
                id = "focus", titleRes = if (uiState.focusActive) R.string.search_action_focus_off else R.string.search_action_focus_on,
                summaryRes = R.string.search_action_focus_summary,
                keywords = listOf("focus", "distraction", "hide apps"),
                run = { actions.setFocusActive(!uiState.focusActive); true },
            ),
            LauncherSearchAction(
                id = "wallpaper", titleRes = R.string.search_action_wallpaper,
                summaryRes = R.string.search_action_wallpaper_summary,
                keywords = listOf("wallpaper", "background"),
                run = {
                    runCatching {
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_SET_WALLPAPER).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                        true
                    }.getOrDefault(false)
                },
            ),
            LauncherSearchAction(
                id = "widget", titleRes = R.string.search_action_widgets,
                summaryRes = R.string.search_action_widgets_summary,
                keywords = listOf("widget", "widgets"),
                run = { actions.addWidget(); true },
            ),
        )
    }
    val results: List<SearchResult> = remember(apps, names, contacts, query, settings, hasContactsAccess, searchActions) {
        if (query.isEmpty()) {
            if (settings.suggestions) {
                val byKey = apps.associateBy { it.key }
                settings.recentAppKeys.mapNotNull(byKey::get).distinctBy { it.key }.take(5).map { SearchResult.App(it) }
            } else emptyList()
        } else {
            val term = SearchQuery(query, settings.fuzzy)
            val matchedApps = apps.mapNotNull { app ->
                val (label, original) = names.getValue(app.key)
                val score = listOfNotNull(label.score(term), original.score(term)).minOrNull() ?: return@mapNotNull null
                score to app
            }.sortedWith(compareBy<Pair<Int, LauncherApp>> { it.first }.thenComparator { a, b -> LauncherAppOrder.compare(a.second, b.second) })
            val matchedContacts = if (settings.contacts && hasContactsAccess) contacts.mapNotNull { contact ->
                contact.score(term)?.let { it to contact }
            }.sortedBy { it.first } else emptyList()
            val matchedActions = searchActions.filter { action ->
                val haystack = (context.getString(action.titleRes) + " " + action.keywords.joinToString(" ")).lowercase()
                query.lowercase().split("\\s+".toRegex()).filter { it.length >= 2 }.any { it in haystack }
            }
            buildList {
                matchedActions.forEach { add(SearchResult.Action(it)) }
                matchedApps.forEach { add(SearchResult.App(it.second)) }
                matchedContacts.forEach { add(SearchResult.Contact(it.second)) }
                if (settings.internet) add(SearchResult.Internet)
            }
        }
    }
    fun open(result: SearchResult, bounds: Rect? = null) {
        keyboard?.hide()
        when (result) {
            is SearchResult.App -> {
                val launch = actions.launchSearchApp
                if (launch != null) { onDismiss(); launch(result.app, bounds) } else onLaunch(result.app)
            }
            is SearchResult.Action -> {
                if (result.action.run()) onDismiss()
                else Toast.makeText(context, R.string.app_unavailable, Toast.LENGTH_SHORT).show()
            }
            else -> {
                val opened = when (result) {
                    is SearchResult.Contact -> SearchLauncher.contact(context, result.contact)
                    SearchResult.Internet -> SearchLauncher.internet(context, query, settings)
                }
                if (opened) onDismiss() else Toast.makeText(context, R.string.app_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
    }
    val list = rememberLazyListState()
    LaunchedEffect(query) { list.requestScrollToItem(0) }
    val density = LocalDensity.current
    var searchBarHeight by remember { mutableStateOf(56.dp) }
    val listTop = 12.dp + searchBarHeight / 2
    // The IME resizes the viewport; navigation-bar space stays inside the list
    // so rows can draw behind the bar and still scroll fully above it.
    val bottomInset = WindowInsets.safeDrawing.exclude(WindowInsets.ime).asPaddingValues().calculateBottomPadding()
    val backDistance = with(density) { 30.dp.toPx() } * if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    // Wallpaper tint and window blur are owned by LauncherScreen, exactly as for the app list.
    Box(modifier.fillMaxSize().testTag("app_search").graphicsLayer {
        alpha = (if (transition != null) 1f else enterAlpha) * (1f - backProgress)
        translationX = backDistance * backProgress
    }.then(if (transition != null) Modifier.clearAndSetSemantics {} else Modifier)
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
        .padding(top = stableStatusBarInset())
        .imePadding().padding(horizontal = 16.dp)) {
        // Clip at the field's widest part, where the overlaid pill conceals the
        // entire edge. Content padding preserves the first result's resting gap.
        LazyColumn(Modifier.fillMaxSize().padding(top = listTop)
            .graphicsLayer { alpha = transition?.searchResultsAlpha ?: 1f }, state = list,
            contentPadding = PaddingValues(top = searchBarHeight / 2 + 16.dp, bottom = 24.dp + bottomInset)) {
            if (query.isNotEmpty() && results.isEmpty()) item {
                Text(stringResource(R.string.search_no_results), Modifier.padding(20.dp), color = appearance.text)
            }
            itemsIndexed(results, key = { _, result -> result.key }) { index, result ->
                val label = when (result) {
                    is SearchResult.App -> result.app.label
                    is SearchResult.Contact -> result.contact.name
                    is SearchResult.Action -> stringResource(result.action.titleRes)
                    SearchResult.Internet -> stringResource(R.string.search_internet_result)
                }
                SearchResultRow(label, result.key, first = index == 0, onClick = { open(result, it) },
                    onLongClick = (result as? SearchResult.App)?.let { { keyboard?.hide(); onDetails(it.app) } }) { iconModifier ->
                    when (result) {
                        is SearchResult.App -> AppIcon(result.app, iconModifier, size = 40.dp)
                        is SearchResult.Contact -> ContactAvatar(result.contact, contactsSource, iconModifier)
                        is SearchResult.Action -> Surface(modifier = iconModifier.size(40.dp), shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
                            Box(contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ms_bolt), null, Modifier.size(24.dp)) }
                        }
                        SearchResult.Internet -> Surface(modifier = iconModifier.size(40.dp), shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                            Box(contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ms_travel_explore), null, Modifier.size(24.dp)) }
                        }
                    }
                }
            }
        }
        LauncherSearchBar(
            queryState, stringResource(R.string.settings_search), "app_search_query",
            modifier = Modifier.padding(top = 12.dp).onSizeChanged { searchBarHeight = with(density) { it.height.toDp() } }
                .onGloballyPositioned { transition?.searchBounds = it.boundsInRoot() }
                .graphicsLayer { alpha = transition?.searchBarAlpha ?: 1f },
            autoFocus = requestKeyboard,
            onSearch = { results.firstOrNull()?.let { open(it) } },
            trailingIcon = {
                IconButton(onClick = { keyboard?.hide(); onSettings() }, modifier = Modifier.testTag("app_search_settings")) {
                    Icon(painterResource(R.drawable.ms_more_vert), stringResource(R.string.search_open_settings))
                }
            },
        )
    }
}

@Composable
private fun SearchResultRow(label: String, key: String, first: Boolean, onClick: (Rect) -> Unit,
    onLongClick: (() -> Unit)?, icon: @Composable (Modifier) -> Unit) {
    val appearance = LocalLauncherAppearance.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Row(Modifier.fillMaxWidth().heightIn(min = LauncherLayout.RowMinHeight).testTag(key)
        .clip(LauncherLayout.RowShape).combinedClickable(onClick = { onClick(bounds) }, onLongClick = onLongClick)
        .semantics { selected = first }.padding(LauncherLayout.ContentInset), verticalAlignment = Alignment.CenterVertically) {
        icon(Modifier.onGloballyPositioned { bounds = it.boundsInWindow() })
        Spacer(Modifier.width(LauncherLayout.IconLabelGap))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f, fill = false).testTag(key + ":label"), color = appearance.text,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (first) FontWeight.Bold else FontWeight.Normal, shadow = appearance.textShadow),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (first) Box(Modifier.padding(start = 8.dp).size(5.dp).background(appearance.text, CircleShape).testTag("search_first_result_dot"))
        }
    }
}

@Composable
private fun ContactAvatar(contact: SearchContact, source: SearchContacts, modifier: Modifier) {
    val photo by produceState<ImageBitmap?>(null, contact.id, contact.photo, source) { value = source.photo(contact) }
    if (photo != null) Image(photo!!, null, modifier.size(40.dp).clip(CircleShape), contentScale = ContentScale.Crop)
    else Surface(modifier = modifier.size(40.dp), shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Box(contentAlignment = Alignment.Center) {
            val name = contact.name.trim()
            Text(if (name.isEmpty()) "?" else String(Character.toChars(name.codePointAt(0))).uppercase(), style = MaterialTheme.typography.titleLarge)
        }
    }
}
