package com.example.mydeskrobot.integration.telephony

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.mydeskrobot.data.hotword.HotwordController
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps STT paused while a cellular call is active (OFFHOOK/RINGING → IDLE).
 * Requires [Manifest.permission.READ_PHONE_STATE] at runtime; degrades gracefully if denied.
 */
class CallSttPauseCoordinator(
    private val context: Context,
) {
    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val registered = AtomicBoolean(false)
    private var callWasActive = false

    private val telephonyCallback31 =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    handleCallState(state)
                }
            }
        } else {
            null
        }

    @Suppress("DEPRECATION")
    private val phoneStateListener = object : PhoneStateListener() {
        @Deprecated("Deprecated in Java")
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            handleCallState(state)
        }
    }

    private fun handleCallState(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING,
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                callWasActive = true
                HotwordController.beginPhoneCallHold()
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (callWasActive) {
                    callWasActive = false
                    HotwordController.endPhoneCallHold()
                    unregister()
                }
            }
        }
    }

    fun onDialLaunched() {
        if (!hasPhoneStatePermission()) {
            Log.i(
                TAG,
                "READ_PHONE_STATE not granted — STT resumes after TTS; use exit phrase during calls",
            )
            return
        }
        if (registered.compareAndSet(false, true)) {
            callWasActive = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyCallback31?.let { callback ->
                    telephonyManager.registerTelephonyCallback(context.mainExecutor, callback)
                }
            } else {
                @Suppress("DEPRECATION")
                telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
            }
            Log.d(TAG, "Watching call state for STT pause")
        }
    }

    fun unregister() {
        if (!registered.compareAndSet(true, false)) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyCallback31?.let { telephonyManager.unregisterTelephonyCallback(it) }
            } else {
                @Suppress("DEPRECATION")
                telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
            }
        }.onFailure { Log.w(TAG, "Failed to unregister call listener", it) }
    }

    private fun hasPhoneStatePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE,
        ) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "CallSttPause"

        @Volatile
        private var instance: CallSttPauseCoordinator? = null

        fun get(context: Context): CallSttPauseCoordinator {
            return instance ?: synchronized(this) {
                instance ?: CallSttPauseCoordinator(context.applicationContext).also { instance = it }
            }
        }
    }
}
