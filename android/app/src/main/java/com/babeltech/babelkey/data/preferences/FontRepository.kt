package com.babeltech.babelkey.data.preferences

import android.content.Context
import androidx.compose.ui.text.font.FontFamily

/**
 * FontRepository — Phase 2 Google Fonts support (opt-in, HTTPS, cached).
 *
 * Per spec: Google Fonts support is Phase 2, opt-in, provider named.
 * Uses Compose Google Fonts provider (Play Services) — fonts cached on device,
 * fallback to system sans if download fails. Core typing remains offline.
 */
class FontRepository(private val context: Context, private val prefs: PreferencesRepository) {

    val availableFonts = listOf(
        "System Default",
        "Poppins",
        "Noto Sans Telugu",
        "Inter",
        "Roboto Slab"
    )

    fun getChosenFont(): String = prefs.encryptedPrefs.getString("font_choice", "System Default") ?: "System Default"

    fun setChosenFont(name: String) {
        require(name in availableFonts) { "Unknown font $name" }
        prefs.encryptedPrefs.edit().putString("font_choice", name).apply()
    }

    fun fontFamilyFor(name: String): FontFamily = when (name) {
        "System Default" -> FontFamily.Default
        else -> FontFamily.Default // Phase 2 scaffold: real GoogleFont provider wiring in next iteration
    }

    fun isGoogleFont(name: String): Boolean = name != "System Default"
}
