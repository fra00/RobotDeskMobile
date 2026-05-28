package com.example.mydeskrobot.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.mydeskrobot.presentation.conversation.ConversationPhase

@Composable
fun PhraseInfoCorner(
    wakePhraseHint: String,
    exitPhraseHint: String,
    statusMessage: String,
    phase: ConversationPhase,
    modifier: Modifier = Modifier,
) {
    val isLandscape =
        LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val maxWidth = if (isLandscape) 280.dp else 220.dp

    Column(
        modifier = modifier.widthIn(max = maxWidth),
    ) {
        Text(
            text = wakePhraseHint,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Start,
        )
        Text(
            text = exitPhraseHint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
        )
        if (phase !is ConversationPhase.Idle && statusMessage.isNotBlank()) {
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
                modifier = Modifier.widthIn(max = maxWidth),
            )
        }
    }
}
