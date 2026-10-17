package com.example.mydeskrobot.integration.proxy.capability.handlers

import com.example.mydeskrobot.integration.proxy.capability.PhoneCapabilityException
import com.example.mydeskrobot.integration.proxy.capability.PhoneCapabilityHandler
import com.example.mydeskrobot.integration.telephony.ContactPhoneLookup
import org.json.JSONArray
import org.json.JSONObject

class ContactsCapabilityHandler(
    private val contactsResolver: ContactPhoneLookup,
) : PhoneCapabilityHandler {

    override suspend fun handle(params: Map<String, String>): JSONObject {
        val query = params["q"]?.trim().orEmpty()
        if (query.isEmpty()) {
            throw PhoneCapabilityException(400, "missing_q")
        }
        if (!contactsResolver.hasPermission()) {
            return JSONObject()
                .put("permission", false)
                .put("matches", JSONArray())
        }
        val matches = contactsResolver.search(query)
        val array = JSONArray()
        for (m in matches) {
            array.put(
                JSONObject()
                    .put("displayName", m.displayName)
                    .put("number", m.number)
                    .put("score", m.score.toDouble()),
            )
        }
        return JSONObject()
            .put("permission", true)
            .put("matches", array)
    }
}
