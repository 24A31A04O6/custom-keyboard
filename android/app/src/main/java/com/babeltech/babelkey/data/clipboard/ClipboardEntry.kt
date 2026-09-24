package com.babeltech.babelkey.data.clipboard

/**
 * ClipboardEntry — value object for clipboard history.
 */
data class ClipboardEntry(
    val id: String,
    val text: String,
    val timestampMs: Long,
    val pinned: Boolean = false
) {
    fun isExpired(nowMs: Long, expiryHours: Int): Boolean {
        if (expiryHours < 0) return false // never
        if (pinned) return false
        val ageHours = (nowMs - timestampMs) / (1000L * 60 * 60)
        return ageHours >= expiryHours
    }
}
