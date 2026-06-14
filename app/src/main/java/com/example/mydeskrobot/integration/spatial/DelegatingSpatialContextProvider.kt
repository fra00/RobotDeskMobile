package com.example.mydeskrobot.integration.spatial

import com.example.mydeskrobot.data.spatial.SpatialPlaceRepository
import com.example.mydeskrobot.domain.spatial.SpatialContextSnapshot
import com.example.mydeskrobot.reasoning.SpatialContextProvider

/**
 * Wired from [ConversationViewModel] after spatial repositories are ready.
 */
class DelegatingSpatialContextProvider : SpatialContextProvider {

    var placeRepository: SpatialPlaceRepository? = null
    var snapshotProvider: (() -> SpatialContextSnapshot)? = null

    private val impl: SpatialContextProvider?
        get() {
            val repo = placeRepository ?: return null
            val snapshot = snapshotProvider ?: return null
            return SpatialPromptProviderImpl(repo) { snapshot() }
        }

    override suspend fun buildContextSection(): String =
        impl?.buildContextSection().orEmpty()
}
