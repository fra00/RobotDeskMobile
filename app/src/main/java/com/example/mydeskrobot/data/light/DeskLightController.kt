package com.example.mydeskrobot.data.light

import android.view.Window
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

/**
 * Toggles bright desk-lamp mode: white UI theme + max screen brightness.
 */
object DeskLightController {

    private val _isBrightMode = MutableStateFlow(false)
    val isBrightMode: StateFlow<Boolean> = _isBrightMode.asStateFlow()

    private var windowRef: WeakReference<Window>? = null
    private var brightnessHelper: ScreenBrightnessHelper? = null

    fun attach(window: Window, brightnessHelper: ScreenBrightnessHelper) {
        windowRef = WeakReference(window)
        this.brightnessHelper = brightnessHelper
        applyToWindow()
    }

    fun detach() {
        windowRef = null
    }

    fun setBrightMode(enabled: Boolean): Boolean {
        if (_isBrightMode.value == enabled) return true
        _isBrightMode.value = enabled
        applyToWindow()
        return true
    }

    private fun applyToWindow() {
        val window = windowRef?.get() ?: return
        val helper = brightnessHelper ?: return
        if (_isBrightMode.value) {
            helper.applyMaxBrightness(window)
        } else {
            helper.restoreBrightness(window)
        }
    }
}
