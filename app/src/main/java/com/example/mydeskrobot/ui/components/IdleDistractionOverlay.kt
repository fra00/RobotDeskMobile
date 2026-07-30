package com.example.mydeskrobot.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.mydeskrobot.R
import com.example.mydeskrobot.domain.model.RobotEmotion
import com.example.mydeskrobot.domain.mood.IdleDistractionKind
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val SceneInk = Color(0xFFE8E8E8)
private val SceneInkDark = Color(0xFF1A1A1A)

/**
 * Symbolic idle-distraction scenes (mimica only). Full-screen replacements for standby eyes.
 */
@Composable
fun IdleDistractionOverlay(
    kind: IdleDistractionKind,
    emotion: RobotEmotion,
    emotionIntensity: Float,
    modifier: Modifier = Modifier,
) {
    when (kind) {
        IdleDistractionKind.HEADPHONES -> HeadphonesDistractionOverlay(
            emotion = emotion,
            emotionIntensity = emotionIntensity,
            modifier = modifier,
        )
        IdleDistractionKind.READING -> ReadingDistractionOverlay(
            emotion = emotion,
            emotionIntensity = emotionIntensity,
            modifier = modifier,
        )
        IdleDistractionKind.AWAY -> AwayDoorDistractionOverlay(modifier)
        IdleDistractionKind.PONG -> RetroPongDistractionOverlay(modifier)
    }
}

/** Scaled [RobotEyes] reused inside headphones / reading scenes (no poke). */
@Composable
private fun SceneRobotEyes(
    emotion: RobotEmotion,
    emotionIntensity: Float,
    modifier: Modifier = Modifier,
    minEyeSize: Dp = 36.dp,
    maxEyeSize: Dp = 88.dp,
    eyeGap: Dp = 20.dp,
) {
    RobotEyes(
        emotion = emotion,
        emotionIntensity = emotionIntensity,
        modifier = modifier,
        minEyeSize = minEyeSize,
        maxEyeSize = maxEyeSize,
        eyeGap = eyeGap,
    )
}

