package com.example.mydeskrobot.domain.heartbeat

object AttentionDomainValidator {

    const val MIN_DESCRIPTION_LENGTH = 20

    fun validateCustom(
        displayName: String,
        description: String,
        existingIds: Set<String>,
        editingId: String?,
    ): String? {
        if (displayName.isBlank()) return "name_required"
        if (description.trim().length < MIN_DESCRIPTION_LENGTH) return "description_too_short"
        val id = editingId ?: slugId(displayName)
        if (editingId == null && id in existingIds) return "id_conflict"
        return null
    }

    fun slugId(displayName: String): String =
        "custom_" + displayName
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(32)
            .ifBlank { "domain_${System.currentTimeMillis()}" }

}
