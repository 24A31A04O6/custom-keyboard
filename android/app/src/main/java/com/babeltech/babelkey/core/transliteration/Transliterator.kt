package com.babeltech.babelkey.core.transliteration

import com.babeltech.babelkey.data.dictionaries.DictionaryRepository

/**
 * Transliterator — Part 1 core purpose #1.
 *
 * Spec terminology correction (Gate 1): the bundled dictionaries store
 * **romanized translation** (e.g. "i" -> "nenu", "hello" -> "namaskaram"),
 * not Unicode Telugu script. True transliteration (roman -> నేను) is a future
 * layer handled by [ScriptConverter]. This class therefore implements
 * **dictionary-based English-to-romanized-Telugu translation** and keeps the
 * folder name `transliteration/` for spec compliance, with this KDoc disclaimer.
 *
 * Engine: pure logic, no Android UI imports. 100% offline. Testable in isolation.
 */
class Transliterator(private val dicts: DictionaryRepository) {

    // Reverse map for phrase lookup (lowercase key -> value)
    private val reverse: Map<String, String> by lazy {
        dicts.translations.entries.associate { (k, v) -> v.lowercase() to k }
    }

    /**
     * Translates a single English word to romanized Telugu if present.
     * Returns null if unknown — caller should fall back to suggestion/autocomplete.
     */
    fun translateWord(english: String): String? {
        if (english.isBlank()) return null
        return dicts.translations[english.lowercase().trim()]
    }

    /**
     * Translates a full sentence word-by-word. Leaves unknown words unchanged.
     * Multi-word phrase keys in translations.json (e.g. "thank you" -> "dhanyavadalu")
     * are matched greedily longest-first.
     */
    fun translateSentence(sentence: String): String {
        if (sentence.isBlank()) return sentence
        val lower = sentence.lowercase()
        // Greedy phrase replacement — try 3-word, 2-word, then 1-word windows
        val words = lower.split(Regex("\\s+"))
        val out = mutableListOf<String>()
        var i = 0
        while (i < words.size) {
            var matched = false
            for (len in 3 downTo 1) {
                if (i + len <= words.size) {
                    val phrase = words.subList(i, i + len).joinToString(" ")
                    val t = dicts.translations[phrase]
                    if (t != null) {
                        out += t
                        i += len
                        matched = true
                        break
                    }
                }
            }
            if (!matched) {
                out += dicts.translations[words[i]] ?: words[i]
                i++
            }
        }
        // Preserve original capitalisation on first word if sentence was capitalised
        if (sentence.firstOrNull()?.isUpperCase() == true && out.isNotEmpty()) {
            out[0] = out[0].replaceFirstChar { it.uppercase() }
        }
        return out.joinToString(" ")
    }

    /** Autocomplete for romanized Telugu words (telugu_dict / tel_eng_dict). */
    fun suggest(prefix: String, limit: Int = 5): List<String> {
        if (prefix.isBlank()) return emptyList()
        val lower = prefix.lowercase()
        // Try exact prefix bucket first, then fall back to scanning unique words
        dicts.teluguPrefix[lower]?.let { return it.take(limit) }
        dicts.telEngPrefix[lower]?.let { return it.take(limit) }
        // Prefix not a bucket key — collect words starting with prefix from all lists
        val pool = mutableSetOf<String>()
        for (lst in dicts.telEngPrefix.values) for (w in lst) if (w.startsWith(lower)) pool += w
        for (lst in dicts.teluguPrefix.values) for (w in lst) if (w.startsWith(lower)) pool += w
        return pool.sorted().take(limit)
    }
}
