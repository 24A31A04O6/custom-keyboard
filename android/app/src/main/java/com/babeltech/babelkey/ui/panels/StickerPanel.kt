package com.babeltech.babelkey.ui.panels

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * StickerPanel — Phase 3 sticker support (opt-in, same provider as GIFs).
 *
 * For v1, stickers are a curated local set (no network). Online sticker search reuses GifRepository provider
 * when GIF opt-in is enabled — no separate permission. This keeps the opt-in surface minimal.
 */
@Composable
fun StickerPanel(onStickerPicked: (String) -> Unit) {
    val localStickers = remember { listOf("😊","🥳","❤️","🔥","👍","🎉","🤗","😂","🙏","✨","💯","🌟") }
    Column(Modifier.fillMaxWidth().height(300.dp).padding(8.dp)) {
        Text("Stickers", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(6)) {
            items(localStickers) { s ->
                ElevatedCard(onClick = { onStickerPicked(s) }, modifier = Modifier.padding(4.dp).aspectRatio(1f)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { Text(s) }
                }
            }
        }
        Text("Local stickers — no network. Online stickers share GIF opt-in (Giphy).", style = MaterialTheme.typography.labelSmall)
    }
}
