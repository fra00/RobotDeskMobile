package com.example.mydeskrobot.integration.spatial

import com.example.mydeskrobot.domain.spatial.SpatialContextSnapshot
import com.example.mydeskrobot.domain.spatial.SpatialResolution
import com.example.mydeskrobot.memory.unified.FakeMemoryDocumentDao
import com.example.mydeskrobot.memory.unified.MemoryDocumentKind
import com.example.mydeskrobot.memory.unified.MemoryDocumentSource
import com.example.mydeskrobot.memory.unified.UnifiedMemoryRepository
import com.example.mydeskrobot.memory.unified.db.MemoryDocumentEntity
import com.example.mydeskrobot.reasoning.SpatialContextOptions
import com.example.mydeskrobot.reasoning.SpatialContextProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedSpatialContextProviderTest {

    @Test
    fun buildContextSection_uses_current_place_and_known_places() = runTest {
        val dao = FakeMemoryDocumentDao(
            listOf(
                MemoryDocumentEntity(
                    id = 1L,
                    value = "Stanza corrente: studio",
                    kind = MemoryDocumentKind.SPATIAL.name,
                    category = "study",
                    source = MemoryDocumentSource.TOOL.name,
                    confidence = 0.82f,
                    createdAt = 1L,
                    updatedAt = 1L,
                    externalRef = UnifiedMemoryRepository.SPATIAL_CURRENT_EXTERNAL_REF,
                ),
                MemoryDocumentEntity(
                    id = 2L,
                    value = "camera: letto, armadio",
                    kind = MemoryDocumentKind.SPATIAL.name,
                    category = "bedroom",
                    source = MemoryDocumentSource.TOOL.name,
                    confidence = 1f,
                    createdAt = 1L,
                    updatedAt = 1L,
                    externalRef = "spatial_place:2",
                ),
            ),
        )
        val provider = UnifiedSpatialContextProviderImpl(
            unifiedMemoryRepository = UnifiedMemoryRepository.createForTest(dao),
            snapshotProvider = { SpatialContextSnapshot() },
        )

        val context = provider.buildContextSection()

        assertTrue(context.contains("studio"))
        assertTrue(context.contains("camera"))
    }

    @Test
    fun composite_falls_back_to_legacy_when_unified_empty() = runTest {
        val unifiedProvider = UnifiedSpatialContextProviderImpl(
            unifiedMemoryRepository = UnifiedMemoryRepository.createForTest(FakeMemoryDocumentDao()),
            snapshotProvider = { SpatialContextSnapshot() },
        )
        val legacyProvider = object : SpatialContextProvider {
            override suspend fun buildContextSection(options: SpatialContextOptions): String =
                "DOVE SONO (autoritativo — posizione fisica desk robot):\n- Stanza corrente: legacy"
        }
        val composite = CompositeSpatialContextProvider(unifiedProvider, legacyProvider)

        val context = composite.buildContextSection()

        assertTrue(context.contains("legacy"))
    }
}
