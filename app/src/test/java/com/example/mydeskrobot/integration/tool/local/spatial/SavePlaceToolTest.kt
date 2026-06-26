package com.example.mydeskrobot.integration.tool.local.spatial

import com.example.mydeskrobot.data.spatial.SpatialPlaceRepository
import com.example.mydeskrobot.data.spatial.db.FakeSpatialPlaceDao
import com.example.mydeskrobot.memory.unified.UnifiedMemoryRepository
import com.example.mydeskrobot.memory.unified.UnifiedMemoryWriter
import com.example.mydeskrobot.memory.unified.FakeMemoryDocumentDao
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SavePlaceToolTest {

    private lateinit var repository: SpatialPlaceRepository
    private lateinit var tool: SavePlaceTool

    @Before
    fun setUp() {
        repository = SpatialPlaceRepository.createForTest(FakeSpatialPlaceDao())
        val memoryWriter = UnifiedMemoryWriter(
            unifiedMemoryRepository = UnifiedMemoryRepository.createForTest(FakeMemoryDocumentDao()),
            activityLogRepository = com.example.mydeskrobot.data.activitylog.ActivityLogRepository.createForTest(
                com.example.mydeskrobot.data.activitylog.FakeActivityLogDao(),
            ),
            settingsRepository = null,
        )
        tool = SavePlaceTool(repository, memoryWriter)
    }

    @Test
    fun `execute saves new place`() = runBlocking {
        val result = tool.execute(
            ToolInvocation(
                name = "save_place",
                params = mapOf(
                    "label" to "studio",
                    "landmarks" to listOf("scrivania", "computer"),
                    "description" to "Il mio studio",
                    "room_type" to "study",
                ),
            ),
        )

        assertTrue(result is com.example.mydeskrobot.reasoning.model.ToolResult.Success)
        val places = repository.listActive()
        assertEquals(1, places.size)
        assertEquals("studio", places.first().label)
        assertTrue(places.first().landmarks.contains("scrivania"))
    }
}
