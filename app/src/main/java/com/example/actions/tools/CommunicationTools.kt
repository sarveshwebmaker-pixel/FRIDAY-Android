package com.example.actions.tools

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.actions.ActionResult
import com.example.contacts.ContactResolutionStatus
import com.example.contacts.ContactResolver
import com.example.security.ActionRiskLevel

class ContactSearchTool(override val id: String = "SEARCH_CONTACT") : FridayTool {
    override val name = "Contact Search Engine"
    override val description = "Searches device contacts with exact and fuzzy matching"
    override val requiredPermissions = listOf(Manifest.permission.READ_CONTACTS)

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val query = parameters["contact"] ?: parameters["query"] ?: ""
        if (query.isBlank()) {
            return ActionResult.MissingParameter("contact", "Who would you like me to look up in your contacts, Boss?")
        }

        val resolution = ContactResolver.resolveContact(context, query)
        return when (resolution.status) {
            ContactResolutionStatus.PERMISSION_REQUIRED -> {
                ActionResult.PermissionRequired(Manifest.permission.READ_CONTACTS, resolution.message)
            }
            ContactResolutionStatus.NOT_FOUND -> {
                ActionResult.NotFound("contact", resolution.message)
            }
            ContactResolutionStatus.NO_PHONE_NUMBER -> {
                val name = resolution.contact?.displayName ?: query
                ActionResult.Success(
                    message = "Found $name (No phone number saved)",
                    spokenDetail = resolution.message,
                    outputData = mapOf("contact" to name)
                )
            }
            ContactResolutionStatus.MULTIPLE_MATCHES -> {
                ActionResult.DisambiguationRequired(
                    prompt = resolution.message,
                    candidates = resolution.candidateMatches.map { it.displayName }
                )
            }
            ContactResolutionStatus.MATCH -> {
                val contact = resolution.contact!!
                ActionResult.Success(
                    message = "Found ${contact.displayName}: ${contact.phoneNumber}",
                    spokenDetail = "I found ${contact.displayName}, phone number is ${contact.phoneNumber}, Boss.",
                    outputData = mapOf("contact" to contact.displayName, "phone" to contact.phoneNumber)
                )
            }
        }
    }
}

class WhatsAppCallTool : FridayTool {
    override val id = "WHATSAPP_CALL"
    override val name = "WhatsApp Voice Call"
    override val description = "Initiates a WhatsApp voice call to a verified contact"
    override val requiredPermissions = listOf(Manifest.permission.READ_CONTACTS)

    companion object {
        private const val TAG = "WhatsAppCallTool"
    }

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val contactName = parameters["contact"] ?: parameters["contactName"] ?: ""
        if (contactName.isBlank()) {
            return ActionResult.MissingParameter("contact", "Who should I call on WhatsApp, Boss?")
        }

        val pm = context.packageManager
        val isInstalled = try {
            pm.getPackageInfo("com.whatsapp", 0)
            true
        } catch (_: Exception) {
            try {
                pm.getPackageInfo("com.whatsapp.w4b", 0)
                true
            } catch (_: Exception) {
                false
            }
        }

        if (!isInstalled) {
            return ActionResult.NotFound("WhatsApp", "WhatsApp is not installed on your phone, Boss.")
        }

        val resolution = ContactResolver.resolveContact(context, contactName)
        when (resolution.status) {
            ContactResolutionStatus.PERMISSION_REQUIRED -> {
                return ActionResult.PermissionRequired(Manifest.permission.READ_CONTACTS, resolution.message)
            }
            ContactResolutionStatus.NOT_FOUND -> {
                return ActionResult.NotFound("contact", resolution.message)
            }
            ContactResolutionStatus.NO_PHONE_NUMBER -> {
                return ActionResult.Failure(resolution.message, resolution.message)
            }
            ContactResolutionStatus.MULTIPLE_MATCHES -> {
                return ActionResult.DisambiguationRequired(
                    prompt = resolution.message,
                    candidates = resolution.candidateMatches.map { it.displayName }
                )
            }
            ContactResolutionStatus.MATCH -> { /* proceed */ }
        }

        val targetContact = resolution.contact!!

        // Query Android ContactsContract Data table for WhatsApp VoIP voice call row if not already found
        val whatsappDataId = targetContact.whatsAppDataId ?: ContactResolver.checkWhatsAppVoip(context, targetContact.displayName).second

