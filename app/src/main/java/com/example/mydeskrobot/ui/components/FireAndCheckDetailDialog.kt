package com.example.mydeskrobot.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FireAndCheckDetailDialog(
    entry: FireAndCheckEntry,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.fire_and_check_dialog_title))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = buildDetailBody(entry),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.fire_and_check_delete_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.conversation_history_close))
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete) {
                Text(text = stringResource(R.string.fire_and_check_delete))
            }
        },
    )
}

@Composable
private fun buildDetailBody(entry: FireAndCheckEntry): String {
    val lines = mutableListOf<String>()
    if (entry.isCommitment) {
        lines += stringResource(R.string.fire_and_check_kind_commitment)
    }
    entry.checkGoal?.trim()?.takeIf { it.isNotBlank() }?.let {
        val labelRes = if (entry.isCommitment) {
            R.string.fire_and_check_label_commitment_goal
        } else {
            R.string.fire_and_check_label_goal
        }
        lines += stringResource(labelRes, it)
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
    if (lines.isEmpty() || (lines.size == 1 && entry.isCommitment)) {
        if (entry.primaryMessage.isNotBlank()) {
            lines += entry.primaryMessage
        }
    }

    lines += stringResource(
        R.string.fire_and_check_label_primary_due,
        formatDue(entry.primaryDueAtMillis),
    )
    lines += stringResource(
        R.string.fire_and_check_label_verification_due,
        formatDue(entry.verificationDueAtMillis),
    )

    val phaseLabel = when (entry.phase) {
        FireAndCheckPhase.SCHEDULED -> stringResource(R.string.fire_and_check_phase_scheduled)
        FireAndCheckPhase.AWAITING_VERIFICATION ->
            if (entry.isCommitment) {
                stringResource(R.string.fire_and_check_phase_awaiting_commitment)
            } else {
                stringResource(R.string.fire_and_check_phase_awaiting)
            }
        FireAndCheckPhase.CHECK_PENDING ->
            if (entry.isCommitment) {
                stringResource(R.string.fire_and_check_phase_pending_commitment)
            } else {
                stringResource(R.string.fire_and_check_phase_pending)
            }
    }
    lines += stringResource(R.string.fire_and_check_label_phase, phaseLabel)
    return lines.joinToString("\n\n")
}

@Composable
private fun formatDue(millis: Long?): String {
    if (millis == null || millis <= 0L) {
        return stringResource(R.string.fire_and_check_label_due_missing)
    }
    val formatter = SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale.ITALY)
    return formatter.format(Date(millis))
}
