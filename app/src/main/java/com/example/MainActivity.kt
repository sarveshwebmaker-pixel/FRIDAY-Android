package com.example

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.core.FridayCore
import com.example.settings.FridaySettingsRepository
import com.example.ui.Greeting
import com.example.ui.MainScreen
import com.example.ui.SettingsScreen
import com.example.ui.SetupScreen
import com.example.ui.theme.FridayTheme

enum class ScreenState {
    MAIN,
    SETTINGS,
    SETUP
}

class MainActivity : ComponentActivity() {

    private lateinit var settingsRepo: FridaySettingsRepository
    private lateinit var fridayCore: FridayCore

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        fridayCore.checkAndStartStandby()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        settingsRepo = FridayApplication.instance.settingsRepo
        fridayCore = FridayApplication.instance.fridayCore

        val startInSetup = !settingsRepo.settings.value.firstRunCompleted

        // Configure lock-screen appearance only when keyguard is locked or externally voice-triggered
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isLocked = keyguardManager?.isKeyguardLocked == true
        val isVoiceTrigger = intent?.getBooleanExtra("TRIGGER_VOICE", false) == true

        if (settingsRepo.settings.value.lockScreenEnabled && (isLocked || isVoiceTrigger)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            }
        }

        // On first run, permissions are handled interactively in SetupScreen.
        // For subsequent launches, defer check to ensure initial window transaction completes cleanly.
        if (!startInSetup) {
            window.decorView.post {
                checkPermissions()
            }
        }

        setContent {
            FridayTheme {
                var currentScreen by remember {
                    mutableStateOf(if (startInSetup) ScreenState.SETUP else ScreenState.MAIN)
                }

                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "ScreenStateTransition"
                ) { screen ->
                    when (screen) {
                        ScreenState.MAIN -> {
                            MainScreen(
                                fridayCore = fridayCore,
                                onNavigateToSettings = { currentScreen = ScreenState.SETTINGS },
                                onNavigateToSetup = { currentScreen = ScreenState.SETUP }
                            )
                        }
                        ScreenState.SETTINGS -> {
                            SettingsScreen(
                                fridayCore = fridayCore,
                                onBack = { currentScreen = ScreenState.MAIN },
                                onLaunchSetup = { currentScreen = ScreenState.SETUP }
                            )
                        }
                        ScreenState.SETUP -> {
                            SetupScreen(
                                fridayCore = fridayCore,
                                onComplete = { currentScreen = ScreenState.MAIN }
                            )
                        }
                    }
                }
            }
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("TRIGGER_VOICE", false) == true) {
            fridayCore.startListeningSession()
        }
    }

    private fun checkPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_CONTACTS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    override fun onResume() {
        super.onResume()
        fridayCore.checkAndStartStandby()
    }

    override fun onDestroy() {
        // Do NOT cleanup fridayCore here so background standby service keeps working after Home is pressed
        super.onDestroy()
    }
}
