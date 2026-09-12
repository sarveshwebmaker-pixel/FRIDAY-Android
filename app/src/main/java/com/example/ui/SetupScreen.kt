package com.example.ui

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Indigo400
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate50
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950

@Composable
fun SetupScreen(
    fridayCore: FridayCore,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val state by fridayCore.state.collectAsState()
    var currentStep by remember { mutableIntStateOf(1) }

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        fridayCore.checkAndStartStandby()
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        // Notification permission handled
    }

    var isContactsGranted by remember {
        mutableStateOf(fridayCore.permissionManager.isContactsGranted())
    }

    val contactsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isContactsGranted = granted
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate950)
            .padding(top = statusBarPadding, bottom = navBarPadding)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Progress
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SETUP STEP $currentStep OF 8",
                    color = Cyan400,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                TextButton(
                    onClick = {
                        fridayCore.settingsRepo.updateFirstRunCompleted(true)
                        onComplete()
                    }
                ) {
                    Text("Skip", color = Slate400, fontSize = 12.sp)
                }
            }

            LinearProgressIndicator(
                progress = { currentStep / 8f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = Cyan400,
                trackColor = Slate800,
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Step Content Animated
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "SetupStepTransition"
                ) { step ->
                    when (step) {
                        1 -> WelcomeStep()
                        2 -> MicrophoneStep(
                            isGranted = state.isMicrophoneGranted,
                            onRequestPermission = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                        )
                        3 -> VoiceSetupStep(
                            onTestVoice = { fridayCore.onOrbTapped() }
                        )
                        4 -> AiSetupStep(isCloudConnected = fridayCore.geminiAiProvider.isCloudConnected)
                        5 -> PermissionsStep(
                            isContactsGranted = isContactsGranted,
                            onRequestNotification = {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onRequestContacts = {
                                contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
                            }
                        )
                        6 -> BackgroundBubbleStep(
                            onRequestOverlay = {
                                val intent = fridayCore.permissionManager.createOverlaySettingsIntent()
                                context.startActivity(intent)
                            }
                        )
                        7 -> BatteryPreferenceStep(
                            selectedMode = state.batteryMode,
                            onSelectMode = { fridayCore.setBatteryMode(it) }
                        )
                        8 -> TestFridayStep(
                            orbState = state.orbState,
                            audioAmplitude = state.audioAmplitude,
                            onOrbClick = { fridayCore.onOrbTapped() }
                        )
                    }
                }
            }

            // Navigation Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStep > 1) {
                    OutlinedButton(
                        onClick = { currentStep-- },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate200),
                        border = BorderStroke(1.dp, Slate700),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Back")
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Button(
                    onClick = {
                        if (currentStep < 8) {
                            currentStep++
                        } else {
                            fridayCore.settingsRepo.updateFirstRunCompleted(true)
                            onComplete()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan400, contentColor = Slate950),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.testTag("setup_next_button")
                ) {
                    Text(
                        text = if (currentStep == 8) "Say “FRIDAY”" else "Continue",
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// STEP 1: WELCOME
@Composable
private fun WelcomeStep() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        FridayOrb(state = OrbState.IDLE, audioAmplitude = 0f, size = 160.dp)
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = "Welcome to FRIDAY",
            color = Slate50,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Your native voice-first personal AI assistant. Designed for low-power standby, high responsiveness, and strict phone security.",
            color = Slate400,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

// STEP 2: MICROPHONE PERMISSION
@Composable
private fun MicrophoneStep(isGranted: Boolean, onRequestPermission: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(if (isGranted) Emerald400.copy(alpha = 0.15f) else Slate800),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Mic,
                contentDescription = null,
                tint = if (isGranted) Emerald400 else Cyan400,
                modifier = Modifier.size(44.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Microphone Permission", color = Slate50, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "FRIDAY requires microphone access to detect the wake word “FRIDAY” and receive your voice commands. Audio is never recorded secretly.",
            color = Slate400,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(20.dp))
        if (!isGranted) {
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = Slate800, contentColor = Cyan400)
            ) {
                Text("Grant Microphone Access")
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Emerald400, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Permission Granted", color = Emerald400, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// STEP 3: VOICE SETUP
@Composable
private fun VoiceSetupStep(onTestVoice: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(Slate800),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = Cyan400, modifier = Modifier.size(44.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Voice Setup", color = Slate50, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "FRIDAY speaks naturally using Android Text-to-Speech synthesis. Pitch, speed, and language can be tailored anytime in Settings.",
            color = Slate400,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onTestVoice,
            colors = ButtonDefaults.buttonColors(containerColor = Slate800, contentColor = Cyan400)
        ) {
            Text("Sample Voice Output")
        }
    }
}

// STEP 4: AI & GEMINI SETUP
@Composable
private fun AiSetupStep(isCloudConnected: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(Indigo400.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Psychology, contentDescription = null, tint = Indigo400, modifier = Modifier.size(44.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("AI Brain Architecture", color = Slate50, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "FRIDAY is architected with Gemini 3.5 Flash for reasoning and intent extraction, backed by an offline local rule engine for zero latency device control.",
            color = Slate400,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Slate900)
                .border(1.dp, Slate800, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Text(
                text = if (isCloudConnected) "Gemini Cloud Connected via Secrets" else "Ready: Local Edge Engine Active",
                color = if (isCloudConnected) Cyan400 else Slate200,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// STEP 5: REQUIRED ANDROID PERMISSIONS
@Composable
private fun PermissionsStep(
    isContactsGranted: Boolean,
    onRequestNotification: () -> Unit,
    onRequestContacts: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(Slate800),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Security, contentDescription = null, tint = Amber400, modifier = Modifier.size(44.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("System Permissions", color = Slate50, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Enable notifications for foreground standby, and contacts access so FRIDAY can dial and make WhatsApp calls by contact name.",
            color = Slate400,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onRequestNotification,
                colors = ButtonDefaults.buttonColors(containerColor = Slate800, contentColor = Amber400)
            ) {
                Text("Notifications")
            }
            Button(
                onClick = onRequestContacts,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isContactsGranted) Emerald400.copy(alpha = 0.2f) else Slate800,
                    contentColor = if (isContactsGranted) Emerald400 else Cyan400
                )
            ) {
                Text(if (isContactsGranted) "Contacts Granted" else "Enable Contacts")
            }
        }
    }
}

// STEP 6: BACKGROUND & BUBBLE SETUP
@Composable
private fun BackgroundBubbleStep(onRequestOverlay: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(Cyan400.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.BubbleChart, contentDescription = null, tint = Cyan400, modifier = Modifier.size(44.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Background & Bubble", color = Slate50, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Optionally allow FRIDAY to draw an unobtrusive floating bubble over other apps so you can invoke voice assistance anywhere.",
            color = Slate400,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onRequestOverlay,
            colors = ButtonDefaults.buttonColors(containerColor = Slate800, contentColor = Cyan400)
        ) {
            Text("Configure Screen Overlay")
        }
    }
}

// STEP 7: BATTERY PREFERENCE
@Composable
private fun BatteryPreferenceStep(selectedMode: BatteryMode, onSelectMode: (BatteryMode) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(Emerald400.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = Emerald400, modifier = Modifier.size(44.dp))
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text("Battery Efficiency", color = Slate50, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Choose your preferred standby profile:", color = Slate400, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(16.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SetupBatteryChoice(
                title = "Normal (Recommended)",
                desc = "Balanced standby with wake word detection.",
                isSelected = selectedMode == BatteryMode.NORMAL,
                color = Cyan400
            ) { onSelectMode(BatteryMode.NORMAL) }

            SetupBatteryChoice(
                title = "Battery Saver",
                desc = "Push-to-talk only; wakes only on manual tap.",
                isSelected = selectedMode == BatteryMode.BATTERY_SAVER,
                color = Emerald400
            ) { onSelectMode(BatteryMode.BATTERY_SAVER) }

            SetupBatteryChoice(
                title = "Performance",
                desc = "Continuous readiness and instant response.",
                isSelected = selectedMode == BatteryMode.PERFORMANCE,
                color = Amber400
            ) { onSelectMode(BatteryMode.PERFORMANCE) }
        }
    }
}

@Composable
private fun SetupBatteryChoice(title: String, desc: String, isSelected: Boolean, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) Slate800 else Slate900)
            .border(1.dp, if (isSelected) color else Slate800, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isSelected) color else Slate700)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(text = title, color = Slate50, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(text = desc, color = Slate400, fontSize = 11.sp)
        }
    }
}

// STEP 8: TEST FRIDAY
@Composable
private fun TestFridayStep(orbState: OrbState, audioAmplitude: Float, onOrbClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        FridayOrb(state = orbState, audioAmplitude = audioAmplitude, size = 180.dp, onClick = onOrbClick)
        Spacer(modifier = Modifier.height(28.dp))
        Text("Test FRIDAY", color = Slate50, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "You are all set! Test FRIDAY now by saying:",
            color = Slate400,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "“Say FRIDAY”",
            color = Cyan400,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "or tap the orb to try “Turn on flashlight” or “What is my battery?”",
            color = Slate400,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}
