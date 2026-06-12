package com.example.mydeskrobot.integration.body

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BodyJointTest {

    @Test
    fun `fromApiName resolves joint`() {
        assertEquals(BodyJoint.HEAD_TILT, BodyJoint.fromApiName("head_tilt"))
    }

    @Test
    fun `fromApiName unknown returns null`() {
        assertNull(BodyJoint.fromApiName("invalid"))
    }
}
