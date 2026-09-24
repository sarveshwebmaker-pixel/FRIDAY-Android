package com.example.bubble

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.example.MainActivity
import com.example.core.OrbState
import kotlin.math.abs
import kotlin.math.sin

/**
 * Floating Listening Orb Manager for FRIDAY.
 *
 * Implements high-performance, non-blocking Android WindowManager overlay:
 * - Appears above other applications ONLY when FRIDAY is actively listening, thinking, speaking, or working.
 * - Disappears automatically when the interaction ends (OrbState.IDLE).
 * - Never blocks normal app usage or lingers permanently while idle.
 * - Distinguishes states: LISTENING (cyan), THINKING (amber), SPEAKING (vibrant emerald),
 *   WORKING (electric blue), ERROR (crimson).
 * - Fully draggable with touch listener; click brings up the main assistant interface.
 * - Safely handles overlay permissions, screen rotation, lifecycle changes, and service death.
 */
class FloatingBubbleManager(
    private val context: Context,
    private val onBubbleClick: (() -> Unit)? = null
) {

    companion object {
        private const val TAG = "FloatingBubbleManager"
        private const val ORB_SIZE_DP = 56
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var bubbleView: OrbOverlayView? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var isAttached = false
    private var currentOrbState: OrbState = OrbState.IDLE

    fun canEnableBubble(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * Updates the floating orb based on FRIDAY's current OrbState and whether the app is in foreground.
     * When the main app is backgrounded and FRIDAY is active, the orb is displayed.
     * When FRIDAY returns to IDLE, the orb is immediately dismissed.
     */
    fun updateOrbState(state: OrbState, isAppInForeground: Boolean) {
        mainHandler.post {
            currentOrbState = state

            // Only show overlay when the app is in the background and FRIDAY is actively engaging
            val shouldShow = !isAppInForeground && state != OrbState.IDLE && canEnableBubble()

            if (shouldShow) {
                showOrUpdateBubble(state)
            } else {
                hideBubble()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOrUpdateBubble(state: OrbState) {
        if (windowManager == null) return

        if (!isAttached || bubbleView == null) {
            val density = context.resources.displayMetrics.density
            val sizePx = (ORB_SIZE_DP * density).toInt()

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                sizePx,
                sizePx,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (context.resources.displayMetrics.widthPixels - sizePx - (16 * density).toInt())
                y = (context.resources.displayMetrics.heightPixels / 3)
            }
            windowParams = params

            val view = OrbOverlayView(context).apply {
                setState(state)
                setOnTouchListener(object : View.OnTouchListener {
                    private var initialX = 0
                    private var initialY = 0
                    private var initialTouchX = 0f
                    private var initialTouchY = 0f
                    private var isMoving = false

                    override fun onTouch(v: View, event: MotionEvent): Boolean {
                        val p = windowParams ?: return false
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                initialX = p.x
                                initialY = p.y
                                initialTouchX = event.rawX
                                initialTouchY = event.rawY
                                isMoving = false
                                return true
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val dx = (event.rawX - initialTouchX).toInt()
                                val dy = (event.rawY - initialTouchY).toInt()
                                if (abs(dx) > 10 || abs(dy) > 10) {
                                    isMoving = true
                                }
                                p.x = initialX + dx
                                p.y = initialY + dy
                                try {
                                    windowManager.updateViewLayout(v, p)
                                } catch (_: Exception) {}
                                return true
                            }
                            MotionEvent.ACTION_UP -> {
                                if (!isMoving) {
                                    // Click action
                                    handleBubbleClick()
                                }
                                return true
                            }
                        }
                        return false
                    }
                })
            }

            try {
                windowManager.addView(view, params)
                bubbleView = view
                isAttached = true
                Log.i(TAG, "Floating orb attached (State: $state)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to attach floating orb window", e)
                isAttached = false
                bubbleView = null
            }
        } else {
            bubbleView?.setState(state)
        }
    }

    private fun handleBubbleClick() {
        if (onBubbleClick != null) {
            onBubbleClick.invoke()
        } else {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("TRIGGER_VOICE", true)
            }
            context.startActivity(intent)
        }
    }

    fun hideBubble() {
        mainHandler.post {
            if (isAttached && bubbleView != null && windowManager != null) {
                try {
                    windowManager.removeView(bubbleView)
                    Log.i(TAG, "Floating orb removed")
                } catch (e: Exception) {
                    Log.w(TAG, "Error removing floating orb: ${e.message}")
                }
            }
            isAttached = false
            bubbleView = null
        }
    }

    fun destroy() {
        hideBubble()
    }

    /**
     * Custom animated View rendering the dynamic FRIDAY orb with distinct color states.
     */
    private class OrbOverlayView(context: Context) : View(context) {

        private var currentState: OrbState = OrbState.LISTENING
        private var pulsePhase = 0f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        private var animator: ValueAnimator? = null

        init {
            startAnimation()
        }

        fun setState(state: OrbState) {
            currentState = state
            postInvalidate()
        }

        private fun startAnimation() {
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1200
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                addUpdateListener { anim ->
                    pulsePhase = anim.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        override fun onDetachedFromWindow() {
            animator?.cancel()
            animator = null
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val maxRadius = (width.coerceAtMost(height) / 2f) * 0.85f

            // Colors based on state
            val (coreColor, glowColor) = when (currentState) {
                OrbState.LISTENING -> Pair(Color.rgb(0, 210, 255), Color.argb(120, 0, 180, 255))
                OrbState.THINKING -> Pair(Color.rgb(255, 180, 0), Color.argb(120, 255, 140, 0))
                OrbState.SPEAKING -> Pair(Color.rgb(0, 230, 150), Color.argb(140, 0, 200, 120))
                OrbState.WORKING -> Pair(Color.rgb(60, 120, 255), Color.argb(120, 40, 90, 230))
                OrbState.ERROR -> Pair(Color.rgb(255, 60, 80), Color.argb(130, 230, 30, 50))
                OrbState.IDLE, OrbState.OFFLINE -> Pair(Color.rgb(100, 110, 130), Color.argb(50, 80, 90, 110))
            }

            // Outer pulse glow
            val pulseRadius = maxRadius * (0.85f + 0.15f * sin(pulsePhase * Math.PI.toFloat() * 2).toFloat())
            glowPaint.color = glowColor
            glowPaint.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy, pulseRadius, glowPaint)

            // Inner solid core
            paint.color = coreColor
            paint.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy, maxRadius * 0.65f, paint)

            // White accent highlight
            paint.color = Color.argb(200, 255, 255, 255)
            canvas.drawCircle(cx - maxRadius * 0.18f, cy - maxRadius * 0.18f, maxRadius * 0.14f, paint)
        }
    }
}