@Composable
private fun HeadphonesDistractionOverlay(
    emotion: RobotEmotion,
    emotionIntensity: Float,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.cd_idle_distraction_headphones)
    val transition = rememberInfiniteTransition(label = "headphonesMusic")
    val notePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "notePhase",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(2f)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        // Band + cups first (behind eyes).
        Canvas(modifier = Modifier.fillMaxSize()) {
            val ink = SceneInk
            val strokeW = size.minDimension * 0.055f
            val cx = size.width / 2f
            val cy = size.height / 2f + size.minDimension * 0.02f

            // Cups large but far apart — clear gap for eyes in the middle.
            val cupW = size.minDimension * 0.16f
            val cupH = size.minDimension * 0.34f
            val innerGap = size.minDimension * 0.28f
            val cupTop = cy - cupH * 0.48f
            val leftCupLeft = cx - innerGap - cupW
            val rightCupLeft = cx + innerGap

            val cupCorner = cupW * 0.4f
            drawRoundRect(
                color = ink,
                topLeft = Offset(leftCupLeft, cupTop),
                size = Size(cupW, cupH),
                cornerRadius = CornerRadius(cupCorner, cupCorner),
            )
            drawRoundRect(
                color = ink,
                topLeft = Offset(rightCupLeft, cupTop),
                size = Size(cupW, cupH),
                cornerRadius = CornerRadius(cupCorner, cupCorner),
            )

            val bandLeft = leftCupLeft + cupW * 0.5f
            val bandRight = rightCupLeft + cupW * 0.5f
            val bandTop = cupTop - size.minDimension * 0.34f
            val bandPath = Path().apply {
                moveTo(bandLeft, cupTop + cupH * 0.06f)
                quadraticBezierTo(cx, bandTop, bandRight, cupTop + cupH * 0.06f)
            }
            drawPath(
                path = bandPath,
                color = ink,
                style = Stroke(
                    width = strokeW,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }

        // Eyes in the open space between cups (drawn on top so never covered).
        SceneRobotEyes(
            emotion = emotion,
            emotionIntensity = emotionIntensity,
            modifier = Modifier
                .fillMaxWidth(0.36f)
                .fillMaxHeight(0.24f)
                .offset(y = 16.dp),
            minEyeSize = 26.dp,
            maxEyeSize = 56.dp,
            eyeGap = 14.dp,
        )

        FloatingMusicNote(
            symbol = "♪",
            progress = (notePhase + 0f) % 1f,
            xBias = -0.48f,
            baseYDp = -48f,
            modifier = Modifier.align(Alignment.Center),
        )
        FloatingMusicNote(
            symbol = "♫",
            progress = (notePhase + 0.5f) % 1f,
            xBias = 0.48f,
            baseYDp = -56f,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun FloatingMusicNote(
    symbol: String,
    progress: Float,
    xBias: Float,
    baseYDp: Float,
    modifier: Modifier = Modifier,
) {
    val rise = progress
    val alpha = when {
        rise < 0.12f -> rise / 0.12f
        rise > 0.72f -> ((1f - rise) / 0.28f).coerceIn(0f, 1f)
        else -> 1f
    }
    Text(
        text = symbol,
        color = SceneInk.copy(alpha = alpha * 0.95f),
        fontSize = (26 + rise * 8).sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .offset(
                x = (xBias * 130f).dp,
                y = (baseYDp - 55f - rise * 70f).dp,
            )
            .graphicsLayer {
                rotationZ = -10f + xBias * 28f + rise * 14f
            },
    )
}

@Composable
private fun ReadingDistractionOverlay(
    emotion: RobotEmotion,
    emotionIntensity: Float,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.cd_idle_distraction_reading)
    val transition = rememberInfiniteTransition(label = "readingScene")
    val flip by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pageFlip",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(2f)
            .semantics { contentDescription = description },
    ) {
        SceneRobotEyes(
            emotion = emotion,
            emotionIntensity = emotionIntensity,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(0.48f)
                .fillMaxHeight(0.28f)
                .padding(top = 20.dp),
            minEyeSize = 32.dp,
            maxEyeSize = 72.dp,
            eyeGap = 18.dp,
        )

        Canvas(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.88f)
                .fillMaxHeight(0.58f)
                .padding(bottom = 16.dp),
        ) {
            val pageFill = Color(0xFFF4F4F4)
            val pageShade = Color(0xFFD8D8D8)
            val ink = SceneInkDark
            val stroke = Stroke(
                width = size.minDimension * 0.02f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )
            val cx = size.width / 2f
            val top = size.height * 0.08f
            val bottom = size.height * 0.94f
            val pageH = bottom - top
            val innerW = size.width * 0.38f
            val midDip = pageH * 0.04f

            fun pagePath(
                left: Float,
                right: Float,
                topY: Float,
                bottomY: Float,
            ): Path = Path().apply {
                moveTo(left, topY + pageH * 0.02f)
                quadraticBezierTo((left + right) / 2f, topY - pageH * 0.02f, right, topY + pageH * 0.02f)
                lineTo(right, bottomY - midDip * 0.15f)
                quadraticBezierTo((left + right) / 2f, bottomY + midDip, left, bottomY - midDip * 0.15f)
                close()
            }

            val leftPage = pagePath(cx - innerW, cx, top, bottom)
            val rightPage = pagePath(cx, cx + innerW, top, bottom)
            drawPath(leftPage, pageFill)
            drawPath(rightPage, pageFill)
            drawPath(leftPage, ink, style = stroke)
            drawPath(rightPage, ink, style = stroke)
            drawLine(
                color = ink,
                start = Offset(cx, top + pageH * 0.03f),
                end = Offset(cx, bottom - midDip * 0.4f),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )

            // Text lines on pages
            for (i in 0 until 5) {
                val y = top + pageH * (0.26f + i * 0.11f)
                val inset = innerW * 0.16f
                drawLine(
                    color = ink.copy(alpha = 0.22f),
                    start = Offset(cx - innerW + inset, y),
                    end = Offset(cx - inset * 0.5f, y),
                    strokeWidth = stroke.width * 0.35f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = ink.copy(alpha = 0.22f),
                    start = Offset(cx + inset * 0.5f, y),
                    end = Offset(cx + innerW - inset, y),
                    strokeWidth = stroke.width * 0.35f,
                    cap = StrokeCap.Round,
                )
            }

            // Turning page (white fill, readable)
            val t = ((flip + 0.15f) % 1f).coerceIn(0f, 1f)
            // Hold open most of the cycle, flip in the middle third
            val flipT = when {
                t < 0.25f -> 0f
                t > 0.75f -> 1f
                else -> (t - 0.25f) / 0.5f
            }
            val angle = (1f - flipT) * Math.PI.toFloat()
            val cosA = cos(angle)
            val foreshorten = abs(cosA).coerceAtLeast(0.06f)
            val pageW = innerW * foreshorten
            val turning = Path().apply {
                if (cosA >= 0f) {
                    moveTo(cx, top + pageH * 0.02f)
                    lineTo(cx + pageW, top + pageH * 0.04f)
                    lineTo(cx + pageW, bottom - midDip * 0.45f)
                    lineTo(cx, bottom - midDip * 0.25f)
                } else {
                    moveTo(cx, top + pageH * 0.02f)
                    lineTo(cx - pageW, top + pageH * 0.04f)
                    lineTo(cx - pageW, bottom - midDip * 0.45f)
                    lineTo(cx, bottom - midDip * 0.25f)
                }
                close()
            }
            drawPath(turning, if (cosA >= 0f) pageFill else pageShade)
            drawPath(turning, ink, style = stroke)
        }
    }
}

