package com.example.mydeskrobot.integration.spatial

import com.example.mydeskrobot.reasoning.SpatialContextProvider
import com.example.mydeskrobot.reasoning.SpatialContextOptions

/**
 * Unified spatial projections first; falls back to legacy place repository when empty.
 */
class CompositeSpatialContextProvider(
    private val unifiedProvider: UnifiedSpatialContextProviderImpl,
    private val legacyProvider: SpatialContextProvider,
) : SpatialContextProvider {

    override suspend fun buildContextSection(options: SpatialContextOptions): String {
        val unifiedContext = unifiedProvider.buildContextSection(options)
        if (unifiedContext.isNotBlank()) return unifiedContext
        return legacyProvider.buildContextSection(options)
    }
}
