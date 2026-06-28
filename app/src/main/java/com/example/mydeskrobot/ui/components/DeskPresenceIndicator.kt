package com.example.mydeskrobot.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.mydeskrobot.R
import com.example.mydeskrobot.domain.presence.DeskOccupancyState

@Composable
fun DeskPresenceIndicator(
    occupancyState: DeskOccupancyState,
    monitorEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!monitorEnabled) return

    val (icon, tint, description) = presenceVisual(occupancyState)

    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = tint,
        modifier = modifier.size(28.dp),
    )
}

@Composable
private fun presenceVisual(
    state: DeskOccupancyState,
): PresenceVisual = when (state) {
    DeskOccupancyState.PRESENT -> PresenceVisual(
        icon = Icons.Filled.Person,
        tint = MaterialTheme.colorScheme.primary,
        description = stringResource(R.string.cd_desk_presence_present),
    )
    DeskOccupancyState.ABSENT -> PresenceVisual(
        icon = Icons.Outlined.PersonOff,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        description = stringResource(R.string.cd_desk_presence_absent),
    )
    DeskOccupancyState.UNCERTAIN -> PresenceVisual(
        icon = Icons.Outlined.PersonOutline,
        tint = MaterialTheme.colorScheme.tertiary,
        description = stringResource(R.string.cd_desk_presence_uncertain),
    )
    DeskOccupancyState.UNKNOWN -> PresenceVisual(
        icon = Icons.Outlined.PersonOutline,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        description = stringResource(R.string.cd_desk_presence_unknown),
    )
}

private data class PresenceVisual(
    val icon: ImageVector,
    val tint: androidx.compose.ui.graphics.Color,
    val description: String,
)
