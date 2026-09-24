package com.babeltech.babelkey.ui.panels

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.babeltech.babelkey.data.clipboard.ClipboardEntry
import com.babeltech.babelkey.data.clipboard.ClipboardRepository

/**
 * ClipboardPanel — history, pin/unpin, auto-clear badge.
 * Never shows secure-field content (enforced in ClipboardRepository).
 */
@Composable
fun ClipboardPanel(repo: ClipboardRepository, onPaste: (ClipboardEntry) -> Unit) {
    val entries by repo.entries.collectAsState()
    Column(Modifier.fillMaxWidth().height(300.dp)) {
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Clipboard — auto-clears after 24h", style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = { repo.clearAll() }) { Text("Clear all") }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(entries) { e ->
                ElevatedCard(Modifier.fillMaxWidth().padding(4.dp)) {
                    Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(e.text.take(80), modifier = Modifier.weight(1f))
                        TextButton(onClick = { if (e.pinned) repo.unpin(e.id) else repo.pin(e.id) }) { Text(if (e.pinned) "Unpin" else "Pin") }
                        TextButton(onClick = { onPaste(e) }) { Text("Paste") }
                    }
                }
            }
        }
    }
}
