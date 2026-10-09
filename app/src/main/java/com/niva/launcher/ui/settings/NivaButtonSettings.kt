package com.niva.launcher.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.niva.launcher.R
import com.niva.launcher.data.NivaButtonAction
import com.niva.launcher.data.NivaButtonGesture
import com.niva.launcher.data.NivaButtonTarget
import com.niva.launcher.platform.NivaSystemActions
import com.niva.launcher.ui.LauncherActions
import com.niva.launcher.ui.LauncherUiState
import com.niva.launcher.ui.components.NivaSystemAccessDialog
import com.niva.launcher.ui.components.labelRes

@Composable
internal fun NivaButtonSettingsScreen(uiState: LauncherUiState, actions: LauncherActions, onBack: () -> Unit,
    home: Boolean = false, onEditIcon: () -> Unit = {}, onChooseGesture: (NivaButtonGesture) -> Unit) {
    val settings = uiState.settings.gestureSettings(home)
    val gestures = if (home) listOf(NivaButtonGesture.SwipeUp, NivaButtonGesture.SwipeDown, NivaButtonGesture.DoubleTap) else NivaButtonGesture.entries
    SettingsScaffold(stringResource(if (home) R.string.settings_gestures else R.string.settings_niva_button),
        if (home) "settings_home_gestures" else "settings_niva_button_page", onBack) { padding ->
        SettingsList(padding) {
            item { SettingsFeatureBanner(stringResource(if (home) R.string.home_gestures_enable else R.string.niva_button_enable), settings.enabled, "gestures_enabled") { value ->
                actions.updateSettings { it.withGestureSettings(home) { settings -> settings.copy(enabled = value) } }
            } }
            if (!home) {
                item {
                    SettingsActionItem(stringResource(R.string.edit_icon), null, 0, 1,
                        "niva_button_icon", onClick = onEditIcon)
                    Spacer(Modifier.height(16.dp))
                }
            }
            gestures.forEachIndexed { index, gesture ->
                item {
                    val target = settings.target(gesture)
                    val title = stringResource(if (home && gesture == NivaButtonGesture.DoubleTap) R.string.home_gesture_double_tap else gesture.labelRes)
                    SettingsActionItem(title, if (target.active) target.summary(uiState) else stringResource(R.string.niva_button_disabled),
                        index, gestures.size, "niva_gesture:${gesture.name}",
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                VerticalDivider(Modifier.height(32.dp))
                                Switch(target.active, onCheckedChange = { enabled ->
                                    actions.updateSettings { current -> current.withGestureSettings(home) { it.withEnabled(gesture, enabled) } }
                                }, enabled = LocalSettingsStorageState.current.canEdit,
                                    modifier = Modifier.padding(start = 16.dp).testTag("niva_gesture_switch:${gesture.name}")
                                        .semantics { contentDescription = title })
                            }
                        }) { onChooseGesture(gesture) }
                }
            }
        }
    }
}

@Composable
internal fun NivaButtonActionSettings(gesture: NivaButtonGesture, uiState: LauncherUiState, actions: LauncherActions,
    onBack: () -> Unit, home: Boolean = false, onChooseTarget: (shortcut: Boolean) -> Unit) {
    val target = uiState.settings.gestureSettings(home).target(gesture)
    var websiteDialog by rememberSaveable { mutableStateOf(false) }
    var accessDialog by rememberSaveable { mutableStateOf(false) }
    val options = NivaButtonAction.entries.filterNot { it == NivaButtonAction.Disabled }
    fun select(choice: NivaButtonTarget) {
        actions.updateSettings { it.withGestureSettings(home) { settings -> settings.withTarget(gesture, choice) } }
        if (choice.action.requiresAccessibility && !NivaSystemActions.hasAccessibility) accessDialog = true
    }
    SettingsScaffold(stringResource(if (home && gesture == NivaButtonGesture.DoubleTap) R.string.home_gesture_double_tap else gesture.labelRes), "niva_action_page", onBack) { padding ->
        SettingsList(padding) {
            item { Spacer(Modifier.height(24.dp)) }
            options.forEachIndexed { index, action ->
                item {
                    val checked = target.action == action
                    SettingsActionItem(stringResource(action.labelRes),
                        if (checked && action in listOf(NivaButtonAction.App, NivaButtonAction.Shortcut, NivaButtonAction.Website))
                            target.summary(uiState) else null,
                        index, options.size, "niva_action:${action.name}",
                        leading = { RadioButton(checked, onClick = null) },
                        modifier = Modifier.semantics { role = Role.RadioButton; selected = checked },
                    ) {
                        when (action) {
                            NivaButtonAction.App, NivaButtonAction.Shortcut -> onChooseTarget(action == NivaButtonAction.Shortcut)
                            NivaButtonAction.Website -> websiteDialog = true
                            else -> select(NivaButtonTarget(action))
                        }
                    }
                }
            }
            if (target.action.requiresAccessibility) item {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.niva_accessibility_required), Modifier.padding(4.dp), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { accessDialog = true }) { Text(stringResource(R.string.media_open_settings)) }
            }
        }
    }
    if (websiteDialog) NivaWebsiteDialog(target.url.orEmpty(), { websiteDialog = false }) { url ->
        select(NivaButtonTarget(NivaButtonAction.Website, url = url))
        websiteDialog = false
    }
    if (accessDialog) NivaSystemAccessDialog { accessDialog = false }
}

@Composable
private fun NivaWebsiteDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val uri = remember(text) { NivaSystemActions.websiteUri(text) }
    val editable = LocalSettingsStorageState.current.canEdit
    fun save() { if (editable && uri != null) onSave(uri.toString()) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.niva_button_open_website)) },
        text = {
            OutlinedTextField(text, onValueChange = { text = it.take(2048) }, enabled = editable, singleLine = true,
                label = { Text(stringResource(R.string.niva_button_url)) },
                supportingText = { Text(stringResource(if (text.isNotBlank() && uri == null) R.string.niva_button_url_error else R.string.niva_button_url_hint)) },
                isError = text.isNotBlank() && uri == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth().testTag("niva_website_url"))
        },
        confirmButton = { TextButton(onClick = ::save, enabled = editable && uri != null) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun NivaButtonTarget.summary(uiState: LauncherUiState): String = when (action) {
    NivaButtonAction.App, NivaButtonAction.Shortcut -> itemKey?.let { uiState.findItem(it)?.label }
        ?: stringResource(R.string.popup_item_unavailable)
    NivaButtonAction.Website -> url.orEmpty()
    else -> stringResource(action.labelRes)
}

private val NivaButtonAction.labelRes: Int get() = when (this) {
    NivaButtonAction.App -> R.string.niva_button_open_app
    NivaButtonAction.Shortcut -> R.string.niva_button_open_shortcut
    NivaButtonAction.Settings -> R.string.niva_settings
    NivaButtonAction.Search -> R.string.settings_search
    NivaButtonAction.Website -> R.string.niva_button_open_website
    NivaButtonAction.LockScreen -> R.string.niva_button_lock_screen
    NivaButtonAction.AppList -> R.string.niva_button_open_app_list
    NivaButtonAction.Notifications -> R.string.niva_button_notifications
    NivaButtonAction.QuickSettings -> R.string.niva_button_quick_settings
    NivaButtonAction.Assistant -> R.string.niva_button_assistant
    NivaButtonAction.Agenda -> R.string.niva_button_agenda
    NivaButtonAction.Disabled -> R.string.niva_button_disabled
}
