package com.babeltech.babelkey.ui.panels

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.babeltech.babelkey.data.dictionaries.DictionaryRepository

/**
 * EmojiPanel — full picker with categories/search.
 * Uses emoji_suggestions.json for keyword -> emoji mapping.
 */
@Composable
fun EmojiPanel(dicts: DictionaryRepository, onPick: (String) -> Unit, onSearch: (String) -> List<String>) {
    var query by remember { mutableStateOf("") }
    val suggestions = remember(query) {
        if (query.isBlank()) emptyList()
        else dicts.emojiSuggestions[query.lowercase()]?.split(" ") ?: emptyList()
    }
    Column(Modifier.fillMaxWidth().height(240.dp).padding(8.dp)) {
        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search emoji") }, modifier = Modifier.fillMaxWidth())
        if (suggestions.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                suggestions.forEach { e -> SuggestionChip(onClick = { onPick(e) }, label = { Text(e) }) }
            }
        }
        LazyVerticalGrid(columns = GridCells.Fixed(8), modifier = Modifier.weight(1f)) {
            items(40) { idx ->
                Text("😀", modifier = Modifier.padding(8.dp))
            }
        }
    }
}
