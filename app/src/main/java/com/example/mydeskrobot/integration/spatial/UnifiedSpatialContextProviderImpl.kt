package com.example.mydeskrobot.integration.spatial

import com.example.mydeskrobot.domain.spatial.SpatialContextSnapshot
import com.example.mydeskrobot.domain.spatial.SpatialFormatOptions
import com.example.mydeskrobot.domain.spatial.SpatialPromptFormatter
import com.example.mydeskrobot.domain.spatial.SpatialResolution
import com.example.mydeskrobot.integration.context.DayContextFormatter
import com.example.mydeskrobot.memory.unified.UnifiedMemoryRepository
import com.example.mydeskrobot.reasoning.SpatialContextProvider
import com.example.mydeskrobot.reasoning.SpatialContextOptions

class UnifiedSpatialContextProviderImpl(
    private val unifiedMemoryRepository: UnifiedMemoryRepository,
    private val snapshotProvider: suspend () -> SpatialContextSnapshot,
) : SpatialContextProvider {

    override suspend fun buildContextSection(options: SpatialContextOptions): String {
        val snapshot = snapshotProvider()
        val currentDoc = unifiedMemoryRepository.getCurrentPlaceDocument()
        val knownPlaces = unifiedMemoryRepository.listSpatialPlaceDocuments()
            .map { DayContextFormatter.parseSpatialPlaceLabel(it.value) }

        if (currentDoc == null && knownPlaces.isEmpty()) return ""

        val enrichedSnapshot = when {
            snapshot.currentPlaceLabel != null -> snapshot
            currentDoc != null -> snapshot.copy(
                currentPlaceLabel = DayContextFormatter.parseCurrentPlaceLabel(currentDoc.value),
                confidence = currentDoc.confidence,
                resolution = if (currentDoc.confidence > 0f) {
                    SpatialResolution.AUTONOMOUS
                } else {
                    SpatialResolution.UNKNOWN
                },
            )
            else -> snapshot
        }

        return SpatialPromptFormatter.format(
            context = enrichedSnapshot,
            knownPlaceLabels = knownPlaces,
            options = SpatialFormatOptions(identityOnly = options.identityOnly),
        )
    }
}
