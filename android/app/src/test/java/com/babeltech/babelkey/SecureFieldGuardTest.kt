package com.babeltech.babelkey

import org.junit.Test
import org.junit.Assert.*
import com.babeltech.babelkey.core.input.EditorCompat
import com.babeltech.babelkey.security.ClipboardPolicy
import com.babeltech.babelkey.core.otp.OtpDetector

class SecureFieldGuardTest {
    @Test fun passwordFieldDetection() {
        // TYPE_TEXT_VARIATION_PASSWORD = 0x80, TYPE_CLASS_TEXT = 1
        val passwordType = 1 or 0x80
        assertTrue(EditorCompat.isPasswordVariation(0x80))
        assertTrue(EditorCompat.shouldSuppressSuggestions(0x80000))
    }
    @Test fun clipboardPolicyRejectsOtp() {
        assertFalse(ClipboardPolicy.isAllowed("123456"))
        assertFalse(ClipboardPolicy.isAllowed("  1234  "))
        assertTrue(ClipboardPolicy.isAllowed("hello world"))
    }
    @Test fun otpEligibility() {
        assertTrue(OtpDetector.isEligible(OtpDetector.FieldInfo(isNumberClass=true, inputType=2, maxLength=6)))
        assertTrue(OtpDetector.isEligible(OtpDetector.FieldInfo(isNumberClass=false, inputType=1, autofillHints=listOf("otp"))))
        assertFalse(OtpDetector.isEligible(OtpDetector.FieldInfo(isNumberClass=false, inputType=1, isPassword=true)))
    }
    @Test fun surrogatePairHandling() {
        val emoji = "\uD83D\uDE00" // 😀 surrogate pair
        val withEmoji = "hi" + emoji
        assertTrue(EditorCompat.containsSurrogatePair(withEmoji))
        assertFalse(EditorCompat.containsSurrogatePair("hello"))
        assertEquals(2, EditorCompat.charsToDeleteForBackspace(withEmoji))
        assertEquals(1, EditorCompat.charsToDeleteForBackspace("hello"))
    }
}
