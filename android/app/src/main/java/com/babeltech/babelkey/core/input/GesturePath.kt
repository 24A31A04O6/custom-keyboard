package com.babeltech.babelkey.core.input

/**
 * GesturePath — Phase 2 stub for glide typing finger-path tracking.
 *
 * Phase 1: no-op stub so tests/build pass. Phase 2: records MotionEvent
 * points, normalizes to key centers, computes candidate words via Viterbi.
 */
class GesturePath {
    private val points = mutableListOf<Pair<Float, Float>>()
    fun addPoint(x: Float, y: Float) { points += x to y }
    fun clear() { points.clear() }
    fun pointCount(): Int = points.size
    fun isActive(): Boolean = points.size >= 3
}
