package com.example.mydeskrobot.integration.spatial

import com.example.mydeskrobot.data.spatial.SpatialPlaceRepository
import com.example.mydeskrobot.domain.spatial.SpatialContextSnapshot
import com.example.mydeskrobot.memory.unified.UnifiedMemoryRepository
import com.example.mydeskrobot.reasoning.SpatialContextProvider
import com.example.mydeskrobot.reasoning.SpatialContextOptions

/**
 * Wired from [ConversationViewModel] after spatial repositories are ready.
 */
class DelegatingSpatialContextProvider : SpatialContextProvider {

    var placeRepository: SpatialPlaceRepository? = null
    var snapshotProvider: (() -> SpatialContextSnapshot)? = null
    var unifiedMemoryRepository: UnifiedMemoryRepository? = null

    private val legacyProvider: SpatialContextProvider?
        get() {
            val repo = placeRepository ?: return null
            val snapshot = snapshotProvider ?: return null
            return SpatialPromptProviderImpl(repo) { snapshot() }
        }

    override suspend fun buildContextSection(options: SpatialContextOptions): String {
        val legacy = legacyProvider ?: return ""
        val unifiedRepo = unifiedMemoryRepository
        val snapshot = snapshotProvider
        if (unifiedRepo != null && snapshot != null) {
            return CompositeSpatialContextProvider(
                unifiedProvider = UnifiedSpatialContextProviderImpl(unifiedRepo) { snapshot() },
                legacyProvider = legacy,
            ).buildContextSection(options)
        }
        return legacy.buildContextSection(options)
    }
}
