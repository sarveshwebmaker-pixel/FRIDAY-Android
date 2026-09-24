package com.example.actions.tools

import android.content.Context
import android.os.Build
import android.telecom.TelecomManager
import android.util.Log
import com.example.actions.ActionResult
import com.example.service.FridayInCallService

class CallControllerTool(override val id: String = "PHONE_CALL_CONTROL") : FridayTool {
    override val name = "Telecom Call Controller"
    override val description = "Accepts, answers, or ends/rejects an incoming or ongoing phone call via Android Telecom"

    companion object {
        private const val TAG = "CallControllerTool"
    }

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val action = (parameters["action"] ?: parameters["command"] ?: "answer").lowercase().trim()

        return try {
            when (action) {
                "answer", "accept", "pickup", "pick_up" -> {
                    if (FridayInCallService.answerActiveCall(context)) {
                        return ActionResult.Success("Answered phone call", spokenDetail = "Call answered, Boss.")
                    }

                    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && telecomManager != null) {
                        try {
                            telecomManager.acceptRingingCall()
                            ActionResult.Success("Answered phone call", spokenDetail = "Call answered, Boss.")
                        } catch (e: SecurityException) {
                            Log.w(TAG, "Telecom permission missing for acceptRingingCall", e)
                            ActionResult.PermissionRequired(
                                "android.permission.ANSWER_PHONE_CALLS",
                                "Boss, I need the Answer Phone Calls permission to pick up calls for you."
                            )
                        }
                    } else {
                        ActionResult.NotSupported(
                            "ANSWER_CALL_LEGACY",
                            "Answering calls programmatically requires Android 8.0 or newer, Boss."
                        )
                    }
                }
                "reject", "decline", "hangup", "hang_up", "end", "end_call" -> {
                    if (FridayInCallService.rejectActiveCall(context)) {
                        return ActionResult.Success("Ended phone call", spokenDetail = "Call ended, Boss.")
                    }

                    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && telecomManager != null) {
                        try {
                            val ended = telecomManager.endCall()
                            if (ended) {
                                ActionResult.Success("Ended phone call", spokenDetail = "Call ended, Boss.")
                            } else {
                                ActionResult.Success("Call ended signal sent", spokenDetail = "Disconnecting the call, Boss.")
                            }
                        } catch (e: SecurityException) {
                            Log.w(TAG, "Telecom permission missing for endCall", e)
                            ActionResult.PermissionRequired(
                                "android.permission.ANSWER_PHONE_CALLS",
                                "Boss, I need call management permission to end or decline calls."
                            )
                        }
                    } else {
                        ActionResult.NotSupported(
                            "END_CALL_LEGACY",
                            "Ending calls programmatically requires Android 9.0 or newer, Boss."
                        )
                    }
                }
                else -> {
                    ActionResult.Failure("Unknown call control action: $action", "I didn't understand the call command '$action', Boss.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed executing call control: ${e.message}", e)
            ActionResult.Failure("Call control failed: ${e.message}", "Couldn't manage the call: ${e.localizedMessage}")
        }
    }
}
