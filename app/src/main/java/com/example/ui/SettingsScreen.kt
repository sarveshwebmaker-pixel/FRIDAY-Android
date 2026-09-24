package com.example.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.actions.ActionResult
import com.example.actions.tools.ToolRegistry
import com.example.core.BatteryMode
import com.example.core.FridayCore
import com.example.core.FridayTelemetry
import com.example.service.FridayAccessibilityService
import kotlinx.coroutines.launch
import com.example.ui.theme.Amber400
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Cyan500
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Indigo400
import com.example.ui.theme.Rose400
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate50
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    fridayCore: FridayCore,
    onBack: () -> Unit,
    onLaunchSetup: () -> Unit
) {
    val context = LocalContext.current
    val settings by fridayCore.settingsRepo.settings.collectAsState()
    val state by fridayCore.state.collectAsState()

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    var customKeyInput by remember { mutableStateOf(settings.customApiKey) }
    val scope = rememberCoroutineScope()
    var testActionResult by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate950)
            .padding(top = statusBarPadding, bottom = navBarPadding)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("settings_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Slate200
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Settings",
                    color = Slate50,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // SECTION 1: GEMINI & AI CONFIGURATION
                item {
                    SettingsSection(title = "AI Engine", icon = Icons.Default.Psychology) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Primary Model", color = Slate50, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Gemini 3.5 Flash (Auto-structured tool actions)", color = Slate400, fontSize = 12.sp)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (fridayCore.geminiAiProvider.isCloudConnected) Cyan400.copy(alpha = 0.15f) else Slate800)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (fridayCore.geminiAiProvider.isCloudConnected) "CONNECTED" else "LOCAL EDGE",
                                        color = if (fridayCore.geminiAiProvider.isCloudConnected) Cyan400 else Slate400,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Text(
                                text = "API Key is automatically injected from AI Studio Secrets into BuildConfig at runtime. You can also supply a custom key override below.",
                                color = Slate400,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )

                            OutlinedTextField(
                                value = customKeyInput,
                                onValueChange = {
                                    customKeyInput = it
                                    fridayCore.settingsRepo.updateCustomApiKey(it)
                                },
                                label = { Text("Custom Gemini API Key (Optional)") },
                                placeholder = { Text("Enter API key") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Cyan400,
                                    unfocusedBorderColor = Slate700,
                                    focusedTextColor = Slate50,
                                    unfocusedTextColor = Slate200,
                                    focusedContainerColor = Slate900,
                                    unfocusedContainerColor = Slate900
                                )
                            )
                        }
                    }
                }

                // SECTION 2: BATTERY MODE
                item {
                    SettingsSection(title = "Battery Management", icon = Icons.Default.BatteryChargingFull) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "FRIDAY operates on a low-power standby principle: audio is never streamed to the cloud continuously.",
                                color = Slate400,
                                fontSize = 12.sp
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                BatteryModeButton(
                                    title = "Normal",
                                    isSelected = settings.batteryMode == BatteryMode.NORMAL,
                                    accent = Cyan400,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    fridayCore.setBatteryMode(BatteryMode.NORMAL)
                                }
                                BatteryModeButton(
                                    title = "Saver",
                                    isSelected = settings.batteryMode == BatteryMode.BATTERY_SAVER,
                                    accent = Emerald400,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    fridayCore.setBatteryMode(BatteryMode.BATTERY_SAVER)
                                }
                                BatteryModeButton(
                                    title = "Perf",
                                    isSelected = settings.batteryMode == BatteryMode.PERFORMANCE,
                                    accent = Amber400,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    fridayCore.setBatteryMode(BatteryMode.PERFORMANCE)
                                }
                            }
                        }
                    }
                }

                // SECTION 3: VOICE SETTINGS
                item {
                    SettingsSection(title = "Voice & Speech Synthesis", icon = Icons.Default.RecordVoiceOver) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Speech Rate
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Speech Rate", color = Slate50, fontSize = 14.sp)
                                    Text("${String.format("%.1f", settings.speechRate)}x", color = Cyan400, fontSize = 14.sp)
                                }
                                Slider(
                                    value = settings.speechRate,
                                    onValueChange = { fridayCore.settingsRepo.updateSpeechRate(it) },
                                    valueRange = 0.6f..1.6f,
                                    steps = 4,
                                    colors = SliderDefaults.colors(thumbColor = Cyan400, activeTrackColor = Cyan400)
                                )
                            }

                            // Speech Pitch
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Voice Pitch", color = Slate50, fontSize = 14.sp)
                                    Text("${String.format("%.1f", settings.speechPitch)}x", color = Cyan400, fontSize = 14.sp)
                                }
                                Slider(
                                    value = settings.speechPitch,
                                    onValueChange = { fridayCore.settingsRepo.updateSpeechPitch(it) },
                                    valueRange = 0.7f..1.4f,
                                    steps = 3,
                                    colors = SliderDefaults.colors(thumbColor = Cyan400, activeTrackColor = Cyan400)
                                )
                            }

                            // Conversation Inactivity Timeout
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Follow-up Window", color = Slate50, fontSize = 14.sp)
                                    Text("${settings.conversationTimeoutSeconds}s", color = Cyan400, fontSize = 14.sp)
                                }
                                Text(
                                    text = "Duration FRIDAY waits for follow-up commands before returning to standby.",
                                    color = Slate400,
                                    fontSize = 11.sp
                                )
                                Slider(
                                    value = settings.conversationTimeoutSeconds.toFloat(),
                                    onValueChange = { fridayCore.settingsRepo.updateConversationTimeout(it.toInt()) },
                                    valueRange = 4f..20f,
                                    steps = 7,
                                    colors = SliderDefaults.colors(thumbColor = Cyan400, activeTrackColor = Cyan400)
                                )
                            }

                            TextButton(
                                onClick = {
                                    fridayCore.onOrbTapped()
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = Cyan400)
                            ) {
                                Text("Test FRIDAY Voice")
                            }
                        }
                    }
                }

                // SECTION 4: PERMISSION & CAPABILITY HEALTH DASHBOARD
                item {
                    SettingsSection(title = "Permissions & System Health", icon = Icons.Default.Mic) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Capability status determines what FRIDAY can automate directly:",
                                color = Slate400,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )

                            // Microphone
                            PermissionHealthRow(
                                name = "Microphone (RECORD_AUDIO)",
                                isGranted = state.isMicrophoneGranted,
                                requirement = "Required for speech recognition and wake word",
                                onAction = { context.startActivity(fridayCore.permissionManager.createAppSettingsIntent()) }
                            )

                            // Accessibility Service
                            val isAccessActive = FridayAccessibilityService.isAvailable()
                            PermissionHealthRow(
                                name = "Accessibility Service",
                                isGranted = isAccessActive,
                                requirement = "Required for UI automation, scrolling, and clicking screens",
                                onAction = { context.startActivity(fridayCore.permissionManager.createAccessibilitySettingsIntent()) }
                            )

                            // Contacts
                            PermissionHealthRow(
                                name = "Contacts (READ_CONTACTS)",
                                isGranted = fridayCore.permissionManager.isContactsGranted(),
                                requirement = "Required for resolving names to numbers and WhatsApp",
                                onAction = { context.startActivity(fridayCore.permissionManager.createAppSettingsIntent()) }
                            )

                            // Phone Calling
                            PermissionHealthRow(
                                name = "Direct Calling (CALL_PHONE)",
                                isGranted = fridayCore.permissionManager.isCallPhoneGranted(),
                                requirement = "Required for initiating telephone calls without manual tap",
                                onAction = { context.startActivity(fridayCore.permissionManager.createAppSettingsIntent()) }
                            )

                            // SMS
                            PermissionHealthRow(
                                name = "SMS Messages (SEND_SMS)",
                                isGranted = fridayCore.permissionManager.isSmsGranted(),
                                requirement = "Required for sending text messages via assistant",
                                onAction = { context.startActivity(fridayCore.permissionManager.createAppSettingsIntent()) }
                            )

                            // Battery Optimization
                            PermissionHealthRow(
                                name = "Battery Optimization Exemption",
                                isGranted = fridayCore.permissionManager.isBatteryOptimizationIgnored(),
                                requirement = "Prevents OS from killing standby background listening",
                                onAction = { context.startActivity(fridayCore.permissionManager.createBatteryOptimizationIntent()) }
                            )

                            // Screen Overlay
                            PermissionHealthRow(
                                name = "Draw Over Other Apps",
                                isGranted = fridayCore.permissionManager.isOverlayGranted(),
                                requirement = "Enables floating assistant orb trigger",
                                onAction = { context.startActivity(fridayCore.permissionManager.createOverlaySettingsIntent()) }
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            SettingsToggleRow(
                                title = "Wake Word Detection (“FRIDAY”)",
                                subtitle = "Continuously listen for wake word when microphone is available",
                                isChecked = settings.wakeWordEnabled,
                                onCheckedChange = { fridayCore.settingsRepo.updateWakeWordEnabled(it) }
                            )
                        }
                    }
                }

                // SECTION 5: BACKGROUND SERVICE & STANDBY
                item {
                    SettingsSection(title = "Background Service", icon = Icons.Default.Sync) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SettingsToggleRow(
                                title = "Foreground Standby Service",
                                subtitle = "Keeps FRIDAY available with a persistent low-power status notification",
                                isChecked = state.isForegroundServiceRunning,
                                onCheckedChange = { fridayCore.toggleForegroundService(it) }
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Slate800)
                                    .clickable {
                                        val intent = fridayCore.permissionManager.createBatteryOptimizationIntent()
                                        context.startActivity(intent)
                                    }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Battery Optimization Exemption", color = Slate50, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text("Tap to configure exempt status to prevent background termination", color = Slate400, fontSize = 11.sp)
                                }
                                Text("CONFIGURE", color = Cyan400, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // SECTION 6: FLOATING BUBBLE & LOCK SCREEN
                item {
                    SettingsSection(title = "Screen Interactions", icon = Icons.Default.BubbleChart) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SettingsToggleRow(
                                title = "Floating Bubble Trigger",
                                subtitle = if (fridayCore.permissionManager.isOverlayGranted())
                                    "Draw floating assistant orb over other apps"
                                else
                                    "Requires 'Display over other apps' permission",
                                isChecked = settings.floatingBubbleEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled && !fridayCore.permissionManager.isOverlayGranted()) {
                                        val intent = fridayCore.permissionManager.createOverlaySettingsIntent()
                                        context.startActivity(intent)
                                    } else {
                                        fridayCore.settingsRepo.updateFloatingBubbleEnabled(enabled)
                                        if (enabled) {
                                            com.example.service.FridayFloatingOverlayService.start(context)
                                        } else {
                                            com.example.service.FridayFloatingOverlayService.stop(context)
                                        }
                                    }
                                }
                            )

                            SettingsToggleRow(
                                title = "Lock-screen Interaction",
                                subtitle = "Wake screen and show FRIDAY interface when locked",
                                isChecked = settings.lockScreenEnabled,
                                onCheckedChange = { fridayCore.settingsRepo.updateLockScreenEnabled(it) }
                            )
                        }
                    }
                }

                // SECTION 7: VOICE IDENTITY & SECURITY
                item {
                    SettingsSection(title = "Voice Recognition & Security", icon = Icons.Default.Security) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Action Security Levels:",
                                color = Slate50,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            ActionSecurityLevelItem(level = "SAFE", desc = "Executes automatically (flashlight, time, battery, apps)", color = Emerald400)
                            ActionSecurityLevelItem(level = "CONFIRM", desc = "Requires explicit tap confirmation (sensitive system actions)", color = Amber400)
                            ActionSecurityLevelItem(level = "BLOCKED", desc = "Forbidden by policy (code execution, shell scripts, security bypass)", color = Rose400)

                            Spacer(modifier = Modifier.height(6.dp))

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Speaker Confidence Threshold", color = Slate50, fontSize = 13.sp)
                                    Text("${(settings.voiceIdentityThreshold * 100).toInt()}%", color = Cyan400, fontSize = 13.sp)
                                }
                                Slider(
                                    value = settings.voiceIdentityThreshold,
                                    onValueChange = { fridayCore.settingsRepo.updateVoiceIdentityThreshold(it) },
                                    valueRange = 0.5f..0.95f,
                                    steps = 8,
                                    colors = SliderDefaults.colors(thumbColor = Cyan400, activeTrackColor = Cyan400)
                                )
                            }

                            ComingSoonBadge("Biometric Multi-Pass Voice Profile (Coming in next phase)")
                        }
                    }
                }

                // SECTION 8: NOTIFICATIONS
                item {
                    SettingsSection(title = "Notifications", icon = Icons.Default.Notifications) {
                        SettingsToggleRow(
                            title = "Status Notifications",
                            subtitle = "Show ongoing FRIDAY status and action execution alerts",
                            isChecked = settings.notificationEnabled,
                            onCheckedChange = { fridayCore.settingsRepo.updateNotificationEnabled(it) }
                        )
                    }
                }

                // SECTION 9: ACTION & TOOL TESTING
                item {
                    SettingsSection(title = "Action & Tool Diagnostics", icon = Icons.Default.PlayArrow) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Directly test native tool execution without voice speech:",
                                color = Slate400,
                                fontSize = 12.sp
                            )

                            if (testActionResult != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Slate800)
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = testActionResult ?: "",
                                        color = Cyan400,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ActionTestButton(
                                    label = "Flashlight",
                                    modifier = Modifier.weight(1f)
                                ) {
                                    scope.launch {
                                        val res = ToolRegistry.executeTool("TOGGLE_FLASHLIGHT", emptyMap(), context)
                                        testActionResult = "Flashlight: ${if (res is ActionResult.Success) "Success (${res.spokenDetail})" else "Failed"}"
                                    }
                                }

                                ActionTestButton(
                                    label = "Volume Up",
                                    modifier = Modifier.weight(1f)
                                ) {
                                    scope.launch {
                                        val res = ToolRegistry.executeTool("ADJUST_VOLUME", mapOf("direction" to "up"), context)
                                        testActionResult = "Volume: ${if (res is ActionResult.Success) "Success" else "Failed"}"
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ActionTestButton(
                                    label = "Battery Info",
                                    modifier = Modifier.weight(1f)
                                ) {
                                    scope.launch {
                                        val res = ToolRegistry.executeTool("GET_BATTERY_INFO", emptyMap(), context)
                                        testActionResult = "Battery: ${(res as? ActionResult.Success)?.spokenDetail ?: "Unavailable"}"
                                    }
                                }

                                ActionTestButton(
                                    label = "Date & Time",
                                    modifier = Modifier.weight(1f)
                                ) {
                                    scope.launch {
                                        val res = ToolRegistry.executeTool("GET_DATE_TIME", emptyMap(), context)
                                        testActionResult = "Time: ${(res as? ActionResult.Success)?.spokenDetail ?: "Unavailable"}"
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ActionTestButton(
                                    label = "Open YouTube",
                                    modifier = Modifier.weight(1f)
                                ) {
                                    scope.launch {
                                        val res = ToolRegistry.executeTool("OPEN_APP", mapOf("appName" to "YouTube"), context)
                                        testActionResult = "Open YouTube: ${if (res is ActionResult.Success) "Success" else "Failed"}"
                                    }
                                }

                                ActionTestButton(
                                    label = "UI Back",
                                    modifier = Modifier.weight(1f)
                                ) {
                                    scope.launch {
                                        val res = ToolRegistry.executeTool("UI_AUTOMATION", mapOf("operation" to "back"), context)
                                        testActionResult = "UI Back: ${if (res is ActionResult.Success) "Success" else "Failed (Check Accessibility)"}"
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ActionTestButton(
                                    label = "Read Notifications",
                                    modifier = Modifier.weight(1f)
                                ) {
                                    scope.launch {
                                        val res = ToolRegistry.executeTool("READ_NOTIFICATIONS", emptyMap(), context)
                                        testActionResult = "Notifications: ${(res as? ActionResult.Success)?.spokenDetail ?: "No unread / Need access"}"
                                    }
                                }

                                ActionTestButton(
                                    label = "Call Control",
                                    modifier = Modifier.weight(1f)
                                ) {
                                    scope.launch {
                                        val res = ToolRegistry.executeTool("CALL_CONTROLLER", mapOf("action" to "answer"), context)
                                        testActionResult = "Call Control: ${if (res is ActionResult.Success) "Success" else (res as? ActionResult.Failure)?.userMessage ?: "Tested"}"
                                    }
                                }
                            }
                        }
                    }
                }

                // SECTION 10: LIVE TELEMETRY & LATENCY
                item {
                    SettingsSection(title = "Execution Latency & Logs", icon = Icons.Default.Info) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val logs by FridayTelemetry.recentLogs.collectAsState()
                            if (logs.isEmpty()) {
                                Text(
                                    text = "No action executions recorded yet. Say or test a command to view real-time latency logs.",
                                    color = Slate400,
                                    fontSize = 12.sp
                                )
                            } else {
                                logs.takeLast(6).reversed().forEach { logItem ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Slate800)
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                            Text(
                                                text = logItem.stage,
                                                color = Cyan400,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = logItem.detail,
                                                color = Slate200,
                                                fontSize = 11.sp,
                                                lineHeight = 14.sp
                                            )
                                        }
                                        if (logItem.stepDurationMs > 0) {
                                            Text(
                                                text = "${logItem.stepDurationMs}ms",
                                                color = Emerald400,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // SECTION 11: SETUP & ABOUT
                item {
                    SettingsSection(title = "About FRIDAY", icon = Icons.Default.Info) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("FRIDAY Voice Assistant v1.3.5 (Voice-First Mobile AI)", color = Slate50, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("A voice-first personal AI assistant built exclusively for Android with real-time Gemini Live WebSocket architecture, phone control actions, screen vision, and notification management.", color = Slate400, fontSize = 12.sp, lineHeight = 16.sp)

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Slate800)
                                    .clickable { onLaunchSetup() }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.RestartAlt, contentDescription = null, tint = Cyan400, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Rerun First-Run Setup Wizard", color = Slate50, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Slate900)
            .border(1.dp, Slate800, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Cyan400,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                color = Slate50,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
        content()
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, color = Slate50, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = Slate400, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Slate950,
                checkedTrackColor = Cyan400,
                uncheckedThumbColor = Slate400,
                uncheckedTrackColor = Slate800
            )
        )
    }
}

@Composable
private fun BatteryModeButton(
    title: String,
    isSelected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) Slate800 else Slate900)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) accent else Slate700,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (isSelected) accent else Slate400,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun ActionSecurityLevelItem(level: String, desc: String, color: Color) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color.copy(alpha = 0.2f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(text = level, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = desc, color = Slate400, fontSize = 11.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun ComingSoonBadge(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Slate800)
            .padding(10.dp)
    ) {
        Text(
            text = label,
            color = Slate400,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun PermissionHealthRow(
    name: String,
    isGranted: Boolean,
    requirement: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Slate800)
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isGranted) Emerald400 else Rose400)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = name,
                    color = Slate50,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = requirement,
                color = Slate400,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isGranted) Emerald400.copy(alpha = 0.15f) else Cyan400.copy(alpha = 0.15f))
                .clickable(onClick = onAction)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = if (isGranted) "ACTIVE" else "FIX / GRANT",
                color = if (isGranted) Emerald400 else Cyan400,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ActionTestButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Slate800)
            .border(1.dp, Slate700, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Cyan400,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
