package com.babeltech.babelkey.suggestion;

import android.util.Log;

import com.google.mlkit.nl.smartreply.SmartReply;
import com.google.mlkit.nl.smartreply.SmartReplyGenerator;
import com.google.mlkit.nl.smartreply.SmartReplySuggestion;
import com.google.mlkit.nl.smartreply.SmartReplySuggestionResult;
import com.google.mlkit.nl.smartreply.TextMessage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * SmartReplyManager
 *
 * Wraps Google ML Kit SmartReply to generate on-device, context-aware quick
 * reply suggestions. All ML inference runs asynchronously; results are delivered
 * via {@link SmartReplyListener} on the main thread.
 *
 * Lifecycle:
 *   - Create once in SuggestionManager constructor
 *   - Call {@link #suggest(String, SmartReplyListener)} whenever the conversation
 *     context changes (e.g. after space/enter)
 *   - Call {@link #close()} from SuggestionManager#detach() to release the
 *     underlying TFLite model and prevent memory leaks
 */
public class SmartReplyManager {

    private static final String TAG = "SmartReplyManager";

    /** Callback delivered on the main thread once suggestions are ready. */
    public interface SmartReplyListener {
        /** @param replies Non-null, non-empty list of reply strings (1–5 items). */
        void onRepliesReady(List<String> replies);
    }

    // ── Rule-based fallbacks ─────────────────────────────────────────────────
    /** Returned when ML Kit reports STATUS_NO_REPLY or STATUS_NOT_SUPPORTED_LANGUAGE. */
    private static final List<String> FALLBACK_QUESTION =
            Arrays.asList("Yes", "No", "Maybe");
    private static final List<String> FALLBACK_GREETING =
            Arrays.asList("Hey!", "Hi there!", "Hello!");
    private static final List<String> FALLBACK_THANKS =
            Arrays.asList("No problem!", "Sure!", "Glad to help!");
    private static final List<String> FALLBACK_APOLOGY =
            Arrays.asList("It's okay!", "No worries!", "All good!");
    private static final List<String> FALLBACK_GENERAL =
            Arrays.asList("Got it", "Okay!", "Sounds good");

    private final SmartReplyGenerator generator;
    private boolean closed = false;

    public SmartReplyManager() {
        generator = SmartReply.getClient();
    }

    /**
     * Generate context-aware smart replies for the given conversation text.
     *
     * The {@code conversationText} is treated as the last message sent by the
     * remote user. ML Kit builds an internal conversation from it; real
     * multi-turn support can be added later by passing a full message list.
     *
     * @param conversationText The last received message / context visible to the user.
     * @param listener         Callback for results  always called even on failure.
     */
    public void suggest(String conversationText, SmartReplyListener listener) {
        if (closed || conversationText == null || conversationText.trim().isEmpty()) {
            listener.onRepliesReady(FALLBACK_GENERAL);
            return;
        }

        // Build a single-message conversation. ML Kit needs at least one message
        // marked as "remote user" to generate local-user replies.
        List<TextMessage> conversation = new ArrayList<>();
        conversation.add(TextMessage.createForRemoteUser(
                conversationText.trim(),
                System.currentTimeMillis(),
                "remote_user"
        ));

        generator.suggestReplies(conversation)
                .addOnSuccessListener(result -> {
                    List<String> replies = parseResult(result, conversationText);
                    listener.onRepliesReady(replies);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "ML Kit Smart Reply failed  using rule-based fallback", e);
                    listener.onRepliesReady(ruleBased(conversationText));
                });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Converts an ML Kit result into a list of reply strings.
     * Falls back to rule-based replies when ML Kit has nothing useful to offer.
     */
    private List<String> parseResult(SmartReplySuggestionResult result, String context) {
        int status = result.getStatus();

        if (status == SmartReplySuggestionResult.STATUS_NOT_SUPPORTED_LANGUAGE) {
            Log.d(TAG, "Language not supported  using rule-based fallback");
            return ruleBased(context);
        }
        if (status == SmartReplySuggestionResult.STATUS_NO_REPLY) {
            Log.d(TAG, "No ML reply generated  using rule-based fallback");
            return ruleBased(context);
        }

        // STATUS_SUCCESS
        List<SmartReplySuggestion> suggestions = result.getSuggestions();
        if (suggestions == null || suggestions.isEmpty()) {
            return ruleBased(context);
        }

        List<String> replies = new ArrayList<>();
        for (SmartReplySuggestion s : suggestions) {
            String text = s.getText();
            if (text != null && !text.trim().isEmpty()) {
                replies.add(text.trim());
            }
            if (replies.size() >= 5) break; // cap at 5 chips
        }
        return replies.isEmpty() ? ruleBased(context) : replies;
    }

    /**
     * Simple rule-based fallback when ML Kit cannot produce replies.
     * Covers the most common conversational patterns without any network call.
     */
    private List<String> ruleBased(String text) {
        if (text == null) return FALLBACK_GENERAL;
        String lower = text.toLowerCase();
        if (lower.contains("?"))                                        return FALLBACK_QUESTION;
        if (lower.contains("hello") || lower.contains("hi ")
                || lower.contains("hey") || lower.startsWith("hi"))    return FALLBACK_GREETING;
        if (lower.contains("thank"))                                    return FALLBACK_THANKS;
        if (lower.contains("sorry") || lower.contains("apolog"))       return FALLBACK_APOLOGY;
        return FALLBACK_GENERAL;
    }

    /**
     * Release the underlying TFLite model. Must be called when the IME is destroyed.
     * After closing, {@link #suggest} returns the general fallback immediately.
     */
    public void close() {
        if (!closed) {
            closed = true;
            try {
                generator.close();
                Log.d(TAG, "SmartReplyGenerator closed");
            } catch (Exception e) {
                Log.w(TAG, "Error closing SmartReplyGenerator", e);
            }
        }
    }
}
