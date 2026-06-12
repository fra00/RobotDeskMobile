package com.example.mydeskrobot.ui.eyes

import com.example.mydeskrobot.domain.model.RobotEmotion

/**
 * Maps [RobotEmotion] + mood intensity to drawable eye expression specs.
 */
object EyeExpressionMapper {

    fun map(emotion: RobotEmotion, intensity: Float): EyePairExpressionSpec {
        val i = ExpressionIntensity.coerce(intensity)
        return when (emotion) {
            RobotEmotion.NEUTRAL -> neutral(i)
            RobotEmotion.HAPPY -> happy(i)
            RobotEmotion.LOVING -> loving(i)
            RobotEmotion.LISTENING -> listening(i)
            RobotEmotion.THINKING -> thinking(i)
            RobotEmotion.SPEAKING -> speaking(i)
            RobotEmotion.SURPRISED -> surprised(i)
            RobotEmotion.CONFUSED -> confused(i)
            RobotEmotion.ANGRY -> angry(i)
            RobotEmotion.SAD -> sad(i)
            RobotEmotion.BORED -> bored(i)
            RobotEmotion.SLEEPING -> closedPair()
            RobotEmotion.DROWSY -> drowsy(i)
            RobotEmotion.WINK -> wink()
        }
    }

    private fun neutral(i: Float): EyePairExpressionSpec {
        val geo = EyeGeometry.neutral()
        val pupil = PupilSpec(visible = true, radiusFraction = 0.2f)
        val brow = EyebrowSpec(
            style = EyebrowStyle.NEUTRAL,
            thicknessFraction = ExpressionIntensity.lerp(0.05f, 0.07f, i),
        )
        return pair(geo, geo, pupil, pupil, brow, brow)
    }

    private fun happy(i: Float): EyePairExpressionSpec {
        val arc = ExpressionIntensity.lerp(1f, 1.2f, i)
        val geo = EyeGeometry.happy().copy(arcCurve = arc)
        val pupil = PupilSpec(
            offsetYFraction = ExpressionIntensity.lerp(-0.06f, -0.12f, i),
            radiusFraction = 0.24f,
        )
        val brow = EyebrowSpec(
            style = EyebrowStyle.HAPPY_ARCH,
            thicknessFraction = ExpressionIntensity.lerp(0.07f, 0.1f, i),
            liftFraction = ExpressionIntensity.lerp(0.12f, 0.18f, i),
        )
        val motionAmp = ExpressionIntensity.lerp(0.5f, 1f, i)
        return pair(
            geo, geo, pupil, pupil, brow, brow,
            motion = EyeMotion.BOUNCE,
            motionAmplitude = motionAmp,
        )
    }

    private fun loving(i: Float): EyePairExpressionSpec {
        val geo = EyeGeometry.loving().copy(
            arcCurve = ExpressionIntensity.lerp(1.1f, 1.25f, i),
        )
        val pupil = PupilSpec(offsetYFraction = -0.1f, radiusFraction = 0.26f)
        val brow = EyebrowSpec(
            style = EyebrowStyle.HAPPY_ARCH,
            thicknessFraction = 0.09f,
            liftFraction = 0.16f,
        )
        return pair(geo, geo, pupil, pupil, brow, brow, motion = EyeMotion.BOUNCE, motionAmplitude = 0.7f)
    }

    private fun listening(i: Float): EyePairExpressionSpec {
        val geo = EyeGeometry.listening()
        val pupil = PupilSpec(radiusFraction = 0.21f)
        return EyePairExpressionSpec(
            left = EyeExpressionSpec(geo, pupil),
            right = EyeExpressionSpec(geo, pupil),
            enableBlink = true,
            enableListeningPulse = true,
        )
    }

    private fun thinking(i: Float): EyePairExpressionSpec {
        val left = EyeGeometry.thinking()
        val right = EyeGeometry.thinking().copy(rotationDeg = 6f)
        val pupil = PupilSpec(offsetYFraction = -0.1f, radiusFraction = 0.2f)
        val brow = EyebrowSpec(style = EyebrowStyle.NEUTRAL, liftFraction = 0.18f)
        return pair(left, right, pupil, pupil, brow, brow)
    }

