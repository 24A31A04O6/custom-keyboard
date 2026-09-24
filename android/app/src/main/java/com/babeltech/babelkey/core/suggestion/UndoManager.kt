package com.babeltech.babelkey.core.suggestion

/**
 * UndoManager — one tap/gesture reverts an unwanted autocorrect/autocomplete change.
 *
 * Pure logic, no UI. UI calls [record] after a correction, and [undo] when the user taps undo.
 */
class UndoManager {
    private var lastOriginal: String? = null
    private var lastCorrected: String? = null
    var canUndo: Boolean = false
        private set

    fun record(original: String, corrected: String) {
        if (original.equals(corrected, ignoreCase = true)) {
            canUndo = false; return
        }
        lastOriginal = original
        lastCorrected = corrected
        canUndo = true
    }

    /** Returns the original word to restore, or null if nothing to undo. */
    fun undo(): String? {
        if (!canUndo) return null
        val o = lastOriginal
        canUndo = false
        lastOriginal = null
        lastCorrected = null
        return o
    }

    fun clear() { canUndo = false; lastOriginal = null; lastCorrected = null }
}
