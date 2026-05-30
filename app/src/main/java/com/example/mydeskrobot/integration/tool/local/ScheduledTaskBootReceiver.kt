package com.example.mydeskrobot.integration.tool.local

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.mydeskrobot.data.scheduled.ScheduledTaskRepository
import kotlinx.coroutines.runBlocking

/**
 * Re-schedules pending alarms after device reboot.
 */
class ScheduledTaskBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != ACTION_QUICKBOOT_POWERON
        ) {
            return
        }
        Log.i(TAG, "Boot completed — rescheduling pending tasks")
        val repository = ScheduledTaskRepository.create(context)
        runBlocking {
            repository.rescheduleAllPendingAlarms(context)
        }
    }

    companion object {
        private const val TAG = "ScheduledTaskBoot"
        private const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    }
}
