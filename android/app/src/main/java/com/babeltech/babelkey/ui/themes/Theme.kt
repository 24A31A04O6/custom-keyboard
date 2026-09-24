package com.babeltech.babelkey.ui.themes

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Theme — built-in themes + custom colors. Follows system light/dark.
 *
 * Phase 1: light/dark + 2 presets (Amoled, Pastel). Google Fonts deferred to Phase 2.
 */
data class Theme(val name: String, val light: ColorScheme, val dark: ColorScheme)

object ColorSchemes {
    val Light = lightColorScheme(primary = Color(0xFF0066CC), surface = Color.White)
    val Dark = darkColorScheme(primary = Color(0xFF66B2FF), surface = Color(0xFF121212))
    val Amoled = darkColorScheme(primary = Color(0xFF00E5CC), surface = Color.Black, background = Color.Black)
    val Pastel = lightColorScheme(primary = Color(0xFFFF8FA3), surface = Color(0xFFFFF8F0))

    val presets: Map<String, Theme> = mapOf(
        "light" to Theme("Light", Light, Dark),
        "dark" to Theme("Dark", Light, Dark),
        "amoled" to Theme("Amoled", Amoled, Amoled),
        "pastel" to Theme("Pastel", Pastel, Pastel),
    )
}
