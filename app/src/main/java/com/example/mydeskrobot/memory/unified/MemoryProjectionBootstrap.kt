package com.example.mydeskrobot.memory.unified

import android.content.Context
import android.util.Log
import com.example.mydeskrobot.data.activitylog.ActivityLogRepository
import com.example.mydeskrobot.data.lists.ListItemRepository
import com.example.mydeskrobot.data.scheduled.ScheduledTaskRepository
import com.example.mydeskrobot.data.spatial.SpatialContextRepository
import com.example.mydeskrobot.data.spatial.SpatialPlaceRepository
import com.example.mydeskrobot.memory.MemorySettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Weekly reconciliation of operational stores → cognitive projections.
 */
object MemoryProjectionBootstrap {

    private const val TAG = "MemoryProjectionBootstrap"
    private val RECONCILE_INTERVAL_MS = TimeUnit.DAYS.toMillis(7)

    fun start(
        context: Context,
        scope: CoroutineScope,
        settingsRepository: MemorySettingsRepository,
        unifiedMemoryRepository: UnifiedMemoryRepository,
    ) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val reconciler = MemoryProjectionReconciler(
                    unifiedMemoryRepository = unifiedMemoryRepository,
                    scheduledTaskRepository = ScheduledTaskRepository.create(context.applicationContext),
                    listItemRepository = ListItemRepository.create(context.applicationContext),
                    activityLogRepository = ActivityLogRepository.create(context.applicationContext),
                    spatialPlaceRepository = runCatching {
                        SpatialPlaceRepository.create(context.applicationContext)
                    }.getOrNull(),
                    spatialContextRepository = SpatialContextRepository(context.applicationContext),
                )

                val episodeSync = reconciler.reconcileEpisodes()
                if (episodeSync.repaired > 0) {
                    Log.w(
                        TAG,
                        "Historical episode projection drift repaired=${episodeSync.repaired} skipped=${episodeSync.skipped}",
                    )
                }

                val lastAt = settingsRepository.getLastProjectionReconcileAtMs()
                val now = System.currentTimeMillis()
                if (lastAt > 0L && now - lastAt < RECONCILE_INTERVAL_MS) return@launch

                val result = reconciler.reconcileAll()
                settingsRepository.setLastProjectionReconcileAtMs(now)
                if (result.repaired > 0) {
                    Log.i(
                        TAG,
                        "Projection reconcile repaired=${result.repaired} skipped=${result.skipped}",
                    )
                }
            }.onFailure { error ->
                Log.w(TAG, "Projection reconcile failed", error)
            }
        }
    }
}
