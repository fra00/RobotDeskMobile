package com.example.mydeskrobot.domain.spatial

/**
 * Tracks multi-angle room scans within the current user turn.
 * [SavePlaceTool] requires enough [recordScan] calls before memorizing a new place.
 */
object SpatialScanSession {

    private const val SCANS_WITH_BODY = 3
    private const val SCANS_WITHOUT_BODY = 1

    private var multiAngleRequired: Boolean = false
    private var scanCount: Int = 0
    private val landmarksAccumulator = linkedSetOf<String>()

    fun configure(bodyAvailable: Boolean) {
        multiAngleRequired = bodyAvailable
    }

    fun requiredScans(): Int = if (multiAngleRequired) SCANS_WITH_BODY else SCANS_WITHOUT_BODY

    fun recordScan(landmarks: List<String>) {
        scanCount++
        landmarks
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .forEach { landmarksAccumulator.add(it) }
    }

    fun mergedLandmarks(): List<String> = landmarksAccumulator.toList()

    fun scanCount(): Int = scanCount

    fun isReadyForNewPlaceSave(): Boolean = scanCount >= requiredScans()

    fun progressLabel(): String = "${scanCount}/${requiredScans()} angolazioni"

    fun reset() {
        scanCount = 0
        landmarksAccumulator.clear()
    }

    fun scanIncompleteMessage(): String {
        return if (multiAngleRequired) {
            "Memorizzazione stanza nuova richiede scan multi-angolo (${progressLabel()}). " +
                "Per ogni angolazione: move_body_joint (es. base_pan −25, poi 0, poi +25) " +
                "e analyze_room_scene; unisci i landmark nel think, poi richiama save_place."
        } else {
            "Memorizzazione stanza nuova richiede almeno una analyze_room_scene " +
                "(${progressLabel()}) prima di save_place."
        }
    }
}
