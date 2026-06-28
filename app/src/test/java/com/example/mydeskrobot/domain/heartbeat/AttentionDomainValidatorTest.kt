package com.example.mydeskrobot.domain.heartbeat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AttentionDomainValidatorTest {

    @Test
    fun validateCustom_rejectsBlankName() {
        val error = AttentionDomainValidator.validateCustom(
            displayName = "  ",
            description = "A".repeat(AttentionDomainValidator.MIN_DESCRIPTION_LENGTH),
            existingIds = emptySet(),
            editingId = null,
        )
        assertEquals("name_required", error)
    }

    @Test
    fun validateCustom_rejectsShortDescription() {
        val error = AttentionDomainValidator.validateCustom(
            displayName = "Test",
            description = "too short",
            existingIds = emptySet(),
            editingId = null,
        )
        assertEquals("description_too_short", error)
    }

    @Test
    fun validateCustom_rejectsDuplicateSlug() {
        val error = AttentionDomainValidator.validateCustom(
            displayName = "My Domain",
            description = "A".repeat(AttentionDomainValidator.MIN_DESCRIPTION_LENGTH),
            existingIds = setOf("custom_my_domain"),
            editingId = null,
        )
        assertEquals("id_conflict", error)
    }

    @Test
    fun validateCustom_allowsEditingSameId() {
        val error = AttentionDomainValidator.validateCustom(
            displayName = "My Domain",
            description = "A".repeat(AttentionDomainValidator.MIN_DESCRIPTION_LENGTH),
            existingIds = setOf("custom_my_domain"),
            editingId = "custom_my_domain",
        )
        assertNull(error)
    }

    @Test
    fun slugId_normalizesDisplayName() {
        assertEquals(
            "custom_pausa_caff",
            AttentionDomainValidator.slugId("Pausa Caffè!!!"),
        )
    }
}
