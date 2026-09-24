package com.babeltech.babelkey

import org.junit.Test
import org.junit.Assert.*
import com.babeltech.babelkey.core.suggestion.RankingPolicy

class SuggestionRankingTest {
    @Test fun personalBoostFirst() {
        val ranked = RankingPolicy.rank("te", listOf("telugu","test","team"), personalWords = setOf("telugu"), limit=3)
        assertEquals("telugu", ranked.first())
    }
    @Test fun prefixPartition() {
        val ranked = RankingPolicy.rank("hel", listOf("hello","help","world","held"), limit=3)
        // hello/help/held start with hel, world does not — hel words should be first
        assertTrue(ranked.take(3).all { it.startsWith("hel") || it.startsWith("hel") })
        assertFalse("world" in ranked.take(3) || ranked.size<3)
    }
    @Test fun dedupAndLimit() {
        val ranked = RankingPolicy.rank("a", listOf("a","a","about","about","above"), limit=2)
        assertEquals(2, ranked.size)
        assertEquals(ranked.toSet().size, ranked.size)
    }
}
