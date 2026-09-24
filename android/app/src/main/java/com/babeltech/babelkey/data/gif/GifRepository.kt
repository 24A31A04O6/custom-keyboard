package com.babeltech.babelkey.data.gif

import com.babeltech.babelkey.data.preferences.PreferencesRepository
import com.babeltech.babelkey.security.NetworkPolicy
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * GifRepository — Phase 3 GIF/sticker support (opt-in, HTTPS, provider named).
 *
 * Provider: Giphy (https://giphy.com) — attribution required (Giphy logo), license per GIF is Giphy TOS.
 * Alternative: Tenor (Google) — same pattern. No SDK bundled; plain HTTPS search to avoid SDK bloat & tracker opt-in.
 *
 * Per spec: Any online feature is opt-in (default OFF), disclosed, provider named, HTTPS.
 * If user never opts in, no network call is made.
 *
 * API key: NOT hard-coded — must be provided via BuildConfig.GIPHY_API_KEY (Gradle property giphyApiKey) or at runtime via prefs.
 * This file never logs the key — key is sent only as query param over HTTPS.
 */
class GifRepository(
    private val prefs: PreferencesRepository,
    private val networkPolicy: NetworkPolicy,
    private val apiKeyProvider: () -> String? = { null } // injected — reads BuildConfig or EncryptedPrefs
) {
    data class Gif(val id: String, val url: String, val previewUrl: String, val title: String)

    companion object {
        const val GIPHY_SEARCH_URL = "https://api.giphy.com/v1/gifs/search"
        const val GIPHY_TRENDING_URL = "https://api.giphy.com/v1/gifs/trending"
        const val PROVIDER_NAME = "Giphy"
        const val PROVIDER_ATTRIBUTION = "Powered by Giphy"
    }

    fun isEnabled(): Boolean = prefs.encryptedPrefs.getBoolean("gif_enabled", false)

    fun setEnabled(enabled: Boolean) {
        prefs.encryptedPrefs.edit().putBoolean("gif_enabled", enabled).apply()
    }

    /** Search GIFs — HTTPS only, opt-in gated. Returns empty if not enabled or no API key. */
    fun search(query: String, limit: Int = 12): List<Gif> {
        if (!isEnabled()) return emptyList()
        if (!networkPolicy.isAiRepliesAllowed() && !isEnabled()) return emptyList() // reuse opt-in gate; GIF has its own pref
        val apiKey = apiKeyProvider() ?: return emptyList()
        if (apiKey.isBlank()) return emptyList()
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "$GIPHY_SEARCH_URL?api_key=$apiKey&q=$encoded&limit=$limit&rating=pg"
        networkPolicy.requireHttps(url)
        return fetchGifs(url)
    }

    fun trending(limit: Int = 12): List<Gif> {
        if (!isEnabled()) return emptyList()
        val apiKey = apiKeyProvider() ?: return emptyList()
        if (apiKey.isBlank()) return emptyList()
        val url = "$GIPHY_TRENDING_URL?api_key=$apiKey&limit=$limit&rating=pg"
        networkPolicy.requireHttps(url)
        return fetchGifs(url)
    }

    private fun fetchGifs(urlStr: String): List<Gif> {
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode != 200) return emptyList()
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
            conn.disconnect()
            parse(body)
        } catch (_: Exception) {
            emptyList() // fail closed — show no GIFs, don't crash
        }
    }

    private fun parse(json: String): List<Gif> {
        return try {
            val obj = JSONObject(json)
            val data = obj.getJSONArray("data")
            List(data.length()) { i ->
                val g = data.getJSONObject(i)
                val id = g.optString("id", "")
                val title = g.optString("title", "")
                val images = g.optJSONObject("images")
                val original = images?.optJSONObject("original")?.optString("url", "") ?: ""
                val preview = images?.optJSONObject("fixed_width_small")?.optString("url", original) ?: original
                Gif(id, original, preview, title)
            }.filter { it.url.isNotBlank() }
        } catch (_: Exception) { emptyList() }
    }
}
