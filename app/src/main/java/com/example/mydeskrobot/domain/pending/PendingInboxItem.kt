package com.example.mydeskrobot.domain.pending

enum class PendingInboxKind {
    REMINDER,
    NOTIFICATION,
}

data class PendingInboxItem(
    val id: String,
    val kind: PendingInboxKind,
    val timeMillis: Long,
    val title: String,
    val body: String,
)
