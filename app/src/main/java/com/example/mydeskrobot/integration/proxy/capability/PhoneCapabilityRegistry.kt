package com.example.mydeskrobot.integration.proxy.capability

/**
 * Id → handler map on the PROXY.
 */
class PhoneCapabilityRegistry(
    handlers: Map<PhoneCapabilityId, PhoneCapabilityHandler> = emptyMap(),
) {
    private val map = handlers.toMutableMap()

    fun register(id: PhoneCapabilityId, handler: PhoneCapabilityHandler) {
        map[id] = handler
    }

    fun get(id: PhoneCapabilityId): PhoneCapabilityHandler? = map[id]

    fun getByWireId(wireId: String): PhoneCapabilityHandler? {
        val id = PhoneCapabilityId.fromWire(wireId) ?: return null
        return map[id]
    }

    fun knownIds(): Set<PhoneCapabilityId> = map.keys.toSet()
}
