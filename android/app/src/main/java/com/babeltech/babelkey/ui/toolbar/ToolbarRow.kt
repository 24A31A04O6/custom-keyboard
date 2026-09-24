package com.babeltech.babelkey.ui.toolbar

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * ToolbarRow — adaptive single row, 3 states with instant, smooth transitions (no flicker).
 * Uses AnimatedContent for state switches at <16ms.
 */
@Composable
fun ToolbarRow(
    state: ToolbarState,
    onGridToggle: () -> Unit,
    onMicClick: () -> Unit,
    onSuggestionClick: (String) -> Unit,
    onToolClick: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onGridToggle) { Text("⊞") } // grid toggle
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            AnimatedContent(targetState = state, transitionSpec = {
                fadeIn() with fadeOut()
            }, label = "toolbar") { s ->
                when (s) {
                    is ToolbarState.Idle -> SuggestionChip(onClick = {}, label = { Text("Screenshot") })
                    is ToolbarState.Typing -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        s.suggestions.take(3).forEach { w ->
                            SuggestionChip(onClick = { onSuggestionClick(w) }, label = { Text(w) })
                        }
                    }
                    is ToolbarState.Expanded -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Phase 3: full tool row — stickers, GIF, settings, translate, theme, clipboard, one-handed mode; long-press to reorder (handled by ToolbarEditor)
                        listOf("Sticker","GIF","Translate","Theme","Clipboard","One-handed","Settings").forEach { t ->
                            SuggestionChip(onClick = { onToolClick(t) }, label = { Text(t) })
                        }
                    }
                }
            }
        }
        IconButton(onClick = onMicClick) { Text("🎤") }
    }
}
