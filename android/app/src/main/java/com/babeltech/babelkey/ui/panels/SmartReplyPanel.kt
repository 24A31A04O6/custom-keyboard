package com.babeltech.babelkey.ui.panels

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.babeltech.babelkey.core.smartreply.SmartReplyEngine
import kotlinx.coroutines.*

/**
 * SmartReplyPanel — Phase 3 AI / smart replies (opt-in, on-device ML Kit primary).
 *
 * Shows 3 contextual replies for the current conversation. Tapping a chip commits it.
 * Opt-in banner when disabled — respects spec "opt-in (default OFF), disclosed, provider named".
 */
@Composable
fun SmartReplyPanel(
    engine: SmartReplyEngine,
    contextText: String,
    onReplyPicked: (String) -> Unit,
    onOptInChange: (Boolean) -> Unit
) {
    var enabled by remember { mutableStateOf(engine.isEnabled()) }
    var replies by remember(contextText, enabled) { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(contextText, enabled) {
        if (!enabled || contextText.isBlank()) { replies = emptyList(); return@LaunchedEffect }
        loading = true
        replies = engine.generateOnDevice(contextText)
        loading = false
    }

    Column(Modifier.fillMaxWidth().padding(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Smart replies — on-device (ML Kit)", style = MaterialTheme.typography.titleSmall)
            Switch(checked = enabled, onCheckedChange = {
                enabled = it
                // Persisted via PreferencesRepository.isAiRepliesEnabled — caller wires `onOptInChange`
                onOptInChange(it)
                if (!it) replies = emptyList()
            })
        }
        if (!enabled) {
            Text(
                "Smart replies are opt-in (default OFF). On-device ML Kit runs offline — no text sent off device. Online fallback (if ever added) would be HTTPS to a named provider and is also opt-in.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp)
            )
            OutlinedButton(onClick = { enabled = true; onOptInChange(true) }) { Text("Enable smart replies") }
            return
        }
        if (loading) { CircularProgressIndicator(Modifier.size(24.dp)); return }
        if (replies.isEmpty() && contextText.isNotBlank()) {
            Text("No suggestions for this context.", style = MaterialTheme.typography.bodySmall)
            return
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            replies.take(3).forEach { r ->
                SuggestionChip(onClick = { onReplyPicked(r) }, label = { Text(r) })
            }
        }
        Text("Provider: ML Kit Smart Reply (on-device, offline).", style = MaterialTheme.typography.labelSmall)
    }
}
