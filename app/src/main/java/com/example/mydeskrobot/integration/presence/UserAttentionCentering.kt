package com.example.mydeskrobot.integration.presence

import android.util.Log
import com.example.mydeskrobot.data.body.BodySettings
import com.example.mydeskrobot.data.presence.DeskPresenceSettings
import com.example.mydeskrobot.data.presence.FaceGazeStateStore
import com.example.mydeskrobot.domain.presence.AttentionCenteringPolicy
import com.example.mydeskrobot.domain.presence.FaceGazeSnapshot
import com.example.mydeskrobot.integration.body.BodyApiClient
import com.example.mydeskrobot.integration.body.BodyApiResult
import com.example.mydeskrobot.integration.body.BodyJoint
import com.example.mydeskrobot.integration.body.BodyMove
import com.example.mydeskrobot.integration.body.BodyStatus
import kotlinx.coroutines.delay

/**
 * Closed-loop centering toward the user before a conversational reply.
 * When no face is in frame, sweeps horizontally to find the user first.
 */
class UserAttentionCentering(
    private val bodySettingsProvider: suspend () -> BodySettings,
    private val deskPresenceSettingsProvider: suspend () -> DeskPresenceSettings,
) {
    private var lastCenteredAtMs = 0L
    private var panSign: Int = -1

    suspend fun tryCenterOnUser(): AttentionCenteringResult {
        val bodySettings = bodySettingsProvider()
        if (!bodySettings.isConfigured()) {
            return AttentionCenteringResult.SkippedBodyDisabled
        }

        val presenceSettings = deskPresenceSettingsProvider()
        if (!presenceSettings.enabled) {
            return AttentionCenteringResult.SkippedPresenceDisabled
        }

        val now = System.currentTimeMillis()
        if (now - lastCenteredAtMs < AttentionCenteringPolicy.MIN_INTERVAL_MS) {
            return AttentionCenteringResult.SkippedCooldown
        }

        val client = BodyApiClient.createIfConfigured(bodySettings)
            ?: return AttentionCenteringResult.SkippedBodyDisabled

        var gaze = usableGaze(now)
        if (gaze?.isCentered() == true) {
            lastCenteredAtMs = now
            return AttentionCenteringResult.AlreadyCentered
        }

        var status = fetchStatus(client)
        var moveCount = 0

        if (gaze == null) {
            Log.i(TAG, "No face in frame — scanning for user")
            val scanResult = scanForFace(client, status)
            moveCount += scanResult.moves
            status = scanResult.status ?: status
            gaze = scanResult.gaze
            if (scanResult.panSignFlipped) {
                panSign = -panSign
            }
        }

        if (gaze != null) {
            val loopResult = centerHorizontally(client, gaze, status)
            moveCount += loopResult.moves
            status = loopResult.status ?: status
            gaze = loopResult.gaze

            AttentionCenteringPlanner.planVerticalMove(gaze, status)?.let { tilt ->
                if (executeMove(client, tilt)) {
                    moveCount++
                    delay(AttentionCenteringPolicy.SETTLE_AFTER_MOVE_MS)
                }
            }
        }

        lastCenteredAtMs = System.currentTimeMillis()
        if (moveCount == 0) {
            return AttentionCenteringResult.AlreadyCentered
        }

        Log.i(TAG, "Attention centering done: $moveCount move(s), panSign=$panSign, faceFound=${gaze != null}")
        return AttentionCenteringResult.Centered(moveCount)
    }

    private suspend fun scanForFace(
        client: BodyApiClient,
        startStatus: BodyStatus?,
    ): ScanResult {
        var status = startStatus
        var moves = 0
        var sign = panSign
        var panSignFlipped = false
        var stepsWithoutMove = 0

        repeat(AttentionCenteringPolicy.MAX_SCAN_STEPS) {
            val currentPan = status?.joints?.get(BodyJoint.BASE_PAN.apiName)?.position ?: 0
            val move = AttentionCenteringPlanner.planScanPanMove(currentPan, sign)
            if (move == null) {
                if (!panSignFlipped) {
                    sign = -sign
                    panSignFlipped = true
                    stepsWithoutMove++
                    if (stepsWithoutMove >= 2) return@repeat
                    return@repeat
                }
                return@repeat
            }
            stepsWithoutMove = 0

            if (!executeMove(client, move)) {
                return ScanResult(null, status, moves, panSignFlipped)
            }
            moves++

            delay(AttentionCenteringPolicy.SETTLE_AFTER_MOVE_MS)
            val afterMoveMs = System.currentTimeMillis()
            val found = waitForUsableGazeAfter(afterMoveMs)
            if (found != null) {
                return ScanResult(found, fetchStatus(client) ?: status, moves, panSignFlipped)
            }

            status = fetchStatus(client) ?: status
        }

        return ScanResult(null, status, moves, panSignFlipped)
    }

    private suspend fun centerHorizontally(
        client: BodyApiClient,
        initialGaze: FaceGazeSnapshot,
        startStatus: BodyStatus?,
    ): LoopResult {
        var gaze = initialGaze
        var status = startStatus
        var moves = 0
        var flipUsed = false

        repeat(AttentionCenteringPolicy.MAX_LOOP_ITERATIONS) {
            if (gaze.isCentered()) return@repeat

            val errorBefore = AttentionCenteringPlanner.horizontalError(gaze)
            val move = AttentionCenteringPlanner.planHorizontalMove(gaze, status, panSign)
                ?: return@repeat

            if (!executeMove(client, move)) {
                return LoopResult(gaze, status, moves)
            }
            moves++

            delay(AttentionCenteringPolicy.SETTLE_AFTER_MOVE_MS)
            val afterMoveMs = System.currentTimeMillis()
            val newGaze = waitForUsableGazeAfter(afterMoveMs) ?: gaze
            val errorAfter = AttentionCenteringPlanner.horizontalError(newGaze)

            if (errorAfter > errorBefore + AttentionCenteringPolicy.WORSEN_EPSILON && !flipUsed) {
                panSign = -panSign
                flipUsed = true
                Log.i(TAG, "Horizontal error worsened ($errorBefore → $errorAfter), panSign=$panSign")

                val flipMove = AttentionCenteringPlanner.planHorizontalMove(newGaze, status, panSign)
                if (flipMove != null) {
                    if (!executeMove(client, flipMove)) {
                        return LoopResult(newGaze, status, moves)
                    }
                    moves++
                    delay(AttentionCenteringPolicy.SETTLE_AFTER_MOVE_MS)
                    gaze = waitForUsableGazeAfter(System.currentTimeMillis()) ?: newGaze
                } else {
                    gaze = newGaze
                }
            } else {
                gaze = newGaze
            }

            status = fetchStatus(client) ?: status
        }

        return LoopResult(gaze, status, moves)
    }

    private fun usableGaze(now: Long): FaceGazeSnapshot? {
        val gaze = FaceGazeStateStore.current() ?: return null
        if (!isFresh(gaze, now)) return null
        if (gaze.confidence < AttentionCenteringPolicy.MIN_CONFIDENCE) return null
        return gaze
    }

    private fun isFresh(gaze: FaceGazeSnapshot, now: Long): Boolean =
        now - gaze.capturedAt <= AttentionCenteringPolicy.MAX_GAZE_AGE_MS

    private suspend fun waitForUsableGazeAfter(notBeforeMs: Long): FaceGazeSnapshot? {
        val deadline = System.currentTimeMillis() + AttentionCenteringPolicy.GAZE_WAIT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val gaze = FaceGazeStateStore.current()
            if (gaze != null &&
                gaze.capturedAt >= notBeforeMs - 50L &&
                gaze.confidence >= AttentionCenteringPolicy.MIN_CONFIDENCE
            ) {
                return gaze
            }
            delay(AttentionCenteringPolicy.GAZE_POLL_MS)
        }
        val fallback = FaceGazeStateStore.current()
        return fallback?.takeIf { it.confidence >= AttentionCenteringPolicy.MIN_CONFIDENCE }
    }

    private suspend fun fetchStatus(client: BodyApiClient): BodyStatus? =
        when (val result = client.getStatus()) {
            is BodyApiResult.Success -> result.data
            is BodyApiResult.Error -> {
                Log.w(TAG, "Status unavailable: ${result.message}")
                null
            }
        }

    private suspend fun executeMove(client: BodyApiClient, move: BodyMove.Joint): Boolean {
        return when (
            val result = client.moveJoint(
                joint = move.joint,
                delta = move.delta,
                position = move.position,
                speed = move.speed,
            )
        ) {
            is BodyApiResult.Success -> true
            is BodyApiResult.Error -> {
                Log.w(TAG, "Move failed (${move.joint}): ${result.message}")
                false
            }
        }
    }

    private data class ScanResult(
        val gaze: FaceGazeSnapshot?,
        val status: BodyStatus?,
        val moves: Int,
        val panSignFlipped: Boolean,
    )

    private data class LoopResult(
        val gaze: FaceGazeSnapshot,
        val status: BodyStatus?,
        val moves: Int,
    )

    companion object {
        private const val TAG = "UserAttention"
    }
}

sealed interface AttentionCenteringResult {
    data object SkippedBodyDisabled : AttentionCenteringResult
    data object SkippedPresenceDisabled : AttentionCenteringResult
    data object SkippedCooldown : AttentionCenteringResult
    data object AlreadyCentered : AttentionCenteringResult
    data class Centered(val moveCount: Int) : AttentionCenteringResult
    data class PartialFailure(val message: String) : AttentionCenteringResult
}
