package com.example.mydeskrobot.integration.whatsapp

enum class WhatsAppChatType {
    CONTACT,
    GROUP,
}

enum class WhatsAppTargetSource {
    CONTACTS,
    MEMORY,
}

data class WhatsAppTargetMatch(
    val displayName: String,
    val sendId: String,
    val chatType: WhatsAppChatType,
    val source: WhatsAppTargetSource,
    val score: Float,
)

sealed class WhatsAppTargetResolveResult {
    data class Single(val match: WhatsAppTargetMatch) : WhatsAppTargetResolveResult()
    data class Multiple(val matches: List<WhatsAppTargetMatch>) : WhatsAppTargetResolveResult()
    data class NotFound(val query: String) : WhatsAppTargetResolveResult()
    data object PermissionDenied : WhatsAppTargetResolveResult()
    data object InvalidQuery : WhatsAppTargetResolveResult()
}
