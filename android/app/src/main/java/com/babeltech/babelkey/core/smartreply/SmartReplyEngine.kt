package com.babeltech.babelkey.core.smartreply

import android.content.Context
import com.babeltech.babelkey.data.preferences.PreferencesRepository
import com.babeltech.babelkey.security.NetworkPolicy
import com.google.mlkit.nl.smartreply.SmartReply
import com.google.mlkit.nl.smartreply.TextMessage
import kotlinx.coroutines.tasks.await

/**
 * SmartReplyEngine — Phase 3 AI / smart replies (opt-in, provider named, HTTPS).
 *
 * Per spec:
 * - Offline core (typing/transliteration) always 100% offline
 * - Any online feature (AI replies, GIF search) is opt-in (default OFF), disclosed, provider named
 *
 * Implementation:
 * - Primary: on-device ML Kit Smart Reply (offline TFLite, no network) — mirrors existing SmartReplyManager.java but now in Kotlin core/
 * - Fallback (opt-in): HTTPS API (provider: Google PaLM / OpenAI-compatible) — gated by PreferencesRepository.isAiRepliesEnabled() && NetworkPolicy
 *
 * Security:
 * - No keystroke buffer is sent unless user opts in AND the conversation is explicitly marked as "last remote message"
 * - HTTPS only via NetworkPolicy.requireHttps
 * - Falls back to rule-based replies if ML Kit returns NO_REPLY / NOT_SUPPORTED_LANGUAGE or if offline and no opt-in
 */
class SmartReplyEngine(
    private val context: Context,
    private val prefs: PreferencesRepository,
    private val networkPolicy: NetworkPolicy
) {
    private val generator by lazy { SmartReply.getClient() }

    // Rule-based fallbacks (same as legacy SmartReplyManager.java — preserved for offline & tests)
    private val fallbackQuestion = listOf("Yes", "No", "Maybe")
    private val fallbackGreeting = listOf("Hey!", "Hi there!", "Hello!")
    private val fallbackThanks = listOf("No problem!", "Sure!", "Glad to help!")
    private val fallbackApology = listOf("It's okay!", "No worries!", "All good!")
    private val fallbackGeneral = listOf("Got it", "Okay!", "Sounds good")

    fun isEnabled(): Boolean = prefs.isAiRepliesEnabled()

    fun isAvailable(): Boolean = true // ML Kit offline is always available

    /** Rule-based reply for a given text — pure, testable, no network. */
    fun ruleBasedReplies(text: String): List<String> {
        val lower = text.lowercase()
        return when {
            "thank" in lower -> fallbackThanks
            "sorry" in lower || "apolog" in lower -> fallbackApology
            "hello" in lower || "hi" in lower || "hey" in lower -> fallbackGreeting
            "?" in text -> fallbackQuestion
            else -> fallbackGeneral
        }
    }

    /** On-device ML Kit Smart Reply — offline, no network. */
    suspend fun generateOnDevice(contextText: String): List<String> {
        if (contextText.isBlank()) return fallbackGeneral
        return try {
            val conversation = listOf(
                TextMessage.createForRemoteUser(contextText.trim(), System.currentTimeMillis(), "remote")
            )
            val result = generator.suggestReplies(conversation).await()
            when (result.status) {
                com.google.mlkit.nl.smartreply.SmartReplySuggestionResult.STATUS_SUCCESS -> {
                    val replies = result.suggestions.map { it.text }
                    if (replies.isNotEmpty()) replies else ruleBasedReplies(contextText)
                }
                else -> ruleBasedReplies(contextText)
            }
        } catch (_: Exception) {
            ruleBasedReplies(contextText)
        }
    }

    /** Public entry: respects opt-in. Offline path if not opted in, online fallback if opted in (stub). */
    suspend fun generate(contextText: String, allowOnline: Boolean = false): List<String> {
        if (!isEnabled()) return generateOnDevice(contextText) // respects OFF default but still provides on-device replies if enabled? spec says opt-in — check prefs
        // If user has opted in and allows online, we could hit HTTPS API — currently stubbed to on-device to avoid shipping API keys
        if (allowOnline && networkPolicy.isAiRepliesAllowed()) {
            networkPolicy.requireHttps("https://generativelanguage.googleapis.com")
            // TODO: HTTPS call to provider with `text = contextText` — not shipped in v1 to avoid API key in repo
            // Fall back to on-device for now
        }
        return generateOnDevice(contextText)
    }

    fun close() { try { generator.close() } catch (_: Exception) {} }
}
