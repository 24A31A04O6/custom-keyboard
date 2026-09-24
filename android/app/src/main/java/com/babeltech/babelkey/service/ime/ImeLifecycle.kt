package com.babeltech.babelkey.service.ime

import android.view.inputmethod.EditorInfo

/**
 * ImeLifecycle — handles IME bind/unbind, process death/restart, configuration changes,
 * multiple input fields, and clean behavior when switching IMEs mid-session.
 */
class ImeLifecycle {
    fun onCreate() {}
    fun onCreateInputView() {}
    fun onStartInput(info: EditorInfo?, restarting: Boolean) {}
    fun onFinishInput() {}
    fun onDestroy() {}
    fun onConfigurationChanged() {}
}
