package com.example.mydeskrobot.integration.proxy.capability

/**
 * Capability ids served by the PROXY HTTP registry (not LLM tool names).
 */
enum class PhoneCapabilityId(val wireId: String) {
    PING("ping"),
    EXERCISES("exercises"),
    CONTACTS("contacts"),
    LOCAL_NOTIFICATION("local_notification"),
    ;

    companion object {
        fun fromWire(id: String): PhoneCapabilityId? =
            entries.firstOrNull { it.wireId.equals(id.trim(), ignoreCase = true) }
    }
}
