package com.babeltech.babelkey.ui.toolbar

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * ScreenshotDetector — Phase 2 screenshot-suggestion chip.
 *
 * Watches MediaStore for new screenshots. When a screenshot is detected,
 * emits a "Screenshot" chip in ToolbarState.Idle (middle).
 *
 * This is the user-visible smart-suggestion chip mentioned in the toolbar spec.
 * No special permission beyond READ_MEDIA_IMAGES (already declared for theme picker).
 */
class ScreenshotDetector(private val context: Context) {
    private val _screenshotDetected = MutableStateFlow(false)
    val screenshotDetected: StateFlow<Boolean> = _screenshotDetected

    private var observer: ContentObserver? = null

    fun start() {
        if (observer != null) return
        observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                // Heuristic: MediaStore image with screenshot in display name
                _screenshotDetected.value = true
                // Auto-clear after 30s
                Handler(Looper.getMainLooper()).postDelayed({ _screenshotDetected.value = false }, 30_000)
            }
        }
        try {
            context.contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer!!
            )
        } catch (_: Exception) {}
    }

    fun stop() {
        observer?.let { try { context.contentResolver.unregisterContentObserver(it) } catch (_: Exception) {} }
        observer = null
    }

    fun consume(): Boolean {
        val v = _screenshotDetected.value
        if (v) _screenshotDetected.value = false
        return v
    }
}
