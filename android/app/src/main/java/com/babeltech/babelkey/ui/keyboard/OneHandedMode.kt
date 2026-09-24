package com.babeltech.babelkey.ui.keyboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.babeltech.babelkey.data.preferences.OneHandedPrefs
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.Alignment

/**
 * OneHandedMode — Phase 3 one-handed keyboard container.
 *
 * Wraps the keyboard content and constrains its width + alignment per prefs.
 * Respects system reduced-motion (no animation if `prefersReducedMotion`).
 * Accessibility: TalkBack focus stays within narrowed bounds; switch access unchanged.
 *
 * Spec location: toolbar expanded row includes one-handed toggle; panel lists LEFT/RIGHT/OFF.
 */
@Composable
fun OneHandedContainer(
    prefs: OneHandedPrefs,
    prefersReducedMotion: Boolean = false,
    content: @Composable () -> Unit
) {
    var mode by remember { mutableStateOf(prefs.getMode()) }
    var width by remember { mutableStateOf(prefs.getWidthFraction()) }
    val alignment = when (mode) {
        OneHandedPrefs.Mode.LEFT -> Alignment.CenterStart
        OneHandedPrefs.Mode.RIGHT -> Alignment.CenterEnd
        OneHandedPrefs.Mode.OFF -> Alignment.Center
    }
    val targetWidth = if (mode == OneHandedPrefs.Mode.OFF) 1f else width
    val animatedWidth = if (prefersReducedMotion) targetWidth else animateDpAsState(
        targetValue = (targetWidth * 360).dp, label = "oneHandedWidth"
    ).value

    Column(Modifier.fillMaxWidth()) {
        // Toolbar to toggle — shown inside keyboard container for discoverability
        Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == OneHandedPrefs.Mode.OFF, onClick = { mode = OneHandedPrefs.Mode.OFF; prefs.setMode(mode) }, label = { Text("Full") })
            FilterChip(selected = mode == OneHandedPrefs.Mode.LEFT, onClick = { mode = OneHandedPrefs.Mode.LEFT; prefs.setMode(mode) }, label = { Text("Left") })
            FilterChip(selected = mode == OneHandedPrefs.Mode.RIGHT, onClick = { mode = OneHandedPrefs.Mode.RIGHT; prefs.setMode(mode) }, label = { Text("Right") })
            if (mode != OneHandedPrefs.Mode.OFF) {
                Slider(value = width, onValueChange = { width = it; prefs.setWidthFraction(it) }, valueRange = 0.7f..1f, modifier = Modifier.weight(1f))
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = alignment) {
            Box(Modifier.fillMaxWidth(if (mode == OneHandedPrefs.Mode.OFF) 1f else width)) { content() }
        }
    }
}
