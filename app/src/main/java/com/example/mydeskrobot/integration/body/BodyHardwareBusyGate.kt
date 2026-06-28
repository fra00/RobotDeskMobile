package com.example.mydeskrobot.integration.body

/**
 * Shared flag while LLM tool chain executes body hardware tools.
 */
class BodyHardwareBusyGate {
    @Volatile
    var isBusy: Boolean = false
}
