package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Native Android Accessibility Service enabling FRIDAY to interact with on-screen UI
 * when granted by the user in Android Accessibility Settings.
 *
 * Supports semantic node finding (by text, viewId, contentDescription), text input,
 * scrolling, global navigation (back, home, recents, notifications, quick settings),
 * and gesture dispatching.
 */
class FridayAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "FridayAccessibility"

        @Volatile
        var instance: FridayAccessibilityService? = null
            private set

        fun isAvailable(): Boolean = instance != null

        fun getService(): FridayAccessibilityService? = instance
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "FRIDAY Accessibility Service connected and active")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Active window monitoring can be used for contextual screen understanding
    }

    override fun onInterrupt() {
        Log.w(TAG, "FRIDAY Accessibility Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        Log.i(TAG, "FRIDAY Accessibility Service destroyed")
    }

    // --- UI Automation Helpers ---

    /**
     * Finds a clickable node matching [targetText] and clicks it.
     */
    fun clickByText(targetText: String, exact: Boolean = false): Boolean {
        val root = rootInActiveWindow ?: return false
        val cleanTarget = targetText.trim()
        if (cleanTarget.isEmpty()) return false

        val matchingNodes = root.findAccessibilityNodeInfosByText(cleanTarget)
        for (node in matchingNodes) {
            val nodeText = node.text?.toString() ?: ""
            val matches = if (exact) {
                nodeText.equals(cleanTarget, ignoreCase = true)
            } else {
                nodeText.contains(cleanTarget, ignoreCase = true)
            }

            if (matches) {
                val clickableNode = findClickableParent(node)
                val success = clickableNode?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
                if (success) {
                    Log.i(TAG, "Successfully clicked node with text: '$cleanTarget'")
                    return true
                }
            }
        }
        return false
    }

    /**
     * Finds a node by its view ID resource name and clicks it.
     */
    fun clickById(viewId: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val matchingNodes = root.findAccessibilityNodeInfosByViewId(viewId)
        for (node in matchingNodes) {
            val clickable = findClickableParent(node)
            val success = clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
            if (success) {
                Log.i(TAG, "Successfully clicked node with viewId: '$viewId'")
                return true
            }
        }
        return false
    }

    /**
     * Finds a node by its content description and clicks it.
     */
    fun clickByDescription(description: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val cleanDesc = description.trim()
        if (cleanDesc.isEmpty()) return false

        return traverseAndClick(root) { node ->
            val desc = node.contentDescription?.toString() ?: ""
            desc.contains(cleanDesc, ignoreCase = true)
        }
    }

    /**
     * Sets text in the currently focused input or the first editable field found.
     */
    fun enterText(text: String, targetHint: String? = null): Boolean {
        val root = rootInActiveWindow ?: return false

        // Try to find the currently focused input field first
        val focusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusedNode != null && focusedNode.isEditable) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val success = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (success) {
                Log.i(TAG, "Set text in active focused input: '$text'")
                return true
            }
        }

        // Otherwise find an editable node matching hint or the first editable node
        var targetNode: AccessibilityNodeInfo? = null
        fun findEditable(node: AccessibilityNodeInfo) {
            if (targetNode != null) return
            if (node.isEditable) {
                if (targetHint != null) {
                    val hint = node.hintText?.toString() ?: ""
                    val current = node.text?.toString() ?: ""
                    if (hint.contains(targetHint, ignoreCase = true) || current.contains(targetHint, ignoreCase = true)) {
                        targetNode = node
                        return
                    }
                } else {
                    targetNode = node
                    return
                }
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                findEditable(child)
            }
        }

        findEditable(root)
        val nodeToEdit = targetNode ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val res = nodeToEdit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (res) {
            Log.i(TAG, "Set text in matching input: '$text'")
        }
        return res
    }

    /**
     * Scrolls the screen forward (down) or backward (up).
     */
    fun scrollScreen(forward: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val action = if (forward) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }

        fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.isScrollable) return node
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val found = findScrollable(child)
                if (found != null) return found
            }
            return null
        }

        val scrollable = findScrollable(root) ?: root
        return scrollable.performAction(action)
    }

    /**
     * Performs standard Android global actions: BACK, HOME, RECENTS, NOTIFICATIONS, QUICK_SETTINGS.
     */
    fun performGlobal(globalAction: Int): Boolean {
        return performGlobalAction(globalAction)
    }

    /**
     * Extracts visible screen text hierarchy for screen reading.
     */
    fun getVisibleScreenText(maxLength: Int = 1500): String {
        val root = rootInActiveWindow ?: return ""
        val builder = StringBuilder()

        fun collectText(node: AccessibilityNodeInfo) {
            if (builder.length >= maxLength) return
            val text = node.text?.toString()?.trim()
            val desc = node.contentDescription?.toString()?.trim()

            if (!text.isNullOrEmpty()) {
                builder.append(text).append(" ")
            } else if (!desc.isNullOrEmpty()) {
                builder.append("[").append(desc).append("] ")
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                collectText(child)
            }
        }

        collectText(root)
        return builder.toString().trim()
    }

    /**
     * Dispatches a tap gesture at physical screen coordinates.
     */
    fun tapAt(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply {
                moveTo(x, y)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, 50)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            return dispatchGesture(gesture, null, null)
        }
        return false
    }

    private fun findClickableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent
        }
        return node // Fallback to original node
    }

    private fun traverseAndClick(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): Boolean {
        if (predicate(node)) {
            val clickable = findClickableParent(node)
            if (clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                return true
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (traverseAndClick(child, predicate)) {
                return true
            }
        }
        return false
    }
}
