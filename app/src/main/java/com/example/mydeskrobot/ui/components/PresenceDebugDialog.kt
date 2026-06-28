package com.example.mydeskrobot.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.mydeskrobot.R
import com.example.mydeskrobot.data.presence.PresenceDebugStateStore
import com.example.mydeskrobot.domain.presence.DeskOccupancyState
import com.example.mydeskrobot.domain.presence.FaceDebugBox
import com.example.mydeskrobot.domain.presence.NormalizedPoint
import com.example.mydeskrobot.domain.presence.NormalizedRect
import com.example.mydeskrobot.domain.presence.PresenceDebugFrame
import kotlin.math.max

@Composable
fun PresenceDebugDialog(
    onDismiss: () -> Unit,
) {
    val frame by PresenceDebugStateStore.frame.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.presence_debug_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.presence_debug_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (frame == null) {
                    Text(
                        text = stringResource(R.string.presence_debug_no_frame),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    PresenceDebugOverlay(frame = frame!!)
                    PresenceDebugStats(frame = frame!!)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_close))
            }
        },
    )
}

@Composable
private fun PresenceDebugOverlay(frame: PresenceDebugFrame) {
    val aspect = max(frame.imageWidth, 1).toFloat() / max(frame.imageHeight, 1).toFloat()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .background(Color.Black.copy(alpha = 0.85f)),
    ) {
        val roiColor = Color(0xFF4CAF50)
        val faceOutColor = Color(0x66FFFFFF)
        val faceInColor = Color(0xFF42A5F5)
        val faceRejectedColor = Color(0xFFE53935)
        val poseInColor = Color(0xFFFF7043)
        val poseOutColor = Color(0x66FF7043)
        val centerColor = Color(0x33FFFFFF)

        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height

            drawRect(
                color = centerColor,
                topLeft = Offset(w * 0.5f - 1f, 0f),
                size = Size(2f, h),
            )
            drawRect(
                color = centerColor,
                topLeft = Offset(0f, h * 0.5f - 1f),
                size = Size(w, 2f),
            )

            drawNormalizedRect(frame.roi, w, h, roiColor, strokeWidth = 3f)

            frame.faces.forEach { face ->
                val color = when {
                    !face.accepted -> faceRejectedColor
                    face.inRoi -> faceInColor
                    else -> faceOutColor
                }
                drawFaceBox(face, w, h, color)
            }

            val leftShoulder = frame.posePoints.find { it.label == "L_shoulder" }
            val rightShoulder = frame.posePoints.find { it.label == "R_shoulder" }
            if (leftShoulder != null && rightShoulder != null) {
                drawLine(
                    color = if (leftShoulder.inRoi && rightShoulder.inRoi) poseInColor else poseOutColor,
                    start = Offset(leftShoulder.x * w, leftShoulder.y * h),
                    end = Offset(rightShoulder.x * w, rightShoulder.y * h),
                    strokeWidth = 3f,
                )
            }
            frame.posePoints.forEach { point ->
                drawPosePoint(point, w, h, if (point.inRoi) poseInColor else poseOutColor)
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        LegendChip(color = Color(0xFF4CAF50), label = stringResource(R.string.presence_debug_legend_roi))
        LegendChip(color = Color(0xFF42A5F5), label = stringResource(R.string.presence_debug_legend_face))
        LegendChip(color = Color(0xFFE53935), label = stringResource(R.string.presence_debug_legend_rejected))
        LegendChip(color = Color(0xFFFF7043), label = stringResource(R.string.presence_debug_legend_pose))
    }
}

@Composable
private fun LegendChip(color: Color, label: String) {
    Text(
        text = "■ $label",
        style = MaterialTheme.typography.labelSmall,
        color = color,
    )
}

@Composable
private fun PresenceDebugStats(frame: PresenceDebugFrame) {
    val stateLabel = when (frame.occupancyState) {
        DeskOccupancyState.PRESENT -> stringResource(R.string.presence_debug_state_present)
        DeskOccupancyState.ABSENT -> stringResource(R.string.presence_debug_state_absent)
        DeskOccupancyState.UNCERTAIN -> stringResource(R.string.presence_debug_state_uncertain)
        DeskOccupancyState.UNKNOWN -> stringResource(R.string.presence_debug_state_unknown)
    }

    Text(
        text = stringResource(
            R.string.presence_debug_fusion_line,
            stateLabel,
            frame.occupancyConfidence,
            if (frame.facePresent) "✓" else "✗",
            if (frame.posePresent) "✓" else "✗",
        ),
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = FontFamily.Monospace,
    )
    val acceptedInRoi = frame.faces.count { it.accepted && it.inRoi }
    Text(
        text = stringResource(
            R.string.presence_debug_faces_line,
            frame.faces.size,
            acceptedInRoi,
            frame.signals.facesInRoi,
            frame.signals.maxFaceConfidence,
        ),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
    )
    Text(
        text = stringResource(
            R.string.presence_debug_pose_line,
            frame.posePoints.count { it.inRoi },
            frame.posePoints.size,
            frame.signals.poseConfidence,
        ),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
    )
    frame.signals.primaryFaceOffsetX?.let { ox ->
        val oy = frame.signals.primaryFaceOffsetY ?: 0f
        Text(
            text = stringResource(R.string.presence_debug_gaze_line, ox, oy),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
    Text(
        text = stringResource(
            R.string.presence_debug_frame_line,
            frame.imageWidth,
            frame.imageHeight,
        ),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNormalizedRect(
    rect: NormalizedRect,
    w: Float,
    h: Float,
    color: Color,
    strokeWidth: Float,
) {
    drawRect(
        color = color,
        topLeft = Offset(rect.left * w, rect.top * h),
        size = Size((rect.right - rect.left) * w, (rect.bottom - rect.top) * h),
        style = Stroke(width = strokeWidth),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFaceBox(
    face: FaceDebugBox,
    w: Float,
    h: Float,
    color: Color,
) {
    drawNormalizedRect(face.box, w, h, color, strokeWidth = if (face.inRoi) 4f else 2f)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPosePoint(
    point: NormalizedPoint,
    w: Float,
    h: Float,
    color: Color,
) {
    drawCircle(
        color = color,
        radius = if (point.inRoi) 10f else 7f,
        center = Offset(point.x * w, point.y * h),
    )
}
