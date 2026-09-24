package com.babeltech.babelkey.security

import android.text.InputType
import android.view.inputmethod.EditorInfo

/**
 * SecureFieldGuard — cross-cutting guard for password / incognito fields.
 *
 * When guard returns true, caller must suppress:
 * - Suggestion strip / learning
 * - Clipboard history writes
 * - Personal dictionary additions
 */
object SecureFieldGuard {

    fun isSecure(editorInfo: EditorInfo?): Boolean {
        if (editorInfo == null) return false
        val inputType = editorInfo.inputType
        val clazz = inputType and InputType.TYPE_MASK_CLASS
        // TYPE_CLASS_TEXT with password variations
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        if (clazz == InputType.TYPE_CLASS_TEXT) {
            if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD) return true
            if (variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) return true
            if (variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) return true
        }
        if (clazz == InputType.TYPE_CLASS_NUMBER) {
            if (variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) return true
        }
        // TYPE_TEXT_FLAG_NO_SUGGESTIONS is a strong signal (often incognito)
        if ((inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) return true
        // EditorInfo privateImeOptions containing "incognito" (Gboard/ChromeOS convention)
        val opts = editorInfo.privateImeOptions ?: return false
        return opts.contains("incognito", ignoreCase = true)
    }

    fun isIncognito(editorInfo: EditorInfo?): Boolean {
        if (editorInfo == null) return false
        val opts = editorInfo.privateImeOptions ?: return false
        return opts.contains("incognito", ignoreCase = true)
    }
}
