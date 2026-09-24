package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.util.Log

/**
 * Android InCallService for direct telecom call management.
 * Hands-free answering and rejecting incoming cellular calls.
 */
class FridayInCallService : InCallService() {

    companion object {
        private const val TAG = "FridayInCallService"
        var activeCall: Call? = null
            private set

        @SuppressLint("MissingPermission")
        fun answerActiveCall(context: Context): Boolean {
            val call = activeCall
            if (call != null) {
                try {
                    call.answer(VideoProfile.STATE_AUDIO_ONLY)
                    Log.i(TAG, "Answered call via InCallService")
                    return true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed answering call via InCallService", e)
                }
            }

            // Fallback to TelecomManager.acceptRingingCall()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                try {
                    tm?.acceptRingingCall()
                    Log.i(TAG, "Answered call via TelecomManager.acceptRingingCall()")
                    return true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed answering call via TelecomManager", e)
                }
            }
            return false
        }

        @SuppressLint("MissingPermission")
        fun rejectActiveCall(context: Context): Boolean {
            val call = activeCall
            if (call != null) {
                try {
                    call.disconnect()
                    Log.i(TAG, "Disconnected call via InCallService")
                    return true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed disconnecting call via InCallService", e)
                }
            }

            // Fallback to TelecomManager.endCall()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                try {
                    @Suppress("DEPRECATION")
                    val res = tm?.endCall() ?: false
                    Log.i(TAG, "Ended call via TelecomManager.endCall(): $res")
                    return res
                } catch (e: Exception) {
                    Log.e(TAG, "Failed ending call via TelecomManager", e)
                }
            }
            return false
        }
    }

    override fun onCallAdded(call: Call?) {
        super.onCallAdded(call)
        activeCall = call
        Log.i(TAG, "onCallAdded: state=${call?.state}")
        call?.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call?, state: Int) {
                super.onStateChanged(call, state)
                Log.i(TAG, "Call state changed: $state")
                if (state == Call.STATE_DISCONNECTED) {
                    if (activeCall == call) {
                        activeCall = null
                    }
                }
            }
        })
    }

    override fun onCallRemoved(call: Call?) {
        super.onCallRemoved(call)
        Log.i(TAG, "onCallRemoved")
        if (activeCall == call) {
            activeCall = null
        }
    }
}
