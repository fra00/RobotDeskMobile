package com.example.mydeskrobot.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.mydeskrobot.R

@Composable
fun ThinkingGearIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    val transition = rememberInfiniteTransition(label = "thinkingGear")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_000, easing = LinearEasing),
        ),
        label = "thinkingGearRotation",
    )

    Icon(
        imageVector = Icons.Outlined.Settings,
        contentDescription = stringResource(R.string.cd_thinking),
        tint = MaterialTheme.colorScheme.secondary,
        modifier = modifier
            .size(size)
            .rotate(rotation),
    )
}
