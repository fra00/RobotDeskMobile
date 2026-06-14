package com.example.mydeskrobot.integration.spatial

import com.example.mydeskrobot.data.spatial.SpatialContextManager
import com.example.mydeskrobot.data.spatial.SpatialContextRepository
import com.example.mydeskrobot.data.spatial.SpatialPlaceRepository
import kotlinx.coroutines.CoroutineScope

/**
 * Holds spatial repositories and binds [SpatialContextManager] from the ViewModel scope.
 */
class SpatialRuntimeBindings(
    val placeRepository: SpatialPlaceRepository,
    val contextRepository: SpatialContextRepository,
    val contextProvider: DelegatingSpatialContextProvider,
) {
    @Volatile
    var manager: SpatialContextManager? = null
        private set

    fun bindManager(scope: CoroutineScope): SpatialContextManager {
        val bound = SpatialContextManager(placeRepository, contextRepository, scope)
        manager = bound
        contextProvider.placeRepository = placeRepository
        contextProvider.snapshotProvider = { bound.snapshot.value }
        return bound
    }

    fun requireManager(): SpatialContextManager =
        manager ?: error("SpatialContextManager not bound yet")
}
