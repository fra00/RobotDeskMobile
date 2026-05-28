package com.example.mydeskrobot.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.mydeskrobot.R
import com.example.mydeskrobot.ui.theme.RobotHappyAccent

@Composable
fun HappyMoodIndicator(
    modifier: Modifier = Modifier,
    heartSize: Dp = 28.dp,
) {
    val transition = rememberInfiniteTransition(label = "happyHearts")

    val leftScale by transition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "happyHeartLeft",
    )

    val rightScale by transition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(260),
        ),
        label = "happyHeartRight",
    )

    val moodDescription = stringResource(R.string.cd_happy_mood)

    Row(
        modifier = modifier.semantics {
            contentDescription = moodDescription
        },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = null,
            tint = RobotHappyAccent,
            modifier = Modifier
                .size(heartSize)
                .scale(leftScale),
        )
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = null,
            tint = RobotHappyAccent.copy(alpha = 0.85f),
            modifier = Modifier
                .size(heartSize * 0.82f)
                .scale(rightScale),
        )
    }
}
