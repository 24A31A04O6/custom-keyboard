package com.babeltech.babelkey.core.suggestion

import com.babeltech.babelkey.core.spellcheck.SpellChecker

/**
 * AutocorrectEngine — thin wrapper over [SpellChecker] that also tracks
 * whether a word was autocorrected (for [UndoManager]).
 */
class AutocorrectEngine(private val spellChecker: SpellChecker) {
    data class Result(val original: String, val corrected: String, val didCorrect: Boolean)

    fun autocorrect(word: String): Result {
        val c = spellChecker.correct(word)
        return if (c != null && !c.equals(word, ignoreCase = true)) Result(word, c, true)
        else Result(word, word, false)
    }
}
