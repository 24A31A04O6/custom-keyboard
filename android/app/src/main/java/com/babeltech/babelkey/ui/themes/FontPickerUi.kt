package com.babeltech.babelkey.ui.themes

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.babeltech.babelkey.data.preferences.FontRepository

/**
 * FontPickerUi — Phase 2 Google Fonts picker (opt-in).
 *
 * Shows available fonts, indicates which require download (HTTPS), and
 * persists choice via FontRepository (EncryptedSharedPreferences).
 */
@Composable
fun FontPickerUi(repo: FontRepository, onChosen: (String) -> Unit) {
    var chosen by remember { mutableStateOf(repo.getChosenFont()) }
    Column(Modifier.padding(16.dp)) {
        Text("Keyboard font (Google Fonts — downloads over HTTPS when chosen)", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        repo.availableFonts.forEach { name ->
            ElevatedCard(
                onClick = { chosen = name; repo.setChosenFont(name); onChosen(name) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(name)
                    if (repo.isGoogleFont(name)) Badge { Text("HTTPS") }
                    if (name == chosen) Text(" ✓")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Core typing works 100% offline. Font download is opt-in, HTTPS only.", style = MaterialTheme.typography.bodySmall)
    }
}
