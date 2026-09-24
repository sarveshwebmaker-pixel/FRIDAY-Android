package com.example.actions.tools

import android.content.Context
import com.example.actions.ActionResult
import com.example.memory.FridayMemoryVault

class MemorySaveTool : FridayTool {
    override val id = "MEMORY_SAVE"
    override val name = "Personal Memory Vault: Save"
    override val description = "Stores user facts, preferences, and details in encrypted local storage"

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val vault = FridayMemoryVault(context)
        val key = parameters["key"] ?: parameters["topic"] ?: "user_fact"
        val fact = parameters["fact"] ?: parameters["value"] ?: parameters["memory"] ?: ""

        if (fact.isBlank()) {
            return ActionResult.MissingParameter("fact", "What would you like me to remember, Boss?")
        }

        if (vault.isSensitiveCredential(key) || vault.isSensitiveCredential(fact)) {
            return ActionResult.Failure(
                error = "Security Policy: Credentials cannot be stored",
                userMessage = "Boss, for your security, I cannot store passwords, PINs, or financial credentials in memory."
            )
        }

        val success = vault.remember(key, fact)
        return if (success) {
            ActionResult.Success(
                message = "Saved to memory: $fact",
                spokenDetail = "I've committed that to memory, Boss."
            )
        } else {
            ActionResult.Failure("Failed encrypting memory", "I couldn't write that to encrypted storage, Boss.")
        }
    }
}

class MemoryReadTool : FridayTool {
    override val id = "MEMORY_READ"
    override val name = "Personal Memory Vault: Recall"
    override val description = "Retrieves remembered user facts and preferences from encrypted local storage"

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val vault = FridayMemoryVault(context)
        val query = parameters["query"] ?: parameters["topic"] ?: parameters["key"] ?: ""

        if (query.isBlank() || query.contains("everything") || query.contains("all") || query.contains("about me")) {
            val all = vault.getAllMemories()
            if (all.isEmpty()) {
                return ActionResult.Success(
                    message = "Memory vault empty",
                    spokenDetail = "My memory vault is currently empty, Boss. Tell me what to remember anytime."
                )
            }
            val summary = all.entries.joinToString(". ") { (k, v) -> "$k is $v" }
            return ActionResult.Success(
                message = summary,
                spokenDetail = "Here is what I remember about you, Boss: $summary."
            )
        }

        val recalled = vault.recall(query)
        return if (recalled != null) {
            ActionResult.Success(
                message = recalled,
                spokenDetail = "You told me: $recalled, Boss."
            )
        } else {
            ActionResult.NotFound("memory", "I don't have a record of that in my memory yet, Boss.")
        }
    }
}

class MemoryDeleteTool : FridayTool {
    override val id = "MEMORY_DELETE"
    override val name = "Personal Memory Vault: Forget"
    override val description = "Deletes or clears a saved memory from local storage"

    override suspend fun execute(parameters: Map<String, String>, context: Context): ActionResult {
        val vault = FridayMemoryVault(context)
        val query = parameters["query"] ?: parameters["topic"] ?: parameters["key"] ?: ""

        if (query.isBlank() || query.contains("everything") || query.contains("all")) {
            vault.clearAll()
            return ActionResult.Success(
                message = "Cleared all memories",
                spokenDetail = "I have cleared all remembered details, Boss."
            )
        }

        val removed = vault.forget(query)
        return if (removed) {
            ActionResult.Success(
                message = "Forgot: $query",
                spokenDetail = "I've forgotten that, Boss."
            )
        } else {
            ActionResult.NotFound("memory", "I couldn't find a matching memory to forget, Boss.")
        }
    }
}
