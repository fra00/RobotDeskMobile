package com.example.mydeskrobot.integration.input.heartbeat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Thin receiver: delegates heartbeat ticks to [HeartbeatOrchestrator].
 */
class HeartbeatAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_HEARTBEAT_TICK) return
        HeartbeatModule.createOrchestrator(context).onAlarmTick()
    }

    companion object {
        const val ACTION_HEARTBEAT_TICK = "com.example.mydeskrobot.action.HEARTBEAT_TICK"
    }
}
