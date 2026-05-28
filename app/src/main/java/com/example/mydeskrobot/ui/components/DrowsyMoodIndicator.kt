package com.example.mydeskrobot.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.mydeskrobot.R

@Composable
fun DrowsyMoodIndicator(
    modifier: Modifier = Modifier,
    iconSize: Dp = 30.dp,
) {
    val transition = rememberInfiniteTransition(label = "drowsyPillow")
    val tilt by transition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pillowTilt",
    )

    val moodDescription = stringResource(R.string.cd_drowsy)

    Row(
        modifier = modifier
            .semantics { contentDescription = moodDescription }
            .rotate(tilt),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Bed,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier
                .size(iconSize)
                .offset(y = 2.dp),
        )
        Icon(
            imageVector = Icons.Outlined.Bed,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f),
            modifier = Modifier.size(iconSize * 0.85f),
        )
    }
}
