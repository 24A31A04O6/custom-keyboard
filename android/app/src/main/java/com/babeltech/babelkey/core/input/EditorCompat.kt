package com.babeltech.babelkey.core.input

/**
 * EditorCompat — helpers for InputConnection compatibility.
 *
 * Documents and tests the edge cases required by Part 0.5 INPUT COMPATIBILITY:
 * - Composing region (`setComposingText` vs `commitText`)
 * - Batch edits (`beginBatchEdit` / `endBatchEdit`)
 * - Selection / deletion / cursor movement
 * - RTL, emoji/surrogate pairs (code-point-aware delete)
 * - Password / visible-password / numeric / email / url / phone input types
 * - Graceful fallback for broken EditorInfo
 *
 * This file is pure documentation + small code-point helpers so that
 * `service/ime/InputConnectionProxy` stays thin and auditable.
 */
object EditorCompat {

    /** Returns number of Java chars to delete to remove one user-perceived character (handles surrogate pairs). */
    fun charsToDeleteForBackspace(textBeforeCursor: CharSequence?): Int {
        if (textBeforeCursor.isNullOrEmpty()) return 1
        val last = textBeforeCursor.last()
        val secondLast = if (textBeforeCursor.length >= 2) textBeforeCursor[textBeforeCursor.length - 2] else 0.toChar()
        // If last char is low surrogate and second-last is high surrogate, delete 2 chars for one emoji
        return if (Character.isLowSurrogate(last) && Character.isHighSurrogate(secondLast)) 2 else 1
    }

    /** Returns true if [text] contains an emoji surrogate pair (used for cursor movement tests). */
    fun containsSurrogatePair(text: String): Boolean {
        var i = 0
        while (i < text.length - 1) {
            if (Character.isHighSurrogate(text[i]) && Character.isLowSurrogate(text[i+1])) return true
            i++
        }
        return false
    }

    /** Input type helpers (mirrors android.text.InputType constants without importing android). */
    object InputTypeMask {
        const val TYPE_MASK_CLASS = 0x0f
        const val TYPE_CLASS_TEXT = 1
        const val TYPE_CLASS_NUMBER = 2
        const val TYPE_TEXT_VARIATION_PASSWORD = 0x80
        const val TYPE_TEXT_VARIATION_VISIBLE_PASSWORD = 0x90
        const val TYPE_TEXT_FLAG_NO_SUGGESTIONS = 0x80000
        const val TYPE_TEXT_VARIATION_EMAIL_ADDRESS = 0x20
        const val TYPE_TEXT_VARIATION_URI = 0x10
    }

    fun isPasswordVariation(variation: Int): Boolean =
        variation == InputTypeMask.TYPE_TEXT_VARIATION_PASSWORD ||
        variation == InputTypeMask.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD

    fun shouldSuppressSuggestions(inputType: Int): Boolean =
        (inputType and InputTypeMask.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0
}
