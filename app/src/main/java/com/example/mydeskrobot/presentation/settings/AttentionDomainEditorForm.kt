package com.example.mydeskrobot.presentation.settings

import com.example.mydeskrobot.domain.heartbeat.AttentionDomainState
import com.example.mydeskrobot.domain.heartbeat.DomainTrigger

data class AttentionDomainEditorFormState(
    val editingId: String? = null,
    val displayName: String = "",
    val description: String = "",
    val enabled: Boolean = true,
)

fun AttentionDomainState.toEditorForm(): AttentionDomainEditorFormState =
    AttentionDomainEditorFormState(
        editingId = id,
        displayName = displayName,
        description = userPrompt.orEmpty(),
        enabled = enabled,
    )

fun AttentionDomainEditorFormState.toDomainState(
    resolvedId: String,
): AttentionDomainState =
    AttentionDomainState(
        id = resolvedId,
        displayName = displayName.trim(),
        enabled = enabled,
        isBuiltIn = false,
        userPrompt = description.trim(),
        trigger = DomainTrigger.Wellness,
        lastCheckedAt = null,
        requiresPresenceCheck = false,
        canUseCamera = false,
    )
