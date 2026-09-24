package com.babeltech.babelkey.core.otp

import java.util.regex.Pattern

/**
 * OtpDetector — pure OTP detection + field-eligibility logic.
 *
 * Per spec:
 * - Supports 4–8 digit numeric OTPs
 * - Chip appears only on OTP-eligible fields (numeric type / short maxLen / autofill hint)
 * - Expires ~5 min or once used
 * - Never written to clipboard history / disk
 */
object OtpDetector {
    private val OTP_PATTERN = Pattern.compile("\\b\\d{4,8}\\b")

    fun extractOtp(text: String): String? {
        val m = OTP_PATTERN.matcher(text)
        return if (m.find()) m.group() else null
    }

    fun isOtp(text: String): Boolean = OTP_PATTERN.matcher(text.trim()).matches()

    // Field eligibility — mirrors Android InputType / autofill hints without importing android.* in core
    data class FieldInfo(
        val isNumberClass: Boolean,
        val inputType: Int,
        val maxLength: Int? = null,
        val autofillHints: List<String> = emptyList(),
        val isPassword: Boolean = false
    )

    fun isEligible(field: FieldInfo): Boolean {
        if (field.isPassword) return false // never on password fields
        val hintSaysOtp = field.autofillHints.any { it.lowercase().contains("otp") || it.lowercase().contains("verification") || it.lowercase().contains("sms") }
        val numeric = field.isNumberClass
        val shortMax = field.maxLength != null && field.maxLength in 4..8
        return numeric || hintSaysOtp || shortMax
    }

    // Android helper: call from service/ime with real EditorInfo
    fun isEligibleEditorInfo(editorInfo: android.view.inputmethod.EditorInfo): Boolean {
        val inputType = editorInfo.inputType
        val clazz = inputType and android.text.InputType.TYPE_MASK_CLASS
        val isNumberClass = clazz == android.text.InputType.TYPE_CLASS_NUMBER
        val isPassword = (inputType and android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD) == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        val hints = editorInfo.autofillHints?.toList() ?: emptyList()
        val maxLen = try {
            // Try to read InputFilter.LengthFilter max via reflection on EditorInfo extras if present
            null
        } catch (_: Exception) { null }
        return isEligible(FieldInfo(isNumberClass, inputType, maxLen, hints as List<String>, isPassword))
    }
}
