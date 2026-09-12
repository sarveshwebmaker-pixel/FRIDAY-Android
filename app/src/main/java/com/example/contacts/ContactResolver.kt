package com.example.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat

enum class ContactResolutionStatus {
    MATCH,
    MULTIPLE_MATCHES,
    NOT_FOUND,
    NO_PHONE_NUMBER,
    PERMISSION_REQUIRED
}

data class ContactMatch(
    val contactId: String,
    val displayName: String,
    val phoneNumber: String,
    val matchConfidence: Float,
    val hasWhatsAppVoip: Boolean = false,
    val whatsAppDataId: Long? = null
)

data class ContactResolutionResult(
    val status: ContactResolutionStatus,
    val contact: ContactMatch? = null,
    val candidateMatches: List<ContactMatch> = emptyList(),
    val message: String
)

/**
 * Robust, high-accuracy contact resolver for voice commands.
 * Handles spoken name normalization (suffixes, honorifics, possessives),
 * exact and phonetic/partial matching, WhatsApp VoIP capability checking,
 * and disambiguation for multiple matching contacts.
 */
object ContactResolver {

    private const val TAG = "ContactResolver"
    const val WHATSAPP_VOIP_MIME = "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"

    private val SUFFIX_PATTERNS = listOf(
        Regex("(?i)\\s+(?:bhai|bhau|bro|brother|sir|ji|madam|mam|uncle|aunty|friend|colleague|boss)\\b"),
        Regex("(?i)\\s+'s\\s+(?:contact|phone|number|mobile|chat)\\b"),
        Regex("(?i)'s\\b"),
        Regex("(?i)\\s+(?:contact|phone|number|mobile)\\b")
    )

