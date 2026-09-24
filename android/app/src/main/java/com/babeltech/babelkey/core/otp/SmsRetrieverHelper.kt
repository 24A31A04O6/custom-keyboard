package com.babeltech.babelkey.core.otp

import android.content.Context
import android.content.pm.PackageManager
import android.util.Base64
import java.security.MessageDigest

/**
 * SmsRetrieverHelper — generates the 11-char app hash for SMS Retriever API.
 *
 * Spec OTP architecture: "Generate/document the app hash string needed for it."
 *
 * Hash = base64( first 9 bytes of SHA-256( packageName + " " + hex-cert ) ) truncated to 11 chars.
 * See https://developers.google.com/identity/sms-retriever/verify#computing_your_apps_hash_string
 */
object SmsRetrieverHelper {

    fun getAppHash(context: Context): String? = try {
        val pkg = context.packageName
        val sig = getSigningCertSha256Hex(context) ?: return null
        computeHash(pkg, sig)
    } catch (_: Exception) { null }

    fun computeHash(packageName: String, certHex: String): String {
        val appInfo = "$packageName $certHex"
        val md = MessageDigest.getInstance("SHA-256")
        md.update(appInfo.toByteArray(Charsets.UTF_8))
        var hash = Base64.encodeToString(md.digest(), Base64.NO_WRAP or Base64.NO_PADDING or Base64.NO_WRAP)
        // SMS Retriever expects 11 chars
        if (hash.length > 11) hash = hash.substring(0, 11)
        return hash
    }

    private fun getSigningCertSha256Hex(context: Context): String? = try {
        val pm = context.packageManager
        val pkg = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val sigs = pkg.signingInfo?.apkContentsSigners ?: return null
        if (sigs.isEmpty()) return null
        val cert = sigs[0].toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        md.digest(cert).joinToString("") { "%02x".format(it) }
    } catch (_: Exception) { null }

    /** Returns a formatted SMS sample showing where the hash should appear. */
    fun sampleSms(otp: String, hash: String): String =
        "<#> Your BabelKey verification code is: $otp\n$hash"
}
