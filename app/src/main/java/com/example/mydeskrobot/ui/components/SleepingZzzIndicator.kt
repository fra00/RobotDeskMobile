package com.example.mydeskrobot.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mydeskrobot.R

@Composable
fun SleepingZzzIndicator(
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "sleepZzz")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = -18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "zzzDrift",
    )
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "zzzAlpha",
    )

    val description = stringResource(R.string.cd_sleeping)

    Box(
        modifier = modifier
            .semantics { contentDescription = description }
            .offset(y = drift.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "z",
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-22).dp, y = 6.dp)
                .alpha(alpha * 0.7f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 22.sp,
        )
        Text(
            text = "Z",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .alpha(alpha),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 28.sp,
        )
        Text(
            text = "zz",
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 24.dp, y = (-4).dp)
                .alpha(alpha * 0.85f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 20.sp,
        )
    }
}
