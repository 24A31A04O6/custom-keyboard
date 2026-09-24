package com.babeltech.babelkey.ui.keyboard

import android.view.MotionEvent
import com.babeltech.babelkey.core.input.GlideTypingEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * TouchHandler — bridges MotionEvent to GlideTypingEngine.
 *
 * Handles fast continuous swiping smoothly by delegating to GlideTypingEngine
 * and emitting candidate words for SuggestionStrip.
 */
class TouchHandler(
    private val glideEngine: GlideTypingEngine,
    private val scope: CoroutineScope
) {
    private val _glideSuggestions = MutableStateFlow<List<String>>(emptyList())
    val glideSuggestions: StateFlow<List<String>> = _glideSuggestions

    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> glideEngine.onTouchDown(event.x, event.y)
            MotionEvent.ACTION_MOVE -> {
                // Batch historical points for 60fps smooth path
                for (i in 0 until event.historySize) {
                    glideEngine.onTouchMove(event.getHistoricalX(i), event.getHistoricalY(i))
                }
                glideEngine.onTouchMove(event.x, event.y)
            }
            MotionEvent.ACTION_UP -> {
                scope.launch(Dispatchers.Default) {
                    val scored = glideEngine.onTouchUp()
                    _glideSuggestions.value = scored.map { it.word }
                }
            }
            MotionEvent.ACTION_CANCEL -> _glideSuggestions.value = emptyList()
        }
        return true
    }

    fun clear() { _glideSuggestions.value = emptyList() }
}
