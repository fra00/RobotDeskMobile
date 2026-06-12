package com.example.mydeskrobot.ui.eyes

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.min

internal fun DrawScope.drawEyebrow(
    color: Color,
    centerX: Float,
    centerY: Float,
    radiusX: Float,
    radiusY: Float,
    rotationDeg: Float,
    spec: EyebrowSpec,
    tiltInward: Boolean,
) {
    if (spec.style == EyebrowStyle.NONE) return

    val thickness = min(radiusX, radiusY) * spec.thicknessFraction
    val lift = radiusY * spec.liftFraction
    val browY = centerY - radiusY - lift
    val halfWidth = radiusX * 0.85f

    rotate(degrees = rotationDeg, pivot = Offset(centerX, centerY)) {
        when (spec.style) {
            EyebrowStyle.HAPPY_ARCH -> drawHappyBrow(color, centerX, browY, halfWidth, thickness)
            EyebrowStyle.ANGRY_V -> drawAngryVBrow(color, centerX, browY, halfWidth, thickness, tiltInward)
            EyebrowStyle.SAD_DROP -> drawSadBrow(color, centerX, browY, halfWidth, thickness)
            EyebrowStyle.SURPRISED_HIGH -> drawSurprisedBrow(color, centerX, browY - radiusY * 0.08f, halfWidth, thickness)
            EyebrowStyle.NEUTRAL -> drawNeutralBrow(color, centerX, browY, halfWidth, thickness * 0.85f)
            EyebrowStyle.NONE -> Unit
        }
    }
}

private fun DrawScope.drawHappyBrow(
    color: Color,
    centerX: Float,
    browY: Float,
    halfWidth: Float,
    thickness: Float,
) {
    val path = Path().apply {
        moveTo(centerX - halfWidth, browY + thickness)
        quadraticBezierTo(
            centerX,
            browY - thickness * 2.5f,
            centerX + halfWidth,
            browY + thickness,
        )
    }
    drawPath(path, color, style = Stroke(width = thickness, cap = StrokeCap.Round))
}

private fun DrawScope.drawAngryVBrow(
    color: Color,
    centerX: Float,
    browY: Float,
    halfWidth: Float,
    thickness: Float,
    tiltInward: Boolean,
) {
    val innerY = browY + thickness * 1.2f
    val outerY = browY - thickness * 0.5f
    val path = Path().apply {
        if (tiltInward) {
            moveTo(centerX - halfWidth, outerY)
            lineTo(centerX + halfWidth * 0.15f, innerY)
            moveTo(centerX + halfWidth, outerY)
            lineTo(centerX - halfWidth * 0.15f, innerY)
        } else {
            moveTo(centerX + halfWidth, outerY)
            lineTo(centerX - halfWidth * 0.15f, innerY)
            moveTo(centerX - halfWidth, outerY)
            lineTo(centerX + halfWidth * 0.15f, innerY)
        }
    }
    drawPath(path, color, style = Stroke(width = thickness, cap = StrokeCap.Round))
}

private fun DrawScope.drawSadBrow(
    color: Color,
    centerX: Float,
    browY: Float,
    halfWidth: Float,
    thickness: Float,
) {
    val path = Path().apply {
        moveTo(centerX - halfWidth, browY - thickness)
        quadraticBezierTo(
            centerX,
            browY + thickness * 2f,
            centerX + halfWidth,
            browY - thickness,
        )
    }
    drawPath(path, color, style = Stroke(width = thickness, cap = StrokeCap.Round))
}

private fun DrawScope.drawSurprisedBrow(
    color: Color,
    centerX: Float,
    browY: Float,
    halfWidth: Float,
    thickness: Float,
) {
    drawLine(
        color = color,
        start = Offset(centerX - halfWidth * 0.7f, browY),
        end = Offset(centerX + halfWidth * 0.7f, browY),
        strokeWidth = thickness,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawNeutralBrow(
    color: Color,
    centerX: Float,
    browY: Float,
    halfWidth: Float,
    thickness: Float,
) {
    drawLine(
        color = color,
        start = Offset(centerX - halfWidth * 0.5f, browY),
        end = Offset(centerX + halfWidth * 0.5f, browY),
        strokeWidth = thickness,
        cap = StrokeCap.Round,
    )
}

internal fun DrawScope.drawPupil(
    pupilColor: Color,
    highlightColor: Color,
    centerX: Float,
    centerY: Float,
    radiusX: Float,
    radiusY: Float,
    spec: PupilSpec,
) {
    if (!spec.visible) return

    val px = centerX + spec.offsetXFraction * radiusX * 2f
    val py = centerY + spec.offsetYFraction * radiusY * 2f
    val pr = min(radiusX, radiusY) * spec.radiusFraction

    drawCircle(color = pupilColor, radius = pr, center = Offset(px, py))
    drawCircle(
        color = highlightColor,
        radius = pr * 0.22f,
        center = Offset(px - pr * 0.35f, py - pr * 0.35f),
    )
}
