package com.babeltech.babelkey.core.otp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status

/**
 * OtpSmsReceiver — SMS Retriever broadcast receiver for auto OTP paste (Phase 2).
 *
 * No RECEIVE_SMS permission needed. Provider SMS must contain the 11-char app hash.
 *
 * Register via SmsRetriever.getClient(context).startSmsRetriever() in service/ime.
 */
class OtpSmsReceiver(private val otpManager: OtpManager) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (SmsRetriever.SMS_RETRIEVED_ACTION != intent.action) return
        val extras = intent.extras ?: return
        val status = extras.get(SmsRetriever.EXTRA_STATUS) as? Status ?: return
        when (status.statusCode) {
            CommonStatusCodes.SUCCESS -> {
                val msg = extras.getString(SmsRetriever.EXTRA_SMS_MESSAGE) ?: return
                val otp = OtpDetector.extractOtp(msg) ?: return
                otpManager.onOtpReceived(otp)
            }
            CommonStatusCodes.TIMEOUT -> {
                // Retriever timed out after 5 min — no OTP in window
            }
        }
    }
}
