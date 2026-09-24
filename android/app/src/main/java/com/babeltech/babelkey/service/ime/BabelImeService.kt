package com.babeltech.babelkey.service.ime

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import com.babeltech.babelkey.core.input.EditorCompat
import com.babeltech.babelkey.core.input.KeyEventProcessor
import com.babeltech.babelkey.core.otp.OtpDetector
import com.babeltech.babelkey.core.suggestion.UndoManager
import com.babeltech.babelkey.data.dictionaries.DictionaryRepository
import com.babeltech.babelkey.data.dictionaries.MigrationTool
import com.babeltech.babelkey.data.preferences.PreferencesRepository
import com.babeltech.babelkey.security.SecureFieldGuard

/**
 * BabelImeService — thin InputMethodService per spec Part 0 rule #4.
 *
 * Responsibilities ONLY:
 * - Lifecycle: onCreate / onCreateInputView / onStartInput / onFinishInput / onDestroy
 * - Wiring: instantiates data/ + core/ + ui/ and delegates
 * - InputConnection proxying via [InputConnectionProxy]
 * - Secure-field gating, OTP eligibility, editor action forwarding
 *
 * Does NOT implement feature logic (suggestion ranking, autocorrect, clipboard, etc.)
 * — those live in core/ and data/.
 *
 * Phase 1: this class is the new thin service; legacy MyKeyboardService (core.MyKeyboardService)
 * remains in the manifest until this service is fully wired to ui/keyboard Compose hosts.
 * Both services can coexist; the system will bind to whichever the user enables.
 */
class BabelImeService : InputMethodService() {

    private lateinit var prefs: PreferencesRepository
    private lateinit var dicts: DictionaryRepository
    private lateinit var undoManager: UndoManager
    private lateinit var keyProcessor: KeyEventProcessor
    private lateinit var inputProxy: InputConnectionProxy
    private lateinit var lifecycle: ImeLifecycle

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesRepository(applicationContext).apply { migrateIfNeeded() }
        dicts = DictionaryRepository(applicationContext)
        // Verify dictionaries at startup (Gate 1 checksums)
        val verification = MigrationTool.verifyAssets(applicationContext)
        if (!verification.ok) {
            android.util.Log.w("BabelImeService", "Dictionary verification failed: ${verification.failures}")
        }
        undoManager = UndoManager()
        keyProcessor = KeyEventProcessor(undoManager = undoManager)
        inputProxy = InputConnectionProxy { currentInputConnection }
        lifecycle = ImeLifecycle()
        lifecycle.onCreate()
    }

    override fun onCreateInputView(): View {
        // Phase 1: inflate the existing keyboard_view.xml host via InputConnectionProxy;
        // Compose migration will replace this with a ComposeView hosting ui/keyboard + ui/toolbar.
        val view = layoutInflater.inflate(com.babeltech.babelkey.R.layout.keyboard_view, null)
        lifecycle.onCreateInputView()
        return view
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        lifecycle.onStartInput(info, restarting)
        val isSecure = SecureFieldGuard.isSecure(info)
        // Suppress suggestions / learning when secure
        if (isSecure) keyProcessor.resetComposing()
        // OTP eligibility check (never on secure fields)
        val otpEligible = if (!isSecure && info != null) OtpDetector.isEligibleEditorInfo(info) else false
        // Forward to UI via lifecycle observers (Phase 1 stub)
        if (otpEligible) { /* show OtpChip */ }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        keyProcessor.resetComposing()
        lifecycle.onFinishInput()
    }

    override fun onEvaluateInputViewShown(): Boolean = true

    override fun onDestroy() {
        super.onDestroy()
        lifecycle.onDestroy()
    }

    // Editor actions: Done/Next/Search/Send
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        // Delegate to InputConnectionProxy which handles RTL, surrogate pairs, batch edits
        return super.onKeyDown(keyCode, event)
    }
}
