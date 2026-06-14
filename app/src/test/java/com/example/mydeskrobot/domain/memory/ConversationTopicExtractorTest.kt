package com.example.mydeskrobot.domain.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationTopicExtractorTest {

    @Test
    fun extract_weatherPhrase_returnsMeteo() {
        assertEquals("meteo", ConversationTopicExtractor.extract("Che tempo fa oggi?"))
    }

    @Test
    fun extract_musicPhrase_returnsMusica() {
        assertEquals("musica", ConversationTopicExtractor.extract("Fammi ascoltare un po' di musica"))
    }

    @Test
    fun extract_significantToken_fromLongPhrase() {
        assertEquals("promemoria", ConversationTopicExtractor.extract("Ricordami il promemoria di domani"))
    }

    @Test
    fun extract_blank_returnsNull() {
        assertNull(ConversationTopicExtractor.extract("   "))
    }
}
