package com.example.mydeskrobot.integration.proxy.capability

/**
 * Handles one capability on the PROXY device.
 */
fun interface PhoneCapabilityHandler {
    /**
     * @param params query/body parameters (string map)
     * @return JSON object body (without wrapping); throw [PhoneCapabilityException] for errors
     */
    suspend fun handle(params: Map<String, String>): org.json.JSONObject
}

class PhoneCapabilityException(
    val httpStatus: Int,
    val errorCode: String,
    message: String = errorCode,
) : Exception(message)
