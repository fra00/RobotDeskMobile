package com.example.mydeskrobot.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.example.mydeskrobot.presentation.settings.DeskPresenceSettingsFormState
import kotlin.math.roundToInt

@Composable
fun DeskPresenceSettingsDialog(
    form: DeskPresenceSettingsFormState,
    onFormChange: (DeskPresenceSettingsFormState) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.desk_presence_settings_title)) },
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
                        text = stringResource(R.string.desk_presence_enabled_label),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = form.enabled,
                        onCheckedChange = { onFormChange(form.copy(enabled = it)) },
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.desk_presence_fps_label, form.analysisFps),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = form.analysisFps.toFloat(),
                    onValueChange = { onFormChange(form.copy(analysisFps = it.roundToInt())) },
                    valueRange = 2f..10f,
                    steps = 7,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                val thresholdPercent = (form.faceConfidenceThreshold * 100).roundToInt()
                Text(
                    text = stringResource(R.string.desk_presence_threshold_label, thresholdPercent),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = form.faceConfidenceThreshold,
                    onValueChange = { onFormChange(form.copy(faceConfidenceThreshold = it)) },
                    valueRange = 0.4f..0.95f,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = stringResource(R.string.desk_presence_privacy_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
