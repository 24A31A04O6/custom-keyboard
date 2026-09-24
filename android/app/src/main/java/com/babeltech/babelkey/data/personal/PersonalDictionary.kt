package com.babeltech.babelkey.data.personal

import com.babeltech.babelkey.data.preferences.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * PersonalDictionary — user-added words + learned words, local only (no cloud sync in v1).
 * Backed by EncryptedSharedPreferences via PreferencesRepository.
 */
class PersonalDictionary(private val prefs: PreferencesRepository) {
    private val _words = MutableStateFlow<Set<String>>(emptySet())
    val words: StateFlow<Set<String>> = _words

    fun load() { _words.value = prefs.getPersonalDict() + prefs.getLearnedWords() }

    fun add(word: String) {
        if (word.isBlank()) return
        val w = word.trim().lowercase()
        val next = _words.value + w
        _words.value = next
        prefs.setPersonalDict(next)
    }

    fun remove(word: String) {
        val next = _words.value - word.lowercase()
        _words.value = next
        prefs.setPersonalDict(next)
    }

    fun contains(word: String): Boolean = word.lowercase() in _words.value

    fun learned(word: String) {
        prefs.addLearnedWord(word)
        load()
    }

    fun clearAll() {
        prefs.deleteAllUserData()
        _words.value = emptySet()
    }

    fun exportJson(): String = prefs.exportPersonalDictionaryJson()
}
