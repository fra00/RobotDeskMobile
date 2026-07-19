package com.example.mydeskrobot.domain.proactive

sealed interface GateDecision {
    data object Proceed : GateDecision
    data class Skip(val reason: String) : GateDecision
}
