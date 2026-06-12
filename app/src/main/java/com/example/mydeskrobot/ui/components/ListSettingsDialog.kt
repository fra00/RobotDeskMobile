package com.example.mydeskrobot.ui.components

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.mydeskrobot.R
import com.example.mydeskrobot.presentation.settings.ListItemUi

@Composable
fun ListSettingsDialog(
    listItems: List<ListItemUi>,
    onListItemValueChange: (Long, String) -> Unit,
    onListItemCheckedChange: (Long, Boolean) -> Unit,
    onSaveListItem: (Long, String, Boolean) -> Unit,
    onDeleteListItem: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.list_settings_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.list_settings_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (listItems.isEmpty()) {
                    Text(
                        text = stringResource(R.string.list_settings_empty),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    listItems.forEach { item ->
                        ListItemEditorRow(
                            item = item,
                            onValueChange = { onListItemValueChange(item.id, it) },
                            onCheckedChange = { onListItemCheckedChange(item.id, it) },
                            onSave = { onSaveListItem(item.id, item.text, item.checked) },
                            onDelete = { onDeleteListItem(item.id) },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        },
    )
}

@Composable
private fun ListItemEditorRow(
    item: ListItemUi,
    onValueChange: (String) -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = listTypeLabel(item.type),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (item.supportsChecked) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = item.checked,
                    onCheckedChange = onCheckedChange,
                )
                Text(
                    text = if (item.checked) {
                        stringResource(R.string.list_item_checked)
                    } else {
                        stringResource(R.string.list_item_unchecked)
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        OutlinedTextField(
            value = item.text,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 1,
            maxLines = 3,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDelete) {
                Text(stringResource(R.string.list_item_delete))
            }
            TextButton(onClick = onSave) {
                Text(stringResource(R.string.list_item_save))
            }
        }
    }
}

@Composable
private fun listTypeLabel(type: String): String = when (type) {
    "NOTE" -> stringResource(R.string.list_type_note)
    "TODO" -> stringResource(R.string.list_type_todo)
    "SHOPPING" -> stringResource(R.string.list_type_shopping)
    else -> type
}
