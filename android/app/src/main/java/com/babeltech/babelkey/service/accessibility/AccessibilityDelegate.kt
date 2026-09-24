package com.babeltech.babelkey.service.accessibility

import android.view.View
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

/**
 * AccessibilityDelegate — TalkBack + switch access hooks.
 *
 * Ensures ui/keyboard keys and ui/suggestions chips expose proper
 * contentDescription, role, and actions for accessibility services.
 */
class AccessibilityDelegate : AccessibilityDelegateCompat() {
    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
        super.onInitializeAccessibilityNodeInfo(host, info)
        info.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
    }
}
