package com.example.mydeskrobot.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.mydeskrobot.R
import com.example.mydeskrobot.domain.mood.IdleDistractionKind
import kotlin.math.abs

/**
 * Symbolic idle-distraction overlays (mimica only — no real media or games).
 */
@Composable
fun IdleDistractionOverlay(
    kind: IdleDistractionKind,
    modifier: Modifier = Modifier,
) {
    when (kind) {
        IdleDistractionKind.HEADPHONES -> HeadphonesDistractionOverlay(modifier)
        IdleDistractionKind.READING -> ReadingDistractionOverlay(modifier)
        IdleDistractionKind.AWAY -> AwaySignDistractionOverlay(modifier)
        IdleDistractionKind.PONG -> RetroPongDistractionOverlay(modifier)
    }
}

@Composable
private fun HeadphonesDistractionOverlay(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.cd_idle_distraction_headphones)
    Icon(
        imageVector = Icons.Filled.Headset,
        contentDescription = description,
        modifier = modifier
            .size(72.dp)
            .zIndex(2f)
            .semantics { contentDescription = description },
        tint = Color(0xFFE8E8E8),
    )
}

@Composable
private fun ReadingDistractionOverlay(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.cd_idle_distraction_reading)
    Box(
        modifier = modifier
            .fillMaxSize(0.72f)
            .zIndex(2f)
            .background(Color(0xFF5C3A21), RoundedCornerShape(8.dp))
            .border(3.dp, Color(0xFF3E2716), RoundedCornerShape(8.dp))
            .padding(16.dp)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.idle_distraction_book_title),
                color = Color(0xFFF5E6C8),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.idle_distraction_book_subtitle),
                color = Color(0xFFD4C4A8),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun AwaySignDistractionOverlay(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.cd_idle_distraction_away)
    Box(
        modifier = modifier
            .fillMaxWidth(0.85f)
            .zIndex(2f)
            .background(Color(0xFFF5F0E6), RoundedCornerShape(4.dp))
            .border(4.dp, Color(0xFF2A2A2A), RoundedCornerShape(4.dp))
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.idle_distraction_away_title),
                color = Color(0xFF1A1A1A),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.idle_distraction_away_subtitle),
                color = Color(0xFF444444),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
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

            // Center dashed line
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
