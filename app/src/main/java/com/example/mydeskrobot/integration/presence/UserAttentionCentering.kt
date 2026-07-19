package com.example.mydeskrobot.integration.presence

import android.util.Log
import com.example.mydeskrobot.data.body.BodySettings
import com.example.mydeskrobot.data.presence.DeskPresenceSettings
import com.example.mydeskrobot.data.presence.FaceGazeStateStore
import com.example.mydeskrobot.domain.presence.AttentionCenteringPolicy
import com.example.mydeskrobot.domain.presence.FaceGazeSnapshot
import com.example.mydeskrobot.integration.body.AttentionBodyClient
import com.example.mydeskrobot.integration.body.BodyApiClient
import com.example.mydeskrobot.integration.body.asAttentionBodyClient
import com.example.mydeskrobot.integration.body.BodyApiResult
import com.example.mydeskrobot.integration.body.BodyJoint
import com.example.mydeskrobot.integration.body.BodyMove
import com.example.mydeskrobot.integration.body.BodyStatus
import kotlinx.coroutines.delay

/**
 * Closed-loop centering toward the user before a conversational reply.
 * Runs on each user voice turn while the session is active.
 * Scan + return to base_pan 0 only when there was no face at turn start.
 */
class UserAttentionCentering(
    private val bodySettingsProvider: suspend () -> BodySettings,
    private val deskPresenceSettingsProvider: suspend () -> DeskPresenceSettings,
    private val bodyClientProvider: (BodySettings) -> AttentionBodyClient? = { settings ->
        BodyApiClient.createIfConfigured(settings)?.asAttentionBodyClient()
    },
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
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

        val now = nowMillis()
        val client = bodyClientProvider(bodySettings)
            ?: return AttentionCenteringResult.SkippedBodyDisabled

        val hadFaceAtStart = usableGaze(now) != null
        var gaze = usableGaze(now)
        if (gaze?.isCentered() == true) {
            return AttentionCenteringResult.AlreadyCentered
        }

        // Abort before scan/moves: unreachable ESP32 would otherwise stack connect timeouts
        // (~8s each) across every pan target and delay the LLM by tens of seconds.
        var status = fetchStatus(client)
        if (status == null) {
            Log.w(TAG, "Body unreachable — skip attention centering")
            return AttentionCenteringResult.SkippedBodyUnreachable
        }
        var moveCount = 0

        if (gaze != null) {
            val loopResult = centerHorizontally(client, gaze, status)
            moveCount += loopResult.moves
            status = loopResult.status ?: status
            gaze = loopResult.gaze
            if (loopResult.faceLost) {
                Log.i(TAG, "Gaze lost after ${loopResult.moves} move(s) — holding body position")
            }
        }

        if (gaze == null && !hadFaceAtStart) {
            Log.i(TAG, "No face at turn start — scanning from base_pan 0")
            val scanResult = scanForFace(client, status)
            moveCount += scanResult.moves
            status = scanResult.status ?: status
            gaze = scanResult.gaze

            if (gaze != null) {
                if (!gaze.isCentered()) {
                    val loopResult = centerHorizontally(client, gaze, status)
                    moveCount += loopResult.moves
                    status = loopResult.status ?: status
                    gaze = loopResult.gaze
                }
            } else {
                val neutralMoves = returnBasePanToNeutral(client, status)
                moveCount += neutralMoves.moves
                status = neutralMoves.status ?: status
            }
        }

        if (gaze != null) {
            AttentionCenteringPlanner.planVerticalMove(gaze, status)?.let { tilt ->
                if (executeMove(client, tilt)) {
                    moveCount++
                    pause(AttentionCenteringPolicy.SETTLE_AFTER_MOVE_MS)
                }
            }
        }

        if (moveCount == 0) {
            return AttentionCenteringResult.AlreadyCentered
        }

        Log.i(TAG, "Attention centering done: $moveCount move(s), panSign=$panSign, faceFound=${gaze != null}")
        return AttentionCenteringResult.Centered(moveCount)
    }

    /**
     * Silent pan scan for predictivity / wellness presence (no vertical centering, no TTS).
     */
    suspend fun locateUserNow(timeoutMs: Long = DEFAULT_LOCATE_TIMEOUT_MS): Boolean {
        val bodySettings = bodySettingsProvider()
        if (!bodySettings.isConfigured()) return false

        val client = bodyClientProvider(bodySettings) ?: return false
        if (fetchStatus(client) == null) return false

        val scanStart = nowMillis()
        val deadline = scanStart + timeoutMs

        usableGaze(nowMillis())?.let { return true }

        var status = fetchStatus(client)
        val homed = moveBasePan(client, status, AttentionCenteringPolicy.NEUTRAL_BASE_PAN)
        status = homed.status ?: status
        if (nowMillis() > deadline) return false

        waitForFaceAfterPanMove(homed.moveStartedMs ?: nowMillis(), deadlineMs = deadline)?.let { return true }

        for (targetPan in AttentionCenteringPolicy.expandScanTargets()) {
            if (nowMillis() > deadline) return false
            val moved = moveBasePan(client, status, targetPan)
            status = moved.status ?: status
            waitForFaceAfterPanMove(moved.moveStartedMs ?: nowMillis(), deadlineMs = deadline)?.let { return true }
        }

        return usableGaze(nowMillis()) != null
    }

    private suspend fun scanForFace(
        client: AttentionBodyClient,
        startStatus: BodyStatus?,
    ): ScanResult {
        var status = startStatus
        var moves = 0

        val homed = moveBasePan(client, status, AttentionCenteringPolicy.NEUTRAL_BASE_PAN)
        moves += homed.moves
        status = homed.status ?: status

        waitForFaceAfterPanMove(homed.moveStartedMs ?: nowMillis())?.let { found ->
            return ScanResult(found, status, moves)
        }

        for (targetPan in AttentionCenteringPolicy.expandScanTargets()) {
            val moved = moveBasePan(client, status, targetPan)
            moves += moved.moves
            status = moved.status ?: status

            waitForFaceAfterPanMove(moved.moveStartedMs ?: nowMillis())?.let { found ->
                return ScanResult(found, status, moves)
            }
        }

        Log.i(TAG, "Face scan exhausted — no user in frame")
        return ScanResult(null, status, moves)
    }

    private suspend fun returnBasePanToNeutral(
        client: AttentionBodyClient,
        startStatus: BodyStatus?,
    ): MoveResult {
        val currentPan = startStatus?.joints?.get(BodyJoint.BASE_PAN.apiName)?.position
            ?: AttentionCenteringPolicy.NEUTRAL_BASE_PAN
        if (currentPan == AttentionCenteringPolicy.NEUTRAL_BASE_PAN) {
            return MoveResult(startStatus, 0)
        }
        Log.i(TAG, "Returning base_pan to neutral ($currentPan → 0)")
        return moveBasePan(client, startStatus, AttentionCenteringPolicy.NEUTRAL_BASE_PAN)
    }

    private suspend fun moveBasePan(
        client: AttentionBodyClient,
        startStatus: BodyStatus?,
        targetPan: Int,
    ): MoveResult {
        var status = startStatus
        val currentPan = status?.joints?.get(BodyJoint.BASE_PAN.apiName)?.position
            ?: AttentionCenteringPolicy.NEUTRAL_BASE_PAN
        val move = AttentionCenteringPlanner.planBasePanToPosition(currentPan, targetPan)
            ?: return MoveResult(status, 0)

        if (!executeMove(client, move)) {
            return MoveResult(status, 0)
        }

        val moveStartedMs = nowMillis()
        pause(AttentionCenteringPolicy.SETTLE_AFTER_MOVE_MS)
        status = fetchStatus(client) ?: status
        return MoveResult(status, 1, moveStartedMs)
    }

    private suspend fun centerHorizontally(
        client: AttentionBodyClient,
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
                return LoopResult(gaze, status, moves, faceLost = false)
            }
            moves++

            val moveStartedMs = nowMillis()
            pause(AttentionCenteringPolicy.SETTLE_AFTER_MOVE_MS)
            var newGaze = waitForUsableGazeAfter(moveStartedMs)
            if (newGaze == null) {
                pause(AttentionCenteringPolicy.GAZE_RETRY_EXTRA_WAIT_MS)
                newGaze = waitForUsableGazeAfter(moveStartedMs)
            }
            if (newGaze == null && moves < AttentionCenteringPolicy.MIN_CENTERING_MOVES) {
                Log.w(TAG, "Gaze not ready after move $moves — waiting another frame cycle")
                pause(AttentionCenteringPolicy.SETTLE_AFTER_MOVE_MS)
                newGaze = waitForUsableGazeAfter(moveStartedMs)
            }
            if (newGaze == null) {
                if (moves < AttentionCenteringPolicy.MIN_CENTERING_MOVES) {
                    Log.w(TAG, "Gaze not ready after move $moves — continuing with last offset")
                    newGaze = gaze
                } else {
                    Log.i(TAG, "No fresh gaze after $moves move(s) — stopping tracking, keeping pose")
                    return LoopResult(gaze, status, moves, faceLost = true)
                }
            }

            val errorAfter = AttentionCenteringPlanner.horizontalError(newGaze)

            if (errorAfter > errorBefore + AttentionCenteringPolicy.WORSEN_EPSILON && !flipUsed) {
                panSign = -panSign
                flipUsed = true
                Log.i(TAG, "Horizontal error worsened ($errorBefore → $errorAfter), panSign=$panSign")

                val flipMove = AttentionCenteringPlanner.planHorizontalMove(newGaze, status, panSign)
                if (flipMove != null) {
                    if (!executeMove(client, flipMove)) {
                        return LoopResult(newGaze, status, moves, faceLost = false)
                    }
                    moves++
                    val flipMoveStartedMs = nowMillis()
                    pause(AttentionCenteringPolicy.SETTLE_AFTER_MOVE_MS)
                    val afterFlipGaze = waitForUsableGazeAfter(flipMoveStartedMs)
                        ?: waitForUsableGazeAfter(flipMoveStartedMs, extraWait = true)
                    if (afterFlipGaze == null) {
                        Log.i(TAG, "No fresh gaze after pan flip — keeping pose")
                        return LoopResult(newGaze, status, moves, faceLost = true)
                    }
                    gaze = afterFlipGaze
                } else {
                    gaze = newGaze
                }
            } else {
                gaze = newGaze
            }

            status = fetchStatus(client) ?: status
        }

        return LoopResult(gaze, status, moves, faceLost = false)
    }

    private fun usableGaze(now: Long): FaceGazeSnapshot? {
        val gaze = FaceGazeStateStore.current() ?: return null
        if (!isFresh(gaze, now)) return null
        if (gaze.confidence < AttentionCenteringPolicy.MIN_CONFIDENCE) return null
        return gaze
    }

    private fun isFresh(gaze: FaceGazeSnapshot, now: Long): Boolean =
        now - gaze.capturedAt <= AttentionCenteringPolicy.MAX_GAZE_AGE_MS

    /**
     * After a pan move: accept any face ML Kit saw since the move started (including during settle).
     * The debug overlay can show a face while scan still timed out when [notBeforeMs] was taken
     * after settle ended — that rejected frames captured mid-settle.
     */
    private suspend fun waitForFaceAfterPanMove(
        moveStartedMs: Long,
        deadlineMs: Long? = null,
    ): FaceGazeSnapshot? {
        usableGaze(nowMillis())?.takeIf { it.capturedAt >= moveStartedMs - 50L }?.let { return it }
        return waitForUsableGazeAfter(notBeforeMs = moveStartedMs, deadlineMs = deadlineMs)
    }

    private suspend fun waitForUsableGazeAfter(
        notBeforeMs: Long,
        extraWait: Boolean = false,
        deadlineMs: Long? = null,
    ): FaceGazeSnapshot? {
        if (extraWait) {
            pause(AttentionCenteringPolicy.GAZE_RETRY_EXTRA_WAIT_MS)
        }
        val deadline = deadlineMs ?: (nowMillis() + AttentionCenteringPolicy.GAZE_WAIT_TIMEOUT_MS)
        while (nowMillis() < deadline) {
            val gaze = FaceGazeStateStore.current()
            if (gaze != null &&
                gaze.capturedAt >= notBeforeMs - 50L &&
                gaze.confidence >= AttentionCenteringPolicy.MIN_CONFIDENCE
            ) {
                return gaze
            }
            pause(AttentionCenteringPolicy.GAZE_POLL_MS)
        }
        return null
    }

    private suspend fun fetchStatus(client: AttentionBodyClient): BodyStatus? =
        when (val result = client.getStatus()) {
            is BodyApiResult.Success -> result.data
            is BodyApiResult.Error -> {
                Log.w(TAG, "Status unavailable: ${result.message}")
                null
            }
        }

    private suspend fun executeMove(client: AttentionBodyClient, move: BodyMove.Joint): Boolean {
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
    )

    private data class LoopResult(
        val gaze: FaceGazeSnapshot?,
        val status: BodyStatus?,
        val moves: Int,
        val faceLost: Boolean,
    )

    private data class MoveResult(
        val status: BodyStatus?,
        val moves: Int,
        val moveStartedMs: Long? = null,
    )

    companion object {
        private const val TAG = "UserAttention"
        private const val DEFAULT_LOCATE_TIMEOUT_MS = 8_000L
    }
}

sealed interface AttentionCenteringResult {
    data object SkippedBodyDisabled : AttentionCenteringResult
    data object SkippedPresenceDisabled : AttentionCenteringResult
    /** Body configured but status probe failed — skip moves to avoid stacked HTTP timeouts. */
    data object SkippedBodyUnreachable : AttentionCenteringResult
    data object AlreadyCentered : AttentionCenteringResult
    data class Centered(val moveCount: Int) : AttentionCenteringResult
    data class PartialFailure(val message: String) : AttentionCenteringResult
}