@Composable
private fun AwayDoorDistractionOverlay(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.cd_idle_distraction_away)
    val title = stringResource(R.string.idle_distraction_away_title)
    val subtitle = stringResource(R.string.idle_distraction_away_subtitle)
    val transition = rememberInfiniteTransition(label = "hangingSign")
    val sway by transition.animateFloat(
        initialValue = -7f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sway",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(2f)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        // Nail + string (static)
        Canvas(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .fillMaxHeight(0.22f)
                .align(Alignment.TopCenter)
                .padding(top = 36.dp),
        ) {
            val ink = SceneInk
            val cx = size.width / 2f
            // Nail head
            drawCircle(
                color = ink,
                radius = size.minDimension * 0.08f,
                center = Offset(cx, size.height * 0.15f),
            )
            drawCircle(
                color = Color.Black,
                radius = size.minDimension * 0.035f,
                center = Offset(cx, size.height * 0.15f),
            )
            // String
            drawLine(
                color = ink,
                start = Offset(cx, size.height * 0.22f),
                end = Offset(cx, size.height * 0.98f),
                strokeWidth = size.minDimension * 0.035f,
                cap = StrokeCap.Round,
            )
        }

        // Swinging sign
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 28.dp)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    rotationZ = sway
                }
                .fillMaxWidth(0.72f)
                .background(Color(0xFFF5F0E6), RoundedCornerShape(6.dp))
                .border(4.dp, SceneInkDark, RoundedCornerShape(6.dp))
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                color = SceneInkDark,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = subtitle,
                color = Color(0xFF444444),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
fun RetroPongDistractionOverlay(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.cd_idle_distraction_pong)
    var ballX by remember { mutableFloatStateOf(0.5f) }
    var ballY by remember { mutableFloatStateOf(0.5f) }
    var vx by remember { mutableFloatStateOf(0.35f) }
    var vy by remember { mutableFloatStateOf(0.28f) }
    var leftPaddleY by remember { mutableFloatStateOf(0.5f) }
    var rightPaddleY by remember { mutableFloatStateOf(0.5f) }
    var lastFrameMs by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { frameMs ->
                val prev = lastFrameMs
                lastFrameMs = frameMs
                if (prev == null) return@withFrameMillis
                val dt = ((frameMs - prev).coerceIn(0L, 50L)) / 1000f
                var nx = ballX + vx * dt
                var ny = ballY + vy * dt
                var nvx = vx
                var nvy = vy

                if (ny <= 0.04f) {
                    ny = 0.04f
                    nvy = abs(nvy)
                } else if (ny >= 0.96f) {
                    ny = 0.96f
                    nvy = -abs(nvy)
                }

                val paddleHalf = 0.12f
                if (nx <= 0.08f && abs(ny - leftPaddleY) < paddleHalf) {
                    nx = 0.08f
                    nvx = abs(nvx)
                } else if (nx >= 0.92f && abs(ny - rightPaddleY) < paddleHalf) {
                    nx = 0.92f
                    nvx = -abs(nvx)
                } else if (nx < 0f || nx > 1f) {
                    nx = 0.5f
                    ny = 0.5f
                    nvx = if (nvx >= 0f) -0.35f else 0.35f
                    nvy = 0.28f * if (nvy >= 0f) 1f else -1f
                }

                leftPaddleY = (leftPaddleY + (ny - leftPaddleY) * (dt * 3.2f)).coerceIn(0.15f, 0.85f)
                rightPaddleY = (rightPaddleY + (ny - rightPaddleY) * (dt * 2.8f)).coerceIn(0.15f, 0.85f)

                ballX = nx
                ballY = ny
                vx = nvx
                vy = nvy
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize(0.9f)
            .zIndex(2f)
            .background(Color(0xFF111111), RoundedCornerShape(12.dp))
            .border(8.dp, Color(0xFF555555), RoundedCornerShape(12.dp))
            .padding(10.dp)
            .semantics { contentDescription = description },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(Color(0xFF0A0A0A))

            val dashH = h / 20f
            var y = 0f
            while (y < h) {
                drawRect(
                    color = Color(0xFF2A2A2A),
                    topLeft = Offset(w / 2f - 2f, y),
                    size = Size(4f, dashH * 0.5f),
                )
                y += dashH
            }

            val paddleW = w * 0.03f
            val paddleH = h * 0.22f
            drawRect(
                color = Color(0xFFE0E0E0),
                topLeft = Offset(w * 0.04f, leftPaddleY * h - paddleH / 2f),
                size = Size(paddleW, paddleH),
            )
            drawRect(
                color = Color(0xFFE0E0E0),
                topLeft = Offset(w * 0.93f, rightPaddleY * h - paddleH / 2f),
                size = Size(paddleW, paddleH),
            )
            val ballR = minOf(w, h) * 0.025f
            drawCircle(
                color = Color(0xFFE8E8E8),
                radius = ballR,
                center = Offset(ballX * w, ballY * h),
            )
        }
        Text(
            text = "PONG",
            color = Color(0xFF666666),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 4.dp),
        )
    }
}
