package com.babeltech.babelkey.core.smartreply

/**
 * SmartReplyEngine — Phase 3 stub (AI / smart replies).
 *
 * Phase 1: disabled, gated OFF by default per spec ("opt-in only").
 * Phase 3: will wrap ML Kit Smart Reply or HTTPS API (provider named).
 */
object SmartReplyEngine {
    fun isAvailable(): Boolean = false
    fun generateReplies(contextText: String): List<String> = emptyList()
}
