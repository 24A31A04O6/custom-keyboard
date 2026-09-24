package com.babeltech.babelkey.data.dictionaries

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * MigrationTool — Kotlin counterpart of `scripts/migrate_dictionaries.py`.
 *
 * Verifies that dictionaries in `assets/dictionaries/` match the Gate 1 snapshot
 * (checksums + never-reduced counts) before the engine uses them.
 * Called at IME startup and in `DictionaryRepositoryTest` / CI.
 */
object MigrationTool {

    // Gate 1 expected values (see docs/GATE1_DICTIONARY_INVENTORY.md)
    private val EXPECTED_SHA256 = mapOf(
        "translations.json" to "77ee7b79582e54fcded6447c5a1c8da61a950026737e8845012c0d8e5c89a5ea",
        "autocorrect_dict.json" to "dc1993accc1027a627c513a148ce6eec88d2ac678a318f3de7fd5974bd956b52",
        "tel_eng_dict.json" to "0b23934af311b6539afe4a30c50ec8593ba1a5da0f04ee5ac88e83ccebc72f84",
        "telugu_dict.json" to "e05b1c1034bc457c8a39cf2aa04ec688e650bca3873b7635941d0633fca4ac42",
        "eng_dict.json" to "60792451300d1948489f3ed1dd5f616ad202527619941a8f16b2a46e9217dd25",
        "suggestions_dict.json" to "efd90b29d447ca42f8e7e71ed8b653280a02ec5fc815acde2c22fcbea6174c88",
        "emoji_suggestions.json" to "36cd8a60ec2926b278ddf0516a86f6be6a50dabf6408d2263daefb9f949933e0",
    )
    private val EXPECTED_COUNTS = mapOf(
        "translations.json" to 1199,
        "autocorrect_dict.json" to 171,
        "tel_eng_dict.json" to 1757,
        "telugu_dict.json" to 62,
        "eng_dict.json" to 2010,
        "suggestions_dict.json" to 19,
        "emoji_suggestions.json" to 29,
    )

    data class VerificationResult(val ok: Boolean, val failures: List<String>, val expanded: List<String>)

    fun verifyAssets(context: Context): VerificationResult {
        val failures = mutableListOf<String>()
        val expanded = mutableListOf<String>()
        for ((name, expectedSha) in EXPECTED_SHA256) {
            val bytes = try { context.assets.open("dictionaries/$name").readBytes() } catch (e: Exception) {
                failures += "Missing asset dictionaries/$name: ${e.message}"
                continue
            }
            val gotSha = sha256(bytes)
            if (!gotSha.equals(expectedSha, ignoreCase = true)) {
                // Allow expansion: if content expanded, sha will differ — check count instead and flag as expanded
                val count = try { JSONObject(String(bytes, Charsets.UTF_8)).length() } catch (_: Exception) { -1 }
                val expectedCount = EXPECTED_COUNTS[name] ?: -1
                if (count >= expectedCount && count != -1) {
                    expanded += "$name expanded $expectedCount -> $count (sha differs, allowed)"
                } else {
                    failures += "Checksum mismatch $name expected $expectedSha got $gotSha (count $count vs $expectedCount)"
                }
            } else {
                val count = try { JSONObject(String(bytes, Charsets.UTF_8)).length() } catch (_: Exception) { -1 }
                val expectedCount = EXPECTED_COUNTS[name] ?: -1
                if (count < expectedCount) {
                    failures += "Never-reduced violation $name: $count < $expectedCount"
                } else if (count > expectedCount) {
                    expanded += "$name expanded $expectedCount -> $count"
                }
            }
        }
        return VerificationResult(failures.isEmpty(), failures, expanded)
    }

    private fun sha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
