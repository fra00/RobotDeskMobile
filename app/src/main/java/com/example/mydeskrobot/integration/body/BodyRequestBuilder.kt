package com.example.mydeskrobot.integration.body

object BodyRequestBuilder {

    fun moveJointJson(delta: Int? = null, position: Int? = null, speed: Int? = null): String {
        val parts = mutableListOf<String>()
        when {
            delta != null -> parts.add("\"delta\":${BodyJoint.clampDegrees(delta)}")
            position != null -> parts.add("\"position\":${BodyJoint.clampDegrees(position)}")
            else -> error("delta or position required")
        }
        speed?.let { parts.add("\"speed\":${it.coerceIn(0, 100)}") }
        return "{${parts.joinToString(",")}}"
    }

    fun moveJointsJson(jointPositions: Map<BodyJoint, Int>, speed: Int? = null): String {
        val parts = jointPositions.map { (joint, degrees) ->
            "\"${joint.apiName}\":${BodyJoint.clampDegrees(degrees)}"
        }.toMutableList()
        speed?.let { parts.add("\"speed\":${it.coerceIn(0, 100)}") }
        return "{${parts.joinToString(",")}}"
    }

    fun homeJson(speed: Int? = null): String {
        if (speed == null) return "{}"
        return "{\"speed\":${speed.coerceIn(0, 100)}}"
    }

    fun testJson(speed: Int? = null): String = homeJson(speed)
}
