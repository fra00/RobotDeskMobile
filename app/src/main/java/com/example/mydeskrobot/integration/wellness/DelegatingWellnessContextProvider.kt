package com.example.mydeskrobot.integration.wellness

import com.example.mydeskrobot.data.wellness.WellnessSnapshotRepository
import com.example.mydeskrobot.reasoning.WellnessContextProvider

/**
 * Lazy-bound provider so [ConversationViewModel] can attach the repository after construction.
 */
class DelegatingWellnessContextProvider : WellnessContextProvider {
    @Volatile
    var repository: WellnessSnapshotRepository? = null

    override suspend fun buildContextSection(): String {
        val snapshot = repository?.getLatest() ?: return ""
        return snapshot.toPromptBlock()
    }
}
