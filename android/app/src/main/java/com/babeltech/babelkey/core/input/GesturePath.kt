package com.babeltech.babelkey.core.input

/**
 * GesturePath — Phase 2 glide finger-path tracking.
 *
 * Records MotionEvent points at 60Hz during a swipe. Provides normalization
 * to key centers and total path length for scorer.
 *
 * Performance: addPoint is O(1); total path length computed lazily.
 * Handles fast continuous swiping by sampling (drops points <2dp apart).
 */
class GesturePath {
    data class Point(val x: Float, val y: Float, val tMs: Long = System.currentTimeMillis())

    private val points = mutableListOf<Point>()
    private var totalLengthPx: Float? = null

    fun addPoint(x: Float, y: Float, tMs: Long = System.currentTimeMillis()) {
        val p = Point(x, y, tMs)
        // Sample: drop point if too close to last (reduces jitter, keeps 60fps smooth)
        val last = points.lastOrNull()
        if (last != null) {
            val dx = x - last.x; val dy = y - last.y
            if (dx*dx + dy*dy < 4f) return // <2dp
        }
        points += p
        totalLengthPx = null
    }

    fun clear() { points.clear(); totalLengthPx = null }
    fun pointCount(): Int = points.size
    fun isActive(): Boolean = points.size >= 4 // need at least 4 points for a word gesture
    fun points(): List<Point> = points.toList()

    fun totalLengthPx(): Float {
        if (totalLengthPx != null) return totalLengthPx!!
        var len = 0f
        for (i in 1 until points.size) {
            val dx = points[i].x - points[i-1].x
            val dy = points[i].y - points[i-1].y
            len += kotlin.math.sqrt((dx*dx + dy*dy).toDouble()).toFloat()
        }
        totalLengthPx = len
        return len
    }

    fun bounds(): Pair<Point, Point>? {
        if (points.isEmpty()) return null
        var minX = points[0].x; var maxX = points[0].x
        var minY = points[0].y; var maxY = points[0].y
        for (p in points) { if (p.x < minX) minX = p.x; if (p.x > maxX) maxX = p.x; if (p.y < minY) minY = p.y; if (p.y > maxY) maxY = p.y }
        return Point(minX, minY) to Point(maxX, maxY)
    }

    fun durationMs(): Long =
        if (points.size < 2) 0 else points.last().tMs - points.first().tMs
}
