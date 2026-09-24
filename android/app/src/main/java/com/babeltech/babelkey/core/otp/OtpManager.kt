package com.babeltech.babelkey.core.otp

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * OtpManager — holds OTP chip state in RAM only (never to disk/clipboard), expires ~5 min or once used.
 *
 * Per spec: chip appears only on OTP-eligible fields, expires after ~5 min or once used; never written to clipboard history or disk.
 */
class OtpManager {
    private val handler = Handler(Looper.getMainLooper())
    private var expiryRunnable: Runnable? = null

    private val _otp = MutableStateFlow<String?>(null)
    val otp: StateFlow<String?> = _otp

    private val _eligible = MutableStateFlow(false)
    val eligible: StateFlow<Boolean> = _eligible

    fun setEligible(eligible: Boolean) { _eligible.value = eligible; if (!eligible) clear() }

    fun onOtpReceived(otp: String) {
        if (!_eligible.value) return // ignore if not on eligible field
        if (!OtpDetector.isOtp(otp)) return
        _otp.value = otp
        scheduleExpiry()
    }

    fun consume(): String? {
        val v = _otp.value ?: return null
        clear()
        return v
    }

    fun clear() {
        _otp.value = null
        expiryRunnable?.let { handler.removeCallbacks(it) }
        expiryRunnable = null
    }

    private fun scheduleExpiry() {
        expiryRunnable?.let { handler.removeCallbacks(it) }
        val r = Runnable { clear() }
        expiryRunnable = r
        handler.postDelayed(r, 5 * 60 * 1000L) // 5 minutes
    }
}
