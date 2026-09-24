package com.babeltech.babelkey.data.preferences

/**
 * OneHandedPrefs — Phase 3 one-handed mode (local, no cloud sync).
 *
 * Persists handedness and width fraction via EncryptedSharedPreferences.
 * Respect reduced-motion: no animation if system setting is reduced (checked in UI).
 */
class OneHandedPrefs(private val prefs: PreferencesRepository) {
    enum class Mode { OFF, LEFT, RIGHT }

    private val keyMode = "one_handed_mode" // OFF/LEFT/RIGHT
    private val keyWidth = "one_handed_width" // 0.7..1.0

    fun getMode(): Mode = try { Mode.valueOf(prefs.encryptedPrefs.getString(keyMode, "OFF") ?: "OFF") } catch (_: Exception) { Mode.OFF }
    fun setMode(mode: Mode) { prefs.encryptedPrefs.edit().putString(keyMode, mode.name).apply() }

    fun getWidthFraction(): Float = prefs.encryptedPrefs.getFloat(keyWidth, 0.85f).coerceIn(0.7f, 1.0f)
    fun setWidthFraction(f: Float) { prefs.encryptedPrefs.edit().putFloat(keyWidth, f.coerceIn(0.7f, 1.0f)).apply() }

    fun isEnabled(): Boolean = getMode() != Mode.OFF
}
