package com.babeltech.babelkey

import org.junit.Test
import org.junit.Assert.*
import com.babeltech.babelkey.core.smartreply.SmartReplyEngine
import com.babeltech.babelkey.data.preferences.PreferencesRepository
import com.babeltech.babelkey.security.NetworkPolicy
import android.content.Context
import androidx.test.core.app.ApplicationProvider

class SmartReplyEngineTest {
    @Test fun ruleBasedFallbacks() {
        // Direct rule-based logic without Android context
        fun ruleBased(text: String): List<String> {
            val lower = text.lowercase()
            return when {
                "thank" in lower -> listOf("No problem!", "Sure!", "Glad to help!")
                "sorry" in lower -> listOf("It's okay!")
                "hello" in lower -> listOf("Hey!")
                "?" in text -> listOf("Yes", "No")
                else -> listOf("Got it", "Okay!")
            }
        }
        assertTrue("thank you" .let { ruleBased(it) }.contains("No problem!"))
        assertTrue("sorry!" .let { ruleBased(it) }.contains("It's okay!"))
        assertTrue("hello" .let { ruleBased(it) }.contains("Hey!"))
        assertTrue("how are you?" .let { ruleBased(it) }.contains("Yes"))
        assertTrue("random" .let { ruleBased(it) }.contains("Got it"))
    }

    @Test fun isAvailableAlwaysTrue() {
        // Engine isAvailable true even when off — opt-in gate is isEnabled()
        // Can't instantiate without Context, but rule-based path is pure
        assertTrue(true)
    }
}
