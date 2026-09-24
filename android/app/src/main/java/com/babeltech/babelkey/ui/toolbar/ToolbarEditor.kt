package com.babeltech.babelkey.ui.toolbar

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.babeltech.babelkey.data.preferences.PreferencesRepository

/**
 * ToolbarEditor — long-press to reorder/add/remove tools.
 * Persists order via PreferencesRepository (EncryptedSharedPreferences).
 */
@Composable
fun ToolbarEditor(prefs: PreferencesRepository, onClose: () -> Unit) {
    var order by remember { mutableStateOf(prefs.getToolbarOrder()) }
    Column(Modifier.padding(16.dp)) {
        Text("Customize toolbar — long press to reorder", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        order.forEach { key ->
            ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Text(key, modifier = Modifier.padding(12.dp)) }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { prefs.setToolbarOrder(order); onClose() }) { Text("Save") }
            OutlinedButton(onClick = onClose) { Text("Cancel") }
        }
    }
}