    private fun speaking(i: Float): EyePairExpressionSpec {
        val geo = EyeGeometry.speaking()
        val pupil = PupilSpec(radiusFraction = 0.2f)
        return EyePairExpressionSpec(
            left = EyeExpressionSpec(geo, pupil),
            right = EyeExpressionSpec(geo, pupil),
            enableBlink = false,
            enableSpeakingPulse = true,
        )
    }

    private fun surprised(i: Float): EyePairExpressionSpec {
        val scale = ExpressionIntensity.lerp(1.28f, 1.38f, i)
        val geo = EyeGeometry.surprised().copy(scaleX = scale, scaleY = scale)
        val pupil = PupilSpec(radiusFraction = ExpressionIntensity.lerp(0.16f, 0.14f, i))
        val brow = EyebrowSpec(
            style = EyebrowStyle.SURPRISED_HIGH,
            thicknessFraction = 0.08f,
            liftFraction = ExpressionIntensity.lerp(0.2f, 0.28f, i),
        )
        return EyePairExpressionSpec(
            left = EyeExpressionSpec(geo, pupil, brow),
            right = EyeExpressionSpec(geo, pupil, brow),
            enableBlink = false,
            surprisedPop = true,
        )
    }

    private fun confused(i: Float): EyePairExpressionSpec {
        val leftGeo = EyeGeometry.neutral().copy(rotationDeg = -4f)
        val rightGeo = EyeGeometry.squint().copy(rotationDeg = 12f)
        val leftPupil = PupilSpec(
            offsetXFraction = -0.08f,
            driftAmplitude = ExpressionIntensity.lerp(0.06f, 0.1f, i),
        )
        val rightPupil = PupilSpec(
            offsetXFraction = 0.1f,
            offsetYFraction = 0.04f,
            driftAmplitude = ExpressionIntensity.lerp(0.08f, 0.12f, i),
        )
        val leftBrow = EyebrowSpec(style = EyebrowStyle.NEUTRAL, liftFraction = 0.1f)
        val rightBrow = EyebrowSpec(
            style = EyebrowStyle.ANGRY_V,
            thicknessFraction = 0.06f,
            liftFraction = 0.08f,
        )
        return EyePairExpressionSpec(
            left = EyeExpressionSpec(
                leftGeo,
                leftPupil,
                leftBrow,
                motion = EyeMotion.PUPIL_DRIFT,
                motionAmplitude = i,
            ),
            right = EyeExpressionSpec(
                rightGeo,
                rightPupil,
                rightBrow,
                motion = EyeMotion.PUPIL_DRIFT,
                motionAmplitude = i,
            ),
            enableBlink = false,
        )
    }

    private fun angry(i: Float): EyePairExpressionSpec {
        val rot = ExpressionIntensity.lerp(20f, 28f, i)
        val scaleY = ExpressionIntensity.lerp(0.52f, 0.45f, i)
        val leftGeo = EyeGeometry.angry(rotationDeg = -rot).copy(scaleY = scaleY)
        val rightGeo = EyeGeometry.angry(rotationDeg = rot).copy(scaleY = scaleY)
        val pupilIn = ExpressionIntensity.lerp(0.1f, 0.16f, i)
        val leftPupil = PupilSpec(
            offsetXFraction = pupilIn,
            offsetYFraction = ExpressionIntensity.lerp(0.06f, 0.1f, i),
            radiusFraction = ExpressionIntensity.lerp(0.2f, 0.18f, i),
        )
        val rightPupil = PupilSpec(
            offsetXFraction = -pupilIn,
            offsetYFraction = ExpressionIntensity.lerp(0.06f, 0.1f, i),
            radiusFraction = ExpressionIntensity.lerp(0.2f, 0.18f, i),
        )
        val browThickness = ExpressionIntensity.lerp(0.09f, 0.13f, i)
        val brow = EyebrowSpec(
            style = EyebrowStyle.ANGRY_V,
            thicknessFraction = browThickness,
            liftFraction = ExpressionIntensity.lerp(0.1f, 0.14f, i),
        )
        return EyePairExpressionSpec(
            left = EyeExpressionSpec(
                leftGeo,
                leftPupil,
                brow,
                motion = EyeMotion.SHAKE,
                motionAmplitude = i,
            ),
            right = EyeExpressionSpec(
                rightGeo,
                rightPupil,
                brow,
                motion = EyeMotion.SHAKE,
                motionAmplitude = i,
            ),
            enableBlink = false,
        )
    }