        if (whatsappDataId != null) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    Uri.parse("content://com.android.contacts/data/$whatsappDataId"),
                    ContactResolver.WHATSAPP_VOIP_MIME
                )
                setPackage("com.whatsapp")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            return try {
                context.startActivity(intent)
                ActionResult.Success(
                    message = "WhatsApp voice call initiated to ${targetContact.displayName}",
                    spokenDetail = "Calling ${targetContact.displayName} on WhatsApp, Boss."
                )
            } catch (e: Exception) {
                ActionResult.Failure("Failed to launch WhatsApp VoIP call: ${e.message}", "Couldn't initiate the WhatsApp call to ${targetContact.displayName}, Boss.")
            }
        }

        // Strict: NEVER silently convert to a chat!
        return ActionResult.NotSupported(
            feature = "WHATSAPP_CALL",
            explanation = "I found ${targetContact.displayName}, but WhatsApp hasn't linked a VoIP voice call entry for them in Android contacts yet, Boss."
        )
    }
}

class WhatsAppChatTool : FridayTool {
    override val id = "WHATSAPP_CHAT"
    override val name = "WhatsApp Chat"
    override val description = "Opens a WhatsApp conversation thread with a contact"
    override val requiredPermissions = listOf(Manifest.permission.READ_CONTACTS)

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val contactName = parameters["contact"] ?: parameters["contactName"] ?: ""
        if (contactName.isBlank()) {
            return ActionResult.MissingParameter("contact", "Who should I chat with on WhatsApp, Boss?")
        }

        val resolution = ContactResolver.resolveContact(context, contactName)
        val targetName = when (resolution.status) {
            ContactResolutionStatus.MATCH -> resolution.contact?.displayName ?: contactName
            ContactResolutionStatus.MULTIPLE_MATCHES -> {
                return ActionResult.DisambiguationRequired(
                    prompt = resolution.message,
                    candidates = resolution.candidateMatches.map { it.displayName }
                )
            }
            ContactResolutionStatus.NOT_FOUND -> contactName
            else -> contactName
        }

        val cleanNumber = resolution.contact?.phoneNumber?.replace("[^0-9+]".toRegex(), "")
            ?: contactName.replace("[^0-9+]".toRegex(), "")

        val intent = if (cleanNumber.isNotBlank()) {
            Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber")).apply {
                setPackage("com.whatsapp")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            context.packageManager.getLaunchIntentForPackage("com.whatsapp")?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }

        return if (intent != null) {
            try {
                context.startActivity(intent)
                ActionResult.Success("Opening WhatsApp chat with $targetName", spokenDetail = "Opening chat with $targetName on WhatsApp, Boss.")
            } catch (e: Exception) {
                ActionResult.Failure("Could not open WhatsApp: ${e.message}")
            }
        } else {
            ActionResult.NotFound("WhatsApp", "WhatsApp is not installed on your phone, Boss.")
        }
    }
}

class WhatsAppMessageTool : FridayTool {
    override val id = "WHATSAPP_MESSAGE"
    override val name = "WhatsApp Message Sender"
    override val description = "Opens WhatsApp with a prefilled message for a recipient"
    override val requiredPermissions = listOf(Manifest.permission.READ_CONTACTS)

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val contactName = parameters["contact"] ?: parameters["contactName"] ?: ""
        val message = parameters["message"] ?: parameters["text"] ?: ""

        if (contactName.isBlank()) {
            return ActionResult.MissingParameter("contact", "Who should I send the WhatsApp message to, Boss?")
        }

        val resolution = ContactResolver.resolveContact(context, contactName)
        when (resolution.status) {
            ContactResolutionStatus.PERMISSION_REQUIRED -> {
                return ActionResult.PermissionRequired(Manifest.permission.READ_CONTACTS, resolution.message)
            }
            ContactResolutionStatus.NOT_FOUND -> {
                return ActionResult.NotFound("contact", resolution.message)
            }
            ContactResolutionStatus.MULTIPLE_MATCHES -> {
                return ActionResult.DisambiguationRequired(
                    prompt = resolution.message,
                    candidates = resolution.candidateMatches.map { it.displayName }
                )
            }
            else -> { /* proceed */ }
        }

