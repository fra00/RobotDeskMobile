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
import com.example.mydeskrobot.presentation.settings.MemoryItemUi
import com.example.mydeskrobot.presentation.settings.MemorySettingsFormState

@Composable
fun MemorySettingsDialog(
    form: MemorySettingsFormState,
    memoryItems: List<MemoryItemUi>,
    onFormChange: (MemorySettingsFormState) -> Unit,
    onMemoryItemValueChange: (Long, String) -> Unit,
    onSaveMemoryItem: (Long, String) -> Unit,
    onDeleteMemoryItem: (Long) -> Unit,
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
                Text(stringResource(R.string.memory_edit_title), style = MaterialTheme.typography.labelMedium)
                if (memoryItems.isEmpty()) {
                    Text(stringResource(R.string.memory_preview_empty), style = MaterialTheme.typography.bodySmall)
                } else {
                    memoryItems.forEach { item ->
                        MemoryItemEditorRow(
                            item = item,
                            onValueChange = { onMemoryItemValueChange(item.id, it) },
                            onSave = { onSaveMemoryItem(item.id, item.value) },
                            onDelete = { onDeleteMemoryItem(item.id) },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
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

@Composable
private fun MemoryItemEditorRow(
    item: MemoryItemUi,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "${item.category} · #${item.id}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = item.value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDelete) {
                Text(stringResource(R.string.memory_item_delete))
            }
            TextButton(onClick = onSave) {
                Text(stringResource(R.string.memory_item_save))
            }
        }
    }
}
