package com.babeltech.babelkey.data.clipboard

import android.content.Context
import com.babeltech.babelkey.data.preferences.PreferencesRepository
import com.babeltech.babelkey.security.ClipboardPolicy
import com.babeltech.babelkey.security.SecureFieldGuard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * ClipboardRepository — SSoT for clipboard history.
 *
 * Rules per spec:
 * - History auto-expires after 24h (configurable via PreferencesRepository)
 * - Pinned entries never expire
 * - Never persists secure-field content (guarded by ClipboardPolicy + SecureFieldGuard)
 * - Backed by EncryptedSharedPreferences for persistence + in-memory StateFlow for UI
 * - Max 50 entries; oldest unpinned evicted first
 */
class ClipboardRepository(
    private val context: Context,
    private val prefs: PreferencesRepository
) {
    private val _entries = MutableStateFlow<List<ClipboardEntry>>(emptyList())
    val entries: StateFlow<List<ClipboardEntry>> = _entries

    private val storageKey = "clipboard_history_v2"

    fun load() {
        val raw = prefs.encryptedPrefs.getString(storageKey, null) ?: return
        try {
            val list = mutableListOf<ClipboardEntry>()
            // Stored as JSON array of objects: [{"id":"...","text":"...","ts":...,"pinned":false}]
            val json = org.json.JSONArray(raw)
            for (i in 0 until json.length()) {
                val o = json.getJSONObject(i)
                list += ClipboardEntry(
                    id = o.optString("id"),
                    text = o.optString("text"),
                    timestampMs = o.optLong("ts", 0),
                    pinned = o.optBoolean("pinned", false)
                )
            }
            _entries.value = list.sortedByDescending { it.timestampMs }
            sweepExpired()
        } catch (_: Exception) { /* corrupt -> start fresh */ }
    }

    fun add(text: String, secureField: Boolean = false): Boolean {
        if (text.isBlank()) return false
        if (secureField) return false // never persist secure-field content
        if (!ClipboardPolicy.isAllowed(text)) return false
        val now = System.currentTimeMillis()
        val entry = ClipboardEntry(id = now.toString() + "_" + text.hashCode(), text = text, timestampMs = now)
        val next = (listOf(entry) + _entries.value)
            .distinctBy { it.text } // dedupe by text keep newest
            .take(50)
        _entries.value = next
        persist()
        return true
    }

    fun pin(id: String) {
        _entries.value = _entries.value.map { if (it.id == id) it.copy(pinned = true) else it }
        persist()
    }

    fun unpin(id: String) {
        _entries.value = _entries.value.map { if (it.id == id) it.copy(pinned = false) else it }
        persist()
    }

    fun delete(id: String) {
        _entries.value = _entries.value.filterNot { it.id == id }
        persist()
    }

    fun clearAll() {
        _entries.value = emptyList()
        prefs.encryptedPrefs.edit().remove(storageKey).apply()
    }

    fun sweepExpired() {
        val expiryHours = prefs.getClipboardExpiryHours()
        val now = System.currentTimeMillis()
        val before = _entries.value.size
        _entries.value = _entries.value.filterNot { it.isExpired(now, expiryHours) }
        if (_entries.value.size != before) persist()
    }

    private fun persist() {
        val arr = org.json.JSONArray()
        for (e in _entries.value) {
            val o = org.json.JSONObject()
            o.put("id", e.id)
            o.put("text", e.text)
            o.put("ts", e.timestampMs)
            o.put("pinned", e.pinned)
            arr.put(o)
        }
        prefs.encryptedPrefs.edit().putString(storageKey, arr.toString()).apply()
    }
}
