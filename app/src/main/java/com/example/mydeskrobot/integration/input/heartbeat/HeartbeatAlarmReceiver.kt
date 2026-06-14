package com.example.mydeskrobot.integration.input.heartbeat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.mydeskrobot.data.activitylog.ActivityLogRepository
import com.example.mydeskrobot.data.awareness.UserAwarenessRepository
import com.example.mydeskrobot.data.heartbeat.HeartbeatSettings
import com.example.mydeskrobot.data.heartbeat.HeartbeatSettingsRepository
import com.example.mydeskrobot.data.mood.MoodRepository
import com.example.mydeskrobot.data.scheduled.ScheduledTaskRepository
import com.example.mydeskrobot.data.spatial.SpatialContextRepository
import com.example.mydeskrobot.data.spatial.SpatialPlaceRepository
import com.example.mydeskrobot.data.workingmemory.WorkingMemoryRepository
import com.example.mydeskrobot.domain.input.SystemInputDispatcher
import com.example.mydeskrobot.domain.input.SystemInputEvent
import com.example.mydeskrobot.memory.UserMemoryRepository
import kotlinx.coroutines.runBlocking
import java.util.Calendar

/**
 * Handles heartbeat alarm ticks.
 * Checks settings, builds context payload, and dispatches to the input bus.
 */
class HeartbeatAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_HEARTBEAT_TICK) return

        Log.d(TAG, "Heartbeat tick received")

        val settingsRepo = HeartbeatSettingsRepository(context)
        val settings = runBlocking { settingsRepo.load() }

        if (!settings.enabled) {
            Log.d(TAG, "Heartbeat disabled, skipping")
            return
        }

        if (!isWithinActiveWindow(settings)) {
            Log.d(TAG, "Outside active window (${settings.startHour}-${settings.endHour}), skipping")
            rescheduleNext(context, settings.intervalMinutes)
            return
        }

        val scheduledTaskRepo = ScheduledTaskRepository.create(context)
        val memoryRepo = UserMemoryRepository.create(context)
        val moodRepo = MoodRepository(context)
        val workingMemoryRepo = WorkingMemoryRepository(context)
        val userAwarenessRepo = UserAwarenessRepository(context)
        val activityLogRepo = ActivityLogRepository.create(context)
        val spatialContextRepo = SpatialContextRepository(context)
        val spatialPlaceRepo = SpatialPlaceRepository.create(context)

        runBlocking {
            memoryRepo.pruneExpired()
            activityLogRepo.pruneExpired()
        }

        val contextBuilder = HeartbeatContextBuilder(
            scheduledTaskRepository = scheduledTaskRepo,
            memoryRepository = memoryRepo,
            lastInteractionProvider = { settings.lastInteractionMillis },
            currentMoodProvider = { moodRepo.load() },
            workingMemoryProvider = { workingMemoryRepo.load() },
            userAwarenessProvider = { userAwarenessRepo.load() },
            activityLogRepository = activityLogRepo,
            spatialSnapshotProvider = { spatialContextRepo.load() },
            knownPlacesProvider = { spatialPlaceRepo.labelSummaries() },
        )

        val heartbeat = runBlocking { contextBuilder.build() }
        val inputSource = HeartbeatInputSource()
        val envelope = inputSource.toEnvelope(heartbeat)

        Log.i(TAG, "Dispatching heartbeat to system input bus")
        SystemInputDispatcher.emit(SystemInputEvent.InputReceived(envelope))

        rescheduleNext(context, settings.intervalMinutes)
    }

    private fun isWithinActiveWindow(settings: HeartbeatSettings): Boolean {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        return if (settings.startHour <= settings.endHour) {
            currentHour in settings.startHour until settings.endHour
        } else {
            currentHour >= settings.startHour || currentHour < settings.endHour
        }
    }

    private fun rescheduleNext(context: Context, intervalMinutes: Int) {
        HeartbeatScheduler.rescheduleNext(context, intervalMinutes)
    }

    companion object {
        private const val TAG = "HeartbeatAlarmRx"
        const val ACTION_HEARTBEAT_TICK = "com.example.mydeskrobot.action.HEARTBEAT_TICK"
    }
}