    /**
     * Normalizes raw spoken input into a clean search token.
     * Examples:
     * - "Rahul Kumar" -> "rahul kumar"
     * - "Rahul bhai" -> "rahul"
     * - "Rahul sir" -> "rahul"
     * - "Rahul's contact" -> "rahul"
     * - "Rahul's phone" -> "rahul"
     */
    fun normalizeName(rawInput: String): String {
        var clean = rawInput.trim()
        for (pattern in SUFFIX_PATTERNS) {
            clean = clean.replace(pattern, "")
        }
        return clean.replace(Regex("[^a-zA-Z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()
    }

    /**
     * Searches Android Contacts database and resolves the requested target name.
     * Never blindly selects an ambiguous contact.
     */
    fun resolveContact(context: Context, rawTargetName: String): ContactResolutionResult {
        val normalizedTarget = normalizeName(rawTargetName)
        if (normalizedTarget.isBlank()) {
            return ContactResolutionResult(
                status = ContactResolutionStatus.NOT_FOUND,
                message = "No contact name specified."
            )
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_CONTACTS permission not granted")
            return ContactResolutionResult(
                status = ContactResolutionStatus.PERMISSION_REQUIRED,
                message = "I need permission to access your contacts to find $rawTargetName, Boss."
            )
        }

        val allContacts = loadAllContacts(context)
        if (allContacts.isEmpty()) {
            return ContactResolutionResult(
                status = ContactResolutionStatus.NOT_FOUND,
                message = "I couldn't find '$rawTargetName' in your contacts, Boss."
            )
        }

        // 1. Check Exact Matches (normalized displayName == normalizedTarget)
        val exactMatches = allContacts.filter {
            normalizeName(it.displayName) == normalizedTarget
        }

        if (exactMatches.size == 1) {
            val match = exactMatches[0]
            if (match.phoneNumber.isBlank()) {
                return ContactResolutionResult(
                    status = ContactResolutionStatus.NO_PHONE_NUMBER,
                    contact = match,
                    message = "I found ${match.displayName}, but there is no phone number saved for them, Boss."
                )
            }
            return ContactResolutionResult(
                status = ContactResolutionStatus.MATCH,
                contact = match.copy(matchConfidence = 1.0f),
                message = "Found ${match.displayName}."
            )
        } else if (exactMatches.size > 1) {
            val names = exactMatches.map { it.displayName }.distinct()
            return ContactResolutionResult(
                status = ContactResolutionStatus.MULTIPLE_MATCHES,
                candidateMatches = exactMatches,
                message = "I found ${exactMatches.size} contacts matching '$rawTargetName': ${names.joinToString(", ")}. Which one do you mean, Boss?"
            )
        }

        // 2. Check Word-Level / First-Name Matches (e.g. "Rahul" matches "Rahul Kumar" or "Rahul Sharma")
        val partialMatches = allContacts.filter { contact ->
            val normContact = normalizeName(contact.displayName)
            val tokens = normContact.split(" ")
            tokens.contains(normalizedTarget) || normContact.startsWith("$normalizedTarget ") || normContact.startsWith(normalizedTarget)
        }

        if (partialMatches.size == 1) {
            val match = partialMatches[0]
            if (match.phoneNumber.isBlank()) {
                return ContactResolutionResult(
                    status = ContactResolutionStatus.NO_PHONE_NUMBER,
                    contact = match,
                    message = "I found ${match.displayName}, but there is no phone number saved for them, Boss."
                )
            }
            val confidence = if (normalizeName(match.displayName).startsWith(normalizedTarget)) 0.92f else 0.85f
            return ContactResolutionResult(
                status = ContactResolutionStatus.MATCH,
                contact = match.copy(matchConfidence = confidence),
                message = "Found ${match.displayName}."
            )
        } else if (partialMatches.size > 1) {
            val names = partialMatches.map { it.displayName }.distinct()
            return ContactResolutionResult(
                status = ContactResolutionStatus.MULTIPLE_MATCHES,
                candidateMatches = partialMatches,
                message = "I found ${partialMatches.size} contacts for '$rawTargetName': ${names.take(3).joinToString(", ")}. Which one do you mean, Boss?"
            )
        }

        // 3. Fallback: Substring containment check
        val fuzzyMatches = allContacts.filter { contact ->
            val norm = normalizeName(contact.displayName)
            norm.contains(normalizedTarget) || normalizedTarget.contains(norm)
        }

        return when {
            fuzzyMatches.size == 1 -> {
                val match = fuzzyMatches[0]
                if (match.phoneNumber.isBlank()) {
                    ContactResolutionResult(
                        status = ContactResolutionStatus.NO_PHONE_NUMBER,
                        contact = match,
                        message = "I found ${match.displayName}, but there is no phone number saved for them, Boss."
                    )
                } else {
                    ContactResolutionResult(
                        status = ContactResolutionStatus.MATCH,
                        contact = match.copy(matchConfidence = 0.75f),
                        message = "Found ${match.displayName}."
                    )
                }
            }
            fuzzyMatches.size > 1 -> {
                val names = fuzzyMatches.map { it.displayName }.distinct()
                ContactResolutionResult(
                    status = ContactResolutionStatus.MULTIPLE_MATCHES,
                    candidateMatches = fuzzyMatches,
                    message = "I found multiple contacts matching '$rawTargetName': ${names.take(3).joinToString(", ")}. Which one do you mean, Boss?"
                )
            }
            else -> {
                ContactResolutionResult(
                    status = ContactResolutionStatus.NOT_FOUND,
                    message = "I couldn't find '$rawTargetName' in your contacts, Boss."
                )
            }
        }
    }

    private fun loadAllContacts(context: Context): List<ContactMatch> {
        val contacts = mutableListOf<ContactMatch>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        try {
            val cursor: Cursor? = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )

            cursor?.use {
                val idIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (it.moveToNext()) {
                    val id = if (idIdx != -1) it.getString(idIdx) ?: "" else ""
                    val name = if (nameIdx != -1) it.getString(nameIdx) ?: "" else ""
                    val number = if (numIdx != -1) it.getString(numIdx) ?: "" else ""

                    if (name.isNotBlank()) {
                        // Check if WhatsApp VoIP row exists for this contact
                        val (hasVoip, voipDataId) = checkWhatsAppVoip(context, name)
                        contacts.add(
                            ContactMatch(
                                contactId = id,
                                displayName = name,
                                phoneNumber = number,
                                matchConfidence = 1.0f,
                                hasWhatsAppVoip = hasVoip,
                                whatsAppDataId = voipDataId
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying contacts database", e)
        }

        return contacts
    }

    fun checkWhatsAppVoip(context: Context, contactDisplayName: String): Pair<Boolean, Long?> {
        try {
            val projection = arrayOf(ContactsContract.Data._ID)
            // Try matching by exact or pattern display name and VoIP MIME type
            val selection = "${ContactsContract.Data.MIMETYPE} = ? AND (${ContactsContract.Data.DISPLAY_NAME} = ? OR ${ContactsContract.Data.DISPLAY_NAME} LIKE ?)"
            val selectionArgs = arrayOf(WHATSAPP_VOIP_MIME, contactDisplayName, "%$contactDisplayName%")

            val cursor = context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val idIdx = it.getColumnIndex(ContactsContract.Data._ID)
                    if (idIdx != -1) {
                        val id = it.getLong(idIdx)
                        Log.i(TAG, "Found WhatsApp VoIP Data row ID $id for '$contactDisplayName'")
                        return Pair(true, id)
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "WhatsApp VoIP row check: ${e.message}")
        }
        return Pair(false, null)
    }
}
