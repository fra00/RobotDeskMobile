package com.example.mydeskrobot.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.mydeskrobot.R
import com.example.mydeskrobot.presentation.settings.MemorySettingsFormState

@Composable
fun MemorySettingsDialog(
    form: MemorySettingsFormState,
    previewMemories: List<String>,
    onFormChange: (MemorySettingsFormState) -> Unit,
    onSave: () -> Unit,
    onResetMemory: () -> Unit,
    onReorganizeNow: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.memory_settings_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(stringResource(R.string.memory_enabled_label), style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = form.enabled,
                    onCheckedChange = { onFormChange(form.copy(enabled = it)) },
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = form.intervalSeconds.toString(),
                    onValueChange = { raw ->
                        val parsed = raw.toLongOrNull()
                        if (parsed != null) {
                            onFormChange(form.copy(intervalSeconds = parsed))
                        }
                    },
                    label = { Text(stringResource(R.string.memory_interval_seconds_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.memory_preview_title), style = MaterialTheme.typography.labelMedium)
                if (previewMemories.isEmpty()) {
                    Text(stringResource(R.string.memory_preview_empty), style = MaterialTheme.typography.bodySmall)
                } else {
                    previewMemories.take(8).forEach { value ->
                        Text(
                            text = "• $value",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onResetMemory, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.memory_reset_button))
                }
                TextButton(onClick = onReorganizeNow, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.memory_reorganize_button))
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
