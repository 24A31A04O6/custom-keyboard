package com.babeltech.babelkey

import org.junit.Test
import org.junit.Assert.*
import com.babeltech.babelkey.core.otp.OtpDetector

class OtpDetectorTest {
    @Test fun extractsOtp() {
        assertEquals("123456", OtpDetector.extractOtp("Your code is 123456. Do not share."))
        assertEquals("9874", OtpDetector.extractOtp("OTP 9874"))
        assertNull(OtpDetector.extractOtp("no digits here"))
    }
    @Test fun rejectsWrongLengths() {
        assertFalse(OtpDetector.isOtp("123")) // too short
        assertFalse(OtpDetector.isOtp("123456789")) // too long
        assertTrue(OtpDetector.isOtp("1234"))
        assertTrue(OtpDetector.isOtp("12345678"))
    }
    @Test fun fieldEligibility() {
        val numericField = OtpDetector.FieldInfo(isNumberClass=true, inputType=2)
        assertTrue(OtpDetector.isEligible(numericField))
        val textFieldOtpHint = OtpDetector.FieldInfo(isNumberClass=false, inputType=1, autofillHints=listOf("smsOTP"))
        assertTrue(OtpDetector.isEligible(textFieldOtpHint))
        val arbitraryField = OtpDetector.FieldInfo(isNumberClass=false, inputType=1)
        assertFalse(OtpDetector.isEligible(arbitraryField))
    }
}
