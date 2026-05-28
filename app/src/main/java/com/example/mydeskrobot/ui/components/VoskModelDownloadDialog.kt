package com.example.mydeskrobot.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.mydeskrobot.R
import com.example.mydeskrobot.data.speech.VoskModelManager

@Composable
fun VoskModelDownloadDialog(
    modelState: VoskModelManager.ModelState,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
    onSkip: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (modelState !is VoskModelManager.ModelState.Downloading &&
                modelState !is VoskModelManager.ModelState.Extracting
            ) {
                onDismiss()
            }
        },
        title = {
            Text(stringResource(R.string.vosk_model_dialog_title))
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (modelState) {
                    is VoskModelManager.ModelState.NotDownloaded -> {
                        Text(stringResource(R.string.vosk_model_not_downloaded))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.vosk_model_size_info),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    is VoskModelManager.ModelState.Downloading -> {
                        Text(stringResource(R.string.vosk_model_downloading))
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { modelState.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${(modelState.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    is VoskModelManager.ModelState.Extracting -> {
                        Text(stringResource(R.string.vosk_model_extracting))
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(8.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.vosk_model_please_wait))
                        }
                    }

                    is VoskModelManager.ModelState.Ready -> {
                        Text(stringResource(R.string.vosk_model_ready))
                    }

                    is VoskModelManager.ModelState.Error -> {
                        Text(
                            text = stringResource(R.string.vosk_model_error, modelState.message),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (modelState) {
                is VoskModelManager.ModelState.NotDownloaded -> {
                    Button(onClick = onDownload) {
                        Text(stringResource(R.string.vosk_model_download_button))
                    }
                }

                is VoskModelManager.ModelState.Error -> {
                    Button(onClick = onDownload) {
                        Text(stringResource(R.string.vosk_model_retry_button))
                    }
                }

                is VoskModelManager.ModelState.Ready -> {
                    Button(onClick = onDismiss) {
                        Text(stringResource(R.string.ok))
                    }
                }

                else -> {
                    // No confirm button during download/extraction
                }
            }
        },
        dismissButton = {
            when (modelState) {
                is VoskModelManager.ModelState.NotDownloaded,
                is VoskModelManager.ModelState.Error,
                -> {
                    OutlinedButton(onClick = onSkip) {
                        Text(stringResource(R.string.vosk_model_skip_button))
                    }
                }

                else -> {
                    // No dismiss button during download/extraction or when ready
                }
            }
        },
    )
}
