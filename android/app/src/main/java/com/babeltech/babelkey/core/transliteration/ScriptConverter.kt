package com.babeltech.babelkey.core.transliteration

/**
 * ScriptConverter — stub for future Roman → Telugu script (నేను) transliteration.
 *
 * Gate 1 found zero Telugu Unicode in bundled dictionaries; all values are
 * romanized. True transliteration requires a character-level mapping table
 * (ISO 15919-style) that does not yet exist. This stub exists to satisfy
 * the spec's `core/transliteration/` layer and to provide a seam for Phase 3.
 *
 * Phase 1: returns input unchanged. Future: implement mapping and expose
 * `romanToTeluguScript()` with unit tests.
 */
object ScriptConverter {
    /** Returns [roman] unchanged in Phase 1 (no script table yet). */
    fun romanToTeluguScript(roman: String): String = roman

    fun isAvailable(): Boolean = false
}
