package com.example.mydeskrobot.memory.unified

data class MemoryDocumentFilters(
    val kinds: Set<MemoryDocumentKind>? = null,
    val categories: Set<String>? = null,
    val scheduledDayKey: String? = null,
    val dayKey: String? = null,
    val activeOnly: Boolean = true,
    val actor: String? = null,
)
