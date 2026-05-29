package com.example.mydeskrobot.integration.context

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.mydeskrobot.data.context.RobotContextRepository
import kotlinx.coroutines.runBlocking

/**
 * Clears robot context when a timed profile expires.
 */
class RobotContextExpiryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CLEAR) return
        Log.i(TAG, "Robot context expiry fired — clearing to NORMAL")
        runBlocking {
            RobotContextRepository(context.applicationContext).clearToNormal()
        }
    }

    companion object {
        private const val TAG = "RobotContextExpiry"
        const val ACTION_CLEAR = "com.example.mydeskrobot.action.CLEAR_ROBOT_CONTEXT"
    }
}
