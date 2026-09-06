package com.babeltech.babelkey.managers;

import android.view.inputmethod.InputConnection;
import java.util.List;

/**
 * Callback interface that managers use to communicate back to MyKeyboardService
 * without holding a long-lived reference to InputMethodService.
 * All calls are delivered on the main thread.
 */
public interface ServiceCallback {
    /** Commit a text string at the current cursor position. */
    void commitText(String text);
    /** Called when a theme change has been applied — panels re-tint. */
    void onThemeChanged(boolean isDark);
    /** Refresh the suggestion chip bar with new candidates. */
    void onSuggestionsReady(List<String> suggestions);
    /** Called by GestureHandler after a swipe ends. */
    void onSwipeSuggestionsReady(List<String> suggestions);
    /** Called by VoiceTypingManager after voice result is inserted. */
    void onVoiceResultInserted();
    /**
     * Provides the active InputConnection. Returns null if not active.
     * Do NOT cache the returned value.
     */
    InputConnection getInputConnection();
}
