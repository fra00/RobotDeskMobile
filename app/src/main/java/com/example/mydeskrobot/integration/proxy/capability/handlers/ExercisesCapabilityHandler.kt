package com.example.mydeskrobot.integration.proxy.capability.handlers

import com.example.mydeskrobot.data.health.HealthConnectExerciseReader
import com.example.mydeskrobot.integration.proxy.capability.PhoneCapabilityException
import com.example.mydeskrobot.integration.proxy.capability.PhoneCapabilityHandler
import org.json.JSONArray
import org.json.JSONObject

class ExercisesCapabilityHandler(
    private val reader: HealthConnectExerciseReader,
) : PhoneCapabilityHandler {

    override suspend fun handle(params: Map<String, String>): JSONObject {
        val fromMs = params["fromMs"]?.toLongOrNull()
            ?: throw PhoneCapabilityException(400, "missing_fromMs")
        val toMs = params["toMs"]?.toLongOrNull()
            ?: throw PhoneCapabilityException(400, "missing_toMs")
        if (toMs < fromMs) {
            throw PhoneCapabilityException(400, "invalid_range")
        }
        if (!reader.isAvailable()) {
            return JSONObject()
                .put("available", false)
                .put("sessions", JSONArray())
        }
        if (!reader.hasExerciseReadPermission()) {
            return JSONObject()
                .put("available", true)
                .put("permission", false)
                .put("sessions", JSONArray())
        }
        val sessions = reader.readSessions(fromMs, toMs)
        val array = JSONArray()
        for (s in sessions) {
            array.put(
                JSONObject()
                    .put("metadataId", s.metadataId)
                    .put("exerciseType", s.exerciseType)
                    .put("startTimeMs", s.startTimeMs)
                    .put("endTimeMs", s.endTimeMs)
                    .put("title", s.title),
            )
        }
        return JSONObject()
            .put("available", true)
            .put("permission", true)
            .put("sessions", array)
    }
}
