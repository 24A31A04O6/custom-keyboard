package com.babeltech.babelkey.security

import com.babeltech.babelkey.core.otp.OtpDetector

/**
 * ClipboardPolicy — enforces clipboard write rules.
 *
 * - Never persist OTPs or secure-field-derived text
 * - Never persist overly long clipboard entries (> 10k chars)
 */
object ClipboardPolicy {
    private const val MAX_CLIP_LEN = 10_000

    fun isAllowed(text: String): Boolean {
        if (text.isBlank()) return false
        if (text.length > MAX_CLIP_LEN) return false
        if (OtpDetector.isOtp(text)) return false // never write OTP to history
        return true
    }
}
