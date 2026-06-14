package com.example.mydeskrobot.data.light

import android.content.Context
import android.provider.Settings
import android.view.Window
import android.view.WindowManager

/**
 * Saves and restores window + system screen brightness for desk-lamp mode.
 * System brightness changes require [Settings.System.canWrite].
 */
class ScreenBrightnessHelper(
    private val context: Context,
) {
    private var savedWindowBrightness: Float = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    private var savedSystemBrightness: Int? = null

    fun applyMaxBrightness(window: Window) {
        val attrs = window.attributes
        if (savedWindowBrightness == WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
            savedWindowBrightness = attrs.screenBrightness
        }
        attrs.screenBrightness = 1f
        window.attributes = attrs
        raiseSystemBrightnessIfAllowed()
    }

    fun restoreBrightness(window: Window) {
        val attrs = window.attributes
        attrs.screenBrightness = savedWindowBrightness
        window.attributes = attrs
        savedWindowBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        restoreSystemBrightnessIfAllowed()
    }

    private fun raiseSystemBrightnessIfAllowed() {
        if (!Settings.System.canWrite(context)) return
        runCatching {
            val current = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
            )
            if (savedSystemBrightness == null) {
                savedSystemBrightness = current
            }
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                SYSTEM_BRIGHTNESS_MAX,
            )
        }
    }

    private fun restoreSystemBrightnessIfAllowed() {
        val previous = savedSystemBrightness ?: return
        if (!Settings.System.canWrite(context)) {
            savedSystemBrightness = null
            return
        }
        runCatching {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                previous.coerceIn(0, SYSTEM_BRIGHTNESS_MAX),
            )
        }
        savedSystemBrightness = null
    }

    companion object {
        private const val SYSTEM_BRIGHTNESS_MAX = 255
    }
}
