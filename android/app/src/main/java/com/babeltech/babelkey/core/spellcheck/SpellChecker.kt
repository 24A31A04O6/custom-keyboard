package com.babeltech.babelkey.core.spellcheck

import com.babeltech.babelkey.data.dictionaries.DictionaryRepository

/**
 * SpellChecker — Part 1 core purpose #2: correct common misspellings.
 *
 * Uses `autocorrect_dict.json` (171 pairs). Pure logic, no UI.
 * Preserves capitalisation (e.g. "Bagundhi" -> "Bagundi").
 */
class SpellChecker(private val dicts: DictionaryRepository) {

    fun correct(word: String): String? {
        if (word.isBlank()) return null
        val lower = word.lowercase()
        val replacement = dicts.autocorrect[lower] ?: return null
        return if (word.first().isUpperCase()) replacement.replaceFirstChar { it.uppercase() } else replacement
    }

    fun isMisspelled(word: String): Boolean = dicts.autocorrect.containsKey(word.lowercase())

    fun correctSentence(sentence: String): String {
        if (sentence.isBlank()) return sentence
        return sentence.split(Regex("\\s+")).joinToString(" ") { correct(it) ?: it }
    }
}
