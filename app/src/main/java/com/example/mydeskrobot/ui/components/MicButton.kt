package com.example.mydeskrobot.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.mydeskrobot.R
import com.example.mydeskrobot.presentation.conversation.ConversationPhase

@Composable
fun MicButton(
    phase: ConversationPhase,
    isHotwordListeningActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 64.dp,
) {
    val isCapturingQuestion = phase is ConversationPhase.ActiveListening ||
        phase is ConversationPhase.Thinking ||
        phase is ConversationPhase.CapturingImage ||
        phase is ConversationPhase.Speaking

    val contentDescription = when {
        isCapturingQuestion -> stringResource(R.string.cd_mic_listening)
        isHotwordListeningActive -> stringResource(R.string.cd_hotword_active)
        else -> stringResource(R.string.cd_hotword_inactive)
    }

    FilledIconButton(
        onClick = onClick,
        modifier = modifier.size(size),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = when {
                isCapturingQuestion -> MaterialTheme.colorScheme.tertiary
                isHotwordListeningActive -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Icon(
            imageVector = if (isHotwordListeningActive) Icons.Filled.Mic else Icons.Filled.MicOff,
            contentDescription = contentDescription,
        )
    }
}
