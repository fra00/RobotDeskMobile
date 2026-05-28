package com.example.mydeskrobot.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.outlined.Bed
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.PowerSettingsNew
import com.example.mydeskrobot.domain.model.RobotEmotion
import com.example.mydeskrobot.ui.theme.RobotHappyAccent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.mydeskrobot.R
import com.example.mydeskrobot.presentation.conversation.ConversationPhase

@Composable
fun StandbyStatusIndicator(
    phase: ConversationPhase,
    isHotwordListeningActive: Boolean,
    emotion: RobotEmotion,
    modifier: Modifier = Modifier,
) {
    if (
        isHotwordListeningActive &&
        (phase is ConversationPhase.Thinking || phase is ConversationPhase.CapturingImage)
    ) {
        ThinkingGearIndicator(modifier = modifier)
        return
    }

    if (emotion == RobotEmotion.HAPPY && phase !is ConversationPhase.Thinking) {
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = stringResource(R.string.cd_happy_mood),
            tint = RobotHappyAccent,
            modifier = modifier.size(32.dp),
        )
        return
    }

    if (emotion == RobotEmotion.DROWSY && phase !is ConversationPhase.Thinking) {
        Icon(
            imageVector = Icons.Outlined.Bed,
            contentDescription = stringResource(R.string.cd_drowsy),
            tint = MaterialTheme.colorScheme.secondary,
            modifier = modifier.size(32.dp),
        )
        return
    }

    if (
        emotion == RobotEmotion.SLEEPING &&
        phase is ConversationPhase.WaitingForHotword &&
        isHotwordListeningActive
    ) {
        Icon(
            imageVector = Icons.Outlined.Bed,
            contentDescription = stringResource(R.string.cd_standby_sleeping),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.size(32.dp),
        )
        return
    }

    val (icon, tint, description) = statusVisual(
        phase = phase,
        isHotwordListeningActive = isHotwordListeningActive,
    )

    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = tint,
        modifier = modifier.size(32.dp),
    )
}

@Composable
private fun statusVisual(
    phase: ConversationPhase,
    isHotwordListeningActive: Boolean,
): StatusVisual {
    if (!isHotwordListeningActive) {
        return StatusVisual(
            icon = Icons.Outlined.PowerSettingsNew,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            description = stringResource(R.string.cd_service_off),
        )
    }

    return when (phase) {
        is ConversationPhase.WaitingForHotword -> StatusVisual(
            icon = Icons.Outlined.Hearing,
            tint = MaterialTheme.colorScheme.primary,
            description = stringResource(R.string.cd_standby_on),
        )

        is ConversationPhase.Idle,
        is ConversationPhase.Error,
        -> StatusVisual(
            icon = Icons.Outlined.PowerSettingsNew,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            description = stringResource(R.string.cd_service_off),
        )

        else -> StatusVisual(
            icon = Icons.Filled.Sensors,
            tint = MaterialTheme.colorScheme.tertiary,
            description = stringResource(R.string.cd_session_active),
        )
    }
}

private data class StatusVisual(
    val icon: ImageVector,
    val tint: androidx.compose.ui.graphics.Color,
    val description: String,
)
