package com.babeltech.babelkey.core.suggestion

import com.babeltech.babelkey.data.dictionaries.DictionaryRepository
import com.babeltech.babelkey.data.personal.PersonalDictionary

/**
 * SuggestionEngine — autocorrect + prefix suggestion pipeline.
 *
 * Wires `data/dictionaries` into a ranked suggestion list.
 * No Android UI code. Used by `ui/suggestions/SuggestionStrip` via StateFlow.
 */
class SuggestionEngine(
    private val dicts: DictionaryRepository,
    private val personal: PersonalDictionary? = null,
    private val spellChecker: com.babeltech.babelkey.core.spellcheck.SpellChecker? = null
) {
    fun suggestionsFor(prefix: String, limit: Int = 5): List<String> {
        if (prefix.isBlank()) return emptyList()
        val lower = prefix.lowercase()
        val collected = mutableListOf<String>()

        // 1. Autocorrect correction (if exact misspelling, offer correction first)
        spellChecker?.correct(prefix)?.let { collected += it }

        // 2. English prefix suggestions
        dicts.engPrefix[lower]?.let { collected += it }
        dicts.suggestionsPrefix[lower]?.let { collected += it }

        // 3. Telugu roman prefix suggestions
        dicts.telEngPrefix[lower]?.let { collected += it }
        dicts.teluguPrefix[lower]?.let { collected += it }

        // 4. Fallback: scan all prefix buckets for words starting with prefix
        if (collected.isEmpty()) {
            for (lst in dicts.engPrefix.values) for (w in lst) if (w.startsWith(lower)) collected += w
            for (lst in dicts.telEngPrefix.values) for (w in lst) if (w.startsWith(lower)) collected += w
        }

        val personalWords = personal?.words?.value ?: emptySet()
        return RankingPolicy.rank(lower, collected.distinct(), personalWords, limit)
    }
}
