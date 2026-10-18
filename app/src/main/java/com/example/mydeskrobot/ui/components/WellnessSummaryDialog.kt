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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mydeskrobot.R
import com.example.mydeskrobot.domain.wellness.WellnessDomainStatus
import com.example.mydeskrobot.presentation.settings.WellnessDomainSummaryUi
import com.example.mydeskrobot.presentation.settings.WellnessSummaryUiState

@Composable
fun WellnessSummaryDialog(
    summary: WellnessSummaryUiState?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wellness_summary_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (summary == null || summary.domains.isEmpty()) {
                    Text(
                        text = stringResource(R.string.wellness_summary_empty),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.wellness_summary_day, summary.dayKey),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    summary.domains.forEach { entry ->
                        WellnessDomainRow(entry)
                    }
                }
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
private fun WellnessDomainRow(entry: WellnessDomainSummaryUi) {
    val statusLabel = when (entry.status) {
        WellnessDomainStatus.OK -> stringResource(R.string.wellness_summary_status_ok)
        WellnessDomainStatus.ATTENTION -> stringResource(R.string.wellness_summary_status_attention)
        WellnessDomainStatus.CRITICAL -> stringResource(R.string.wellness_summary_status_critical)
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "${entry.displayName} — $statusLabel",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = entry.summaryIt,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
