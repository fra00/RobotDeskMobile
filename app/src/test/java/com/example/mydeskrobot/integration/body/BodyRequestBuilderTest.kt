package com.example.mydeskrobot.integration.body

import org.junit.Assert.assertTrue
import org.junit.Test

class BodyRequestBuilderTest {

    @Test
    fun `move joint uses delta`() {
        val json = BodyRequestBuilder.moveJointJson(delta = 15, speed = 50)
        assertTrue(json.contains("\"delta\":15"))
        assertTrue(json.contains("\"speed\":50"))
    }

    @Test
    fun `move joint clamps position`() {
        val json = BodyRequestBuilder.moveJointJson(position = 80)
        assertTrue(json.contains("\"position\":45"))
    }

    @Test
    fun `move joints includes joint names`() {
        val json = BodyRequestBuilder.moveJointsJson(
            mapOf(
                BodyJoint.BASE_PAN to 10,
                BodyJoint.HEAD_TILT to -5,
            ),
            speed = 40,
        )
        assertTrue(json.contains("\"base_pan\":10"))
        assertTrue(json.contains("\"head_tilt\":-5"))
    }
}
