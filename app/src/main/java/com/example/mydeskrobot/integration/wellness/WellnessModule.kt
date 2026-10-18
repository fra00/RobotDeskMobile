package com.example.mydeskrobot.integration.wellness

import android.content.Context
import com.example.mydeskrobot.data.activitylog.ActivityLogRepository
import com.example.mydeskrobot.data.wellness.WellnessSnapshotRepository
import com.example.mydeskrobot.memory.unified.UnifiedMemoryFactory

object WellnessModule {

    fun createContextBuilder(context: Context): WellnessContextBuilder {
        val appContext = context.applicationContext
        return WellnessContextBuilder(
            activityLogRepository = ActivityLogRepository.create(appContext),
            unifiedMemoryRepository = UnifiedMemoryFactory.createRepository(appContext),
        )
    }

    fun createWatcher(): WellnessWatcher = WellnessWatcher()

    fun createSnapshotRepository(context: Context): WellnessSnapshotRepository =
        WellnessSnapshotRepository(context.applicationContext)

    fun createNotifier(context: Context): WellnessNotifier =
        WellnessNotifier(context.applicationContext)
}
