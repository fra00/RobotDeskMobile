package com.example.mydeskrobot.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.mydeskrobot.domain.model.RobotEmotion
import com.example.mydeskrobot.ui.eyes.EyePairSpec
import com.example.mydeskrobot.ui.eyes.RobotEmotionEyes
import com.example.mydeskrobot.ui.theme.MyDeskRobotTheme
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun RobotEyes(
    emotion: RobotEmotion,
    modifier: Modifier = Modifier,
    minEyeSize: Dp = 56.dp,
    maxEyeSize: Dp = 160.dp,
    eyeGap: Dp = 36.dp,
) {
    val spec = remember(emotion) { RobotEmotionEyes.specFor(emotion) }
    var isBlinking by remember { mutableStateOf(false) }

    BlinkLoop(
        enabled = spec.enableBlink,
        emotion = emotion,
        onBlink = { blinking -> isBlinking = blinking },
    )

    val pulseScale = rememberPulseScale(spec)

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val resolvedGap = eyeGap.coerceAtMost(maxWidth * 0.2f)
        val byWidth = (maxWidth - resolvedGap) / 2
        val byHeight = maxHeight * 0.42f
        val eyeSize = minOf(byWidth, byHeight).coerceIn(minEyeSize, maxEyeSize)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RobotEye(
                target = spec.left,
                isBlinking = isBlinking,
                pulseScale = pulseScale,
                size = eyeSize,
            )
            RobotEye(
                target = spec.right,
                isBlinking = isBlinking,
                pulseScale = pulseScale,
                size = eyeSize,
            )
        }
    }
}

@Composable
private fun BlinkLoop(
    enabled: Boolean,
    emotion: RobotEmotion,
    onBlink: (Boolean) -> Unit,
) {
    LaunchedEffect(enabled, emotion) {
        onBlink(false)
        if (!enabled) return@LaunchedEffect

        while (true) {
            delay(Random.nextLong(2_800L, 4_500L))
            onBlink(true)
            delay(110L)
            onBlink(false)
        }
    }
}

@Composable
private fun rememberPulseScale(spec: EyePairSpec): Float {
    if (!spec.enableListeningPulse && !spec.enableSpeakingPulse) return 1f

    val infinite = rememberInfiniteTransition(label = "eyePulse")
    val animated by infinite.animateFloat(
        initialValue = 1f,
        targetValue = if (spec.enableListeningPulse) 1.06f else 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (spec.enableListeningPulse) 900 else 500,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "eyePulseScale",
    )
    return animated
}

@Preview(showBackground = true, name = "Neutral")
@Composable
private fun RobotEyesNeutralPreview() {
    MyDeskRobotTheme {
        RobotEyes(emotion = RobotEmotion.NEUTRAL)
    }
}

@Preview(showBackground = true, name = "Happy")
@Composable
private fun RobotEyesHappyPreview() {
    MyDeskRobotTheme {
        RobotEyes(emotion = RobotEmotion.HAPPY)
    }
}

@Preview(showBackground = true, name = "Listening")
@Composable
private fun RobotEyesListeningPreview() {
    MyDeskRobotTheme {
        RobotEyes(emotion = RobotEmotion.LISTENING)
    }
}

@Preview(showBackground = true, name = "Thinking")
@Composable
private fun RobotEyesThinkingPreview() {
    MyDeskRobotTheme {
        RobotEyes(emotion = RobotEmotion.THINKING)
    }
}

@Preview(showBackground = true, name = "Speaking")
@Composable
private fun RobotEyesSpeakingPreview() {
    MyDeskRobotTheme {
        RobotEyes(emotion = RobotEmotion.SPEAKING)
    }
}

@Preview(showBackground = true, name = "Surprised")
@Composable
private fun RobotEyesSurprisedPreview() {
    MyDeskRobotTheme {
        RobotEyes(emotion = RobotEmotion.SURPRISED)
    }
}

@Preview(showBackground = true, name = "Confused")
@Composable
private fun RobotEyesConfusedPreview() {
    MyDeskRobotTheme {
        RobotEyes(emotion = RobotEmotion.CONFUSED)
    }
}

@Preview(showBackground = true, name = "Angry")
@Composable
private fun RobotEyesAngryPreview() {
    MyDeskRobotTheme {
        RobotEyes(emotion = RobotEmotion.ANGRY)
    }
}

@Preview(showBackground = true, name = "Sad")
@Composable
private fun RobotEyesSadPreview() {
    MyDeskRobotTheme {
        RobotEyes(emotion = RobotEmotion.SAD)
    }
}

@Preview(showBackground = true, name = "Bored")
@Composable
private fun RobotEyesBoredPreview() {
    MyDeskRobotTheme {
        RobotEyes(emotion = RobotEmotion.BORED)
    }
}

@Preview(showBackground = true, name = "Sleeping")
@Composable
private fun RobotEyesSleepingPreview() {
    MyDeskRobotTheme {
        RobotEyes(emotion = RobotEmotion.SLEEPING)
    }
}

@Preview(showBackground = true, name = "Drowsy")
@Composable
private fun RobotEyesDrowsyPreview() {
    MyDeskRobotTheme {
        RobotEyes(emotion = RobotEmotion.DROWSY)
    }
}
