package com.example.mydeskrobot.integration.spatial

import com.example.mydeskrobot.data.spatial.SpatialPlaceRepository
import com.example.mydeskrobot.domain.spatial.SpatialContextSnapshot
import com.example.mydeskrobot.domain.spatial.SpatialPromptFormatter
import com.example.mydeskrobot.reasoning.SpatialContextProvider

class SpatialPromptProviderImpl(
    private val placeRepository: SpatialPlaceRepository,
    private val snapshotProvider: suspend () -> SpatialContextSnapshot,
) : SpatialContextProvider {

    override suspend fun buildContextSection(): String {
        val snapshot = snapshotProvider()
        val labels = placeRepository.labelSummaries()
        return SpatialPromptFormatter.format(snapshot, labels)
    }
}
