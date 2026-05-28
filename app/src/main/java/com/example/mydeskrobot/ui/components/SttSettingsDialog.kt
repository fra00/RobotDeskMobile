package com.example.mydeskrobot.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.mydeskrobot.R
import com.example.mydeskrobot.domain.speech.SttProvider

@Composable
fun SttSettingsDialog(
    currentProvider: SttProvider,
    isVoskModelReady: Boolean,
    onProviderChange: (SttProvider) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stt_settings_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.stt_provider_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(modifier = Modifier.height(12.dp))

                SttProviderOption(
                    title = stringResource(R.string.stt_provider_android),
                    description = stringResource(R.string.stt_provider_android_desc),
                    selected = currentProvider == SttProvider.ANDROID,
                    onClick = { onProviderChange(SttProvider.ANDROID) },
                )

                Spacer(modifier = Modifier.height(8.dp))

                SttProviderOption(
                    title = stringResource(R.string.stt_provider_vosk),
                    description = if (isVoskModelReady) {
                        stringResource(R.string.stt_provider_vosk_desc)
                    } else {
                        stringResource(R.string.stt_vosk_not_downloaded)
                    },
                    selected = currentProvider == SttProvider.VOSK,
                    enabled = isVoskModelReady,
                    onClick = { 
                        if (isVoskModelReady) {
                            onProviderChange(SttProvider.VOSK)
                        }
                    },
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

@Composable
private fun SttProviderOption(
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(
            selected = selected,
            enabled = enabled,
            onClick = onClick,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                },
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
            )
        }
    }
}
