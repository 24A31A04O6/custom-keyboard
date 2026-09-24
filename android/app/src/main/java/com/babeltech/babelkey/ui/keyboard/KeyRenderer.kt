package com.babeltech.babelkey.ui.keyboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * KeyRenderer — renders a single key (background, ripple, preview).
 * No business logic; receives state from KeyboardViewModel.
 */
@Composable
fun KeyRenderer(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier.height(48.dp), contentPadding = PaddingValues(4.dp)) {
        Text(label)
    }
}
