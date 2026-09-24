package com.babeltech.babelkey.ui.toolbar

/**
 * ToolbarState — sealed states for adaptive toolbar row (3 states, not fixed icons).
 *
 * 1. Idle: grid-toggle (left) + contextual chip e.g. "Screenshot" (middle) + mic (right)
 * 2. Typing: same layout, middle shows word suggestion chips
 * 3. Expanded: full tool row (stickers, GIF, settings, translate, theme, clipboard, one-handed)
 */
sealed class ToolbarState {
    object Idle : ToolbarState()
    data class Typing(val suggestions: List<String>) : ToolbarState()
    object Expanded : ToolbarState()
}