        val targetContact = resolution.contact
        val cleanNumber = targetContact?.phoneNumber?.replace("[^0-9+]".toRegex(), "")
            ?: contactName.replace("[^0-9+]".toRegex(), "")
        val displayName = targetContact?.displayName ?: contactName

        val encodedMsg = Uri.encode(message)
        val uri = if (cleanNumber.isNotBlank()) {
            Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber&text=$encodedMsg")
        } else {
            Uri.parse("https://api.whatsapp.com/send?text=$encodedMsg")
        }

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.whatsapp")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            ActionResult.Success("Opening WhatsApp with message for $displayName", spokenDetail = "Opening WhatsApp to message $displayName, Boss.")
        } catch (e: Exception) {
            ActionResult.Failure("Failed to launch WhatsApp message: ${e.message}")
        }
    }
}

class NativePhoneCallTool(override val id: String = "CALL_CONTACT") : FridayTool {
    override val name = "Native Phone Dialer & Caller"
    override val description = "Places a native phone call or opens the dialer"
    override val requiredPermissions = listOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS)
    override val riskLevel = ActionRiskLevel.SAFE

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val contactName = parameters["contact"] ?: parameters["contactName"] ?: ""
        if (contactName.isBlank()) {
            return ActionResult.MissingParameter("contact", "Who should I call, Boss?")
        }

        // If it's pure digits, call directly
        val isDirectDigits = contactName.replace("[^0-9+]".toRegex(), "").length >= 3 && !contactName.any { it.isLetter() }
        val targetNumber: String
        val displayName: String

        if (isDirectDigits) {
            targetNumber = contactName.replace("[^0-9+*#]".toRegex(), "")
            displayName = targetNumber
        } else {
            val resolution = ContactResolver.resolveContact(context, contactName)
            when (resolution.status) {
                ContactResolutionStatus.PERMISSION_REQUIRED -> {
                    return ActionResult.PermissionRequired(Manifest.permission.READ_CONTACTS, resolution.message)
                }
                ContactResolutionStatus.NOT_FOUND -> {
                    return ActionResult.NotFound("contact", resolution.message)
                }
                ContactResolutionStatus.NO_PHONE_NUMBER -> {
                    return ActionResult.Failure(resolution.message, resolution.message)
                }
                ContactResolutionStatus.MULTIPLE_MATCHES -> {
                    return ActionResult.DisambiguationRequired(
                        prompt = resolution.message,
                        candidates = resolution.candidateMatches.map { it.displayName }
                    )
                }
                ContactResolutionStatus.MATCH -> {
                    targetNumber = resolution.contact!!.phoneNumber.replace("[^0-9+*#]".toRegex(), "")
                    displayName = resolution.contact.displayName
                }
            }
        }

        val hasCallPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val intent = if (hasCallPerm) {
            Intent(Intent.ACTION_CALL, Uri.parse("tel:$targetNumber")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$targetNumber")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }

        return try {
            context.startActivity(intent)
            val actionVerb = if (hasCallPerm) "Calling" else "Opening dialer for"
            ActionResult.Success(
                message = "$actionVerb $displayName ($targetNumber)",
                spokenDetail = "$actionVerb $displayName, Boss."
            )
        } catch (e: Exception) {
            ActionResult.Failure("Could not place phone call: ${e.message}")
        }
    }
}

class SendSmsTool(override val id: String = "SEND_SMS") : FridayTool {
    override val name = "SMS Text Messaging"
    override val description = "Opens SMS compose or sends text message to contact"
    override val requiredPermissions = listOf(Manifest.permission.READ_CONTACTS)

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val contactName = parameters["contact"] ?: parameters["contactName"] ?: ""
        val message = parameters["message"] ?: parameters["text"] ?: ""

        val cleanInput = contactName.trim()
        val isDigits = cleanInput.matches(Regex("^[+0-9\\s\\-()]+$"))

        var targetNumber = cleanInput
        var targetLabel = if (contactName.isNotBlank()) contactName else "SMS"

