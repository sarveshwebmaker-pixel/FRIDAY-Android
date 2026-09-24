package com.example.actions.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.example.actions.ActionResult
import com.example.security.ActionRiskLevel
import java.net.URLEncoder

/**
 * Legitimate Android UPI & Mobile Payment Tool.
 *
 * Implements standard, compliant Android banking/payment flow:
 * - Prepares valid UPI transaction URI (upi://pay?pa=...&pn=...&am=...&cu=INR)
 * - Targets specific payment applications (Google Pay, PhonePe, Paytm, BHIM) if specified
 * - Launches the secure payment application for mandatory user PIN/biometric authentication
 * - NEVER bypasses Android banking security or attempts unauthorized background fund transfers
 * - Strictly verifies intent dispatch and clarifies that user PIN is required to authorize payment
 */
class PaymentTool : FridayTool {
    override val id: String = "tool_payment"
    override val name: String = "Mobile & UPI Payment"
    override val description: String = "Prepares and opens secure mobile/UPI payment in banking apps for user authorization"
    override val riskLevel: ActionRiskLevel = ActionRiskLevel.CONFIRM

    companion object {
        private const val PACKAGE_GPAY = "com.google.android.apps.nbu.paisa.user"
        private const val PACKAGE_PHONEPE = "com.phonepe.app"
        private const val PACKAGE_PAYTM = "net.one97.paytm"
        private const val PACKAGE_BHIM = "in.org.npci.upiapp"
    }

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val amount = parameters["amount"]?.trim()?.replace("₹", "")?.replace("$", "")?.trim()
        val payee = parameters["payee"] ?: parameters["contact"] ?: parameters["to"] ?: ""
        val targetApp = parameters["app"]?.lowercase() ?: ""
        val note = parameters["note"] ?: "Payment via FRIDAY"

        if (amount.isNullOrBlank()) {
            return ActionResult.Failure(
                error = "AMOUNT_REQUIRED",
                userMessage = "Please specify the amount you want to pay."
            )
        }

        if (payee.isBlank()) {
            return ActionResult.Failure(
                error = "PAYEE_REQUIRED",
                userMessage = "Who would you like to send $amount to?"
            )
        }

        // Format valid UPI URI
        // If payee contains @, treat as UPI VPA; otherwise treat as payee display name
        val encodedPayee = URLEncoder.encode(payee, "UTF-8")
        val encodedNote = URLEncoder.encode(note, "UTF-8")
        val upiUriBuilder = StringBuilder("upi://pay?")
        if (payee.contains("@")) {
            upiUriBuilder.append("pa=").append(payee).append("&")
        }
        upiUriBuilder.append("pn=").append(encodedPayee)
            .append("&am=").append(amount)
            .append("&cu=INR")
            .append("&tn=").append(encodedNote)

        val uri = Uri.parse(upiUriBuilder.toString())
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        // Target specific UPI app if requested
        val chosenPackage = when {
            targetApp.contains("google") || targetApp.contains("gpay") -> PACKAGE_GPAY
            targetApp.contains("phonepe") -> PACKAGE_PHONEPE
            targetApp.contains("paytm") -> PACKAGE_PAYTM
            targetApp.contains("bhim") -> PACKAGE_BHIM
            else -> null
        }

        val pm = context.packageManager
        if (chosenPackage != null) {
            val appInstalled = try {
                pm.getPackageInfo(chosenPackage, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
            if (appInstalled) {
                intent.setPackage(chosenPackage)
            }
        }

        // Verify that at least one UPI or banking app can handle this payment intent
        val activities = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        if (activities.isEmpty()) {
            // Fallback: check if requested payment app package can be opened directly
            val fallbackApp = chosenPackage ?: PACKAGE_GPAY
            val launchIntent = pm.getLaunchIntentForPackage(fallbackApp)
            if (launchIntent != null) {
                launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(launchIntent)
                return ActionResult.Success(
                    message = "Opened payment app for $payee.",
                    spokenDetail = "I've opened the payment app for $payee. Please enter the amount and your PIN to pay.",
                    outputData = mapOf("amount" to amount, "payee" to payee, "status" to "APP_OPENED")
                )
            }

            return ActionResult.Failure(
                error = "NO_UPI_APP",
                userMessage = "No UPI or banking app found on this device to process the payment."
            )
        }

        return try {
            context.startActivity(intent)
            val appName = when (chosenPackage) {
                PACKAGE_GPAY -> "Google Pay"
                PACKAGE_PHONEPE -> "PhonePe"
                PACKAGE_PAYTM -> "Paytm"
                PACKAGE_BHIM -> "BHIM"
                else -> "your payment app"
            }
            ActionResult.Success(
                message = "Prepared payment of ₹$amount to $payee in $appName.",
                spokenDetail = "I've prepared the payment of ₹$amount to $payee in $appName. Please enter your PIN to authorize.",
                outputData = mapOf("amount" to amount, "payee" to payee, "status" to "PREPARED")
            )
        } catch (e: Exception) {
            ActionResult.Failure(
                error = "PAYMENT_INTENT_FAILED: ${e.message}",
                userMessage = "Could not launch the payment application."
            )
        }
    }
}
