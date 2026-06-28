package com.example.mydeskrobot.integration.presence

import com.example.mydeskrobot.data.body.BodySettings
import com.example.mydeskrobot.data.presence.DeskPresenceSettings
import com.example.mydeskrobot.data.presence.FaceGazeStateStore
import com.example.mydeskrobot.domain.presence.AttentionCenteringPolicy
import com.example.mydeskrobot.domain.presence.FaceGazeSnapshot
import com.example.mydeskrobot.integration.body.AttentionBodyClient
import com.example.mydeskrobot.integration.body.BodyApiResult
import com.example.mydeskrobot.integration.body.BodyJoint
import com.example.mydeskrobot.integration.body.BodyJointState
import com.example.mydeskrobot.integration.body.BodyOkResponse
import com.example.mydeskrobot.integration.body.BodyStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserAttentionCenteringTest {

    private var fakeNow = 1_000_000L

    @After
    fun tearDown() {
        FaceGazeStateStore.reset()
    }

    @Test
    fun alreadyCentered_skipsMoves() = runTest {
        val fake = FakeAttentionBodyClient()
        setGaze(FaceGazeSnapshot(0.05f, 0f, 0.8f, capturedAt = fakeNow))

        val result = centering(fake).tryCenterOnUser()

        assertEquals(AttentionCenteringResult.AlreadyCentered, result)
        assertTrue(fake.jointMoves.isEmpty())
    }

    @Test
    fun consecutiveTurns_bothRunCentering() = runTest {
        val fake = FakeAttentionBodyClient()
        setGaze(FaceGazeSnapshot(0.3f, 0f, 0.8f, capturedAt = fakeNow))
        fake.onAfterMove = { setGaze(FaceGazeSnapshot(0.06f, 0f, 0.8f, capturedAt = fakeNow)) }
        val centering = centering(fake)

        val first = centering.tryCenterOnUser()
        assertTrue(first is AttentionCenteringResult.Centered)

        setGaze(FaceGazeSnapshot(0.28f, 0f, 0.8f, capturedAt = fakeNow))
        val second = centering.tryCenterOnUser()
        assertTrue(second is AttentionCenteringResult.Centered)
    }

    @Test
    fun faceAtStart_doesNotCommandBasePanToZero() = runTest {
        val fake = FakeAttentionBodyClient(basePan = 10)
        setGaze(FaceGazeSnapshot(0.3f, 0f, 0.8f, capturedAt = fakeNow))
        fake.onAfterMove = {
            setGaze(FaceGazeSnapshot(0.08f, 0f, 0.8f, capturedAt = fakeNow))
        }

        centering(fake).tryCenterOnUser()

        assertFalse(
            fake.jointMoves.any {
                it.joint == BodyJoint.BASE_PAN && it.position == AttentionCenteringPolicy.NEUTRAL_BASE_PAN
            },
        )
    }

    @Test
    fun noFaceAtStart_failedScan_returnsToNeutral() = runTest {
        val fake = FakeAttentionBodyClient(basePan = 28)
        FaceGazeStateStore.reset()

        centering(fake).tryCenterOnUser()

        assertEquals(AttentionCenteringPolicy.NEUTRAL_BASE_PAN, fake.basePanPosition())
        assertTrue(
            fake.jointMoves.any {
                it.joint == BodyJoint.BASE_PAN && it.position == AttentionCenteringPolicy.NEUTRAL_BASE_PAN
            },
        )
    }

    @Test
    fun noFaceAtStart_scanExploresSymmetricPans() = runTest {
        val fake = FakeAttentionBodyClient()
        FaceGazeStateStore.reset()

        centering(fake).tryCenterOnUser()

        assertTrue(fake.jointMoves.any { it.joint == BodyJoint.BASE_PAN && it.position == 14 })
        assertTrue(fake.jointMoves.any { it.joint == BodyJoint.BASE_PAN && it.position == -14 })
    }

    private fun centering(fake: FakeAttentionBodyClient): UserAttentionCentering {
        fakeNow = 1_000_000L
        return UserAttentionCentering(
            bodySettingsProvider = { BodySettings(enabled = true, baseUrl = "http://test") },
            deskPresenceSettingsProvider = { DeskPresenceSettings(enabled = true) },
            bodyClientProvider = { fake },
            nowMillis = { fakeNow },
            pause = { ms -> fakeNow += ms },
        )
    }

    private fun setGaze(snapshot: FaceGazeSnapshot) {
        FaceGazeStateStore.update(snapshot.copy(capturedAt = fakeNow))
    }

    private data class RecordedMove(
        val joint: BodyJoint,
        val position: Int?,
    )

    private class FakeAttentionBodyClient(
        basePan: Int = 0,
    ) : AttentionBodyClient {
        private val joints = mutableMapOf(
            BodyJoint.BASE_PAN.apiName to BodyJointState(position = basePan),
            BodyJoint.DISPLAY_PAN.apiName to BodyJointState(position = 0),
            BodyJoint.HEAD_TILT.apiName to BodyJointState(position = 0),
        )

        val jointMoves = mutableListOf<RecordedMove>()
        var onMoveToBasePan: (Int) -> Unit = {}
        var onAfterMove: () -> Unit = {}

        fun basePanPosition(): Int = joints.getValue(BodyJoint.BASE_PAN.apiName).position

        override suspend fun getStatus(): BodyApiResult<BodyStatus> =
            BodyApiResult.Success(BodyStatus(joints = joints.toMap()))

        override suspend fun moveJoint(
            joint: BodyJoint,
            delta: Int?,
            position: Int?,
            speed: Int?,
        ): BodyApiResult<BodyOkResponse> {
            jointMoves.add(RecordedMove(joint, position))
            val state = joints.getValue(joint.apiName)
            val newPos = when {
                position != null -> position.coerceIn(-BodyJoint.LIMIT_DEG, BodyJoint.LIMIT_DEG)
                delta != null -> (state.position + delta).coerceIn(-BodyJoint.LIMIT_DEG, BodyJoint.LIMIT_DEG)
                else -> state.position
            }
            joints[joint.apiName] = state.copy(position = newPos, target = newPos)
            if (joint == BodyJoint.BASE_PAN && position != null) {
                onMoveToBasePan(position)
            }
            onAfterMove()
            return BodyApiResult.Success(BodyOkResponse(ok = true))
        }
    }
}
