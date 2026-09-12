package com.example.actions.tools

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.util.Log
import com.example.actions.ActionResult
import com.example.service.FridayAccessibilityService

class UIAutomationTool : FridayTool {
    override val id = "UI_AUTOMATION"
    override val name = "UI Automation & Accessibility Controller"
    override val description = "Interacts with on-screen UI: taps buttons, types text, scrolls, navigates back"
    override val requiresAccessibility = true

    companion object {
        private const val TAG = "UIAutomationTool"
    }

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val service = FridayAccessibilityService.getService()
            ?: return ActionResult.PermissionRequired(
                "ACCESSIBILITY_SERVICE",
                "Boss, I need the FRIDAY Accessibility Service enabled in Android Settings to interact with on-screen buttons and apps."
            )

        val operation = (parameters["operation"] ?: parameters["action"] ?: "click").lowercase().trim()
        val target = parameters["target"] ?: parameters["text"] ?: parameters["label"]
        val inputText = parameters["inputText"] ?: parameters["value"] ?: parameters["text"]
        val viewId = parameters["viewId"] ?: parameters["id"]

        Log.i(TAG, "Executing UI automation: op=$operation, target=$target, viewId=$viewId")

        return when (operation) {
            "click", "tap", "press" -> {
                if (viewId != null && service.clickById(viewId)) {
                    ActionResult.Success("Clicked element with ID $viewId", spokenDetail = "Tapped, Boss.")
                } else if (target != null && service.clickByText(target)) {
                    ActionResult.Success("Tapped '$target'", spokenDetail = "Tapped $target, Boss.")
                } else if (target != null && service.clickByDescription(target)) {
                    ActionResult.Success("Tapped description '$target'", spokenDetail = "Tapped $target, Boss.")
                } else {
                    val x = parameters["x"]?.toFloatOrNull()
                    val y = parameters["y"]?.toFloatOrNull()
                    if (x != null && y != null && service.tapAt(x, y)) {
                        ActionResult.Success("Tapped at ($x, $y)", spokenDetail = "Tapped screen, Boss.")
                    } else {
                        ActionResult.NotFound("UI Element", "I couldn't find '${target ?: viewId}' on the screen to tap, Boss.")
                    }
                }
            }

            "type", "enter_text", "input", "write" -> {
                if (inputText.isNullOrBlank()) {
                    ActionResult.MissingParameter("inputText", "What text should I type on the screen, Boss?")
                } else {
                    val success = service.enterText(inputText, target)
                    if (success) {
                        ActionResult.Success("Typed '$inputText'", spokenDetail = "Entered the text, Boss.")
                    } else {
                        ActionResult.NotFound("Editable Input", "I couldn't find an editable input field on the screen, Boss.")
                    }
                }
            }

            "scroll_down", "scroll_forward", "scroll" -> {
                val success = service.scrollScreen(forward = true)
                if (success) {
                    ActionResult.Success("Scrolled down", spokenDetail = "Scrolled down, Boss.")
                } else {
                    ActionResult.NotSupported("SCROLL", "Cannot scroll further on this screen, Boss.")
                }
            }

            "scroll_up", "scroll_backward" -> {
                val success = service.scrollScreen(forward = false)
                if (success) {
                    ActionResult.Success("Scrolled up", spokenDetail = "Scrolled up, Boss.")
                } else {
                    ActionResult.NotSupported("SCROLL", "Cannot scroll further up, Boss.")
                }
            }

            "back", "go_back" -> {
                val success = service.performGlobal(AccessibilityService.GLOBAL_ACTION_BACK)
                if (success) {
                    ActionResult.Success("Navigated back", spokenDetail = "Heading back, Boss.")
                } else {
                    ActionResult.Failure("Failed to navigate back")
                }
            }

            "home", "go_home" -> {
                val success = service.performGlobal(AccessibilityService.GLOBAL_ACTION_HOME)
                if (success) {
                    ActionResult.Success("Returned to home screen", spokenDetail = "Heading home, Boss.")
                } else {
                    ActionResult.Failure("Failed to return home")
                }
            }

            "recents", "recent_apps", "app_switcher" -> {
                val success = service.performGlobal(AccessibilityService.GLOBAL_ACTION_RECENTS)
                if (success) {
                    ActionResult.Success("Opened recent apps", spokenDetail = "Showing recent apps, Boss.")
                } else {
                    ActionResult.Failure("Failed to open recent apps")
                }
            }

            "read_screen", "read_text", "what_is_on_screen" -> {
                val text = service.getVisibleScreenText(1000)
                if (text.isNotBlank()) {
                    val summary = text.take(200)
                    ActionResult.Success(
                        message = "Screen text: $text",
                        spokenDetail = "On screen: $summary",
                        outputData = mapOf("screenText" to text)
                    )
                } else {
                    ActionResult.NotFound("Screen text", "I didn't find readable text on the current screen, Boss.")
                }
            }

            else -> ActionResult.NotSupported(operation, "I don't know how to perform '$operation' on this app's UI, Boss.")
        }
    }
}
