package com.example.mydeskrobot.data.hotword

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.mydeskrobot.service.HotwordListeningService

object HotwordServiceStarter {

    fun start(context: Context) {
        val intent = Intent(context, HotwordListeningService::class.java).apply {
            action = HotwordListeningService.ACTION_START
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        val intent = Intent(context, HotwordListeningService::class.java).apply {
            action = HotwordListeningService.ACTION_STOP
        }
        context.startService(intent)
    }
}
