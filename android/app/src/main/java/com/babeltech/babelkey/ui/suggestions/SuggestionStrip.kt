package com.babeltech.babelkey.ui.suggestions

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * SuggestionStrip — 3-chip row wired to core/suggestion/SuggestionEngine.
 * Pure display; forwards clicks to core via callback.
 */
@Composable
fun SuggestionStrip(suggestions: List<String>, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        suggestions.take(3).forEach { w ->
            SuggestionChip(onClick = { onPick(w) }, label = { Text(w) })
        }
    }
}

@Composable
fun OtpChip(otp: String, onPaste: () -> Unit) {
    SuggestionChip(onClick = onPaste, label = { Text("OTP $otp — tap to paste") })
}

@Composable
fun UndoChip(onUndo: () -> Unit) {
    SuggestionChip(onClick = onUndo, label = { Text("↩ Undo") })
}
