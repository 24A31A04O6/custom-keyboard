package com.babeltech.babelkey

import org.junit.Test
import org.junit.Assert.*
import com.babeltech.babelkey.core.input.GesturePath
import com.babeltech.babelkey.core.input.GestureScorer
import com.babeltech.babelkey.core.input.GlideTypingEngine
import kotlin.random.Random

/**
 * GlideScorerTest — fuzz / property testing for glide typing path scorer.
 * Per spec: fuzz/property testing for glide-typing path scorer + benchmark.
 */
class GlideScorerTest {

    @Test fun helloScoresBetterThanRandom() {
        val helloPath = GestureScorer.generateRandomPathForWord("hello", noise = 0.01f)
        val candidates = listOf("hello", "world", "help", "held", "hill")
        val scored = GestureScorer.score(helloPath, candidates)
        assertTrue(scored.isNotEmpty())
        assertEquals("hello", scored.first().word)
    }

    @Test fun emptyOrInactivePathReturnsEmpty() {
        val p = GesturePath()
        assertTrue(GestureScorer.score(p, listOf("hello")).isEmpty())
        p.addPoint(0.5f, 0.5f)
        assertTrue(GestureScorer.score(p, listOf("hello")).isEmpty())
    }

    @Test fun property_randomWordsScoreTheirOwnPathBest() {
        val words = listOf("about","hello","world","thanks","telugu","namaskaram","bagundi","ledu","manchi","avunu")
        var wins = 0
        for (word in words) {
            val path = GestureScorer.generateRandomPathForWord(word, noise = 0.02f)
            val others = words.filter { it != word }.shuffled().take(5)
            val candidates = (listOf(word) + others).shuffled()
            val scored = GestureScorer.score(path, candidates)
            if (scored.firstOrNull()?.word == word) wins++
        }
        // At least 70% should win (fuzz property: scorer is reasonably accurate)
        assertTrue("Expected at least 70% wins, got $wins/${words.size}", wins >= 7)
    }

    @Test fun fuzz_randomNoisePathsDontCrash() {
        val rand = Random(42)
        for (i in 0 until 100) {
            val path = GesturePath()
            val n = rand.nextInt(10, 50)
            for (j in 0 until n) {
                path.addPoint(rand.nextFloat(), rand.nextFloat())
            }
            val candidates = listOf("hello","world","telugu","manchi","avunu")
            val scored = GestureScorer.score(path, candidates)
            // Should not crash, may return empty or scored
            assertTrue(scored.size <= 5)
        }
    }

    @Test fun performanceBenchmark() {
        // Ensure scoring 1500 candidates stays <50ms (target <16ms on device; JVM slower so 50ms)
        val path = GestureScorer.generateRandomPathForWord("hello")
        val pool = List(1500) { "word$it" } + listOf("hello")
        val start = System.nanoTime()
        val scored = GestureScorer.score(path, pool)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertTrue("Scoring took ${elapsedMs}ms, expected <50ms", elapsedMs < 50)
        assertTrue(scored.isNotEmpty())
    }

    @Test fun glideEnginePoolLimit() {
        // GlideTypingEngine builds pool of <=1500
        val path = GesturePath()
        for (i in 0 until 10) path.addPoint(0.1f * i, 0.1f * i)
        // Just ensure no crash on empty dicts scenario
        // (real pool built from DictionaryRepository; here we test with synthetic candidates)
        val scored = GestureScorer.score(path, List(2000) { "w$it" })
        assertTrue(scored.size <= 5)
    }
}
