package com.babeltech.babelkey.core.input

import com.babeltech.babelkey.core.suggestion.AutocorrectEngine
import com.babeltech.babelkey.core.suggestion.UndoManager

/**
 * KeyEventProcessor — pure key handling (no android.view.KeyEvent dependency for testability).
 *
 * Converts key codes / characters into text operations that `service/ime` forwards to InputConnection.
 * Handles composing region bookkeeping via [EditorCompat] and autocorrect via [AutocorrectEngine].
 */
class KeyEventProcessor(
    private val autocorrect: AutocorrectEngine? = null,
    private val undoManager: UndoManager? = null
) {
    sealed class Action {
        data class Commit(val text: String): Action()
        data class Delete(val count: Int): Action()
        data class Replace(val deleteCount: Int, val text: String): Action()
        object Close: Action()
    }

    private val composing = StringBuilder()

    fun onCharacter(c: Char, isPasswordField: Boolean = false): List<Action> {
        if (c == ' ') {
            // Commit composing word with autocorrect before space
            val word = composing.toString()
            val result = if (!isPasswordField) autocorrect?.autocorrect(word) else null
            val actions = mutableListOf<Action>()
            if (result != null && result.didCorrect) {
                actions += Action.Replace(word.length, result.corrected)
                undoManager?.record(result.original, result.corrected)
            }
            actions += Action.Commit(" ")
            composing.clear()
            return actions
        }
        composing.append(c)
        return listOf(Action.Commit(c.toString()))
    }

    fun onBackspace(): List<Action> {
        if (composing.isNotEmpty()) composing.deleteCharAt(composing.length - 1)
        return listOf(Action.Delete(1))
    }

    fun onSuggestionPicked(suggestion: String): List<Action> {
        val del = composing.length
        composing.clear()
        composing.append(suggestion)
        // Do not autocorrect a user-picked suggestion; but record for undo if it replaced something
        return listOf(Action.Replace(del, suggestion))
    }

    fun resetComposing() { composing.clear() }
    fun composingText(): String = composing.toString()
}
