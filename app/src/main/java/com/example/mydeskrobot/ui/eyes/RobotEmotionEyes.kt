package com.example.mydeskrobot.ui.eyes

import com.example.mydeskrobot.domain.model.RobotEmotion

object RobotEmotionEyes {

    fun expressionFor(emotion: RobotEmotion, intensity: Float = 0.5f): EyePairExpressionSpec =
        EyeExpressionMapper.map(emotion, intensity)

    /** @deprecated Use [expressionFor] — kept for legacy call sites during migration. */
    fun specFor(emotion: RobotEmotion): EyePairSpec = when (emotion) {
        RobotEmotion.NEUTRAL -> EyePairSpec(
            left = EyeGeometry.neutral(),
            right = EyeGeometry.neutral(),
        )

        RobotEmotion.HAPPY -> EyePairSpec(
            left = EyeGeometry.happy(),
            right = EyeGeometry.happy(),
        )

        RobotEmotion.LISTENING -> EyePairSpec(
            left = EyeGeometry.listening(),
            right = EyeGeometry.listening(),
            enableListeningPulse = true,
        )

        RobotEmotion.THINKING -> EyePairSpec(
            left = EyeGeometry.thinking(),
            right = EyeGeometry.thinking().copy(rotationDeg = 6f),
        )

        RobotEmotion.SPEAKING -> EyePairSpec(
            left = EyeGeometry.speaking(),
            right = EyeGeometry.speaking(),
            enableSpeakingPulse = true,
            enableBlink = false,
        )

        RobotEmotion.SURPRISED -> EyePairSpec(
            left = EyeGeometry.surprised(),
            right = EyeGeometry.surprised(),
            enableBlink = false,
        )

        RobotEmotion.CONFUSED -> EyePairSpec(
            left = EyeGeometry.neutral(),
            right = EyeGeometry.squint(),
            enableBlink = false,
        )

        RobotEmotion.ANGRY -> EyePairSpec(
            left = EyeGeometry.angry(rotationDeg = -18f),
            right = EyeGeometry.angry(rotationDeg = 18f),
            enableBlink = false,
        )

        RobotEmotion.SAD -> EyePairSpec(
            left = EyeGeometry.sad(),
            right = EyeGeometry.sad(),
            enableBlink = false,
        )

        RobotEmotion.BORED -> EyePairSpec(
            left = EyeGeometry.boredLeft(),
            right = EyeGeometry.boredRight(),
            enableBlink = true,
        )

        RobotEmotion.SLEEPING -> EyePairSpec(
            left = EyeGeometry.sleeping(),
            right = EyeGeometry.sleeping(),
            enableBlink = false,
        )

        RobotEmotion.DROWSY -> EyePairSpec(
            left = EyeGeometry.drowsy(),
            right = EyeGeometry.drowsy().copy(rotationDeg = -5f),
            enableBlink = false,
        )

        RobotEmotion.WINK -> EyePairSpec(
            left = EyeGeometry.winkClosed(),
            right = EyeGeometry.winkOpen(),
            enableBlink = false,
        )

        RobotEmotion.LOVING -> EyePairSpec(
            left = EyeGeometry.loving(),
            right = EyeGeometry.loving(),
            enableBlink = true,
        )
    }
}
