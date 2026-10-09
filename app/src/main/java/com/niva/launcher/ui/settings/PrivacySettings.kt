package com.niva.launcher.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.niva.launcher.R
import com.niva.launcher.ui.LauncherActions
import com.niva.launcher.ui.LauncherUiState

/**
 * Privacy controls: what Niva requests, what stays on the device, and how to
 * enable or disable each optional integration. No analytics, ads or tracking
 * exist in this app; this screen documents that and the platform limits.
 */
@Composable
internal fun PrivacySettings(
    uiState: LauncherUiState,
    actions: LauncherActions,
    onBack: () -> Unit,
    navigate: (SettingsPage) -> Unit,
) {
    val settings = uiState.settings
    SettingsScaffold(stringResource(R.string.privacy_title), "settings_privacy", onBack) { padding ->
        SettingsList(padding) {
            item { SettingsHeading(stringResource(R.string.privacy_local_heading)) }
            item {
                Text(
                    stringResource(R.string.privacy_local_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
            item { SettingsHeading(stringResource(R.string.privacy_permissions_heading)) }
            val rows = listOf(
                Triple(R.string.privacy_perm_apps, R.string.privacy_perm_apps_summary, null as SettingsPage?),
                Triple(R.string.privacy_perm_calendar, R.string.privacy_perm_calendar_summary, SettingsPage.Calendar),
                Triple(R.string.privacy_perm_contacts, R.string.privacy_perm_contacts_summary, SettingsPage.Search),
                Triple(R.string.privacy_perm_notifications, R.string.privacy_perm_notifications_summary, SettingsPage.MediaPlayer),
                Triple(R.string.privacy_perm_accessibility, R.string.privacy_perm_accessibility_summary, SettingsPage.NivaButton),
                Triple(R.string.privacy_perm_biometric, R.string.privacy_perm_biometric_summary, SettingsPage.PrivateSpace),
                Triple(R.string.privacy_perm_widgets, R.string.privacy_perm_widgets_summary, null),
            )
            rows.forEachIndexed { index, (title, summary, page) ->
                item(key = "privacy_perm_$index") {
                    SettingsActionItem(
                        stringResource(title), stringResource(summary), index, rows.size,
                        tag = "privacy_perm_$index", enabled = page != null,
                    ) { page?.let(navigate) }
                }
            }
            item { SettingsHeading(stringResource(R.string.privacy_optional_heading)) }
            item {
                Text(
                    stringResource(R.string.privacy_optional_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
            val toggles = listOf(
                Triple(
                    R.string.privacy_toggle_contacts, R.string.privacy_toggle_contacts_summary,
                    { checked: Boolean -> actions.updateSettings { it.copy(search = it.search.copy(contacts = checked)) } } to settings.search.contacts,
                ),
                Triple(
                    R.string.privacy_toggle_agenda, R.string.privacy_toggle_agenda_summary,
                    { checked: Boolean -> actions.updateSettings { it.copy(calendarAgenda = checked) } } to settings.calendarAgenda,
                ),
                Triple(
                    R.string.privacy_toggle_media, R.string.privacy_toggle_media_summary,
                    { checked: Boolean -> actions.updateSettings { it.copy(mediaPlayer = checked) } } to settings.mediaPlayer,
                ),
                Triple(
                    R.string.privacy_toggle_weather, R.string.privacy_toggle_weather_summary,
                    { checked: Boolean -> actions.updateSettings { it.copy(weatherEnabled = checked) } } to settings.weatherEnabled,
                ),
            )
            toggles.forEachIndexed { index, (title, summary, handler) ->
                val (onChange, checked) = handler
                item(key = "privacy_toggle_$index") {
                    SettingsToggleItem(
                        stringResource(title), stringResource(summary), checked,
                        index, toggles.size, "privacy_toggle_$index",
                    ) { onChange(it) }
                }
            }
            item { SettingsHeading(stringResource(R.string.privacy_limits_heading)) }
            item {
                Text(
                    stringResource(R.string.privacy_limits_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
