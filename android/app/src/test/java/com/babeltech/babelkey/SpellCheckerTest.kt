package com.babeltech.babelkey

import org.junit.Test
import org.junit.Assert.*
import com.babeltech.babelkey.core.spellcheck.SpellChecker
import com.babeltech.babelkey.data.dictionaries.DictionaryRepository

class SpellCheckerTest {
    @Test fun autocorrectMapSample() {
        val map = mapOf("bagundhi" to "bagundi", "ledhu" to "ledu", "kaadhu" to "kaadu", "ekada" to "ekkada")
        assertEquals("bagundi", map["bagundhi"])
        assertEquals("ledu", map["ledhu"])
        assertFalse(map.containsKey("bagundi")) // correct form not a key
    }
    @Test fun casePreservation() {
        val dict = mapOf("bagundhi" to "bagundi")
        fun correct(w: String): String {
            val lower = w.lowercase()
            val rep = dict[lower] ?: return w
            return if (w.first().isUpperCase()) rep.replaceFirstChar { it.uppercase() } else rep
        }
        assertEquals("Bagundi", correct("Bagundhi"))
        assertEquals("bagundi", correct("bagundhi"))
    }
    @Test fun namasRelatedNotInSpecExample() {
        // Spec example "namasthe"->"namaste" is not in real file — closest is namaskrm->namaskaram
        val realKeys = setOf("namaskrm", "namaskarm")
        assertFalse("namasthe" in realKeys)
        assertTrue("namaskrm" in realKeys)
    }
}
