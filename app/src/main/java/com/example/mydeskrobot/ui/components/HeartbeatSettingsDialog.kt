package com.example.mydeskrobot.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.mydeskrobot.R
import com.example.mydeskrobot.presentation.settings.HeartbeatSettingsFormState
import kotlin.math.roundToInt

@Composable
fun HeartbeatSettingsDialog(
    form: HeartbeatSettingsFormState,
    onFormChange: (HeartbeatSettingsFormState) -> Unit,
    onManageDomains: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.heartbeat_settings_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.heartbeat_enabled_label),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = form.enabled,
                        onCheckedChange = { onFormChange(form.copy(enabled = it)) },
                    )
                }
                Text(
                    text = stringResource(R.string.heartbeat_enabled_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.heartbeat_interval_label, form.intervalMinutes),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = form.intervalMinutes.toFloat(),
                    onValueChange = { onFormChange(form.copy(intervalMinutes = it.roundToInt())) },
                    valueRange = 5f..30f,
                    steps = 4,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.heartbeat_time_window_label, form.startHour, form.endHour),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.heartbeat_start_hour),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Slider(
                        value = form.startHour.toFloat(),
                        onValueChange = { onFormChange(form.copy(startHour = it.roundToInt())) },
                        valueRange = 0f..23f,
                        steps = 22,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.heartbeat_end_hour),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Slider(
                        value = form.endHour.toFloat(),
                        onValueChange = { onFormChange(form.copy(endHour = it.roundToInt())) },
                        valueRange = 0f..23f,
                        steps = 22,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val thresholdPercent = (form.proactiveThreshold * 100).roundToInt()
                Text(
                    text = stringResource(R.string.heartbeat_threshold_label, thresholdPercent),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = form.proactiveThreshold,
                    onValueChange = { onFormChange(form.copy(proactiveThreshold = it)) },
                    valueRange = 0.5f..1.0f,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.heartbeat_threshold_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.proactivity_predictivity_enabled),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = form.predictivityEnabled,
                        onCheckedChange = { onFormChange(form.copy(predictivityEnabled = it)) },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.proactivity_wellness_enabled),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = form.wellnessEnabled,
                        onCheckedChange = { onFormChange(form.copy(wellnessEnabled = it)) },
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.proactivity_wellness_anchor_label, form.wellnessAnchorMinutes),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = form.wellnessAnchorMinutes.toFloat(),
                    onValueChange = { onFormChange(form.copy(wellnessAnchorMinutes = it.roundToInt())) },
                    valueRange = 15f..180f,
                    steps = 10,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = stringResource(R.string.proactivity_wellness_idle_label, form.wellnessIdleMinutes),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = form.wellnessIdleMinutes.toFloat(),
                    onValueChange = { onFormChange(form.copy(wellnessIdleMinutes = it.roundToInt())) },
                    valueRange = 5f..60f,
                    steps = 10,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = stringResource(R.string.proactivity_wellness_presence_label, form.wellnessPresenceMinutes),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = form.wellnessPresenceMinutes.toFloat(),
                    onValueChange = { onFormChange(form.copy(wellnessPresenceMinutes = it.roundToInt())) },
                    valueRange = 5f..60f,
                    steps = 10,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(onClick = onManageDomains) {
                    Text(stringResource(R.string.heartbeat_manage_domains))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text(stringResource(R.string.settings_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}
