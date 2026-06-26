package com.example.mydeskrobot.memory.unified

import android.content.Context
import com.example.mydeskrobot.data.activitylog.ActivityLogRepository
import com.example.mydeskrobot.data.lists.ListItemRepository
import com.example.mydeskrobot.data.scheduled.ScheduledTaskRepository
import com.example.mydeskrobot.data.spatial.SpatialContextRepository
import com.example.mydeskrobot.data.spatial.SpatialPlaceRepository
import com.example.mydeskrobot.memory.MemorySettingsRepository
import com.example.mydeskrobot.memory.UserMemoryRepository

/**
 * Application-scoped factory for unified memory components.
 */
object UnifiedMemoryFactory {

    @Volatile
    private var repository: UnifiedMemoryRepository? = null

    @Volatile
    private var memoryWriter: UnifiedMemoryWriter? = null

    fun createRepository(context: Context): UnifiedMemoryRepository {
        repository?.let { return it }
        return synchronized(this) {
            repository ?: buildRepository(context.applicationContext).also { repository = it }
        }
    }

    fun createWriter(context: Context): UnifiedMemoryWriter {
        memoryWriter?.let { return it }
        val appContext = context.applicationContext
        return synchronized(this) {
            memoryWriter ?: UnifiedMemoryWriter(
                unifiedMemoryRepository = createRepository(context),
                activityLogRepository = ActivityLogRepository.create(appContext),
                settingsRepository = MemorySettingsRepository(appContext),
            ).also { memoryWriter = it }
        }
    }

    /** For unit tests only — clears cached instances. */
    internal fun resetForTests() {
        synchronized(this) {
            repository = null
            memoryWriter = null
        }
    }

    private fun buildRepository(appContext: Context): UnifiedMemoryRepository =
        UnifiedMemoryRepository.create(
            context = appContext,
            userMemoryRepository = UserMemoryRepository.create(appContext),
            scheduledTaskRepository = ScheduledTaskRepository.create(appContext),
            settingsRepository = MemorySettingsRepository(appContext),
            listItemRepository = ListItemRepository.create(appContext),
            activityLogRepository = ActivityLogRepository.create(appContext),
            spatialPlaceRepository = runCatching { SpatialPlaceRepository.create(appContext) }.getOrNull(),
            spatialContextRepository = SpatialContextRepository(appContext),
        )
}
