package com.example.mydeskrobot.ui.components

import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.mydeskrobot.R
import com.example.mydeskrobot.data.power.BatteryOptimizationHelper
import com.example.mydeskrobot.data.proxy.DeviceModeRepository
import com.example.mydeskrobot.data.proxy.PairingQrBitmap
import com.example.mydeskrobot.data.proxy.PairingQrCodec
import com.example.mydeskrobot.data.proxy.ProcessRestartHelper
import com.example.mydeskrobot.data.proxy.WifiNetworkHelper
import com.example.mydeskrobot.domain.proxy.DeviceMode
import com.example.mydeskrobot.domain.proxy.PairedProxyDevice
import com.example.mydeskrobot.domain.proxy.PairedRobotEndpoint
import com.example.mydeskrobot.domain.proxy.PairingQrPayload
import com.example.mydeskrobot.integration.input.notification.proxy.NotificationProxyClient
import com.example.mydeskrobot.integration.input.notification.proxy.ProxyHttpResult
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

@Composable
fun ModeBotSettingsDialog(
    onDismiss: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { DeviceModeRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(DeviceMode.ROBOT) }
    var pairedProxies by remember { mutableStateOf<List<PairedProxyDevice>>(emptyList()) }
    var pairedRobot by remember { mutableStateOf<PairedRobotEndpoint?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var batteryIgnored by remember {
        mutableStateOf(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context))
    }
    var dualReminderEnabled by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            mode = repo.getDeviceMode()
            pairedProxies = repo.listPairedProxies()
            pairedRobot = repo.getPairedRobot()
            dualReminderEnabled = repo.isDualReminderEnabled()
            batteryIgnored = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
        }
    }

    LaunchedEffect(Unit) { refresh() }

    // While Mode Bot is open on the robot, poll so "In attesa…" becomes the proxy label after handshake.
    LaunchedEffect(mode) {
        if (mode != DeviceMode.ROBOT) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(2000)
            pairedProxies = repo.listPairedProxies()
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents ?: return@rememberLauncherForActivityResult
        val payload = PairingQrCodec.decode(contents)
        if (payload == null) {
            statusMessage = context.getString(R.string.mode_bot_qr_invalid)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            statusMessage = context.getString(R.string.mode_bot_pairing_in_progress)
            val client = NotificationProxyClient(context.applicationContext, repo)
            val handshake = client.confirmPairing(
                ip = payload.ip,
                port = payload.port,
                token = payload.token,
            )
            when (handshake) {
                ProxyHttpResult.Ok -> {
                    repo.setPairedRobot(
                        PairedRobotEndpoint(
                            robotId = payload.robotId,
                            ip = payload.ip,
                            port = payload.port,
                            token = payload.token,
                            name = payload.name,
                        ),
                    )
                    statusMessage = context.getString(R.string.mode_bot_pair_ok, payload.ip)
                    refresh()
                }
                is ProxyHttpResult.Failed -> {
                    repo.clearPairedRobot()
                    statusMessage = handshake.message
                    refresh()
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mode_bot_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.mode_bot_mode_label),
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == DeviceMode.ROBOT,
                        onClick = {
                            if (mode != DeviceMode.ROBOT) {
                                scope.launch {
                                    repo.setDeviceMode(DeviceMode.ROBOT)
                                    repo.setApplyModeRestart(true)
                                    ProcessRestartHelper.restartApp(context)
                                }
                            }
                        },
                        label = { Text(stringResource(R.string.mode_bot_robot)) },
                    )
                    FilterChip(
                        selected = mode == DeviceMode.PROXY,
                        onClick = {
                            if (mode != DeviceMode.PROXY) {
                                scope.launch {
                                    repo.setDeviceMode(DeviceMode.PROXY)
                                    repo.setApplyModeRestart(true)
                                    ProcessRestartHelper.restartApp(context)
                                }
                            }
                        },
                        label = { Text(stringResource(R.string.mode_bot_proxy)) },
                    )
                }

                statusMessage?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall)
                }

                when (mode) {
                    DeviceMode.ROBOT -> RobotPairingPanel(
                        pairedProxies = pairedProxies,
                        qrBitmap = qrBitmap,
                        dualReminderEnabled = dualReminderEnabled,
                        onDualReminderChange = { enabled ->
                            scope.launch {
                                repo.setDualReminderEnabled(enabled)
                                dualReminderEnabled = enabled
                            }
                        },
                        onGenerateQr = {
                            scope.launch {
                                if (!WifiNetworkHelper.isOnWifi(context)) {
                                    statusMessage = context.getString(R.string.mode_bot_wifi_required)
                                    return@launch
                                }
                                val ip = WifiNetworkHelper.wifiIpv4Address(context)
                                if (ip.isNullOrBlank()) {
                                    statusMessage = context.getString(R.string.mode_bot_no_ip)
                                    return@launch
                                }
                                val invite = repo.createPairingInvite()
                                val payload = PairingQrPayload(
                                    name = invite.name,
                                    ip = ip,
                                    port = PairingQrCodec.DEFAULT_PORT,
                                    token = invite.token,
                                    robotId = invite.robotId,
                                )
                                val json = PairingQrCodec.encode(payload)
                                qrBitmap = PairingQrBitmap.encode(json)
                                statusMessage = context.getString(R.string.mode_bot_qr_ready, ip)
                                refresh()
                            }
                        },
                        onRevoke = { token ->
                            scope.launch {
                                repo.revokePairedProxy(token)
                                refresh()
                            }
                        },
                    )
                    DeviceMode.PROXY -> ProxyPairingPanel(
                        pairedRobot = pairedRobot,
                        batteryIgnored = batteryIgnored,
                        onScanQr = {
                            val options = ScanOptions().apply {
                                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                setPrompt(context.getString(R.string.mode_bot_scan_prompt))
                                setBeepEnabled(false)
                            }
                            scanLauncher.launch(options)
                        },
                        onUnpair = {
                            scope.launch {
                                repo.clearPairedRobot()
                                refresh()
                            }
                        },
                        onOpenNotificationSettings = onOpenNotificationSettings,
                        onBatteryHelp = {
                            val intent = BatteryOptimizationHelper.createRequestIntent(context)
                                ?: Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            context.startActivity(intent)
                            batteryIgnored =
                                BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_close))
            }
        },
    )
}

