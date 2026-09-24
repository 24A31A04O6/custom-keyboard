package com.babeltech.babelkey.ui.panels

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.babeltech.babelkey.data.gif.GifRepository
import kotlinx.coroutines.*

/**
 * GifPanel — Phase 3 GIF/sticker picker (opt-in, provider: Giphy, HTTPS).
 *
 * Shows search field + grid of GIF previews. Tapping a GIF commits its URL (or downloads and commits).
 * Opt-in banner + attribution ("Powered by Giphy") always visible when enabled.
 *
 * Security: no GIF fetch unless `GifRepository.isEnabled()` (default OFF) — panel shows opt-in prompt otherwise.
 */
@Composable
fun GifPanel(
    repo: GifRepository,
    onGifPicked: (GifRepository.Gif) -> Unit,
    onOptInChange: (Boolean) -> Unit
) {
    var enabled by remember { mutableStateOf(repo.isEnabled()) }
    var query by remember { mutableStateOf("") }
    var gifs by remember { mutableStateOf<List<GifRepository.Gif>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxWidth().height(360.dp).padding(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("GIFs — ${GifRepository.PROVIDER_ATTRIBUTION}", style = MaterialTheme.typography.titleSmall)
            Switch(checked = enabled, onCheckedChange = { enabled = it; repo.setEnabled(it); onOptInChange(it) })
        }
        if (!enabled) {
            Text(
                "GIF search is opt-in (default OFF). When on, searches are sent over HTTPS to ${GifRepository.PROVIDER_NAME}.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp)
            )
            OutlinedButton(onClick = { enabled = true; repo.setEnabled(true); onOptInChange(true) }) { Text("Enable GIF search") }
            return
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text("Search GIFs") }, modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            Button(onClick = {
                loading = true
                scope.launch(Dispatchers.IO) {
                    val res = if (query.isBlank()) repo.trending() else repo.search(query)
                    withContext(Dispatchers.Main) { gifs = res; loading = false }
                }
            }) { Text(if (query.isBlank()) "Trending" else "Search") }
            if (loading) CircularProgressIndicator(Modifier.size(24.dp))
        }
        LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.weight(1f)) {
            items(gifs) { gif ->
                ElevatedCard(onClick = { onGifPicked(gif) }, modifier = Modifier.padding(4.dp).aspectRatio(1f)) {
                    // In production, use Coil AsyncImage with gif.previewUrl
                    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Text(gif.title.take(20), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Text("Provider: ${GifRepository.PROVIDER_NAME} over HTTPS; no keystroke/clipboard sent.", style = MaterialTheme.typography.labelSmall)
    }
}
