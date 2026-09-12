package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.BatteryMode
import com.example.core.FridayCore
import com.example.core.OrbState
import com.example.ui.theme.Amber400
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Cyan500
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Indigo400
import com.example.ui.theme.Rose400
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate50
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950

@Composable
fun MainScreen(
    fridayCore: FridayCore,
    onNavigateToSettings: () -> Unit,
    onNavigateToSetup: () -> Unit
) {
    val state by fridayCore.state.collectAsState()
    var showBatterySelector by remember { mutableStateOf(false) }

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Slate950, Slate900, Slate950)
                )
            )
            .padding(top = statusBarPadding, bottom = navBarPadding)
    ) {
        // TOP BAR: Brand, Battery Mode Pill, Settings Icon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // App Branding
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (state.isWakeWordActive && state.isMicrophoneGranted) Cyan400 else Slate600)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "FRIDAY",
                    color = Slate50,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }

            // Center: Battery Mode Badge (Clickable)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Slate800)
                    .border(1.dp, Slate700, RoundedCornerShape(20.dp))
                    .clickable { showBatterySelector = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val badgeColor = when (state.batteryMode) {
                    BatteryMode.NORMAL -> Cyan400
                    BatteryMode.BATTERY_SAVER -> Emerald400
                    BatteryMode.PERFORMANCE -> Amber400
                }
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(badgeColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = state.batteryMode.badgeLabel,
                    color = Slate200,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
            }

            // Right: Settings Action Button
            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Slate800)
                    .testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Slate400,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // CENTER: Animated FRIDAY AI Orb + Status Typography
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Orb
            FridayOrb(
                state = state.orbState,
                audioAmplitude = state.audioAmplitude,
                size = 230.dp,
                onClick = { fridayCore.onOrbTapped() },
                modifier = Modifier.testTag("friday_orb")
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Main Status Text
            Text(
                text = state.statusText,
                color = when (state.orbState) {
                    OrbState.ERROR -> Rose400
                    OrbState.LISTENING -> Cyan400
                    OrbState.THINKING -> Indigo400
                    OrbState.WORKING -> Amber400
                    else -> Slate50
                },
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle / Spoken Context / Wake instruction
            val subtitleText = when {
                state.lastRecognizedText != null && state.orbState == OrbState.THINKING ->
                    "“${state.lastRecognizedText}”"
                state.currentSpeechResponse != null && state.orbState == OrbState.SPEAKING ->
                    state.currentSpeechResponse ?: ""
                state.errorMessage != null ->
                    state.errorMessage ?: ""
                state.isWakeWordActive && state.isMicrophoneGranted && state.batteryMode != BatteryMode.BATTERY_SAVER ->
                    "Say “FRIDAY” or tap orb"
                !state.isMicrophoneGranted ->
                    "Tap to enable microphone"
                state.batteryMode == BatteryMode.BATTERY_SAVER ->
                    "Battery Saver active • Tap orb to speak"
                else ->
                    "Tap orb to activate"
            }

            Text(
                text = subtitleText,
                color = Slate400,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // Interaction Mood & Active Conversation Banner
            if (state.isConversationActive) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Cyan500.copy(alpha = 0.15f))
                        .border(1.dp, Cyan400.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Cyan400)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Active Conversation • ${state.mood.label}",
                        color = Cyan400,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                }
            } else if (state.mood != com.example.identity.FridayMood.CALM) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.mood.label,
                    color = Slate400,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Security Watcher Alert if present
            if (state.securityStatusMessage != null && state.securityStatusMessage != "Device state secure and normal.") {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Amber400.copy(alpha = 0.12f))
                        .border(1.dp, Amber400.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Security Status",
                        tint = Amber400,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = state.securityStatusMessage ?: "",
                        color = Amber400,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // BOTTOM CONTROLS: Tap-to-Talk Mic Bar & Test Quick Prompts
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Quick voice test action chips (helps testing in emulator or noisy environments)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                QuickCommandChip("Flashlight") {
                    fridayCore.startListeningSession()
                }
                Spacer(modifier = Modifier.width(8.dp))
                QuickCommandChip("Battery") {
                    fridayCore.startListeningSession()
                }
                Spacer(modifier = Modifier.width(8.dp))
                QuickCommandChip("Time") {
                    fridayCore.startListeningSession()
                }
            }

            // Primary interactive voice button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Slate800)
                    .border(1.dp, Slate700, RoundedCornerShape(28.dp))
                    .clickable { fridayCore.onOrbTapped() }
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .testTag("voice_action_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (state.orbState == OrbState.LISTENING) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Voice Control",
                    tint = if (state.orbState == OrbState.LISTENING) CyanGlow else Cyan400,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = when (state.orbState) {
                        OrbState.LISTENING -> "Tap to finish listening"
                        OrbState.SPEAKING -> "Tap to stop speaking"
                        OrbState.THINKING, OrbState.WORKING -> "Processing..."
                        else -> if (state.isMicrophoneGranted) "Tap to Speak" else "Grant Microphone Access"
                    },
                    color = Slate50,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // CONFIRMATION MODAL: Handles CONFIRM Risk Level actions
        if (state.pendingAction != null) {
            AlertDialog(
                onDismissRequest = { fridayCore.cancelPendingAction() },
                containerColor = Slate900,
                titleContentColor = Slate50,
                textContentColor = Slate400,
                shape = RoundedCornerShape(24.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = Amber400,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Action Confirmation", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                text = {
                    Column {
                        Text(
                            text = state.pendingAction?.confirmationPrompt
                                ?: "FRIDAY requested permission to perform a sensitive phone action: ${state.pendingAction?.actionType}.",
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = Slate200
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Slate800)
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "Action Level: CONFIRM (Manual authorization required)",
                                fontSize = 12.sp,
                                color = Amber400,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { fridayCore.confirmPendingAction() },
                        colors = ButtonDefaults.textButtonColors(contentColor = Emerald400)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Confirm & Execute")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { fridayCore.cancelPendingAction() },
                        colors = ButtonDefaults.textButtonColors(contentColor = Slate400)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cancel")
                        }
                    }
                }
            )
        }

        // BATTERY MODE SELECTION DIALOG
        if (showBatterySelector) {
            AlertDialog(
                onDismissRequest = { showBatterySelector = false },
                containerColor = Slate900,
                titleContentColor = Slate50,
                textContentColor = Slate400,
                shape = RoundedCornerShape(24.dp),
                title = {
                    Text("Select Battery Mode", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        BatteryModeOption(
                            mode = BatteryMode.NORMAL,
                            isSelected = state.batteryMode == BatteryMode.NORMAL,
                            accent = Cyan400
                        ) {
                            fridayCore.setBatteryMode(BatteryMode.NORMAL)
                            showBatterySelector = false
                        }
                        BatteryModeOption(
                            mode = BatteryMode.BATTERY_SAVER,
                            isSelected = state.batteryMode == BatteryMode.BATTERY_SAVER,
                            accent = Emerald400
                        ) {
                            fridayCore.setBatteryMode(BatteryMode.BATTERY_SAVER)
                            showBatterySelector = false
                        }
                        BatteryModeOption(
                            mode = BatteryMode.PERFORMANCE,
                            isSelected = state.batteryMode == BatteryMode.PERFORMANCE,
                            accent = Amber400
                        ) {
                            fridayCore.setBatteryMode(BatteryMode.PERFORMANCE)
                            showBatterySelector = false
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showBatterySelector = false }) {
                        Text("Done", color = Cyan400)
                    }
                }
            )
        }
    }
}

@Composable
private fun QuickCommandChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Slate800.copy(alpha = 0.7f))
            .border(1.dp, Slate700, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = Slate400,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun BatteryModeOption(
    mode: BatteryMode,
    isSelected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Slate800 else Slate900)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) accent else Slate700,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (isSelected) accent else Slate700)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = mode.title,
                color = Slate50,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Text(
                text = mode.description,
                color = Slate400,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}

// Previewable Greeting retained for screenshot test compatibility
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", color = Slate50, modifier = modifier)
}
