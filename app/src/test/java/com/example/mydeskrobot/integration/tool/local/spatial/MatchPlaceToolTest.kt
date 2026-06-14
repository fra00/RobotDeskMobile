package com.example.mydeskrobot.integration.tool.local.spatial

import com.example.mydeskrobot.data.spatial.SpatialPlaceRepository
import com.example.mydeskrobot.data.spatial.db.FakeSpatialPlaceDao
import com.example.mydeskrobot.domain.spatial.MatchConfidenceBand
import com.example.mydeskrobot.domain.spatial.RoomType
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MatchPlaceToolTest {

    private lateinit var repository: SpatialPlaceRepository
    private lateinit var tool: MatchPlaceTool

    @Before
    fun setUp() = runBlocking {
        repository = SpatialPlaceRepository.createForTest(FakeSpatialPlaceDao())
        repository.savePlace(
            label = "studio",
            landmarks = listOf("scrivania", "computer", "monitor"),
            description = "",
            roomType = RoomType.STUDY,
        )
        repository.savePlace(
            label = "camera",
            landmarks = listOf("letto", "armadio", "comodino"),
            description = "",
            roomType = RoomType.BEDROOM,
        )
        tool = MatchPlaceTool(repository)
    }

    @Test
    fun `execute matches studio landmarks`() = runBlocking {
        val result = tool.execute(
            ToolInvocation(
                name = "match_place",
                params = mapOf("landmarks" to listOf("scrivania", "pc", "lampada")),
            ),
        )

        assertTrue(result is com.example.mydeskrobot.reasoning.model.ToolResult.Success)
        val data = (result as com.example.mydeskrobot.reasoning.model.ToolResult.Success).data
        val best = data["best_match"] as Map<*, *>
        assertEquals("studio", best["label"])
        assertEquals(MatchConfidenceBand.HIGH.name.lowercase(), data["band"])
    }
}
