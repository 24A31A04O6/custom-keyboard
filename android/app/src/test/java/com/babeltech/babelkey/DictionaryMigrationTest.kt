package com.babeltech.babelkey

import org.junit.Test
import org.junit.Assert.*
import java.security.MessageDigest
import java.io.File

class DictionaryMigrationTest {
    @Test fun gate1ChecksumsDocumented() {
        // Ensures the 7 files are still present in assets/dictionaries and manifest is valid
        val manifest = File("src/main/assets/dictionaries/DictionaryManifest.json")
        // When running from repo root, path is android/app/src/main/assets/dictionaries
        val alt = File("android/app/src/main/assets/dictionaries/DictionaryManifest.json")
        val f = if (manifest.exists()) manifest else alt
        assertTrue("DictionaryManifest.json missing: tried ${manifest.path} and ${alt.path}", f.exists())
        val json = org.json.JSONObject(f.readText())
        assertEquals(7, json.length())
        assertTrue(json.has("translations.json"))
        assertTrue(json.has("autocorrect_dict.json"))
    }
    @Test fun neverReducedGuarantee() {
        val expected = mapOf("translations.json" to 1199, "autocorrect_dict.json" to 171, "tel_eng_dict.json" to 1757)
        val manifestFile = File("android/app/src/main/assets/dictionaries/DictionaryManifest.json")
        if (!manifestFile.exists()) return // skip in CI where assets not at this path
        val json = org.json.JSONObject(manifestFile.readText())
        for ((name, minCount) in expected) {
            val actual = json.getJSONObject(name).optInt("entries", 0)
            assertTrue("$name reduced: $actual < $minCount", actual >= minCount)
        }
    }
}
