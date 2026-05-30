package com.example.mydeskrobot.ui.eyes

/**
 * Parametri geometrici di un singolo occhio (forma bianca, senza pupilla).
 * [arcCurve]: &gt; 0 arco felice verso l'alto, &lt; 0 palpebra calata (triste/stanco).
 */
data class EyeGeometry(
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotationDeg: Float = 0f,
    val offsetXFraction: Float = 0f,
    val offsetYFraction: Float = 0f,
    val arcCurve: Float = 0f,
) {
    companion object {
        fun neutral() = EyeGeometry(scaleX = 1f, scaleY = 1.05f)

        fun happy() = EyeGeometry(scaleX = 1f, scaleY = 0.9f, arcCurve = 1f)

        fun listening() = EyeGeometry(scaleX = 1.08f, scaleY = 1.1f)

        fun thinking() = EyeGeometry(scaleX = 0.95f, scaleY = 0.75f, offsetYFraction = -0.08f)

        fun speaking() = EyeGeometry(scaleX = 1.05f, scaleY = 1f)

        fun surprised() = EyeGeometry(scaleX = 1.32f, scaleY = 1.32f)

        fun sad() = EyeGeometry(scaleX = 1.05f, scaleY = 0.35f, offsetYFraction = 0.12f, arcCurve = -1f)

        fun squint() = EyeGeometry(scaleX = 0.92f, scaleY = 0.42f, rotationDeg = 10f)

        /** [arcCurve] = 2 → forma arrabbiata (taglio diagonale verso il centro del viso). */
        fun angry(rotationDeg: Float = -14f) =
            EyeGeometry(scaleX = 1.12f, scaleY = 0.58f, rotationDeg = rotationDeg, arcCurve = 2f)

        /**
         * Noia: palpebra a metà (taglio piatto in alto) + sguardo in basso verso l'esterno.
         * [arcCurve] in (-0.35, -0.1) → forma bored in [RobotEye].
         */
        fun boredLeft() = EyeGeometry(
            scaleX = 0.96f,
            scaleY = 0.4f,
            offsetXFraction = -0.12f,
            offsetYFraction = 0.14f,
            rotationDeg = -7f,
            arcCurve = -0.2f,
        )

        fun boredRight() = EyeGeometry(
            scaleX = 0.96f,
            scaleY = 0.4f,
            offsetXFraction = 0.12f,
            offsetYFraction = 0.14f,
            rotationDeg = 7f,
            arcCurve = -0.2f,
        )

        fun sleeping() = EyeGeometry(scaleX = 1.05f, scaleY = 0.06f, offsetYFraction = 0.02f)

        fun drowsy() = EyeGeometry(scaleX = 1f, scaleY = 0.48f, offsetYFraction = 0.08f, arcCurve = -0.15f)

        /** Occhio chiuso in uno strizzamento (linea orizzontale). */
        fun winkClosed() = EyeGeometry(scaleX = 1.05f, scaleY = 0.08f, offsetYFraction = 0.02f)

        /** Occhio aperto accanto a un occhiolino. */
        fun winkOpen() = EyeGeometry(scaleX = 1.05f, scaleY = 0.88f, arcCurve = 1f)

        /** Sorriso morbido, occhi leggermente più grandi. */
        fun loving() = EyeGeometry(scaleX = 1.12f, scaleY = 0.95f, arcCurve = 1.15f)
    }
}

data class EyePairSpec(
    val left: EyeGeometry,
    val right: EyeGeometry,
    val enableBlink: Boolean = true,
    val enableListeningPulse: Boolean = false,
    val enableSpeakingPulse: Boolean = false,
)
