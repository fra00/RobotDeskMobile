package com.example.mydeskrobot.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.mydeskrobot.R
import com.example.mydeskrobot.domain.check.FireAndCheckEntry
import com.example.mydeskrobot.domain.check.FireAndCheckPhase

@Composable
fun FireAndCheckDetailDialog(
    entry: FireAndCheckEntry,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.fire_and_check_dialog_title))
        },
        text = {
            Text(
                text = buildDetailBody(entry),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.conversation_history_close))
            }
        },
    )
}

@Composable
private fun buildDetailBody(entry: FireAndCheckEntry): String {
    val lines = mutableListOf<String>()
    entry.checkGoal?.trim()?.takeIf { it.isNotBlank() }?.let {
        lines += stringResource(R.string.fire_and_check_label_goal, it)
    }
    entry.verificationMessage?.trim()?.takeIf { it.isNotBlank() }?.let {
        lines += stringResource(R.string.fire_and_check_label_verification, it)
    }
    if (entry.triggerReason.isNotBlank() &&
        entry.triggerReason != entry.checkGoal &&
        entry.triggerReason != entry.verificationMessage
    ) {
        lines += stringResource(R.string.fire_and_check_label_trigger, entry.triggerReason)
    }
    if (lines.isEmpty()) {
        lines += entry.primaryMessage
    }
    val phaseLabel = when (entry.phase) {
        FireAndCheckPhase.SCHEDULED -> stringResource(R.string.fire_and_check_phase_scheduled)
        FireAndCheckPhase.AWAITING_VERIFICATION -> stringResource(R.string.fire_and_check_phase_awaiting)
        FireAndCheckPhase.CHECK_PENDING -> stringResource(R.string.fire_and_check_phase_pending)
    }
    lines += stringResource(R.string.fire_and_check_label_phase, phaseLabel)
    return lines.joinToString("\n\n")
}
