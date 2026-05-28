package com.example.mydeskrobot.data.vision

import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import java.lang.ref.WeakReference

/**
 * Activity corrente in primo piano per CameraX (il ViewModel può sopravvivere
 * alla ricreazione dell'Activity — non tenere un riferimento fisso nel costruttore).
 */
object VisionCaptureActivityProvider {

    private var resumedActivityRef: WeakReference<ComponentActivity>? = null

    fun setResumedActivity(activity: ComponentActivity) {
        resumedActivityRef = WeakReference(activity)
    }

    fun clearActivity(activity: ComponentActivity) {
        val current = resumedActivityRef?.get()
        if (current === activity) {
            resumedActivityRef = null
        }
    }

    fun getCaptureActivity(): ComponentActivity? {
        val activity = resumedActivityRef?.get() ?: return null
        if (activity.isFinishing || activity.isDestroyed) {
            resumedActivityRef = null
            return null
        }
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            return null
        }
        return activity
    }
}
