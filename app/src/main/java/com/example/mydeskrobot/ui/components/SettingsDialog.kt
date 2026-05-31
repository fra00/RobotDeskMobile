package com.example.mydeskrobot.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.mydeskrobot.R

@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    onOpenLlmSettings: () -> Unit,
    onOpenMemorySettings: () -> Unit,
    onOpenSttSettings: () -> Unit,
    onOpenVoskModelSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenHeartbeatSettings: () -> Unit,
    isVoskModelReady: Boolean,
    sttProviderName: String,
    notificationsEnabled: Boolean,
    isNotificationAccessGranted: Boolean,
    heartbeatEnabled: Boolean,
    onNotificationsEnabledChange: (Boolean) -> Unit,
    onSaveNotifications: () -> Unit,
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.settings_llm),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenLlmSettings)
                        .padding(vertical = 12.dp),
                )
                Text(
                    text = stringResource(R.string.settings_memory),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenMemorySettings)
                        .padding(vertical = 12.dp),
                )
                SettingsRow(
                    label = stringResource(R.string.settings_stt),
                    value = sttProviderName,
                    onClick = onOpenSttSettings,
                )
                SettingsRow(
                    label = stringResource(R.string.vosk_model_settings_entry),
                    value = if (isVoskModelReady) {
                        stringResource(R.string.vosk_model_status_ready)
                    } else {
                        stringResource(R.string.vosk_model_status_not_downloaded)
                    },
                    onClick = onOpenVoskModelSettings,
                )
                SettingsRow(
                    label = stringResource(R.string.settings_heartbeat),
                    value = if (heartbeatEnabled) {
                        stringResource(R.string.heartbeat_status_enabled)
                    } else {
                        stringResource(R.string.heartbeat_status_disabled)
                    },
                    onClick = onOpenHeartbeatSettings,
                )
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.settings_notifications),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNotificationsEnabledChange(!notificationsEnabled) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.notification_settings_enable),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = onNotificationsEnabledChange,
                    )
                }

                if (!isNotificationAccessGranted) {
                    Text(
                        text = stringResource(R.string.notification_settings_permission_required),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.notification_settings_open_permissions))
                    }
                } else {
                    SettingsRow(
                        label = stringResource(R.string.notification_settings_apps_title),
                        value = stringResource(R.string.settings_configure),
                        onClick = onOpenNotificationSettings,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSaveNotifications) {
                Text(stringResource(R.string.settings_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_close))
            }
        },
    )
}

@Composable
private fun SettingsRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
