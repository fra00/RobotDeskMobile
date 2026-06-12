package com.example.mydeskrobot.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.mydeskrobot.domain.model.RobotEmotion
import com.example.mydeskrobot.ui.eyes.EyeExpressionSpec
import com.example.mydeskrobot.ui.eyes.EyeMotion
import com.example.mydeskrobot.ui.eyes.EyePairExpressionSpec
import com.example.mydeskrobot.ui.eyes.EyeExpressionMapper
import com.example.mydeskrobot.ui.theme.MyDeskRobotTheme
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun RobotEyes(
    emotion: RobotEmotion,
    modifier: Modifier = Modifier,
    emotionIntensity: Float = 0.5f,
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
    val spec = remember(emotion, emotionIntensity) {
        EyeExpressionMapper.map(emotion, emotionIntensity)
    }
    var isBlinking by remember { mutableStateOf(false) }

    BlinkLoop(
        enabled = spec.enableBlink,
        emotion = emotion,
        onBlink = { blinking -> isBlinking = blinking },
    )

    val pulseScale = rememberPulseScale(spec)
    val surprisedScale = rememberSurprisedPopScale(emotion, spec.surprisedPop)
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
                .align(Alignment.Center)
                .graphicsLayer {
                    scaleX = surprisedScale
                    scaleY = surprisedScale
                },
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedEye(
                expression = spec.left,
                isBlinking = isBlinking || squishLeft,
                pulseScale = pulseScale,
                size = eyeSize,
                modifier = eyeClickModifier(
                    onClick = onLeftEyeClick,
                    interactionSource = leftInteraction,
                    contentDescription = leftEyeContentDescription,
                ),
            )
            AnimatedEye(
                expression = spec.right,
                isBlinking = isBlinking || squishRight,
                pulseScale = pulseScale,
                size = eyeSize,
                modifier = eyeClickModifier(
                    onClick = onRightEyeClick,
                    interactionSource = rightInteraction,
                    contentDescription = rightEyeContentDescription,
                ),
            )
        }
    }
}

@Composable
private fun AnimatedEye(
    expression: EyeExpressionSpec,
    isBlinking: Boolean,
    pulseScale: Float,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val motion = rememberEyeMotion(expression)
    val pupilDrift = rememberPupilDrift(expression)
    val droopScale = rememberDroopScale(expression)

    RobotEye(
        expression = expression,
        isBlinking = isBlinking,
        pulseScale = pulseScale,
        pupilDriftOffset = pupilDrift,
        droopExtraScaleY = droopScale,
        modifier = modifier.graphicsLayer {
            translationX = motion.translationXDp * density.density
            translationY = motion.translationYDp * density.density
        },
        eyeSize = size,
    )
}

private data class EyeMotionOffset(val translationXDp: Float, val translationYDp: Float)

@Composable
private fun rememberEyeMotion(expression: EyeExpressionSpec): EyeMotionOffset {
    val amp = expression.motionAmplitude.coerceIn(0f, 1f)
    return when (expression.motion) {
        EyeMotion.SHAKE -> rememberShakeOffset(amp)
        EyeMotion.BOUNCE -> rememberBounceOffset(amp)
        else -> EyeMotionOffset(0f, 0f)
    }
}

@Composable
private fun rememberShakeOffset(amp: Float): EyeMotionOffset {
    val infinite = rememberInfiniteTransition(label = "eyeShake")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 80, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shakePhase",
    )
    val px = (2f + 4f * amp) * (if (phase < 0.5f) -1f else 1f)
    return EyeMotionOffset(translationXDp = px, translationYDp = 0f)
}

@Composable
private fun rememberBounceOffset(amp: Float): EyeMotionOffset {
    val infinite = rememberInfiniteTransition(label = "eyeBounce")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bouncePhase",
    )
    val py = -sin(phase * Math.PI).toFloat() * (3f + 3f * amp)
    return EyeMotionOffset(translationXDp = 0f, translationYDp = py)
}

@Composable
private fun rememberPupilDrift(expression: EyeExpressionSpec): Float {
    if (expression.motion != EyeMotion.PUPIL_DRIFT) return 0f
    val amp = expression.motionAmplitude.coerceIn(0f, 1f)
    val infinite = rememberInfiniteTransition(label = "pupilDrift")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "driftPhase",
    )
    return sin(phase * 2f * Math.PI.toFloat()) * amp
}

@Composable
private fun rememberDroopScale(expression: EyeExpressionSpec): Float {
    if (expression.motion != EyeMotion.SLOW_DROOP) return 1f
    val target = 1f - 0.12f * expression.motionAmplitude.coerceIn(0f, 1f)
    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "droopScale",
    )
    return scale
}

@Composable
private fun rememberSurprisedPopScale(emotion: RobotEmotion, enabled: Boolean): Float {
    var popTarget by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(emotion) {
        if (enabled && emotion == RobotEmotion.SURPRISED) {
            popTarget = 1.14f
            delay(180)
            popTarget = 1f
        } else {
            popTarget = 1f
        }
    }
    val scale by animateFloatAsState(
        targetValue = popTarget,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "surprisedPop",
    )
    return scale
}

private fun eyeClickModifier(
    onClick: (() -> Unit)?,
    interactionSource: MutableInteractionSource,
    contentDescription: String?,
): Modifier {
    if (onClick == null) return Modifier
    return Modifier
        .semantics {
            role = Role.Button
            contentDescription?.let { this.contentDescription = it }
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        )
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
private fun rememberPulseScale(spec: EyePairExpressionSpec): Float {
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
        RobotEyes(emotion = RobotEmotion.HAPPY, emotionIntensity = 0.8f)
    }
}

@Preview(showBackground = true, name = "Angry High")
@Composable
private fun RobotEyesAngryPreview() {
    MyDeskRobotTheme {
        RobotEyes(emotion = RobotEmotion.ANGRY, emotionIntensity = 0.9f)
    }
}

@Preview(showBackground = true, name = "Angry Low")
@Composable
private fun RobotEyesAngryLowPreview() {
    MyDeskRobotTheme {
        RobotEyes(emotion = RobotEmotion.ANGRY, emotionIntensity = 0.35f)
    }
}

@Preview(showBackground = true, name = "Confused")
@Composable
private fun RobotEyesConfusedPreview() {
    MyDeskRobotTheme {
        RobotEyes(emotion = RobotEmotion.CONFUSED, emotionIntensity = 0.7f)
    }
}

@Preview(showBackground = true, name = "Surprised")
@Composable
private fun RobotEyesSurprisedPreview() {
    MyDeskRobotTheme {
        RobotEyes(emotion = RobotEmotion.SURPRISED)
    }
}

@Preview(showBackground = true, name = "Sleeping")
@Composable
private fun RobotEyesSleepingPreview() {
    MyDeskRobotTheme {
        RobotEyes(emotion = RobotEmotion.SLEEPING)
    }
}
