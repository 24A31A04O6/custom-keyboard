package com.babeltech.babelkey.service.ime

import android.content.IntentFilter
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import com.babeltech.babelkey.core.input.GlideTypingEngine
import com.babeltech.babelkey.core.input.KeyEventProcessor
import com.babeltech.babelkey.core.otp.OtpDetector
import com.babeltech.babelkey.core.otp.OtpManager
import com.babeltech.babelkey.core.otp.OtpSmsReceiver
import com.babeltech.babelkey.core.suggestion.UndoManager
import com.babeltech.babelkey.data.dictionaries.DictionaryRepository
import com.babeltech.babelkey.data.dictionaries.MigrationTool
import com.babeltech.babelkey.data.preferences.FontRepository
import com.babeltech.babelkey.data.preferences.PreferencesRepository
import com.babeltech.babelkey.security.SecureFieldGuard
import com.babeltech.babelkey.ui.toolbar.ScreenshotDetector
import com.google.android.gms.auth.api.phone.SmsRetriever

/**
 * BabelImeService — thin InputMethodService per spec Part 0 rule #4.
 *
 * Responsibilities ONLY: lifecycle, wiring data/core/ui, InputConnection proxying.
 * All feature logic lives in core/ and data/.
 *
 * Phase 1: thin service with dictionary verification + secure-field gating.
 * Phase 2 additions (wired here, UI integration incremental):
 * - GlideTypingEngine (Viterbi scoring, <16ms, off-main-thread)
 * - Google Fonts via FontRepository (opt-in, HTTPS, cached)
 * - SMS Retriever for OTP auto-paste (OtpManager + OtpSmsReceiver, 4–8 digits, 5-min expiry, never clipboard/disk)
 * - ScreenshotDetector for contextual ToolbarState.Idle chip
 *
 * Legacy MyKeyboardService remains as alias for one release to avoid breakage.
 */
class BabelImeService : InputMethodService() {

    private lateinit var prefs: PreferencesRepository
    private lateinit var dicts: DictionaryRepository
    private lateinit var fontRepo: FontRepository
    private lateinit var undoManager: UndoManager
    private lateinit var keyProcessor: KeyEventProcessor
    private lateinit var inputProxy: InputConnectionProxy
    private lateinit var lifecycleDelegate: ImeLifecycle
    private lateinit var glideEngine: GlideTypingEngine
    private lateinit var otpManager: OtpManager
    private lateinit var screenshotDetector: ScreenshotDetector
    private var otpReceiver: OtpSmsReceiver? = null

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesRepository(applicationContext).apply { migrateIfNeeded() }
        dicts = DictionaryRepository(applicationContext)
        fontRepo = FontRepository(applicationContext, prefs)
        val verification = MigrationTool.verifyAssets(applicationContext)
        if (!verification.ok) {
            android.util.Log.w("BabelImeService", "Dictionary verification failed: ${verification.failures}")
        }
        undoManager = UndoManager()
        keyProcessor = KeyEventProcessor(undoManager = undoManager)
        inputProxy = InputConnectionProxy { currentInputConnection }
        lifecycleDelegate = ImeLifecycle()
        glideEngine = GlideTypingEngine(dicts)
        otpManager = OtpManager()
        screenshotDetector = ScreenshotDetector(applicationContext)
        lifecycleDelegate.onCreate()
        // Phase 2: start OTP retriever + screenshot watcher
        startSmsRetriever()
        screenshotDetector.start()
    }

    override fun onCreateInputView(): View {
        // Phase 2 incremental: keep legacy keyboard_view.xml host for v1 stability.
        // Next iteration swaps to ComposeView hosting ui/keyboard + ui/toolbar + ui/suggestions.
        // Compose host is available behind feature flag (prefs.isSwipeEnabled) — not enabled until Phase 2 stabilized.
        val view = layoutInflater.inflate(com.babeltech.babelkey.R.layout.keyboard_view, null)
        lifecycleDelegate.onCreateInputView()
        return view
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        lifecycleDelegate.onStartInput(info, restarting)
        val isSecure = SecureFieldGuard.isSecure(info)
        if (isSecure) keyProcessor.resetComposing()
        val otpEligible = if (!isSecure && info != null) OtpDetector.isEligibleEditorInfo(info) else false
        otpManager.setEligible(otpEligible)
        if (otpEligible) startSmsRetriever()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        keyProcessor.resetComposing()
        lifecycleDelegate.onFinishInput()
    }

    override fun onEvaluateInputViewShown(): Boolean = true

    override fun onDestroy() {
        super.onDestroy()
        lifecycleDelegate.onDestroy()
        screenshotDetector.stop()
        otpReceiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
    }

    private fun startSmsRetriever() {
        try {
            val client = SmsRetriever.getClient(this)
            val task = client.startSmsRetriever()
            task.addOnSuccessListener {
                android.util.Log.d("BabelImeService", "SmsRetriever started")
                if (otpReceiver == null) {
                    otpReceiver = OtpSmsReceiver(otpManager)
                    registerReceiver(otpReceiver, IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION))
                }
            }
            task.addOnFailureListener { e -> android.util.Log.w("BabelImeService", "SmsRetriever failed", e) }
        } catch (e: Exception) {
            android.util.Log.w("BabelImeService", "SmsRetriever not available", e)
        }
    }

    /** Called when OTP chip is tapped — commits OTP and never writes to clipboard. */
    fun onOtpChipTapped() {
        val otp = otpManager.consume() ?: return
        inputProxy.commitText(otp)
    }
}
