package com.example.mydeskrobot.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    isVoskModelReady: Boolean,
    sttProviderName: String,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_title)) },
        text = {
            Column {
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
            }
        },
        confirmButton = {
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
