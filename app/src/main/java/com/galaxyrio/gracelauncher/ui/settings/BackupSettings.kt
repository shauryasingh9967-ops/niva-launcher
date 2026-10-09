package com.galaxyrio.gracelauncher.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.galaxyrio.gracelauncher.data.SettingsBackup
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Backup & restore: export settings to a JSON file, import with validation. */
@Composable
internal fun BackupSettings(
    uiState: LauncherUiState,
    actions: LauncherActions,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmImport by remember { mutableStateOf<String?>(null) }

    fun readText(uri: Uri, onResult: (String?) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?.toString(Charsets.UTF_8)?.take(4 * 1024 * 1024)
            }.getOrNull()
            withContext(Dispatchers.Main) { onResult(text) }
        }
    }

    fun writeText(uri: Uri, text: String, onResult: (Boolean) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                true
            }.getOrDefault(false)
            withContext(Dispatchers.Main) { onResult(ok) }
        }
    }

    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val json = runCatching { actions.exportBackup() }.getOrNull()
            if (json == null) {
                busy = false
                message = context.getString(R.string.backup_export_failed)
                return@launch
            }
            writeText(uri, json) { ok ->
                busy = false
                message = context.getString(if (ok) R.string.backup_export_done else R.string.backup_export_failed)
            }
        }
    }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        readText(uri) { text ->
            if (text == null) {
                busy = false
                message = context.getString(R.string.backup_import_failed)
                return@readText
            }
            // Validate before asking for confirmation; never apply unvalidated data.
            val parsed = try {
                SettingsBackup.parse(text)
            } catch (e: SettingsBackup.InvalidBackupException) {
                busy = false
                message = context.getString(R.string.backup_invalid, e.message ?: "")
                return@readText
            } catch (e: Exception) {
                busy = false
                message = context.getString(R.string.backup_import_failed)
                return@readText
            }
            busy = false
            confirmImport = text
            // Keep the parsed value out of the dialog state; re-parse on confirm (cheap, validated shape).
            @Suppress("UNUSED_VARIABLE") val unused = parsed
        }
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            confirmButton = { TextButton(onClick = { message = null }) { Text(stringResource(android.R.string.ok)) } },
            text = { Text(text) },
        )
    }

    confirmImport?.let { raw ->
        AlertDialog(
            onDismissRequest = { confirmImport = null },
            title = { Text(stringResource(R.string.backup_import_title)) },
            text = { Text(stringResource(R.string.backup_import_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmImport = null
                    busy = true
                    scope.launch {
                        val ok = try {
                            actions.importBackup(SettingsBackup.parse(raw))
                        } catch (e: Exception) {
                            false
                        }
                        busy = false
                        message = context.getString(if (ok) R.string.backup_import_done else R.string.backup_import_failed)
                    }
                }) { Text(stringResource(R.string.backup_import_confirm_button)) }
            },
            dismissButton = { TextButton(onClick = { confirmImport = null }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }

    SettingsScaffold(stringResource(R.string.backup_title), "settings_backup", onBack) { padding ->
        SettingsList(padding) {
            item { SettingsHeading(stringResource(R.string.backup_heading)) }
            item {
                Text(
                    stringResource(R.string.backup_description),
                    modifier = Modifier,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
            item {
                SettingsActionItem(
                    stringResource(R.string.backup_export), stringResource(R.string.backup_export_summary),
                    0, 2, "backup_export", enabled = !busy,
                ) {
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                    exportPicker.launch("niva-launcher-backup-$stamp.json")
                }
            }
            item {
                SettingsActionItem(
                    stringResource(R.string.backup_import), stringResource(R.string.backup_import_summary),
                    1, 2, "backup_import", enabled = !busy,
                ) {
                    importPicker.launch(arrayOf("application/json", "text/plain", "*/*"))
                }
            }
        }
    }
}
