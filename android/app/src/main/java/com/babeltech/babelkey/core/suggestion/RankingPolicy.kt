package com.babeltech.babelkey.core.suggestion

/**
 * RankingPolicy — merges multiple suggestion sources and ranks them.
 *
 * Pure function: no Android deps. Testable in isolation.
 */
object RankingPolicy {
    /**
     * Rank candidates: exact prefix matches first, then personal dict boost,
     * then by length (shorter preferred), then alphabetically. Deduplicates.
     */
    fun rank(
        prefix: String,
        candidates: List<String>,
        personalWords: Set<String> = emptySet(),
        limit: Int = 5
    ): List<String> {
        if (candidates.isEmpty()) return emptyList()
        val lowerPrefix = prefix.lowercase()
        val seen = LinkedHashSet<String>()
        // Personal words that start with prefix get top boost
        val personalBoost = personalWords.filter { it.lowercase().startsWith(lowerPrefix) && it !in candidates }
        val pool = (personalBoost + candidates).distinct()
        val sorted = pool.sortedWith(compareBy<String>(
            { if (it.lowercase().startsWith(lowerPrefix)) 0 else 1 },
            { if (it.lowercase() in personalWords) 0 else 1 },
            { it.length },
            { it }
        ))
        for (w in sorted) {
            seen += w
            if (seen.size >= limit) break
        }
        return seen.toList()
    }
}
