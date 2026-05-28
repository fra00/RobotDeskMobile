package com.example.mydeskrobot.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.mydeskrobot.ui.eyes.EyeGeometry
import com.example.mydeskrobot.ui.theme.RobotEyeWhite

@Composable
fun RobotEye(
    target: EyeGeometry,
    isBlinking: Boolean,
    pulseScale: Float,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    color: Color = RobotEyeWhite,
) {
    val animatedScaleX by animateFloatAsState(
        targetValue = (if (isBlinking) 1f else target.scaleX) * pulseScale,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "eyeScaleX",
    )
    val animatedScaleY by animateFloatAsState(
        targetValue = if (isBlinking) 0.06f else target.scaleY * pulseScale,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "eyeScaleY",
    )
    val animatedRotation by animateFloatAsState(
        targetValue = if (isBlinking) 0f else target.rotationDeg,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "eyeRotation",
    )
    val animatedOffsetX by animateFloatAsState(
        targetValue = if (isBlinking) 0f else target.offsetXFraction,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "eyeOffsetX",
    )
    val animatedOffsetY by animateFloatAsState(
        targetValue = if (isBlinking) 0f else target.offsetYFraction,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "eyeOffsetY",
    )
    val animatedArc by animateFloatAsState(
        targetValue = if (isBlinking) 0f else target.arcCurve,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "eyeArc",
    )

    Canvas(modifier = modifier.size(size)) {
        val geom = EyeGeometry(
            scaleX = animatedScaleX,
            scaleY = animatedScaleY,
            rotationDeg = animatedRotation,
            offsetXFraction = animatedOffsetX,
            offsetYFraction = animatedOffsetY,
            arcCurve = animatedArc,
        )
        drawRobotEye(color = color, size = this.size, geometry = geom)
    }
}

private fun DrawScope.drawRobotEye(
    color: Color,
    size: Size,
    geometry: EyeGeometry,
) {
    val centerX = size.width / 2f + geometry.offsetXFraction * size.width
    val centerY = size.height / 2f + geometry.offsetYFraction * size.height
    val radiusX = size.width / 2f * geometry.scaleX
    val radiusY = size.height / 2f * geometry.scaleY

    rotate(degrees = geometry.rotationDeg, pivot = Offset(centerX, centerY)) {
        when {
            geometry.arcCurve > 1.5f -> drawAngryEye(
                color = color,
                centerX = centerX,
                centerY = centerY,
                radiusX = radiusX,
                radiusY = radiusY,
                tiltInward = geometry.rotationDeg <= 0f,
            )
            geometry.arcCurve > 0.35f -> drawHappyEye(color, centerX, centerY, radiusX, radiusY)
            geometry.arcCurve < -0.35f -> drawSadEye(color, centerX, centerY, radiusX, radiusY)
            geometry.arcCurve <= -0.18f -> drawBoredEye(color, centerX, centerY, radiusX, radiusY)
            else -> drawOvalEye(color, centerX, centerY, radiusX, radiusY)
        }
    }
}

private fun DrawScope.drawOvalEye(
    color: Color,
    centerX: Float,
    centerY: Float,
    radiusX: Float,
    radiusY: Float,
) {
    drawOval(
        color = color,
        topLeft = Offset(centerX - radiusX, centerY - radiusY),
        size = Size(radiusX * 2f, radiusY * 2f),
    )
}

private fun DrawScope.drawHappyEye(
    color: Color,
    centerX: Float,
    centerY: Float,
    radiusX: Float,
    radiusY: Float,
) {
    val rect = Rect(
        left = centerX - radiusX,
        top = centerY - radiusY,
        right = centerX + radiusX,
        bottom = centerY + radiusY,
    )
    val path = Path().apply {
        moveTo(rect.left, rect.top + rect.height * 0.58f)
        quadraticBezierTo(
            x1 = rect.left + rect.width / 2f,
            y1 = rect.top - rect.height * 0.22f,
            x2 = rect.right,
            y2 = rect.top + rect.height * 0.58f,
        )
        quadraticBezierTo(
            x1 = rect.left + rect.width / 2f,
            y1 = rect.top + rect.height * 0.42f,
            x2 = rect.left,
            y2 = rect.top + rect.height * 0.58f,
        )
        close()
    }
    drawPath(path, color)
}

/** Occhio inclinato verso il centro del viso (rabbia). */
private fun DrawScope.drawAngryEye(
    color: Color,
    centerX: Float,
    centerY: Float,
    radiusX: Float,
    radiusY: Float,
    tiltInward: Boolean,
) {
    val path = Path().apply {
        if (tiltInward) {
            moveTo(centerX - radiusX, centerY - radiusY * 0.15f)
            lineTo(centerX + radiusX, centerY - radiusY)
            lineTo(centerX + radiusX, centerY + radiusY * 0.9f)
            lineTo(centerX - radiusX, centerY + radiusY * 0.35f)
        } else {
            moveTo(centerX - radiusX, centerY - radiusY)
            lineTo(centerX + radiusX, centerY - radiusY * 0.15f)
            lineTo(centerX + radiusX, centerY + radiusY * 0.35f)
            lineTo(centerX - radiusX, centerY + radiusY * 0.9f)
        }
        close()
    }
    drawPath(path, color)
}

/** Palpebra calata con taglio orizzontale in alto (noia). */
private fun DrawScope.drawBoredEye(
    color: Color,
    centerX: Float,
    centerY: Float,
    radiusX: Float,
    radiusY: Float,
) {
    val lidY = centerY - radiusY * 0.2f
    val bottom = centerY + radiusY
    val path = Path().apply {
        moveTo(centerX - radiusX, lidY)
        lineTo(centerX + radiusX, lidY)
        quadraticBezierTo(
            x1 = centerX + radiusX,
            y1 = bottom,
            x2 = centerX,
            y2 = bottom,
        )
        quadraticBezierTo(
            x1 = centerX - radiusX,
            y1 = bottom,
            x2 = centerX - radiusX,
            y2 = lidY,
        )
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawSadEye(
    color: Color,
    centerX: Float,
    centerY: Float,
    radiusX: Float,
    radiusY: Float,
) {
    val rect = Rect(
        left = centerX - radiusX,
        top = centerY - radiusY,
        right = centerX + radiusX,
        bottom = centerY + radiusY,
    )
    val path = Path().apply {
        moveTo(rect.left, rect.top + rect.height * 0.35f)
        quadraticBezierTo(
            x1 = rect.left + rect.width / 2f,
            y1 = rect.top + rect.height * 1.18f,
            x2 = rect.right,
            y2 = rect.top + rect.height * 0.35f,
        )
        quadraticBezierTo(
            x1 = rect.left + rect.width / 2f,
            y1 = rect.top + rect.height * 0.52f,
            x2 = rect.left,
            y2 = rect.top + rect.height * 0.35f,
        )
        close()
    }
    drawPath(path, color)
}
