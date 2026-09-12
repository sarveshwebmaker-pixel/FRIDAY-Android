package com.example.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.actions.ActionExecutor
import com.example.actions.ActionResult
import com.example.actions.PhoneAction
import com.example.ai.AiProvider
import com.example.ai.FridayAiResult
import com.example.ai.GeminiAiProvider
import com.example.ai.OfflineAiProvider
import com.example.ai.StructuredAction
import com.example.ai.UserIntentCategory
import com.example.ai.UserIntentClassifier
import com.example.identity.FridayMood
import com.example.identity.FridayPersonality
import com.example.identity.VoiceIdentityEngine
import com.example.permissions.FridayPermissionManager
import com.example.security.ActionRiskLevel
import com.example.security.FridaySecurityWatcher
import com.example.security.SecurityManager
import com.example.service.FridayForegroundService
import com.example.settings.FridaySettingsRepository
import com.example.skills.SkillRegistry
import com.example.voice.CentralAudioController
import com.example.voice.MicrophoneState
import com.example.voice.VoiceEngine
import com.example.voice.VoiceEngineListener
import com.example.wakeword.WakeWordEngine
import com.example.wakeword.WakeWordListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FridayCore(
    private val context: Context,
    val settingsRepo: FridaySettingsRepository
) : VoiceEngineListener, WakeWordListener {

    companion object {
        private const val TAG = "FridayCore"

        fun stripWakeWordPrefix(text: String): String {
            val trimmed = text.trim()
            val pattern = Regex("^(hey\\s+friday|hi\\s+friday|ok\\s+friday|friday)[,\\s:]*", RegexOption.IGNORE_CASE)
            return trimmed.replaceFirst(pattern, "").trim()
        }

        fun hasWakeWord(text: String): Boolean {
            val pattern = Regex("\\b(hey\\s+friday|hi\\s+friday|ok\\s+friday|friday)\\b", RegexOption.IGNORE_CASE)
            return pattern.containsMatchIn(text)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    val permissionManager = FridayPermissionManager(context)
    val securityManager = SecurityManager()
    val securityWatcher = FridaySecurityWatcher(context)
    val conversationContext = ConversationContext()
    val identityEngine = VoiceIdentityEngine(settingsRepo.settings.value.voiceIdentityThreshold)
    val actionExecutor = ActionExecutor(context)
    val skillRegistry = SkillRegistry()

    val centralAudioController = CentralAudioController(context)

    private val offlineAiProvider = OfflineAiProvider()
    val geminiAiProvider = GeminiAiProvider(
        customApiKeyProvider = { settingsRepo.settings.value.customApiKey },
        offlineFallback = offlineAiProvider
    )

    private val voiceEngine = VoiceEngine(context, this)
    private val wakeWordEngine = WakeWordEngine(context, this)

    private var inactivityRunnable: Runnable? = null

    private val _state = MutableStateFlow(
        FridayState(
            orbState = OrbState.IDLE,
            statusText = "FRIDAY — Ready",
            batteryMode = settingsRepo.settings.value.batteryMode,
            isWakeWordActive = settingsRepo.settings.value.wakeWordEnabled,
            isMicrophoneGranted = permissionManager.isMicrophoneGranted(),
            micState = centralAudioController.micState.value,
            activeProviderName = if (geminiAiProvider.isCloudConnected) "Gemini 3.5 Flash" else "Local Edge NLU"
        )
    )
    val state: StateFlow<FridayState> = _state.asStateFlow()

    init {
        // Collect mic state changes from central controller
        scope.launch {
            centralAudioController.micState.collect { mic ->
                _state.value = _state.value.copy(micState = mic)
            }
        }

        // Observe settings changes
        scope.launch {
            settingsRepo.settings.collect { settings ->
                identityEngine.setThreshold(settings.voiceIdentityThreshold)
                wakeWordEngine.updateBatteryMode(settings.batteryMode)

                _state.value = _state.value.copy(
                    batteryMode = settings.batteryMode,
                    isWakeWordActive = settings.wakeWordEnabled,
                    activeProviderName = if (geminiAiProvider.isCloudConnected && settings.batteryMode != BatteryMode.BATTERY_SAVER) {
                        "Gemini 3.5 Flash"
                    } else {
                        "Local Edge NLU"
                    }
                )

                if (settings.wakeWordEnabled && settings.batteryMode != BatteryMode.BATTERY_SAVER && permissionManager.isMicrophoneGranted() && !conversationContext.isSessionActive) {
                    centralAudioController.acquireForWakeWord(
                        stopVoiceAction = { voiceEngine.stopListening() },
                        startWakeWordAction = { wakeWordEngine.startDetection() }
                    )
                } else {
                    wakeWordEngine.stopDetection()
                }
            }
        }

        checkAndStartStandby()
    }

    fun checkAndStartStandby() {
        val hasMic = permissionManager.isMicrophoneGranted()
        _state.value = _state.value.copy(
            isMicrophoneGranted = hasMic,
            isForegroundServiceRunning = FridayForegroundService.isRunning
        )

        val settings = settingsRepo.settings.value
        if (hasMic && settings.wakeWordEnabled && settings.batteryMode != BatteryMode.BATTERY_SAVER && !conversationContext.isSessionActive) {
            if (!FridayForegroundService.isRunning) {
                try {
                    FridayForegroundService.startService(context)
                    _state.value = _state.value.copy(isForegroundServiceRunning = true)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not start FridayForegroundService", e)
                }
            }
            centralAudioController.acquireForWakeWord(
                stopVoiceAction = { voiceEngine.stopListening() },
                startWakeWordAction = { wakeWordEngine.startDetection() }
            )
        }
    }

    fun onOrbTapped() {
        when (_state.value.orbState) {
            OrbState.IDLE, OrbState.ERROR, OrbState.OFFLINE -> {
                startConversationSession()
            }
            OrbState.LISTENING -> {
                if (conversationContext.isSessionActive) {
                    endConversationSession()
                    transitionToIdle("FRIDAY — Ready")
                } else {
                    stopListening()
                }
            }
            OrbState.SPEAKING -> {
                voiceEngine.stopSpeaking()
                if (conversationContext.isSessionActive) {
                    endConversationSession()
                }
                transitionToIdle("FRIDAY — Ready")
            }
            OrbState.THINKING, OrbState.WORKING -> {
                // In progress
            }
        }
    }

    // --- Active Conversation Lifecycle ---

    fun startListeningSession() = startConversationSession()

    fun startConversationSession() {
        if (!permissionManager.isMicrophoneGranted()) {
            _state.value = _state.value.copy(
                orbState = OrbState.ERROR,
                statusText = "Microphone Permission Required",
                errorMessage = "Please grant microphone permission to interact with FRIDAY."
            )
            return
        }

        cancelInactivityTimeout()
        conversationContext.startSession()

        _state.value = _state.value.copy(
            orbState = OrbState.LISTENING,
            sessionState = AssistantSessionState.LISTENING,
            isConversationActive = true,
            statusText = "FRIDAY — Listening...",
            errorMessage = null,
            audioAmplitude = 0.2f
        )

        centralAudioController.acquireForCommandListening(
            reason = "Active conversation session",
            stopWakeWordAction = { wakeWordEngine.stopDetection() },
            startVoiceAction = { sessionId -> voiceEngine.startListening(sessionId) }
        )
    }

    fun endConversationSession() {
        cancelInactivityTimeout()
        conversationContext.endSession()
        _state.value = _state.value.copy(
            isConversationActive = false
        )
    }

    private fun scheduleInactivityTimeout() {
        cancelInactivityTimeout()
        val timeoutSeconds = settingsRepo.settings.value.conversationTimeoutSeconds
        val timeoutMs = timeoutSeconds * 1000L

        inactivityRunnable = Runnable {
            Log.i(TAG, "Conversation session timed out after ${timeoutSeconds}s of silence. Returning to standby.")
            voiceEngine.stopListening()
            endConversationSession()
            transitionToIdle("FRIDAY — Ready")
        }
        mainHandler.postDelayed(inactivityRunnable!!, timeoutMs)
    }

    private fun cancelInactivityTimeout() {
        inactivityRunnable?.let {
            mainHandler.removeCallbacks(it)
            inactivityRunnable = null
        }
    }

    fun stopListening() {
        cancelInactivityTimeout()
        voiceEngine.stopListening()
        endConversationSession()
        transitionToIdle("FRIDAY — Ready")
    }

    // --- WakeWordListener ---
    override fun onWakeWordDetected(keyword: String) {
        Log.i(TAG, "Wake word detected: '$keyword'. Initiating command session.")
        FridayTelemetry.recordWakeDetected(keyword)
        _state.value = _state.value.copy(sessionState = AssistantSessionState.WAKE_DETECTED)
        startConversationSession()
    }

    override fun onWakeWordError(error: String) {
        Log.w(TAG, "Wake word error: $error")
    }

    // --- VoiceEngineListener ---
    override fun onSpeechStart() {
        cancelInactivityTimeout()
        _state.value = _state.value.copy(
            orbState = OrbState.LISTENING,
            statusText = "FRIDAY — Listening..."
        )
    }

    override fun onSpeechRmsChanged(rmsdB: Float) {
        if (_state.value.orbState == OrbState.LISTENING) {
            _state.value = _state.value.copy(audioAmplitude = rmsdB)
        }
    }

    override fun onSpeechPartialResult(partialText: String) {
        if (partialText.isNotBlank() && _state.value.orbState == OrbState.LISTENING) {
            _state.value = _state.value.copy(
                statusText = "Heard: $partialText",
                lastRecognizedText = partialText,
                audioAmplitude = 0.5f
            )
        }
    }

    override fun onSpeechResult(recognizedText: String, confidence: Float) {
        cancelInactivityTimeout()
        centralAudioController.setProcessing(centralAudioController.activeSessionId)

        if (recognizedText.isBlank()) {
            if (conversationContext.isSessionActive) {
                scheduleInactivityTimeout()
                mainHandler.postDelayed({
                    if (conversationContext.isSessionActive && _state.value.orbState == OrbState.LISTENING) {
                        centralAudioController.acquireForCommandListening(
                            reason = "Speech retry in active session",
                            stopWakeWordAction = { wakeWordEngine.stopDetection() },
                            startVoiceAction = { sessionId -> voiceEngine.startListening(sessionId) }
                        )
                    }
                }, 100)
            } else {
                transitionToIdle("FRIDAY — Ready")
            }
            return
        }

        // When in standby, check if utterance contains the wake word
        if (!conversationContext.isSessionActive) {
            if (!hasWakeWord(recognizedText)) {
                Log.d(TAG, "Standby speech ignored (no wake word found): '$recognizedText'")
                transitionToIdle("FRIDAY — Ready")
                return
            }
            Log.i(TAG, "Wake word detected in utterance: '$recognizedText'. Activating session.")
            FridayTelemetry.recordWakeDetected("FRIDAY")
            conversationContext.startSession()
            _state.value = _state.value.copy(
                isConversationActive = true,
                sessionState = AssistantSessionState.WAKE_DETECTED
            )
        }

        val cleanCommand = stripWakeWordPrefix(recognizedText)
        FridayTelemetry.recordSpeechRecognized(cleanCommand, confidence)

        if (cleanCommand.isBlank()) {
            // User just said the wake word ("FRIDAY")
            speakReply("Yes, I'm listening.")
            return
        }

        // Check if user requested dismissal / ending the conversation
        val lower = cleanCommand.lowercase().trim()
        if (lower == "bye" || lower == "goodbye" || lower == "stop listening" || lower == "that's all" ||
            lower == "dismiss" || lower == "go to sleep" || lower == "nevermind" || lower == "cancel") {
            endConversationSession()
            speakReply("Standing by.")
            return
        }

        _state.value = _state.value.copy(
            orbState = OrbState.THINKING,
            sessionState = AssistantSessionState.PROCESSING,
            statusText = "FRIDAY — Thinking...",
            lastRecognizedText = recognizedText,
            speechConfidence = confidence,
            audioAmplitude = 0f
        )

        // Inactive session or active session: do not blindly block recognized speech based on a rigid 0.65 threshold.
        // If confidence is extremely low (< 0.15), only then flag unclear speech.
        if (confidence < 0.15f) {
            Log.w(TAG, "Speech confidence extremely low ($confidence) for '$recognizedText'. Asking clarification.")
            _state.value = _state.value.copy(
                orbState = OrbState.ERROR,
                statusText = "Unclear speech (${(confidence * 100).toInt()}%)",
                errorMessage = "Speech confidence too low (${(confidence * 100).toInt()}%)."
            )
            val speech = FridayPersonality.formatSpeech("UNCLEAR_COMMAND")
            speakReply(speech.speechText)
            return
        }

        // Check Contextual Command Resolution: "off", "on", "again", "turn it off", "louder", "close it", etc.
        val contextualResolution = conversationContext.resolveContextualCommand(cleanCommand)
        if (contextualResolution != null) {
            Log.i(TAG, "Context resolved command: ${contextualResolution.resolvedText} for entity: ${contextualResolution.targetEntity}")
            executeContextualAction(contextualResolution)
            return
        }

        processUserSpeech(cleanCommand, confidence)
    }

    override fun onSpeechResult(recognizedText: String) {
        onSpeechResult(recognizedText, 1.0f)
    }

    override fun onSpeechError(errorCode: Int, message: String) {
        Log.w(TAG, "Speech recognition error: $message ($errorCode)")

        if (conversationContext.isSessionActive) {
            val timeoutSeconds = settingsRepo.settings.value.conversationTimeoutSeconds
            if (conversationContext.isSessionExpired(timeoutSeconds)) {
                endConversationSession()
                transitionToIdle("FRIDAY — Ready")
            } else {
                scheduleInactivityTimeout()
                mainHandler.postDelayed({
                    if (conversationContext.isSessionActive && _state.value.orbState != OrbState.SPEAKING) {
                        _state.value = _state.value.copy(
                            orbState = OrbState.LISTENING,
                            statusText = "FRIDAY — Listening..."
                        )
                        centralAudioController.acquireForCommandListening(
                            reason = "Speech retry in active session",
                            stopWakeWordAction = { wakeWordEngine.stopDetection() },
                            startVoiceAction = { sessionId -> voiceEngine.startListening(sessionId) }
                        )
                    }
                }, 200)
            }
            return
        }

        _state.value = _state.value.copy(
            orbState = OrbState.ERROR,
            statusText = message,
            audioAmplitude = 0f
        )
        scheduleReturnToIdle(2000)
    }

    override fun onTtsStart() {
        cancelInactivityTimeout()
        FridayTelemetry.recordTtsStarted()
        _state.value = _state.value.copy(
            orbState = OrbState.SPEAKING,
            sessionState = AssistantSessionState.SPEAKING,
            statusText = "FRIDAY — Speaking...",
            audioAmplitude = 0.6f
        )
    }

    override fun onTtsDone() {
        centralAudioController.onSpeakingCompleted {
            if (conversationContext.isSessionActive) {
                // Natural Conversation Mode: Keep listening for follow-up without wake word
                _state.value = _state.value.copy(
                    orbState = OrbState.LISTENING,
                    sessionState = AssistantSessionState.FOLLOW_UP_LISTENING,
                    statusText = "FRIDAY — Listening...",
                    audioAmplitude = 0.2f
                )
                scheduleInactivityTimeout()
                centralAudioController.acquireForCommandListening(
                    reason = "Follow-up conversation turn",
                    stopWakeWordAction = { wakeWordEngine.stopDetection() },
                    startVoiceAction = { sessionId -> voiceEngine.startListening(sessionId) }
                )
            } else {
                transitionToIdle("FRIDAY — Ready")
            }
        }
    }

    override fun onTtsError(error: String) {
        centralAudioController.onSpeakingCompleted {
            if (conversationContext.isSessionActive) {
                scheduleInactivityTimeout()
                centralAudioController.acquireForCommandListening(
                    reason = "Follow-up after TTS error",
                    stopWakeWordAction = { wakeWordEngine.stopDetection() },
                    startVoiceAction = { sessionId -> voiceEngine.startListening(sessionId) }
                )
            } else {
                transitionToIdle("FRIDAY — Ready")
            }
        }
    }

    // --- Contextual Execution ---
    private fun executeContextualAction(resolved: com.example.core.ResolvedContextCommand) {
        FridayTelemetry.recordFastPathExecution()
        FridayTelemetry.recordIntentStarted()
        FridayTelemetry.recordActionExecutionStarted()

        _state.value = _state.value.copy(
            orbState = OrbState.WORKING,
            sessionState = AssistantSessionState.EXECUTING,
            statusText = "FRIDAY — Executing...",
            lastRecognizedText = resolved.resolvedText
        )

        scope.launch(Dispatchers.Default) {
            val action = StructuredAction(
                intent = when (resolved.actionType) {
                    "TOGGLE_FLASHLIGHT", "ADJUST_VOLUME" -> "DEVICE_CONTROL"
                    "OPEN_APP" -> "LAUNCH_APP"
                    "CLOSE_APP" -> "CLOSE_APP"
                    "SEARCH_WEB" -> "SEARCH_WEB"
                    "WHATSAPP_CALL" -> "WHATSAPP_CALL"
                    "WHATSAPP_CHAT" -> "WHATSAPP_CHAT"
                    "WHATSAPP_MESSAGE" -> "WHATSAPP_MESSAGE"
                    "CALL_CONTACT" -> "CALL_CONTACT"
                    else -> "DEVICE_CONTROL"
                },
                actionType = resolved.actionType,
                parameters = resolved.parameters,
                speechResponse = "",
                riskLevel = ActionRiskLevel.SAFE
            )

            val skill = skillRegistry.findSkillForIntent(action.intent, action.actionType)
            val result = skill?.execute(action, actionExecutor)

            val isSuccess = result is ActionResult.Success
            FridayTelemetry.recordCommandComplete(isSuccess = isSuccess, errorMessage = if (!isSuccess) (result as? ActionResult.Failure)?.error else null)

            val formatted = FridayPersonality.formatSpeech(
                actionType = resolved.actionType,
                parameters = resolved.parameters,
                isSuccess = isSuccess,
                isFollowUp = true
            )

            val replyText = when (result) {
                is ActionResult.Success -> result.spokenDetail ?: formatted.speechText
                is ActionResult.Failure -> result.userMessage ?: FridayPersonality.formatError("ACTION_FAILED").speechText
                is ActionResult.MissingParameter -> result.prompt
                is ActionResult.PermissionRequired -> FridayPersonality.formatError("PERMISSION_REQUIRED", result.explanation).speechText
                is ActionResult.DisambiguationRequired -> FridayPersonality.formatError("MULTIPLE_CONTACTS", result.prompt).speechText
                is ActionResult.NeedsConfirmation -> result.action.description
                is ActionResult.NotFound -> {
                    val errType = if (resolved.actionType.contains("APP")) "APP_NOT_FOUND" else "CONTACT_NOT_FOUND"
                    FridayPersonality.formatError(errType).speechText
                }
                is ActionResult.NotSupported -> result.explanation
                is ActionResult.Cancelled -> result.reason
                is ActionResult.Timeout -> "Operation timed out."
                null -> formatted.speechText
            }

            conversationContext.recordTurn(resolved.resolvedText, replyText, "FOLLOW_UP")

            conversationContext.recordInteraction(
                actionType = resolved.actionType,
                targetEntity = resolved.targetEntity,
                parameters = resolved.parameters,
                mood = formatted.mood
            )

            launch(Dispatchers.Main) {
                _state.value = _state.value.copy(mood = formatted.mood)
                speakReply(replyText)
            }
        }
    }

    // --- Processing Pipeline ---
    private fun processUserSpeech(commandText: String, speechConfidence: Float = 1.0f) {
        scope.launch(Dispatchers.Default) {
            val classified = UserIntentClassifier.classify(commandText, conversationContext)

            if (classified.isLocalFastPath) {
                FridayTelemetry.recordFastPathExecution()
            }

            // Casual Conversation Mode: instant natural reply, zero tool latency
            if (classified.category == UserIntentCategory.CASUAL_CONVERSATION) {
                val reply = classified.conversationResponse ?: "Ready whenever you are."
                conversationContext.recordTurn(commandText, reply, "CASUAL_CONVERSATION")
                FridayTelemetry.recordCommandComplete(isSuccess = true)
                launch(Dispatchers.Main) {
                    _state.value = _state.value.copy(mood = classified.mood)
                    speakReply(reply)
                }
                return@launch
            }

            // Direct local answers for known factual or context-referencing questions
            if (classified.category == UserIntentCategory.QUESTION && classified.conversationResponse != null) {
                val reply = classified.conversationResponse
                conversationContext.recordTurn(commandText, reply, "QUESTION")
                FridayTelemetry.recordCommandComplete(isSuccess = true)
                launch(Dispatchers.Main) {
                    _state.value = _state.value.copy(mood = classified.mood)
                    speakReply(reply)
                }
                return@launch
            }

            // Local Fast-Path Direct Actions
            if (classified.structuredAction != null && (classified.category == UserIntentCategory.ACTION || classified.category == UserIntentCategory.FOLLOW_UP)) {
                FridayTelemetry.recordIntentStarted()
                launch(Dispatchers.Main) {
                    executeSafeAction(classified.structuredAction)
                }
                return@launch
            }

            // General or Open-Ended Query Handling via AI Provider
            val currentMode = settingsRepo.settings.value.batteryMode
            FridayTelemetry.recordIntentStarted()

            val aiProvider: AiProvider = if (!geminiAiProvider.isCloudConnected || currentMode == BatteryMode.BATTERY_SAVER) {
                offlineAiProvider
            } else {
                geminiAiProvider
            }

            val contextSummary = conversationContext.getRecentContextSummary()
            val queryText = if (contextSummary.isNotBlank()) {
                "$commandText (Context: $contextSummary)"
            } else {
                commandText
            }

            val aiResult = aiProvider.processCommand(queryText, currentMode)

            val multiPlan = com.example.actions.planner.ActionPlanner.createPlanFromConjunctions(commandText) { seg ->
                val res = kotlinx.coroutines.runBlocking { offlineAiProvider.processCommand(seg, currentMode) }
                if (res is FridayAiResult.Success && res.action.actionType != "UNKNOWN_INTENT") {
                    com.example.actions.planner.PlannedStep(res.action.actionType, res.action.parameters, res.action.speechResponse)
                } else null
            }

            val action = if (multiPlan != null && multiPlan.steps.size > 1) {
                StructuredAction(
                    intent = "MULTI_STEP_PLAN",
                    actionType = "MULTI_STEP_ACTION",
                    speechResponse = "Executing multi-step sequence, Boss.",
                    steps = multiPlan.steps.map { step ->
                        StructuredAction(
                            intent = step.toolId,
                            actionType = step.toolId,
                            parameters = step.parameters,
                            speechResponse = step.description
                        )
                    }
                )
            } else {
                when (aiResult) {
                    is FridayAiResult.Success -> aiResult.action
                    is FridayAiResult.Error -> {
                        val fallback = FridayPersonality.formatSpeech(
                            actionType = "GENERAL_QUERY",
                            isSuccess = false,
                            errorDetail = aiResult.message
                        )
                        speakReply(fallback.speechText)
                        return@launch
                    }
                }
            }

            // Inspect device security signals via official lightweight Security Watcher
            val watcherReport = securityWatcher.inspectCurrentState()

            // Normal safe commands execute with zero voice identity friction
            val isNormalCommand = SecurityManager.isSafeAction(action.actionType)

            val (voiceScore, securityResult) = if (isNormalCommand) {
                Pair(null, securityManager.evaluateAction(
                    actionType = action.actionType,
                    parameters = action.parameters,
                    voiceConfidence = null,
                    minVoiceThreshold = identityEngine.getThreshold(),
                    watcherReport = watcherReport
                ))
            } else {
                val voiceResult = identityEngine.verifySpeaker(commandText)
                Log.d(TAG, "Voice identity score for sensitive action '${action.actionType}': ${voiceResult.confidence}")
                Pair(voiceResult.confidence, securityManager.evaluateAction(
                    actionType = action.actionType,
                    parameters = action.parameters,
                    voiceConfidence = voiceResult.confidence,
                    minVoiceThreshold = identityEngine.getThreshold(),
                    watcherReport = watcherReport
                ))
            }

            launch(Dispatchers.Main) {
                _state.value = _state.value.copy(
                    voiceConfidence = voiceScore,
                    securityStatusMessage = watcherReport.summary
                )

                when (securityResult.riskLevel) {
                    ActionRiskLevel.BLOCKED -> {
                        securityWatcher.recordSecurityFailure()
                        _state.value = _state.value.copy(
                            orbState = OrbState.ERROR,
                            mood = FridayMood.SERIOUS,
                            statusText = "Action Blocked",
                            errorMessage = securityResult.reason
                        )
                        val blockedSpeech = FridayPersonality.formatSpeech(
                            actionType = "BLOCKED_ACTION",
                            isSuccess = false,
                            explicitMood = FridayMood.SERIOUS
                        )
                        speakReply(blockedSpeech.speechText)
                    }

                    ActionRiskLevel.CONFIRM -> {
                        _state.value = _state.value.copy(
                            orbState = OrbState.WORKING,
                            mood = FridayMood.CONCERNED,
                            statusText = "Confirmation Required",
                            pendingAction = action
                        )
                        val prompt = action.confirmationPrompt ?: securityResult.reason
                        speakReply(prompt)
                    }

                    ActionRiskLevel.SAFE -> {
                        securityWatcher.resetSecurityFailures()
                        executeSafeAction(action)
                    }
                }
            }
        }
    }

    private fun executeSafeAction(action: StructuredAction) {
        FridayTelemetry.recordActionExecutionStarted()
        _state.value = _state.value.copy(
            orbState = OrbState.WORKING,
            sessionState = AssistantSessionState.EXECUTING,
            statusText = "FRIDAY — Executing..."
        )

        scope.launch(Dispatchers.Default) {
            val result = if (action.steps.isNotEmpty() || action.actionType == "MULTI_STEP_ACTION") {
                val planned = action.steps.map { s ->
                    com.example.actions.planner.PlannedStep(s.actionType, s.parameters, s.speechResponse)
                }
                com.example.actions.planner.ActionPlanner.executePlan(
                    com.example.actions.planner.ExecutionPlan(planned),
                    context
                )
            } else {
                val skill = skillRegistry.findSkillForIntent(action.intent, action.actionType)
                skill?.execute(action, actionExecutor)
            }

            val isSuccess = result is ActionResult.Success
            FridayTelemetry.recordCommandComplete(isSuccess = isSuccess, errorMessage = if (!isSuccess) (result as? ActionResult.Failure)?.error else null)

            val formatted = FridayPersonality.formatSpeech(
                actionType = action.actionType,
                parameters = action.parameters,
                isSuccess = isSuccess,
                isFollowUp = false
            )

            // Extract target entity for conversational continuity
            val targetEntity = when (action.actionType) {
                "TOGGLE_FLASHLIGHT" -> "flashlight"
                "ADJUST_VOLUME" -> "volume"
                "OPEN_APP", "LAUNCH_APP" -> action.parameters["appName"] ?: "app"
                "PLAY_MUSIC" -> action.parameters["query"] ?: action.parameters["song"] ?: "song"
                "SEARCH_WEB" -> action.parameters["targetApp"] ?: "web"
                "SET_TIMER" -> "timer"
                "WHATSAPP_CALL" -> action.parameters["contact"] ?: "whatsapp"
                "WHATSAPP_CHAT" -> action.parameters["contact"] ?: "whatsapp"
                "WHATSAPP_MESSAGE" -> action.parameters["contact"] ?: "whatsapp"
                "CALL_CONTACT" -> action.parameters["contact"] ?: "phone"
                else -> action.actionType
            }

            conversationContext.recordInteraction(
                actionType = action.actionType,
                targetEntity = targetEntity,
                parameters = action.parameters,
                mood = formatted.mood
            )

            val finalReply = when (result) {
                is ActionResult.Success -> {
                    result.spokenDetail ?: if (action.speechResponse.isNotBlank() &&
                        !action.speechResponse.contains("Command executed", ignoreCase = true)) {
                        action.speechResponse
                    } else {
                        formatted.speechText
                    }
                }
                is ActionResult.Failure -> result.userMessage ?: FridayPersonality.formatError("ACTION_FAILED").speechText
                is ActionResult.MissingParameter -> result.prompt
                is ActionResult.PermissionRequired -> FridayPersonality.formatError("PERMISSION_REQUIRED", result.explanation).speechText
                is ActionResult.DisambiguationRequired -> FridayPersonality.formatError("MULTIPLE_CONTACTS", result.prompt).speechText
                is ActionResult.NeedsConfirmation -> result.action.description
                is ActionResult.NotFound -> {
                    val errType = if (action.actionType.contains("APP")) "APP_NOT_FOUND" else "CONTACT_NOT_FOUND"
                    FridayPersonality.formatError(errType).speechText
                }
                is ActionResult.NotSupported -> result.explanation
                is ActionResult.Cancelled -> result.reason
                is ActionResult.Timeout -> "Operation timed out, Boss."
                null -> if (action.speechResponse.isNotBlank()) action.speechResponse else formatted.speechText
            }

            conversationContext.recordTurn(action.actionType, finalReply, "ACTION")

            launch(Dispatchers.Main) {
                _state.value = _state.value.copy(mood = formatted.mood)
                speakReply(finalReply)
            }
        }
    }

    fun confirmPendingAction() {
        val action = _state.value.pendingAction ?: return
        _state.value = _state.value.copy(pendingAction = null)

        scope.launch(Dispatchers.Default) {
            val skill = skillRegistry.findSkillForIntent(action.intent, action.actionType)
            val result = skill?.execute(action, actionExecutor)

            launch(Dispatchers.Main) {
                val formatted = FridayPersonality.formatSpeech(action.actionType, action.parameters, isSuccess = true)
                val reply = when (result) {
                    is ActionResult.Success -> result.spokenDetail ?: formatted.speechText
                    is ActionResult.Failure -> result.userMessage ?: formatted.speechText
                    else -> formatted.speechText
                }
                speakReply(reply)
            }
        }
    }

    fun cancelPendingAction() {
        _state.value = _state.value.copy(
            pendingAction = null,
            statusText = "Action Cancelled"
        )
        speakReply("Action cancelled.")
    }

    private fun speakReply(text: String) {
        val settings = settingsRepo.settings.value
        _state.value = _state.value.copy(
            orbState = OrbState.SPEAKING,
            statusText = "FRIDAY — Speaking...",
            currentSpeechResponse = text
        )
        centralAudioController.setSpeaking {
            voiceEngine.speak(text, pitch = settings.speechPitch, rate = settings.speechRate)
        }
    }

    private fun transitionToIdle(status: String = "FRIDAY — Ready") {
        cancelInactivityTimeout()
        _state.value = _state.value.copy(
            orbState = OrbState.IDLE,
            sessionState = AssistantSessionState.STANDBY,
            isConversationActive = false,
            statusText = status,
            audioAmplitude = 0f
        )

        // Resume wake-word detection for low-power standby if enabled
        val settings = settingsRepo.settings.value
        if (settings.wakeWordEnabled && settings.batteryMode != BatteryMode.BATTERY_SAVER && permissionManager.isMicrophoneGranted()) {
            centralAudioController.acquireForWakeWord(
                stopVoiceAction = { voiceEngine.stopListening() },
                startWakeWordAction = { wakeWordEngine.startDetection() }
            )
        } else {
            centralAudioController.releaseMic {
                voiceEngine.stopListening()
                wakeWordEngine.stopDetection()
            }
        }
    }

    private fun scheduleReturnToIdle(delayMs: Long) {
        mainHandler.postDelayed({
            if (_state.value.orbState == OrbState.ERROR || _state.value.orbState == OrbState.WORKING) {
                if (!conversationContext.isSessionActive) {
                    transitionToIdle("FRIDAY — Ready")
                }
            }
        }, delayMs)
    }

    fun setBatteryMode(mode: BatteryMode) {
        settingsRepo.updateBatteryMode(mode)
    }

    fun toggleForegroundService(enable: Boolean) {
        if (enable) {
            FridayForegroundService.startService(context)
        } else {
            FridayForegroundService.stopService(context)
        }
        _state.value = _state.value.copy(isForegroundServiceRunning = enable)
    }

    fun cleanup() {
        cancelInactivityTimeout()
        centralAudioController.releaseMic {
            wakeWordEngine.stopDetection()
            voiceEngine.shutdown()
        }
    }
}
