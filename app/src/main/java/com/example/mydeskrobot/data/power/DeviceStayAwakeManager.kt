package com.example.mydeskrobot.data.power

import android.content.Context
import android.os.PowerManager

/**
 * Impedisce standby CPU e schermo spento mentre il robot è in ascolto.
 */
class DeviceStayAwakeManager(context: Context) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private var cpuWakeLock: PowerManager.WakeLock? = null
    private var screenWakeLock: PowerManager.WakeLock? = null

    @Suppress("DEPRECATION")
    fun acquire() {
        if (cpuWakeLock?.isHeld != true) {
            cpuWakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKE_LOCK_TAG_CPU,
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        }

        if (screenWakeLock?.isHeld != true) {
            screenWakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                WAKE_LOCK_TAG_SCREEN,
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    fun release() {
        releaseLock(cpuWakeLock)
        releaseLock(screenWakeLock)
        cpuWakeLock = null
        screenWakeLock = null
    }

    private fun releaseLock(lock: PowerManager.WakeLock?) {
        if (lock?.isHeld == true) {
            lock.release()
        }
    }

    companion object {
        private const val WAKE_LOCK_TAG_CPU = "MyDeskRobot::Cpu"
        private const val WAKE_LOCK_TAG_SCREEN = "MyDeskRobot::Screen"
    }
}
