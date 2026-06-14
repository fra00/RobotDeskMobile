package com.example.mydeskrobot.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.mydeskrobot.R
import com.example.mydeskrobot.domain.check.FireAndCheckEntry
import com.example.mydeskrobot.domain.check.FireAndCheckPhase

@Composable
fun FireAndCheckIndicator(
    entry: FireAndCheckEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(
        when (entry.phase) {
            FireAndCheckPhase.SCHEDULED -> R.string.cd_fire_and_check_scheduled
            FireAndCheckPhase.AWAITING_VERIFICATION -> R.string.cd_fire_and_check_awaiting
            FireAndCheckPhase.CHECK_PENDING -> R.string.cd_fire_and_check_pending
        },
    )

    Icon(
        imageVector = when (entry.phase) {
            FireAndCheckPhase.SCHEDULED -> Icons.Outlined.Schedule
            FireAndCheckPhase.AWAITING_VERIFICATION,
            FireAndCheckPhase.CHECK_PENDING,
            -> Icons.Outlined.FactCheck
        },
        contentDescription = description,
        tint = MaterialTheme.colorScheme.tertiary,
        modifier = modifier
            .size(24.dp)
            .clickable(onClick = onClick),
    )
}