@Composable
private fun ColumnScope.RobotPairingPanel(
    pairedProxies: List<PairedProxyDevice>,
    qrBitmap: Bitmap?,
    dualReminderEnabled: Boolean,
    onDualReminderChange: (Boolean) -> Unit,
    onGenerateQr: () -> Unit,
    onRevoke: (String) -> Unit,
) {
    Text(
        text = stringResource(R.string.mode_bot_robot_help),
        style = MaterialTheme.typography.bodySmall,
    )
    val hasProxy = pairedProxies.isNotEmpty()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = dualReminderEnabled && hasProxy,
            onCheckedChange = { checked ->
                if (hasProxy) onDualReminderChange(checked)
            },
            enabled = hasProxy,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.mode_bot_dual_reminder),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.mode_bot_dual_reminder_hint),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    OutlinedButton(onClick = onGenerateQr, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.mode_bot_show_qr))
    }
    qrBitmap?.let { bmp ->
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = stringResource(R.string.mode_bot_qr_cd),
            modifier = Modifier
                .size(220.dp)
                .padding(8.dp)
                .align(Alignment.CenterHorizontally),
        )
    }
    if (pairedProxies.isEmpty()) {
        Text(
            text = stringResource(R.string.mode_bot_no_proxies),
            style = MaterialTheme.typography.bodySmall,
        )
    } else {
        Text(
            text = stringResource(R.string.mode_bot_paired_list),
            style = MaterialTheme.typography.titleSmall,
        )
        pairedProxies.forEach { proxy ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = proxy.label.ifBlank { proxy.token.take(8) },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onRevoke(proxy.token) }) {
                    Text(stringResource(R.string.mode_bot_revoke))
                }
            }
        }
    }
}

@Composable
private fun ProxyPairingPanel(
    pairedRobot: PairedRobotEndpoint?,
    batteryIgnored: Boolean,
    onScanQr: () -> Unit,
    onUnpair: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onBatteryHelp: () -> Unit,
) {
    Text(
        text = stringResource(R.string.mode_bot_proxy_help),
        style = MaterialTheme.typography.bodySmall,
    )
    if (pairedRobot == null) {
        OutlinedButton(onClick = onScanQr, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.mode_bot_scan_qr))
        }
    } else {
        Text(
            text = stringResource(
                R.string.mode_bot_paired_robot,
                pairedRobot.name,
                pairedRobot.ip,
                pairedRobot.port,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = onScanQr, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.mode_bot_rescan_qr))
        }
        TextButton(onClick = onUnpair) {
            Text(stringResource(R.string.mode_bot_unpair))
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
    OutlinedButton(onClick = onOpenNotificationSettings, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.mode_bot_open_notification_settings))
    }
    Text(
        text = if (batteryIgnored) {
            stringResource(R.string.mode_bot_battery_ok)
        } else {
            stringResource(R.string.mode_bot_battery_warn)
        },
        style = MaterialTheme.typography.bodySmall,
    )
    if (!batteryIgnored) {
        OutlinedButton(onClick = onBatteryHelp, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.mode_bot_battery_button))
        }
    }
}
