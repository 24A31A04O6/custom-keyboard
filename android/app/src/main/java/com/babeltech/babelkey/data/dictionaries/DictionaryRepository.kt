package com.babeltech.babelkey.data.dictionaries

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * DictionaryRepository — Single Source of Truth for all word dictionaries.
 *
 * Data layer: `data/dictionaries/` owns loading + caching of the 7 JSON files
 * that were inventoried at Gate 1. Both `core/` and `ui/` read via this
 * repository — never duplicate Maps.
 *
 * - Primary (Part 1, never reduced): translations.json (1199), autocorrect_dict.json (171)
 * - Supporting: tel_eng_dict.json (1757/1313), telugu_dict.json (62/163),
 *               eng_dict.json (2010/1428), suggestions_dict.json (19/70),
 *               emoji_suggestions.json (29)
 *
 * Offline: all loads are from `assets/dictionaries/` (bundled). 100% offline.
 *
 * Migration: files are copied from `res/raw/` via `scripts/migrate_dictionaries.py`.
 * Checksums are verified against Gate-1 snapshot before copy.
 */
class DictionaryRepository(private val context: Context) {

    // KV: English -> romanized Telugu (e.g. "i" -> "nenu", "hello" -> "namaskaram")
    val translations: Map<String, String> by lazy { loadKv("dictionaries/translations.json") }

    // KV: misspelling -> correction (e.g. "bagundhi" -> "bagundi")
    val autocorrect: Map<String, String> by lazy { loadKv("dictionaries/autocorrect_dict.json") }

    // KV: keyword -> emoji string (space-separated)
    val emojiSuggestions: Map<String, String> by lazy { loadKv("dictionaries/emoji_suggestions.json") }

    // Prefix: prefix -> word list
    val engPrefix: Map<String, List<String>> by lazy { loadPrefix("dictionaries/eng_dict.json") }
    val telEngPrefix: Map<String, List<String>> by lazy { loadPrefix("dictionaries/tel_eng_dict.json") }
    val teluguPrefix: Map<String, List<String>> by lazy { loadPrefix("dictionaries/telugu_dict.json") }
    val suggestionsPrefix: Map<String, List<String>> by lazy { loadPrefix("dictionaries/suggestions_dict.json") }

    // Manifest for auditing (checksums + counts)
    val manifest: Map<String, DictionaryEntry> by lazy { loadManifest() }

    data class DictionaryEntry(val bytes: Int, val sha256: String, val entries: Int, val kind: String)

    private fun loadKv(assetPath: String): Map<String, String> {
        val json = readAsset(assetPath) ?: return emptyMap()
        if (json.isBlank()) return emptyMap()
        val obj = JSONObject(json)
        val out = mutableMapOf<String, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = obj.optString(k, null) ?: continue
            if (k.isBlank() || v.isBlank()) continue
            out[k.trim().lowercase()] = v.trim()
        }
        return out
    }

    private fun loadPrefix(assetPath: String): Map<String, List<String>> {
        val json = readAsset(assetPath) ?: return emptyMap()
        if (json.isBlank()) return emptyMap()
        val obj = JSONObject(json)
        val out = mutableMapOf<String, List<String>>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val arr: JSONArray = obj.optJSONArray(k) ?: continue
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val v = arr.optString(i, null) ?: continue
                if (v.isBlank()) continue
                list.add(v.trim())
            }
            if (k.isBlank() || list.isEmpty()) continue
            out[k.trim().lowercase()] = list
        }
        return out
    }

    private fun loadManifest(): Map<String, DictionaryEntry> {
        val json = readAsset("dictionaries/DictionaryManifest.json") ?: return emptyMap()
        return try {
            val obj = JSONObject(json)
            val out = mutableMapOf<String, DictionaryEntry>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val e = obj.getJSONObject(k)
                out[k] = DictionaryEntry(
                    bytes = e.optInt("bytes", 0),
                    sha256 = e.optString("sha256", ""),
                    entries = e.optInt("entries", 0),
                    kind = e.optString("kind", "")
                )
            }
            out
        } catch (_: Exception) { emptyMap() }
    }

    private fun readAsset(path: String): String? = try {
        context.assets.open(path).use { ins ->
            BufferedReader(InputStreamReader(ins, Charsets.UTF_8)).readText()
        }
    } catch (_: Exception) { null }

    // Legacy fallback: if assets not yet migrated, try res/raw via Resources (not used in new build)
}
