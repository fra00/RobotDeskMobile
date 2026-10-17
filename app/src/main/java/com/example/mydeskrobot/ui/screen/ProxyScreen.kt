package com.example.mydeskrobot.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.mydeskrobot.R
import com.example.mydeskrobot.data.input.InputSettingsRepository
import com.example.mydeskrobot.data.proxy.DeviceModeRepository
import com.example.mydeskrobot.domain.proxy.PairedRobotEndpoint
import com.example.mydeskrobot.ui.components.ModeBotSettingsDialog
import com.example.mydeskrobot.ui.components.NotificationSettingsDialog
import kotlinx.coroutines.launch

/**
 * Lightweight UI when [com.example.mydeskrobot.domain.proxy.DeviceMode.PROXY] is active.
 */
@Composable
fun ProxyScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val modeRepo = remember { DeviceModeRepository(context.applicationContext) }
    val inputRepo = remember { InputSettingsRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var pairedRobot by remember { mutableStateOf<PairedRobotEndpoint?>(null) }
    var showModeBot by remember { mutableStateOf(false) }
    var showNotificationSettings by remember { mutableStateOf(false) }
    var notificationsEnabled by remember { mutableStateOf(false) }
    var accessGranted by remember { mutableStateOf(false) }
    var allowedPackages by remember { mutableStateOf(emptySet<String>()) }

    fun refresh() {
        scope.launch {
            pairedRobot = modeRepo.getPairedRobot()
            notificationsEnabled = inputRepo.isNotificationsEnabled()
            accessGranted = inputRepo.isNotificationAccessGranted()
            allowedPackages = inputRepo.getAllowedPackages()
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.proxy_screen_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.proxy_screen_subtitle),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = if (pairedRobot == null) {
                stringResource(R.string.proxy_screen_not_paired)
            } else {
                stringResource(
                    R.string.proxy_screen_paired,
                    pairedRobot!!.name,
                    pairedRobot!!.ip,
                )
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedButton(
            onClick = { showModeBot = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.mode_bot_title))
        }
    }

    if (showModeBot) {
        ModeBotSettingsDialog(
            onDismiss = {
                showModeBot = false
                refresh()
            },
            onOpenNotificationSettings = {
                showModeBot = false
                showNotificationSettings = true
                refresh()
            },
        )
    }

    if (showNotificationSettings) {
        NotificationSettingsDialog(
            isEnabled = notificationsEnabled,
            isNotificationAccessGranted = accessGranted,
            allowedPackages = allowedPackages,
            onEnabledChange = { enabled ->
                notificationsEnabled = enabled
            },
            onPackageToggle = { pkg ->
                allowedPackages = if (pkg in allowedPackages) {
                    allowedPackages - pkg
                } else {
                    allowedPackages + pkg
                }
            },
            onDismiss = {
                showNotificationSettings = false
                refresh()
            },
            onSave = {
                scope.launch {
                    inputRepo.setNotificationsEnabled(notificationsEnabled)
                    inputRepo.setAllowedPackages(allowedPackages)
                    showNotificationSettings = false
                    refresh()
                }
            },
        )
    }
}
