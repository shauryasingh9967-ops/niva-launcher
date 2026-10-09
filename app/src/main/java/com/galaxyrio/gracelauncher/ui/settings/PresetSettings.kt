package com.galaxyrio.gracelauncher.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.AppearancePreset
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.overlays.TextEntryDialog
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Appearance presets: save the current look under a name, apply or delete later. */
@Composable
internal fun PresetSettings(
    uiState: LauncherUiState,
    actions: LauncherActions,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val presets by actions.presets.collectAsState()
    var showNameDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<AppearancePreset?>(null) }
    var applyTarget by remember { mutableStateOf<AppearancePreset?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            confirmButton = { TextButton(onClick = { message = null }) { Text(stringResource(android.R.string.ok)) } },
            text = { Text(text) },
        )
    }

    if (showNameDialog) {
        TextEntryDialog(
            title = stringResource(R.string.preset_save_title),
            initial = "",
            onDismiss = { showNameDialog = false },
            onSave = { name ->
                showNameDialog = false
                if (name.isBlank()) return@TextEntryDialog
                busy = true
                scope.launch {
                    val ok = actions.savePreset(name)
                    busy = false
                    if (!ok) message = context.getString(R.string.preset_save_failed)
                }
            },
        )
    }

    deleteTarget?.let { preset ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.preset_delete_title)) },
            text = { Text(stringResource(R.string.preset_delete_confirm, preset.name)) },
            confirmButton = {
                TextButton(onClick = {
                    actions.deletePreset(preset.id)
                    deleteTarget = null
                }) { Text(stringResource(R.string.preset_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }

    applyTarget?.let { preset ->
        AlertDialog(
            onDismissRequest = { applyTarget = null },
            title = { Text(stringResource(R.string.preset_apply_title)) },
            text = { Text(stringResource(R.string.preset_apply_confirm, preset.name)) },
            confirmButton = {
                TextButton(onClick = {
                    applyTarget = null
                    busy = true
                    scope.launch {
                        val ok = actions.applyPreset(preset)
                        busy = false
                        message = context.getString(if (ok) R.string.preset_applied else R.string.preset_apply_failed)
                    }
                }) { Text(stringResource(R.string.preset_apply)) }
            },
            dismissButton = { TextButton(onClick = { applyTarget = null }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }

    SettingsScaffold(stringResource(R.string.preset_title), "settings_presets", onBack) { padding ->
        SettingsList(padding) {
            item { SettingsHeading(stringResource(R.string.preset_heading)) }
            item {
                Text(
                    stringResource(R.string.preset_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
            item {
                SettingsActionItem(
                    stringResource(R.string.preset_save_current), stringResource(R.string.preset_save_current_summary),
                    0, 1, "preset_save", enabled = !busy,
                ) { showNameDialog = true }
            }
            if (presets.isNotEmpty()) {
                item { SettingsHeading(stringResource(R.string.preset_saved_heading)) }
                presets.sortedByDescending { it.createdAt }.forEachIndexed { index, preset ->
                    item(key = preset.id) {
                        val date = remember(preset.createdAt) {
                            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(preset.createdAt))
                        }
                        SettingsActionItem(
                            preset.name, date, index, presets.size, "preset_${preset.id}",
                            enabled = !busy,
                            trailing = {
                                TextButton(onClick = { deleteTarget = preset }, enabled = !busy) {
                                    Text(stringResource(R.string.preset_delete))
                                }
                            },
                        ) { applyTarget = preset }
                    }
                }
            }
        }
    }
}
