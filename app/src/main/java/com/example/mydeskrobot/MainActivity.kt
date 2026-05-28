package com.example.mydeskrobot

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mydeskrobot.data.power.BatteryOptimizationHelper
import com.example.mydeskrobot.data.vision.CameraXVisionImageCapture
import com.example.mydeskrobot.data.vision.VisionCaptureActivityProvider
import com.example.mydeskrobot.presentation.conversation.ConversationUiEvent
import com.example.mydeskrobot.presentation.conversation.ConversationViewModel
import com.example.mydeskrobot.presentation.conversation.ConversationViewModelFactory
import com.example.mydeskrobot.ui.screen.RobotScreen
import com.example.mydeskrobot.ui.theme.MyDeskRobotTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ConversationViewModel by viewModels {
        ConversationViewModelFactory(
            context = applicationContext,
            visionImageCapture = CameraXVisionImageCapture(),
        )
    }

    private var pendingEnableHotword = false

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val micGranted = results[Manifest.permission.RECORD_AUDIO] == true
        val cameraGranted = results[Manifest.permission.CAMERA] == true
        val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            results[Manifest.permission.POST_NOTIFICATIONS] == true
        } else {
            true
        }

        if (pendingEnableHotword && micGranted && cameraGranted && notificationsGranted) {
            enableHotwordWithBatteryCheck()
        }
        pendingEnableHotword = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAlwaysOnDisplay()
        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val settingsUiState by viewModel.settingsUiState.collectAsStateWithLifecycle()

            MyDeskRobotTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RobotScreen(
                        uiState = uiState,
                        settingsUiState = settingsUiState,
                        onEvent = { event ->
                            when (event) {
                                ConversationUiEvent.OnToggleHotwordListening ->
                                    onToggleHotwordListening()
                                else -> viewModel.onEvent(event)
                            }
                        },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        VisionCaptureActivityProvider.setResumedActivity(this)
    }

    override fun onResume() {
        super.onResume()
        VisionCaptureActivityProvider.setResumedActivity(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        VisionCaptureActivityProvider.clearActivity(this)
        super.onDestroy()
    }

    private fun configureAlwaysOnDisplay() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
    }

    private fun onToggleHotwordListening() {
        if (viewModel.uiState.value.isHotwordListeningActive) {
            viewModel.disableHotwordListening()
            return
        }

        if (hasRequiredPermissions()) {
            enableHotwordWithBatteryCheck()
        } else {
            pendingEnableHotword = true
            requestPermissions.launch(requiredPermissions())
        }
    }

    private fun enableHotwordWithBatteryCheck() {
        viewModel.enableHotwordListening()
        requestBatteryOptimizationExemptionIfNeeded()
    }

    private fun requestBatteryOptimizationExemptionIfNeeded() {
        val intent = BatteryOptimizationHelper.createRequestIntent(this) ?: return
        startActivity(intent)
    }

    private fun hasRequiredPermissions(): Boolean {
        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        val camera = checkSelfPermission(Manifest.permission.CAMERA) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return mic && camera && notifications
    }

    private fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        } else {
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA,
            )
        }
}
