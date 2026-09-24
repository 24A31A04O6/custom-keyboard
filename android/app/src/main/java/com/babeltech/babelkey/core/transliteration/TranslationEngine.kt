package com.babeltech.babelkey.core.transliteration

import com.babeltech.babelkey.data.dictionaries.DictionaryRepository
import com.babeltech.babelkey.security.NetworkPolicy

/**
 * TranslationEngine — wraps [Transliterator] with optional online fallback.
 *
 * Per spec: core typing, transliteration, spellcheck, autocorrect are 100% offline.
 * Online translation fallback is opt-in (default OFF), disclosed, provider named.
 *
 * Phase 1: offline only. Online path is stubbed and gated by [NetworkPolicy].
 */
class TranslationEngine(
    private val transliterator: Transliterator,
    private val networkPolicy: NetworkPolicy
) {
    fun translateOffline(word: String): String? = transliterator.translateWord(word)

    fun translateSentenceOffline(sentence: String): String = transliterator.translateSentence(sentence)

    fun translateWithFallback(sentence: String, allowOnline: Boolean, callback: (String) -> Unit) {
        val offline = transliterator.translateSentence(sentence)
        // If offline already translated something (not identical), return it
        if (!offline.equals(sentence, ignoreCase = true)) {
            callback(offline); return
        }
        if (!allowOnline || !networkPolicy.isOnlineFallbackAllowed()) {
            callback(offline); return
        }
        // Online fallback would go here (HTTPS POST to provider). For Phase 1, never reaches network.
        callback(offline)
    }
}
