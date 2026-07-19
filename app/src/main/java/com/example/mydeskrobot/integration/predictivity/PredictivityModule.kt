package com.example.mydeskrobot.integration.predictivity

import android.content.Context
import com.example.mydeskrobot.data.activitylog.ActivityLogLifecycleCoordinator
import com.example.mydeskrobot.data.activitylog.ActivityLogRepository
import com.example.mydeskrobot.data.llm.LlmPromptLoader
import com.example.mydeskrobot.data.llm.LlmSettingsRepositoryImpl
import com.example.mydeskrobot.data.predictivity.HabitSlotRepository
import com.example.mydeskrobot.data.predictivity.PredictivityMiningRepository
import com.example.mydeskrobot.integration.llm.LlmClientFactory
import com.example.mydeskrobot.memory.unified.UnifiedMemoryFactory
import kotlinx.coroutines.runBlocking

object PredictivityModule {

    fun createLifecycleCoordinator(context: Context): ActivityLogLifecycleCoordinator {
        val appContext = context.applicationContext
        val activityLog = ActivityLogRepository.create(appContext)
        val miner = createMiner(appContext, activityLog)
        return ActivityLogLifecycleCoordinator(miner = miner, activityLog = activityLog)
    }

    fun createMiner(context: Context, activityLog: ActivityLogRepository): RecurringHabitSlotMiner {
        val appContext = context.applicationContext
        val settings = runBlocking { LlmSettingsRepositoryImpl.create(appContext).load() }
        val llmClient = LlmClientFactory.create(settings)
        val normalizer = HabitLabelNormalizer(
            llmClient = llmClient,
            normalizePrompt = LlmPromptLoader.loadHabitLabelNormalizePrompt(appContext),
        )
        return RecurringHabitSlotMiner(
            activityLogRepository = activityLog,
            habitSlotRepository = HabitSlotRepository(
                UnifiedMemoryFactory.createRepository(appContext),
            ),
            miningRepository = PredictivityMiningRepository(appContext),
            labelNormalizer = normalizer,
        )
    }

    fun createHabitSlotRepository(context: Context): HabitSlotRepository =
        HabitSlotRepository(UnifiedMemoryFactory.createRepository(context.applicationContext))
}
