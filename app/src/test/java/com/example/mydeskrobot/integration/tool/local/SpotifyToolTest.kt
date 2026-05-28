package com.example.mydeskrobot.integration.tool.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyToolTest {

    @Test
    fun `normalizeQuery returns default for blank`() {
        assertEquals("musica", SpotifyTool.normalizeQuery(""))
        assertEquals("musica", SpotifyTool.normalizeQuery("   "))
    }

    @Test
    fun `normalizeQuery trims input`() {
        assertEquals("Nirvana", SpotifyTool.normalizeQuery("  Nirvana  "))
    }

    @Test
    fun `buildSearchUri encodes artist name`() {
        val uri = SpotifyTool.buildSearchUri("Nirvana")
        assertEquals(
            "https://open.spotify.com/search/Nirvana",
            uri.toString(),
        )
    }

    @Test
    fun `buildSearchUri encodes spaces and accents`() {
        val uri = SpotifyTool.buildSearchUri("musica italiana")
        assertTrue(uri.toString().startsWith("https://open.spotify.com/search/"))
        assertTrue(uri.toString().contains("musica"))
    }

    @Test
    fun `buildSearchUri uses default for empty query`() {
        val uri = SpotifyTool.buildSearchUri("")
        assertEquals(
            "https://open.spotify.com/search/musica",
            uri.toString(),
        )
    }

    @Test
    fun `buildSpotifySchemeUri uses spotify scheme`() {
        val uri = SpotifyTool.buildSpotifySchemeUri("country music")
        assertTrue(uri.toString().startsWith("spotify:search:"))
    }
}