        if (contactName.isNotBlank() && !isDigits) {
            val resolution = ContactResolver.resolveContact(context, contactName)
            when (resolution.status) {
                ContactResolutionStatus.PERMISSION_REQUIRED -> {
                    return ActionResult.PermissionRequired(
                        permission = Manifest.permission.READ_CONTACTS,
                        explanation = resolution.message
                    )
                }
                ContactResolutionStatus.NOT_FOUND -> {
                    return ActionResult.NotFound("contact", resolution.message)
                }
                ContactResolutionStatus.NO_PHONE_NUMBER -> {
                    return ActionResult.Failure(resolution.message, resolution.message)
                }
                ContactResolutionStatus.MULTIPLE_MATCHES -> {
                    return ActionResult.DisambiguationRequired(
                        prompt = resolution.message,
                        candidates = resolution.candidateMatches.map { it.displayName }
                    )
                }
                ContactResolutionStatus.MATCH -> {
                    targetNumber = resolution.contact!!.phoneNumber
                    targetLabel = resolution.contact.displayName
                }
            }
        }

        val cleanNumber = targetNumber.replace("[^0-9+]".toRegex(), "")
        val smsUri = if (cleanNumber.isNotBlank()) Uri.parse("smsto:$cleanNumber") else Uri.parse("smsto:")
        val intent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
            if (message.isNotBlank()) {
                putExtra("sms_body", message)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            ActionResult.Success(
                message = "Opened SMS composer for $targetLabel",
                spokenDetail = "Opening messages for $targetLabel, Boss."
            )
        } catch (e: Exception) {
            ActionResult.Failure("Failed to open SMS app: ${e.message}")
        }
    }
}

class SendEmailTool : FridayTool {
    override val id = "SEND_EMAIL"
    override val name = "Email Composer"
    override val description = "Composes an email to a recipient with subject and body"

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val recipient = parameters["recipient"] ?: parameters["email"] ?: parameters["contact"] ?: ""
        val subject = parameters["subject"] ?: ""
        val body = parameters["body"] ?: parameters["message"] ?: ""

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$recipient")
            if (subject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
            if (body.isNotBlank()) putExtra(Intent.EXTRA_TEXT, body)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            ActionResult.Success(
                message = "Opened email composer",
                spokenDetail = if (recipient.isNotBlank()) "Composing email to $recipient, Boss." else "Opening email client, Boss."
            )
        } catch (e: Exception) {
            ActionResult.Failure("Failed to launch email: ${e.message}")
        }
    }
}

class ShareContentTool : FridayTool {
    override val id = "SHARE_CONTENT"
    override val name = "Content Sharer"
    override val description = "Shares text, links, or info across applications"

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val content = parameters["content"] ?: parameters["text"] ?: ""
        val title = parameters["title"] ?: "Share with FRIDAY"

        if (content.isBlank()) {
            return ActionResult.MissingParameter("content", "What content should I share, Boss?")
        }

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, content)
        }
        val chooser = Intent.createChooser(sendIntent, title).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(chooser)
            ActionResult.Success("Share sheet opened", spokenDetail = "Opening share menu for you, Boss.")
        } catch (e: Exception) {
            ActionResult.Failure("Could not share content: ${e.message}")
        }
    }
}

class ClipboardTool : FridayTool {
    override val id = "CLIPBOARD_ACTION"
    override val name = "Clipboard Manager"
    override val description = "Copies or reads text from the Android system clipboard"

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return ActionResult.Failure("Clipboard service unavailable")

        val mode = (parameters["mode"] ?: parameters["action"] ?: "copy").lowercase()
        val text = parameters["text"] ?: parameters["content"] ?: ""

        return when (mode) {
            "copy", "set" -> {
                if (text.isBlank()) return ActionResult.MissingParameter("text", "What should I copy to the clipboard, Boss?")
                val clip = ClipData.newPlainText("FRIDAY", text)
                clipboard.setPrimaryClip(clip)
                ActionResult.Success("Copied to clipboard", spokenDetail = "Copied to your clipboard, Boss.")
            }
            "read", "get", "paste" -> {
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val pasted = clip.getItemAt(0).text?.toString() ?: ""
                    ActionResult.Success("Clipboard content: $pasted", spokenDetail = "Your clipboard says: $pasted, Boss.", outputData = mapOf("text" to pasted))
                } else {
                    ActionResult.NotFound("clipboard", "Your clipboard is currently empty, Boss.")
                }
            }
            else -> ActionResult.Failure("Unknown clipboard mode: $mode")
        }
    }
}
