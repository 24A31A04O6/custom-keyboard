package com.babeltech.babelkey.ui.keyboard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.babeltech.babelkey.core.input.KeyEventProcessor

/**
 * KeyboardViewModel — UI state holder for keyboard layouts.
 * Pure StateFlow; ui/ only displays state and forwards actions to core/input.
 */
class KeyboardViewModel(private val keyProcessor: KeyEventProcessor) {
    private val _composing = MutableStateFlow("")
    val composing: StateFlow<String> = _composing

    private val _layout = MutableStateFlow(Layout.QWERTY)
    val layout: StateFlow<Layout> = _layout

    enum class Layout { QWERTY, SYMBOLS, SYMBOLS_SHIFT, NUMPAD, EMOJI }

    fun onKey(c: Char) {
        val actions = keyProcessor.onCharacter(c)
        // Forward to service/ime via callback — kept as StateFlow update here for preview
        _composing.value = keyProcessor.composingText()
    }

    fun onBackspace() { keyProcessor.onBackspace(); _composing.value = keyProcessor.composingText() }

    fun switchLayout(l: Layout) { _layout.value = l }

    fun reset() { keyProcessor.resetComposing(); _composing.value = "" }
}
