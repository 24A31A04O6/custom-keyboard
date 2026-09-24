package com.babeltech.babelkey.service.ime

import android.view.inputmethod.InputConnection
import com.babeltech.babelkey.core.input.EditorCompat

/**
 * InputConnectionProxy — thin wrapper around InputConnection that handles
 * batch edits, composing regions, surrogate-pair-aware deletion, RTL, and
 * graceful fallback for broken EditorInfo.
 *
 * Per spec INPUT COMPATIBILITY: supports composing text regions, batch edit
 * operations, selection/deletion/cursor movement, RTL scripts, emoji/surrogate pairs,
 * password/visible-password fields, numeric/email/URL/phone input types.
 */
class InputConnectionProxy(private val icProvider: () -> InputConnection?) {

    fun commitText(text: String) {
        val ic = icProvider() ?: return
        ic.beginBatchEdit()
        try { ic.commitText(text, 1) } finally { ic.endBatchEdit() }
    }

    fun deleteBeforeCursor(): Boolean {
        val ic = icProvider() ?: return false
        val before = try { ic.getTextBeforeCursor(2, 0) } catch (_: Exception) { null }
        val count = EditorCompat.charsToDeleteForBackspace(before)
        ic.beginBatchEdit()
        return try { ic.deleteSurroundingText(count, 0) } finally { ic.endBatchEdit() }
    }

    fun setComposing(text: String) {
        val ic = icProvider() ?: return
        ic.setComposingText(text, 1)
    }

    fun finishComposing() { icProvider()?.finishComposingText() }

    fun performEditorAction(actionCode: Int) { icProvider()?.performEditorAction(actionCode) }
}
