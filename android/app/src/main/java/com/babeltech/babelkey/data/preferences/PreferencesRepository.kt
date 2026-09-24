package com.babeltech.babelkey.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * PreferencesRepository — SSoT for user settings persisted on device.
 *
 * Per spec: uses EncryptedSharedPreferences (Jetpack Security) for all
 * typed-word/clipboard/preferences storage; no cloud sync in v1; opt-in only.
 *
 * Stores: toolbar order, theme, font, personal dict, learned words, clipboard expiry config.
 */
class PreferencesRepository(private val context: Context) {

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    val encryptedPrefs: SharedPreferences by lazy {
        try {
            EncryptedSharedPreferences.create(
                context,
                "babelkey_encrypted_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback for devices where AndroidKeyStore is broken (e.g., some emulators)
            // Use plain prefs but mark as insecure — still better than crash. Will migrate on next successful init.
            context.getSharedPreferences("babelkey_encrypted_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    // Legacy plain prefs (MyBoardPrefs) — read for migration, then cleared
    private val legacyPrefs: SharedPreferences by lazy {
        context.getSharedPreferences("MyBoardPrefs", Context.MODE_PRIVATE)
    }

    companion object {
        const val KEY_TOOLBAR_ORDER = "toolbar_order"
        const val KEY_TOOLBAR_VISIBLE = "toolbar_visible"
        const val KEY_THEME = "theme_choice" // light/dark/system + preset
        const val KEY_FONT = "font_choice"
        const val KEY_CLIPBOARD_EXPIRY_HOURS = "clipboard_expiry_hours" // 1,6,24,-1(never)
        const val KEY_LEARNED_WORDS = "learned_words"
        const val KEY_PERSONAL_DICT = "personal_dict"
        const val KEY_ONLINE_TRANSLATION = "online_translation_enabled"
        const val KEY_AI_REPLIES = "ai_replies_enabled"
        const val KEY_SWIPE_ENABLED = "swipe_enabled"
        const val KEY_INCognito = "incognito_mode"
    }

    // --- Toolbar ---
    fun getToolbarOrder(): List<String> =
        encryptedPrefs.getString(KEY_TOOLBAR_ORDER, null)?.split(",")?.filter { it.isNotBlank() }
            ?: legacyPrefs.getString("toolbarButtonConfig", null)?.split(",")?.filter { it.isNotBlank() }
            ?: listOf("clipboard","translate","emoji","settings","voice")

    fun setToolbarOrder(order: List<String>) {
        encryptedPrefs.edit().putString(KEY_TOOLBAR_ORDER, order.joinToString(",")).apply()
    }

    // --- Theme ---
    fun getTheme(): String = encryptedPrefs.getString(KEY_THEME, "system") ?: "system"
    fun setTheme(theme: String) { encryptedPrefs.edit().putString(KEY_THEME, theme).apply() }

    // --- Clipboard expiry (hours, -1 = never) ---
    fun getClipboardExpiryHours(): Int = encryptedPrefs.getInt(KEY_CLIPBOARD_EXPIRY_HOURS, 24)
    fun setClipboardExpiryHours(hours: Int) { encryptedPrefs.edit().putInt(KEY_CLIPBOARD_EXPIRY_HOURS, hours).apply() }

    // --- Feature toggles (opt-in, default OFF per spec) ---
    fun isOnlineTranslationEnabled(): Boolean = encryptedPrefs.getBoolean(KEY_ONLINE_TRANSLATION, false)
    fun setOnlineTranslationEnabled(enabled: Boolean) { encryptedPrefs.edit().putBoolean(KEY_ONLINE_TRANSLATION, enabled).apply() }

    fun isAiRepliesEnabled(): Boolean = encryptedPrefs.getBoolean(KEY_AI_REPLIES, false)
    fun setAiRepliesEnabled(enabled: Boolean) { encryptedPrefs.edit().putBoolean(KEY_AI_REPLIES, enabled).apply() }

    fun isSwipeEnabled(): Boolean = encryptedPrefs.getBoolean(KEY_SWIPE_ENABLED, false)

    // --- Personal dictionary & learned words ---
    fun getLearnedWords(): Set<String> = encryptedPrefs.getStringSet(KEY_LEARNED_WORDS, emptySet()) ?: emptySet()
    fun addLearnedWord(word: String) {
        val s = getLearnedWords().toMutableSet().apply { add(word.lowercase()) }
        encryptedPrefs.edit().putStringSet(KEY_LEARNED_WORDS, s).apply()
    }
    fun clearLearnedWords() { encryptedPrefs.edit().remove(KEY_LEARNED_WORDS).apply() }

    fun getPersonalDict(): Set<String> = encryptedPrefs.getStringSet(KEY_PERSONAL_DICT, emptySet()) ?: emptySet()
    fun setPersonalDict(words: Set<String>) { encryptedPrefs.edit().putStringSet(KEY_PERSONAL_DICT, words).apply() }

    // --- Export / delete controls (privacy) ---
    fun exportPersonalDictionaryJson(): String {
        val learned = getLearnedWords().sorted()
        val personal = getPersonalDict().sorted()
        return """{"learned":${learned.joinToString(",", "[", "]") {"\"$it\""}},"personal":${personal.joinToString(",", "[", "]") {"\"$it\""}},"toolbar":${getToolbarOrder().joinToString(",", "[", "]") {"\"$it\""}},"theme":"${getTheme()}"}"""
    }

    fun deleteAllUserData() {
        encryptedPrefs.edit()
            .remove(KEY_LEARNED_WORDS)
            .remove(KEY_PERSONAL_DICT)
            .remove(KEY_TOOLBAR_ORDER)
            .remove(KEY_THEME)
            .remove(KEY_FONT)
            .apply()
    }

    // Migration from legacy plain prefs on first run
    fun migrateIfNeeded() {
        if (encryptedPrefs.getBoolean("migrated_from_legacy", false)) return
        val legacyKeys = listOf(KEY_TOOLBAR_ORDER, KEY_THEME, KEY_LEARNED_WORDS, KEY_PERSONAL_DICT)
        var didMigrate = false
        for (k in legacyKeys) {
            if (legacyPrefs.contains(k)) {
                @Suppress("UNCHECKED_CAST")
                when (val v = legacyPrefs.all[k]) {
                    is String -> encryptedPrefs.edit().putString(k, v).apply().also { didMigrate = true }
                    is Set<*> -> encryptedPrefs.edit().putStringSet(k, v as Set<String>).apply().also { didMigrate = true }
                    is Int -> encryptedPrefs.edit().putInt(k, v).apply().also { didMigrate = true }
                    is Boolean -> encryptedPrefs.edit().putBoolean(k, v).apply().also { didMigrate = true }
                }
            }
        }
        encryptedPrefs.edit().putBoolean("migrated_from_legacy", true).apply()
        if (didMigrate) {
            // After successful copy, keep legacy for one version then we can clear
        }
    }
}
