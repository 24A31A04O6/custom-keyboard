package com.babeltech.babelkey.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * IncognitoMode — user-toggled incognito that suppresses learning/clipboard.
 */
class IncognitoMode {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled
    fun set(enabled: Boolean) { _enabled.value = enabled }
    fun isEnabled(): Boolean = _enabled.value
}
