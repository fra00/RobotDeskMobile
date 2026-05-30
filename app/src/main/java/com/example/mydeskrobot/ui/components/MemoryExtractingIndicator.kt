package com.example.mydeskrobot.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.mydeskrobot.R

/** Shown while the app scans conversation history for durable user memories (standby). */
@Composable
fun MemoryExtractingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
) {
    val transition = rememberInfiniteTransition(label = "memoryExtracting")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "memoryExtractingAlpha",
    )

    Icon(
        imageVector = Icons.Outlined.Psychology,
        contentDescription = stringResource(R.string.cd_memory_extracting),
        tint = MaterialTheme.colorScheme.tertiary,
        modifier = modifier
            .size(size)
            .alpha(alpha),
    )
}
