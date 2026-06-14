package com.example.mydeskrobot.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.mydeskrobot.R
import com.example.mydeskrobot.reasoning.model.RobotProfile

@Composable
fun RobotContextProfileIndicator(
    profile: RobotProfile,
    modifier: Modifier = Modifier,
) {
    if (profile == RobotProfile.NORMAL) return

    val visual = profileVisual(profile) ?: return

    Icon(
        imageVector = visual.icon,
        contentDescription = visual.contentDescription,
        tint = visual.tint,
        modifier = modifier.size(24.dp),
    )
}

@Composable
private fun profileVisual(profile: RobotProfile): ProfileVisual? {
    val tint = MaterialTheme.colorScheme.secondary
    return when (profile) {
        RobotProfile.WORK -> ProfileVisual(
            icon = Icons.Outlined.Work,
            tint = tint,
            contentDescription = stringResource(R.string.cd_robot_context_work),
        )
        RobotProfile.CALL -> ProfileVisual(
            icon = Icons.Outlined.Call,
            tint = tint,
            contentDescription = stringResource(R.string.cd_robot_context_call),
        )
        RobotProfile.MEETING -> ProfileVisual(
            icon = Icons.Outlined.Groups,
            tint = tint,
            contentDescription = stringResource(R.string.cd_robot_context_meeting),
        )
        RobotProfile.FOCUS -> ProfileVisual(
            icon = Icons.Outlined.CenterFocusStrong,
            tint = tint,
            contentDescription = stringResource(R.string.cd_robot_context_focus),
        )
        RobotProfile.NORMAL -> null
    }
}

private data class ProfileVisual(
    val icon: ImageVector,
    val tint: androidx.compose.ui.graphics.Color,
    val contentDescription: String,
)
