package com.example.mydeskrobot.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
    squishLeft: Boolean = false,
    squishRight: Boolean = false,
    onLeftEyeClick: (() -> Unit)? = null,
    onRightEyeClick: (() -> Unit)? = null,
    leftEyeContentDescription: String? = null,
    rightEyeContentDescription: String? = null,
) {
    val spec = remember(emotion) { RobotEmotionEyes.specFor(emotion) }
    var isBlinking by remember { mutableStateOf(false) }

    BlinkLoop(
        enabled = spec.enableBlink,
        emotion = emotion,
        onBlink = { blinking -> isBlinking = blinking },
    )

    val pulseScale = rememberPulseScale(spec)
    val leftInteraction = remember { MutableInteractionSource() }
    val rightInteraction = remember { MutableInteractionSource() }

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
                isBlinking = isBlinking || squishLeft,
                pulseScale = pulseScale,
                size = eyeSize,
                modifier = Modifier
                    .then(
                        if (onLeftEyeClick != null) {
                            Modifier
                                .semantics {
                                    role = Role.Button
                                    leftEyeContentDescription?.let { contentDescription = it }
                                }
                                .clickable(
                                    interactionSource = leftInteraction,
                                    indication = null,
                                    onClick = onLeftEyeClick,
                                )
                        } else {
                            Modifier
                        },
                    ),
            )
            RobotEye(
                target = spec.right,
                isBlinking = isBlinking || squishRight,
                pulseScale = pulseScale,
                size = eyeSize,
                modifier = Modifier
                    .then(
                        if (onRightEyeClick != null) {
                            Modifier
                                .semantics {
                                    role = Role.Button
                                    rightEyeContentDescription?.let { contentDescription = it }
                                }
                                .clickable(
                                    interactionSource = rightInteraction,
                                    indication = null,
                                    onClick = onRightEyeClick,
                                )
                        } else {
                            Modifier
                        },
                    ),
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

@Preview(showBackground = true, name = "Wink")
@Composable
private fun RobotEyesWinkPreview() {
    MyDeskRobotTheme {
        RobotEyes(emotion = RobotEmotion.WINK)
    }
}

@Preview(showBackground = true, name = "Loving")
@Composable
private fun RobotEyesLovingPreview() {
    MyDeskRobotTheme {
        RobotEyes(emotion = RobotEmotion.LOVING)
    }
}
