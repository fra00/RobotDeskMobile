package com.example.mydeskrobot

import android.Manifest
import android.content.Intent
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mydeskrobot.data.light.DeskLightController
import com.example.mydeskrobot.data.light.ScreenBrightnessHelper
import com.example.mydeskrobot.data.power.BatteryOptimizationHelper
import com.example.mydeskrobot.data.proxy.DeviceModeRepository
import com.example.mydeskrobot.data.vision.CameraXVisionImageCapture
import com.example.mydeskrobot.data.vision.VisionCaptureActivityProvider
import com.example.mydeskrobot.domain.proxy.DeviceMode
import com.example.mydeskrobot.integration.input.notification.proxy.NotificationProxyForegroundService
import com.example.mydeskrobot.integration.input.notification.proxy.NotificationProxyHttpServer
import com.example.mydeskrobot.integration.proxy.capability.PhoneCapabilityHttpServer
import com.example.mydeskrobot.integration.wellness.WellnessNotifier
import com.example.mydeskrobot.presentation.conversation.ConversationUiEvent
import com.example.mydeskrobot.presentation.conversation.ConversationViewModel
import com.example.mydeskrobot.presentation.conversation.ConversationViewModelFactory
import com.example.mydeskrobot.ui.screen.ProxyScreen
import com.example.mydeskrobot.ui.screen.RobotScreen
import com.example.mydeskrobot.ui.theme.MyDeskRobotTheme
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    private val screenBrightnessHelper by lazy { ScreenBrightnessHelper(applicationContext) }

    private val viewModel: ConversationViewModel by viewModels {
        ConversationViewModelFactory(
            context = applicationContext,
            visionImageCapture = CameraXVisionImageCapture(),
        )
    }

    private var deviceMode: DeviceMode = DeviceMode.ROBOT

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
            requestOptionalPermissionsIfNeeded()
            enableHotwordWithBatteryCheck()
        }
        pendingEnableHotword = false
    }

    private val requestOptionalPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        // Optional: denial does not block the app; resolve_phone_contact falls back to memories.
    }

    private val requestProxyNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val modeRepo = DeviceModeRepository(applicationContext)
        deviceMode = runBlocking {
            val mode = modeRepo.getDeviceMode()
            modeRepo.clearApplyModeRestart()
            mode
        }

        if (deviceMode == DeviceMode.PROXY) {
            configureProxyDisplay()
            enableEdgeToEdge()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestProxyNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            NotificationProxyForegroundService.start(this)
            PhoneCapabilityHttpServer.startIfNeeded(this)
            setContent {
                MyDeskRobotTheme(darkTheme = true, deskLampMode = false, dynamicColor = true) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        ProxyScreen(modifier = Modifier.padding(innerPadding))
                    }
                }
            }
            return
        }

        configureAlwaysOnDisplay()
        enableEdgeToEdge()
        NotificationProxyHttpServer.startIfNeeded(this)
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val settingsUiState by viewModel.settingsUiState.collectAsStateWithLifecycle()
            val deskLampMode by DeskLightController.isBrightMode.collectAsStateWithLifecycle()

            DisposableEffect(deskLampMode) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = deskLampMode
                insetsController.isAppearanceLightNavigationBars = deskLampMode
                onDispose { }
            }

            MyDeskRobotTheme(
                darkTheme = true,
                deskLampMode = deskLampMode,
                dynamicColor = !deskLampMode,
            ) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = if (deskLampMode) Color.White else MaterialTheme.colorScheme.background,
                ) { innerPadding ->
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
        requestOptionalPermissionsIfNeeded()
        handleWellnessDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWellnessDeepLink(intent)
    }

    private fun handleWellnessDeepLink(intent: Intent?) {
        if (deviceMode != DeviceMode.ROBOT) return
        if (intent?.getBooleanExtra(WellnessNotifier.EXTRA_OPEN_WELLNESS_SUMMARY, false) != true) {
            return
        }
        intent.removeExtra(WellnessNotifier.EXTRA_OPEN_WELLNESS_SUMMARY)
        viewModel.onEvent(ConversationUiEvent.OnOpenWellnessSummary)
    }

    override fun onStart() {
        super.onStart()
        if (deviceMode == DeviceMode.ROBOT) {
            VisionCaptureActivityProvider.setResumedActivity(this)
        }
    }

    override fun onResume() {
        super.onResume()
        if (deviceMode == DeviceMode.ROBOT) {
            VisionCaptureActivityProvider.setResumedActivity(this)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            DeskLightController.attach(window, screenBrightnessHelper)
            NotificationProxyHttpServer.startIfNeeded(this)
        } else if (deviceMode == DeviceMode.PROXY) {
            PhoneCapabilityHttpServer.startIfNeeded(this)
        }
    }

    override fun onPause() {
        if (deviceMode == DeviceMode.ROBOT) {
            DeskLightController.detach()
        }
        super.onPause()
    }

    override fun onDestroy() {
        if (deviceMode == DeviceMode.ROBOT) {
            if (DeskLightController.isBrightMode.value) {
                DeskLightController.attach(window, screenBrightnessHelper)
                DeskLightController.setBrightMode(false)
            }
            VisionCaptureActivityProvider.clearActivity(this)
            NotificationProxyHttpServer.stop()
        }
        super.onDestroy()
    }

    private fun configureProxyDisplay() {
        // Proxy does not need always-on desk display.
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
            requestOptionalPermissionsIfNeeded()
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

    private fun optionalPermissions(): Array<String> = arrayOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CONTACTS,
    )

    private fun requestOptionalPermissionsIfNeeded() {
        val missing = optionalPermissions().filter { permission ->
            checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestOptionalPermissions.launch(missing.toTypedArray())
        }
    }
}
