package com.example.mydeskrobot.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.mydeskrobot.R
import com.example.mydeskrobot.presentation.settings.DayActivityGroupUi
import com.example.mydeskrobot.presentation.settings.LogDaySettingsFormState

@Composable
fun LogDaySettingsDialog(
    form: LogDaySettingsFormState,
    dayGroups: List<DayActivityGroupUi>,
    onFormChange: (LogDaySettingsFormState) -> Unit,
    onRefreshSummary: () -> Unit,
    onClearLog: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.log_day_settings_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    stringResource(R.string.log_day_enabled_label),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = form.enabled,
                    onCheckedChange = { onFormChange(form.copy(enabled = it)) },
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = form.intervalMinutes.toString(),
                    onValueChange = { raw ->
                        val parsed = raw.toLongOrNull()
                        if (parsed != null) {
                            onFormChange(form.copy(intervalMinutes = parsed))
                        }
                    },
                    label = { Text(stringResource(R.string.log_day_interval_minutes_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.log_day_summary_title),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    TextButton(
                        onClick = onRefreshSummary,
                        enabled = !form.isRefreshingSummary,
                    ) {
                        if (form.isRefreshingSummary) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(stringResource(R.string.log_day_refresh_summary))
                        }
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Text(
                        text = form.habitSummary?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.log_day_summary_empty),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    stringResource(R.string.log_day_events_title),
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (form.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                } else if (dayGroups.isEmpty()) {
                    Text(
                        stringResource(R.string.log_day_empty),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    dayGroups.forEach { group ->
                        Text(
                            text = group.dayLabel,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                        group.events.forEach { event ->
                            val meta = buildList {
                                event.episodeKindLabel?.let { add(it) }
                                event.confidenceLabel?.let { add(it) }
                                event.scheduledLabel?.let { add(it) }
                                add(event.sourceLabel)
                            }.joinToString(" · ")
                            val detail = event.rawPhrase?.let { " — $it" }.orEmpty()
                            Text(
                                text = "${event.timeLabel} · ${event.label}$detail ($meta)",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onClearLog, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.log_day_clear))
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
