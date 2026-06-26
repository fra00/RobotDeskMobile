package com.example.mydeskrobot.integration.spatial

import com.example.mydeskrobot.data.spatial.SpatialPlaceRepository
import com.example.mydeskrobot.domain.spatial.SpatialContextSnapshot
import com.example.mydeskrobot.domain.spatial.SpatialFormatOptions
import com.example.mydeskrobot.domain.spatial.SpatialPromptFormatter
import com.example.mydeskrobot.reasoning.SpatialContextProvider
import com.example.mydeskrobot.reasoning.SpatialContextOptions

class SpatialPromptProviderImpl(
    private val placeRepository: SpatialPlaceRepository,
    private val snapshotProvider: suspend () -> SpatialContextSnapshot,
) : SpatialContextProvider {

    override suspend fun buildContextSection(options: SpatialContextOptions): String {
        val snapshot = snapshotProvider()
        val labels = placeRepository.labelSummaries()
        return SpatialPromptFormatter.format(
            context = snapshot,
            knownPlaceLabels = labels,
            options = SpatialFormatOptions(identityOnly = options.identityOnly),
        )
    }
}
