package com.babeltech.babelkey.security

/**
 * NetworkPolicy — enforces HTTPS-only and opt-in gating for online features.
 *
 * All network calls must go through here. Plain HTTP is rejected.
 */
class NetworkPolicy(private val prefs: com.babeltech.babelkey.data.preferences.PreferencesRepository) {

    fun isOnlineFallbackAllowed(): Boolean = prefs.isOnlineTranslationEnabled()

    fun isAiRepliesAllowed(): Boolean = prefs.isAiRepliesEnabled()

    fun requireHttps(url: String) {
        require(url.startsWith("https://")) { "NetworkPolicy: plain HTTP rejected: $url" }
    }

    companion object {
        fun assertHttps(url: String) {
            require(url.startsWith("https://")) { "Insecure URL: $url — HTTPS required" }
        }
    }
}