    private fun sad(i: Float): EyePairExpressionSpec {
        val geo = EyeGeometry.sad()
        val pupil = PupilSpec(
            offsetYFraction = ExpressionIntensity.lerp(0.08f, 0.12f, i),
            radiusFraction = 0.2f,
        )
        val brow = EyebrowSpec(
            style = EyebrowStyle.SAD_DROP,
            thicknessFraction = 0.07f,
            liftFraction = 0.08f,
        )
        return pair(geo, geo, pupil, pupil, brow, brow, enableBlink = false)
    }

    private fun bored(i: Float): EyePairExpressionSpec {
        val scaleY = ExpressionIntensity.lerp(0.38f, 0.32f, i)
        val left = EyeGeometry.boredLeft().copy(scaleY = scaleY)
        val right = EyeGeometry.boredRight().copy(scaleY = scaleY)
        val pupil = PupilSpec(
            offsetYFraction = 0.1f,
            radiusFraction = 0.18f,
        )
        val brow = EyebrowSpec(
            style = EyebrowStyle.SAD_DROP,
            thicknessFraction = 0.06f,
            liftFraction = 0.06f,
        )
        return EyePairExpressionSpec(
            left = EyeExpressionSpec(left, pupil, brow, motion = EyeMotion.SLOW_DROOP, motionAmplitude = i),
            right = EyeExpressionSpec(right, pupil, brow, motion = EyeMotion.SLOW_DROOP, motionAmplitude = i),
            enableBlink = true,
        )
    }

    private fun closedPair(): EyePairExpressionSpec {
        val geo = EyeGeometry.sleeping()
        val hidden = PupilSpec(visible = false)
        val noBrow = EyebrowSpec(style = EyebrowStyle.NONE)
        return pair(geo, geo, hidden, hidden, noBrow, noBrow, enableBlink = false)
    }

    private fun drowsy(i: Float): EyePairExpressionSpec {
        val left = EyeGeometry.drowsy()
        val right = EyeGeometry.drowsy().copy(rotationDeg = -5f)
        val hidden = PupilSpec(visible = false)
        val noBrow = EyebrowSpec(style = EyebrowStyle.NONE)
        return pair(left, right, hidden, hidden, noBrow, noBrow, enableBlink = false)
    }

    private fun wink(): EyePairExpressionSpec {
        val closed = EyeGeometry.winkClosed()
        val open = EyeGeometry.winkOpen()
        val hidden = PupilSpec(visible = false)
        val openPupil = PupilSpec(offsetYFraction = -0.06f, radiusFraction = 0.22f)
        val happyBrow = EyebrowSpec(style = EyebrowStyle.HAPPY_ARCH, liftFraction = 0.12f)
        return EyePairExpressionSpec(
            left = EyeExpressionSpec(closed, hidden, EyebrowSpec(style = EyebrowStyle.NONE)),
            right = EyeExpressionSpec(open, openPupil, happyBrow),
            enableBlink = false,
        )
    }

    private fun pair(
        leftGeo: EyeGeometry,
        rightGeo: EyeGeometry,
        leftPupil: PupilSpec,
        rightPupil: PupilSpec,
        leftBrow: EyebrowSpec,
        rightBrow: EyebrowSpec,
        enableBlink: Boolean = true,
        motion: EyeMotion = EyeMotion.NONE,
        motionAmplitude: Float = 1f,
    ): EyePairExpressionSpec = EyePairExpressionSpec(
        left = EyeExpressionSpec(leftGeo, leftPupil, leftBrow, motion, motionAmplitude),
        right = EyeExpressionSpec(rightGeo, rightPupil, rightBrow, motion, motionAmplitude),
        enableBlink = enableBlink,
    )
}
