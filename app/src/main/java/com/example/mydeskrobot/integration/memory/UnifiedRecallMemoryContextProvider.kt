package com.example.mydeskrobot.integration.memory

import com.example.mydeskrobot.integration.context.RecallContextFormatter
import com.example.mydeskrobot.memory.unified.UnifiedMemoryRepository
import com.example.mydeskrobot.reasoning.MemoryContextOptions
import com.example.mydeskrobot.reasoning.MemoryContextProvider
import com.example.mydeskrobot.reasoning.memory.MemoryRecallPlan
import com.example.mydeskrobot.reasoning.memory.MemoryRetrievalProfile

/**
 * Single recall path: LLM recall plan → unified index → one prompt block.
 * Spatial identity ("dove siamo") is injected via [com.example.mydeskrobot.reasoning.SpatialContextProvider].
 * Fresh photo turns exclude spatial from recall — vision overrides memory.
 */
class UnifiedRecallMemoryContextProvider(
    private val unifiedMemoryRepository: UnifiedMemoryRepository,
) : MemoryContextProvider {

    override suspend fun buildContextFor(
        userText: String,
        recallPlan: MemoryRecallPlan?,
        profileOverride: MemoryRetrievalProfile?,
        options: MemoryContextOptions,
    ): String {
        val plan = when {
            profileOverride == MemoryRetrievalProfile.VISION || options.freshVisionVerify ->
                MemoryRecallPlan.visionCatalog()
            recallPlan?.skipRecall == true -> return ""
            recallPlan != null -> recallPlan
            else -> return ""
        }
        val recalled = unifiedMemoryRepository.recallForQuestion(
            plan.toRequest(userText = userText, options = options),
        )

        if (recalled.isEmpty()) return ""

        unifiedMemoryRepository.markUsed(recalled)
        return RecallContextFormatter.formatRecallBlock(
            documents = recalled,
            focusDayKey = plan.focusDayKey,
            temporalScope = plan.temporalScope,
        )
    }
}
