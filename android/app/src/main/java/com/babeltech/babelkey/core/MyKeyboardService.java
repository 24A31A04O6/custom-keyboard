package com.babeltech.babelkey.core;

import com.babeltech.babelkey.R;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.babeltech.babelkey.clipboard.KeyboardClipboardManager;
import com.babeltech.babelkey.emoji.EmojiKeyboardView;
import com.babeltech.babelkey.gesture.GestureHandler;
import com.babeltech.babelkey.layout.KeySymbolMap;
import com.babeltech.babelkey.layout.SymbolPopupWindow;
import com.babeltech.babelkey.settings.KeyboardSettingsActivity;
import com.babeltech.babelkey.suggestion.SuggestionManager;
import com.babeltech.babelkey.suggestion.SuggestionView;
import com.babeltech.babelkey.theme.PickImageActivity;
import com.babeltech.babelkey.theme.SettingsThemeActivity;
import com.babeltech.babelkey.theme.SoundHapticManager;
import com.babeltech.babelkey.theme.ThemeManager;
import com.babeltech.babelkey.voice.VoiceTypingManager;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// TODO: Migrate away from android.inputmethodservice.KeyboardView (deprecated since API 29).
//       Until that migration is complete these classes are kept via ProGuard keep-rules.
@SuppressWarnings("deprecation")
public class MyKeyboardService extends InputMethodService
        implements KeyboardView.OnKeyboardActionListener,
        SuggestionView.SuggestionClickListener,
        EmojiKeyboardView.EmojiListener {

    private static final String TAG = "MyKeyboardService";

    // Keyboard modes
    public static final int MODE_QWERTY = 0; // public: SuggestionManager compares layer against it
    private static final int MODE_SYMBOLS = 1, MODE_SYM_SHIFT = 2;
    private static final int MODE_EMOJI = 3, MODE_NUMPAD = 4;
    // Shift states
    private static final int SHIFT_OFF = 0, SHIFT_ON = 1, SHIFT_CAPS = 2;
    // Key codes
    private static final int CODE_SHIFT      = Keyboard.KEYCODE_SHIFT;
    private static final int CODE_BACKSPACE  = Keyboard.KEYCODE_DELETE;
    private static final int CODE_CANCEL     = Keyboard.KEYCODE_CANCEL;
    private static final int CODE_DONE       = Keyboard.KEYCODE_DONE;
    private static final int CODE_SYM_SHIFT  = -2;
    private static final int CODE_EMOJI      = -10;
    private static final int CODE_NUMPAD     = -12;
    private static final int CODE_SYM_DIRECT = -13;
    private static final int CODE_SPACE      = 32;
    // Prefs
    private static final String PREFS_NAME   = "MyBoardPrefs";
    private static final String PREF_SWIPE   = "swipeTypingEnabled";
    private static final String PREF_AI      = "aiRepliesEnabled";
    private static final String PREF_KB_H    = "kb_height_v2";
    private static final String PREF_TOOLBAR = "toolbarButtonConfig";
    private static final int    DEFAULT_DP   = 0;
    // Flipper child indices
    private static final int FLIPPER_KB    = 0;
    private static final int FLIPPER_EMOJI = 1;
    private static final int FLIPPER_THEME = 2;
    private static final int FLIPPER_MISC  = 3;

    // Managers
    private ThemeManager             themeManager;
    private SoundHapticManager       soundManager;
    private VoiceTypingManager       voiceManager;
    private KeyboardClipboardManager clipManager;
    private GestureHandler           gestureHandler;
    private SuggestionManager        suggManager;

    // Views
    private BabelKeyboardView  keyboardView;
    private EditorInfo         currentEditorInfo = null;
    private LinearLayout  keyboardRoot;
    private LinearLayout  suggestionContainer, clipboardContainer, phrasesContainer;
    private LinearLayout  clipboardPanel, otpBanner, grammarBanner, statsContainer;
    private LinearLayout  trackpadOverlayLayout, aiRepliesContainer, voiceTypingPanel;
    private LinearLayout  toolbarRow, toolbarCustomizerPanel, toolbarCustomizerList;
    private LinearLayout  toolbarStripDynamic;
    private final java.util.LinkedHashMap<String, View> tbBtnViews = new java.util.LinkedHashMap<>();
    private View          phrasesPanel, aiRepliesPanel, emojiPanel;
    private ScrollView    statsPanel, settingsPanel;
    private TextView      otpText, grammarText, themePreviewLabel, voiceStatusText;
    private View          resizeOverlay, resizeDragHandle;
    private boolean       resizeModeActive = false;
    private float         resizeDragStartY = 0f;
    private int           resizeDragStartH = 0;
    private android.widget.ViewFlipper  contentFlipper;
    private android.widget.FrameLayout  emojiFlipperHost;
    private android.widget.PopupWindow  keyPreviewPopup;
    private TextView                    keyPreviewText;
    private View                        clipboardDrawer;

    // ── Symbol long-press popup ───────────────────────────────────────────────
    private SymbolPopupWindow symbolPopup;
    /** X-centre of the most-recently pressed key, in window coordinates (px). */
    private int               longPressKeyX = 0;


    // Keyboard objects
    private Keyboard          qwertyKeyboard, symbolsKeyboard, symbolsShiftKeyboard, numpadKeyboard;
    private EmojiKeyboardView emojiKeyboardView;

    // State
    private int     currentMode        = MODE_QWERTY;
    private int     shiftState         = SHIFT_OFF;
    @SuppressWarnings("unused")
    private int     lastPressedKeyCode = 0;
    private boolean swipeEnabled       = false;

    // Toolbar button definitions
    private static class TBDef {
        String key, icon, label;
        TBDef(String k, String i, String l) { key = k; icon = i; label = l; }
    }
    private static final String PERM_KEY = "settings";
    private final List<TBDef> allBtns = Arrays.asList(
        new TBDef("clipboard", "\uD83D\uDCCB", "Clipboard"),
        new TBDef("translate", "\uD83C\uDF10", "Translate"),
        new TBDef("autocorrect", "\u2728", "Auto Correct"),
        new TBDef("emoji", "\uD83D\uDE0A", "Emoji"),
        new TBDef("settings", "\u2699\uFE0F", "Settings"),
        new TBDef("voice", "\uD83C\uDFA4", "Voice Typing"),
        new TBDef("undo", "\u21A9\uFE0F", "Undo"),
        new TBDef("phrases", "\uD83D\uDCAC", "Phrases"),
        new TBDef("stats", "\uD83D\uDCCA", "Stats"));
    private List<String> tbOrder   = new ArrayList<>();
    private Set<String>  tbVisible = new HashSet<>();

    private final Handler     handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    // ServiceCallback implementation
    private final ServiceCallback svcCb = new ServiceCallback() {
        @Override public void commitText(String t) {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) { ic.commitText(t, 1); suggManager.currentWord.setLength(0); suggManager.clearSuggestions(); }
        }
        @Override public void onThemeChanged(boolean dark) {
            clipManager.onThemeChanged(dark); suggManager.onThemeChanged(dark);
        }
        @Override public void onSuggestionsReady(List<String> s) { if (suggManager != null) suggManager.updateSuggestions(); }
        @Override public void onSwipeSuggestionsReady(List<String> s) { if (suggManager != null) suggManager.showSwipeSuggestions(s); }
        @Override public void onVoiceResultInserted() {
            suggManager.currentWord.setLength(0); suggManager.clearSuggestions(); autoCapitalize();
        }
        @Override public InputConnection getInputConnection() { return getCurrentInputConnection(); }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        
        final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            CrashLogger.logError("MyKeyboardService", "UncaughtException", throwable.getMessage(), throwable);
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        });
        
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        swipeEnabled = prefs.getBoolean(PREF_SWIPE, false);
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        themeManager   = new ThemeManager(this, prefs, svcCb);
        soundManager   = new SoundHapticManager(this, prefs);
        voiceManager   = new VoiceTypingManager(this, prefs, svcCb);
        clipManager    = new KeyboardClipboardManager(this, prefs, cm, svcCb);
        suggManager    = new SuggestionManager(this, prefs, svcCb);
        suggManager.setAiEnabled(prefs.getBoolean("aiRepliesEnabled", false));
        gestureHandler = new GestureHandler(this, prefs, svcCb, suggManager.DICT, suggManager.TELUGU_DICT);
        soundManager.init();
        suggManager.loadAll();
        clipManager.loadHistory(); clipManager.loadPinned(); clipManager.startListening();
    }

    @Override
    public View onCreateInputView() {
        View root = LayoutInflater.from(this).inflate(R.layout.keyboard_view, null);
        keyboardRoot = root.findViewById(R.id.keyboard_root);
        keyboardView           = keyboardRoot.findViewById(R.id.keyboard_view);
        suggestionContainer    = keyboardRoot.findViewById(R.id.suggestion_container);
        clipboardContainer     = keyboardRoot.findViewById(R.id.clipboard_container);
        phrasesContainer       = keyboardRoot.findViewById(R.id.phrases_container);
        clipboardPanel         = keyboardRoot.findViewById(R.id.clipboard_panel);
        phrasesPanel           = keyboardRoot.findViewById(R.id.phrases_panel);
        otpBanner              = keyboardRoot.findViewById(R.id.otp_banner);
        otpText                = keyboardRoot.findViewById(R.id.otp_text);
        grammarBanner          = keyboardRoot.findViewById(R.id.grammar_banner);
        grammarText            = keyboardRoot.findViewById(R.id.grammar_text);
        statsPanel             = keyboardRoot.findViewById(R.id.stats_panel);
        statsContainer         = keyboardRoot.findViewById(R.id.stats_container);
        settingsPanel          = keyboardRoot.findViewById(R.id.settings_panel);
        themePreviewLabel      = keyboardRoot.findViewById(R.id.theme_preview_label);
        aiRepliesPanel         = keyboardRoot.findViewById(R.id.ai_replies_panel);
        aiRepliesContainer     = keyboardRoot.findViewById(R.id.ai_replies_container);
        trackpadOverlayLayout  = root.findViewById(R.id.trackpad_overlay);
        resizeOverlay          = root.findViewById(R.id.resize_overlay);
        resizeDragHandle       = root.findViewById(R.id.resize_drag_handle);
        contentFlipper         = keyboardRoot.findViewById(R.id.content_flipper);
        emojiFlipperHost       = keyboardRoot.findViewById(R.id.emoji_flipper_host);
        toolbarRow             = keyboardRoot.findViewById(R.id.toolbar_row);
        toolbarStripDynamic    = keyboardRoot.findViewById(R.id.toolbar_strip_dynamic);
        voiceTypingPanel       = keyboardRoot.findViewById(R.id.voice_typing_panel);
        voiceStatusText        = keyboardRoot.findViewById(R.id.voice_status_text);
        toolbarCustomizerPanel = keyboardRoot.findViewById(R.id.toolbar_customizer_panel);
        toolbarCustomizerList  = keyboardRoot.findViewById(R.id.toolbar_customizer_list);
        clipboardDrawer        = keyboardRoot.findViewById(R.id.clipboard_drawer);

        keyPreviewText = new TextView(this);
        keyPreviewText.setGravity(android.view.Gravity.CENTER);
        keyPreviewText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 32f);
        keyPreviewText.setTextColor(Color.WHITE);
        keyPreviewText.setTypeface(null, android.graphics.Typeface.BOLD);
        keyPreviewText.setBackground(getResources().getDrawable(R.drawable.key_preview_bg, null));
        keyPreviewText.setMinWidth(dpToPx(48)); keyPreviewText.setMinHeight(dpToPx(56));
        keyPreviewText.setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10));
        keyPreviewPopup = new android.widget.PopupWindow(keyPreviewText,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        keyPreviewPopup.setBackgroundDrawable(null); keyPreviewPopup.setTouchable(false);
        keyPreviewPopup.setFocusable(false); keyPreviewPopup.setOutsideTouchable(false);
        // ResponsiveKeyboardLayout extends Keyboard and supports fitTo() so the
        // keyboard scales to the real view width and to user-chosen resize height.
        qwertyKeyboard       = new com.babeltech.babelkey.layout.ResponsiveKeyboardLayout(this, R.xml.keyboard_qwerty);
        symbolsKeyboard      = new com.babeltech.babelkey.layout.ResponsiveKeyboardLayout(this, R.xml.keyboard_symbols);
        symbolsShiftKeyboard = new com.babeltech.babelkey.layout.ResponsiveKeyboardLayout(this, R.xml.keyboard_symbols_shift);
        numpadKeyboard       = new com.babeltech.babelkey.layout.ResponsiveKeyboardLayout(this, R.xml.keyboard_numpad);
        keyboardView.setOnKeyboardActionListener(this); keyboardView.setPreviewEnabled(false);
        // Touch events are handled by GestureHandler


        themeManager.attachViews(keyboardRoot, keyboardView, themePreviewLabel,
            voiceTypingPanel, clipboardPanel, phrasesPanel, statsPanel, settingsPanel, keyPreviewText);
        LinearLayout toolbarStrip = keyboardRoot.findViewById(R.id.toolbar_strip);
        voiceManager.attachViews(voiceTypingPanel, voiceStatusText, toolbarStrip);
        clipManager.attachDrawer(clipboardDrawer);
        clipManager.attachOtpBanner(otpBanner, otpText);
        symbolPopup = new SymbolPopupWindow(this);

        keyboardView.setBabelListener(key -> handleLongPress(key));

        suggManager.attachViews(suggestionContainer, themeManager.isDark(), statsPanel, statsContainer,
            grammarBanner, grammarText, aiRepliesPanel, aiRepliesContainer, phrasesPanel, phrasesContainer);
        gestureHandler.attachKeyboardView(keyboardView, trackpadOverlayLayout);
        gestureHandler.attachPreview(keyPreviewPopup, keyPreviewText);
        gestureHandler.attachSymbolPopup(symbolPopup);
        gestureHandler.setCurrentMode(currentMode); gestureHandler.setShiftState(shiftState);
        setKbH(prefs.getInt(PREF_KB_H, DEFAULT_DP));
        wireResizeOverlay(root); wireSettingsPanel();
        loadToolbarConfig(); rebuildToolbar();
        View bCust = keyboardRoot.findViewById(R.id.btn_customize_toolbar);
        if (bCust != null) bCust.setOnClickListener(v -> toggleTBCustomizer());
        View bDone = keyboardRoot.findViewById(R.id.btn_tb_customizer_done);
        if (bDone != null) bDone.setOnClickListener(v -> toggleTBCustomizer());
        View bVB = keyboardRoot.findViewById(R.id.btn_voice_back);
        if (bVB  != null) bVB.setOnClickListener(v -> voiceManager.exitVoiceTypingMode());
        View bVC = keyboardRoot.findViewById(R.id.btn_voice_collapse);
        if (bVC  != null) bVC.setOnClickListener(v -> voiceManager.exitVoiceTypingMode());
        View bVM = keyboardRoot.findViewById(R.id.btn_voice_mic);
        if (bVM  != null) bVM.setOnClickListener(v -> {
            if (voiceManager.isListening()) {
                voiceManager.exitVoiceTypingMode();
            } else {
                voiceManager.handleVoiceTyping();
            }
        });
        View bOP = keyboardRoot.findViewById(R.id.btn_otp_paste);
        if (bOP  != null) bOP.setOnClickListener(v -> clipManager.pasteOtp());
        View bOD = keyboardRoot.findViewById(R.id.btn_otp_dismiss);
        if (bOD  != null) bOD.setOnClickListener(v -> clipManager.hideOtpBanner());
        View bGD = keyboardRoot.findViewById(R.id.btn_grammar_dismiss);
        if (bGD != null) bGD.setOnClickListener(v -> suggManager.hideGrammarBanner());
        clipManager.refresh(); suggManager.refreshPhrases();
        themeManager.applyCurrentTheme(); switchToQwerty();
        return root;
    }

    @Override public void onStartInput(EditorInfo a, boolean r) {
        super.onStartInput(a, r);
        currentEditorInfo = a;
        updateEnterKey(a);
        suggManager.currentWord.setLength(0);
        suggManager.clearSuggestions();
        if (keyboardView != null) keyboardView.cleanupAllPointers();
        if (gestureHandler != null) gestureHandler.reset();
        if (symbolPopup != null && symbolPopup.isShowing()) symbolPopup.dismiss();
    }

    @Override public void onStartInputView(EditorInfo i, boolean r) {
        super.onStartInputView(i, r);
        if (keyboardView != null) keyboardView.cleanupAllPointers();
        if (gestureHandler != null) gestureHandler.reset();
        if (symbolPopup != null && symbolPopup.isShowing()) symbolPopup.dismiss();
        hideAllPanels();
        switchToQwerty();
        setShift(SHIFT_OFF);
        autoCapitalize();
        updateEnterKey(i);
        if (suggManager.isAiEnabled()) handler.postDelayed(() -> suggManager.updateAiReplies(getCurrentInputConnection()), 500);
    }

    @Override public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        if (keyboardView != null) keyboardView.cleanupAllPointers();
        if (gestureHandler != null) gestureHandler.reset();
        if (symbolPopup != null && symbolPopup.isShowing()) symbolPopup.dismiss();
        hideAllPanels();
        switchToQwerty();
    }

    @Override public void onUpdateSelection(int oSS, int oSE, int nSS, int nSE, int cS, int cE) {
        super.onUpdateSelection(oSS, oSE, nSS, nSE, cS, cE);
        if (suggManager.currentWord.length() > 0 && (Math.abs(nSS - oSE) > 1 || nSS != nSE)) {
            suggManager.currentWord.setLength(0); suggManager.clearSuggestions();
        }
    }

    @Override public void onFinishInput() {
        super.onFinishInput();
        suggManager.currentWord.setLength(0);
        suggManager.clearSuggestions();
        if (keyboardView != null) keyboardView.cleanupAllPointers();
        if (gestureHandler != null) gestureHandler.reset();
        if (symbolPopup != null && symbolPopup.isShowing()) symbolPopup.dismiss();
        hideAllPanels();
        switchToQwerty();
    }

    @Override public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (symbolPopup != null && symbolPopup.isShowing()) symbolPopup.dismiss();
        if (keyboardView != null) keyboardView.destroy();
        if (gestureHandler != null) gestureHandler.destroy();
        clipManager.destroy(); voiceManager.destroy(); soundManager.destroy();
        themeManager.detach(); suggManager.detach();
    }

    @Override
    public boolean onEvaluateInputViewShown() {
        android.content.res.Configuration config = getResources().getConfiguration();
        if (config.keyboard == android.content.res.Configuration.KEYBOARD_QWERTY &&
                config.hardKeyboardHidden == android.content.res.Configuration.HARDKEYBOARDHIDDEN_NO) {
            return false;
        }
        return super.onEvaluateInputViewShown();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (symbolPopup != null && symbolPopup.isShowing()) symbolPopup.dismiss();
        if (gestureHandler != null) gestureHandler.reset();
        if (keyboardView != null) keyboardView.cleanupAllPointers();
        hideAllPanels();
        boolean isHardwareKeyboardPresent = newConfig.keyboard == android.content.res.Configuration.KEYBOARD_QWERTY &&
                newConfig.hardKeyboardHidden == android.content.res.Configuration.HARDKEYBOARDHIDDEN_NO;
        if (isHardwareKeyboardPresent) {
            requestHideSelf(0);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (symbolPopup != null && symbolPopup.isShowing()) {
                symbolPopup.dismiss();
                return true;
            }
            if (gestureHandler != null && gestureHandler.isInTrackpad()) {
                gestureHandler.exitTrackpad();
                return true;
            }
            if (isAnyPanelVisible()) {
                hideAllPanels();
                return true;
            }
            if (currentMode != MODE_QWERTY) {
                switchToQwerty();
                return true;
            }
            if (isInputViewShown()) {
                requestHideSelf(0);
                return true;
            }
        }
        // Hardware keyboard input tracking
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            if (suggManager.currentWord.length() > 0) {
                suggManager.currentWord.setLength(suggManager.currentWord.length() - 1);
                suggManager.updateSuggestions(suggManager.currentWord.toString());
            }
        } else if (keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_ENTER) {
            suggManager.currentWord.setLength(0);
            suggManager.clearSuggestions();
        } else if (event != null && event.getUnicodeChar() > 0) {
            char c = (char) event.getUnicodeChar();
            if (Character.isLetterOrDigit(c)) {
                suggManager.currentWord.append(c);
                suggManager.updateSuggestions(suggManager.currentWord.toString());
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean isAnyPanelVisible() {
        return (clipboardPanel != null && clipboardPanel.getVisibility() == View.VISIBLE)
                || (phrasesPanel != null && phrasesPanel.getVisibility() == View.VISIBLE)
                || (statsPanel != null && statsPanel.getVisibility() == View.VISIBLE)
                || (settingsPanel != null && settingsPanel.getVisibility() == View.VISIBLE)
                || (grammarBanner != null && grammarBanner.getVisibility() == View.VISIBLE)
                || (aiRepliesPanel != null && aiRepliesPanel.getVisibility() == View.VISIBLE)
                || (toolbarCustomizerPanel != null && toolbarCustomizerPanel.getVisibility() == View.VISIBLE);
    }

    @Override public void onPress(int c) {
        if (keyboardView != null) soundManager.playHaptic(keyboardView, HapticFeedbackConstants.KEYBOARD_TAP);
        soundManager.playKeySound(); lastPressedKeyCode = c;

        // Track key X-centre for popup positioning (window coordinates)
        if (keyboardView != null && keyboardView.getKeyboard() != null) {
            for (Keyboard.Key key : keyboardView.getKeyboard().getKeys()) {
                if (key.codes != null && key.codes.length > 0 && key.codes[0] == c) {
                    int[] kvLoc = new int[2];
                    keyboardView.getLocationInWindow(kvLoc);
                    longPressKeyX = kvLoc[0] + keyboardView.getPaddingLeft() + key.x + key.width / 2;
                    break;
                }
            }
        }
        gestureHandler.showPreview(c);
    }
    @Override public void onRelease(int c) {
        gestureHandler.hidePreview();
        // If popup is showing, release commits the selected symbol
        if (symbolPopup != null && symbolPopup.isShowing()) {
            symbolPopup.commit();
        }
    }

    /** Long-press: show symbol alternatives popup if available. */
    private boolean handleLongPress(Keyboard.Key popupKey) {
        if (popupKey == null || popupKey.codes == null || popupKey.codes.length == 0) return false;
        int code = popupKey.codes[0];
        String[] symbols = KeySymbolMap.get(code);
        if (symbols == null || symbols.length <= 1) return false; // no alternatives

        // Dismiss the system key-preview if any
        gestureHandler.hidePreview();

        if (symbolPopup != null && keyboardView != null) {
            symbolPopup.show(keyboardView, symbols, longPressKeyX, chosen -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    // Commit the chosen symbol (handle shift for uppercase variants)
                    ic.commitText(chosen, 1);
                    if (shiftState == SHIFT_ON) setShift(SHIFT_OFF);
                    suggManager.currentWord.setLength(0);
                    suggManager.clearSuggestions();
                }
            });
            return true;
        }
        return false;
    }

    @Override public void onKey(int code, int[] cs) {
        InputConnection ic = getCurrentInputConnection(); if (ic == null) return;
        if (gestureHandler.isInTrackpad()) return;
        // Typing a key dismisses the Gboard-style toolbar editor (like Gboard)
        closeTBCustomizer();
        switch (code) {
            case CODE_BACKSPACE:  if (!gestureHandler.isBsGestureUsed()) handleBS(ic); break;
            case CODE_SHIFT:      handleShift(); break;
            case CODE_DONE:       handleEnter(ic); break;
            case CODE_SPACE:      if (!gestureHandler.isSpaceSwiping() && !gestureHandler.isInTrackpad()) handleSpace(ic); break;
            case CODE_CANCEL:     if (currentMode == MODE_QWERTY) switchToSymbols(); else switchToQwerty(); break;
            case CODE_EMOJI:      switchToEmoji(); break;
            case CODE_NUMPAD:     switchToNumpad(); break;
            case CODE_SYM_DIRECT: switchToSymbols(); break;
            case CODE_SYM_SHIFT:  if (currentMode == MODE_SYMBOLS) switchToSymShift(); else switchToSymbols(); break;
            default:              if (!gestureHandler.isSwipeTyping()) handleChar(ic, code); break;
        }
    }
    @Override public void onText(CharSequence t) { InputConnection ic = getCurrentInputConnection(); if (ic != null) ic.commitText(t, 1); }
    @Override public void swipeDown() {} @Override public void swipeLeft() {}
    @Override public void swipeRight() {} @Override public void swipeUp() {}

    private void handleBS(InputConnection ic) {
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,   KeyEvent.KEYCODE_DEL));
        if (suggManager.currentWord.length() > 0) suggManager.currentWord.deleteCharAt(suggManager.currentWord.length() - 1);
        suggManager.updateSuggestions();
    }
    private void handleShift() {
        switch (shiftState) {
            case SHIFT_OFF:  setShift(SHIFT_ON);   break;
            case SHIFT_ON:   setShift(SHIFT_CAPS);  break;
            default:         setShift(SHIFT_OFF);  break;
        }
    }
    private void handleEnter(InputConnection ic) {
        EditorInfo ei = currentEditorInfo != null ? currentEditorInfo : getCurrentInputEditorInfo();
        if (ei != null) {
            int a = ei.imeOptions & EditorInfo.IME_MASK_ACTION;
            boolean ml = (ei.inputType & android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
            boolean ne = (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0;
            if (ml) { ic.commitText("\n", 1); postGrammarAi(); return; }
            if (!ne) {
                switch (a) {
                    case EditorInfo.IME_ACTION_GO:
                    case EditorInfo.IME_ACTION_SEARCH:
                    case EditorInfo.IME_ACTION_SEND:
                    case EditorInfo.IME_ACTION_DONE:
                    case EditorInfo.IME_ACTION_NEXT:
                        ic.performEditorAction(a); return;
                }
            }
        }
        ic.commitText("\n", 1); postGrammarAi();
    }
    private void postGrammarAi() {
        suggManager.currentWord.setLength(0); suggManager.clearSuggestions();
        handler.postDelayed(() -> suggManager.checkGrammar(getCurrentInputConnection()), 300);
        if (suggManager.isAiEnabled()) handler.postDelayed(() -> suggManager.updateAiReplies(getCurrentInputConnection()), 400);
    }
    private void handleSpace(InputConnection ic) {
        if (suggManager.checkShortcut(ic)) return;

        // ── Double-tap space → insert ". " ──────────────────────────────────
        if (gestureHandler.isDoubleTapSpace()) {
            // Verify the character before the trailing space is not whitespace or punctuation
            CharSequence before2 = ic.getTextBeforeCursor(2, 0);
            if (before2 != null && before2.length() >= 2) {
                char prevChar = before2.charAt(before2.length() - 2); // char before the space
                boolean isPunct = (prevChar == '.' || prevChar == '!' || prevChar == '?'
                        || prevChar == ',' || prevChar == ':' || prevChar == ';');
                if (!Character.isWhitespace(prevChar) && !isPunct) {
                    // Replace trailing space with ". "
                    ic.deleteSurroundingText(1, 0);   // remove the first space
                    ic.commitText(". ", 1);            // insert period + new space
                    suggManager.currentWord.setLength(0);
                    suggManager.clearSuggestions();
                    // Trigger sentence capitalisation
                    setShift(SHIFT_ON);
                    gestureHandler.recordSpaceTap(); // reset so triple-tap doesn't re-trigger
                    return;
                }
            }
        }

        // ── Normal space ────────────────────────────────────────────────────
        if (currentMode != MODE_QWERTY) {
            ic.commitText(" ", 1);
            gestureHandler.recordSpaceTap();
            suggManager.currentWord.setLength(0);
            suggManager.clearSuggestions();
            return;
        }

        suggManager.learnWord();
        ic.commitText(" ", 1);
        gestureHandler.recordSpaceTap(); // stamp time for next double-tap check
        suggManager.currentWord.setLength(0);
        suggManager.clearSuggestions();
        autoCapitalize();
        handler.postDelayed(() -> suggManager.checkGrammar(getCurrentInputConnection()), 200);
        if (suggManager.isAiEnabled())
            handler.postDelayed(() -> suggManager.updateAiReplies(getCurrentInputConnection()), 300);
    }
    private void handleChar(InputConnection ic, int code) {
        char c = (char) code;
        if (Character.isLetter(c) && (shiftState == SHIFT_ON || shiftState == SHIFT_CAPS)) c = Character.toUpperCase(c);
        ic.commitText(String.valueOf(c), 1);
        if (shiftState == SHIFT_ON) setShift(SHIFT_OFF);
        if (Character.isLetter(c)) {
            if (currentMode == MODE_QWERTY) {
                suggManager.currentWord.append(Character.toLowerCase(c));
                suggManager.updateSuggestions();
            } else {
                suggManager.currentWord.setLength(0);
                suggManager.clearSuggestions();
            }
        } else {
            suggManager.currentWord.setLength(0);
            suggManager.clearSuggestions();
            if (c == '.' || c == '!' || c == '?') autoCapitalize();
        }
    }

    @Override public void onSuggestionClicked(String word, int i) {
        InputConnection ic = getCurrentInputConnection(); if (ic == null) return;
        int len = suggManager.currentWord.length(); if (len > 0) ic.deleteSurroundingText(len, 0);
        ic.commitText(word + " ", 1); suggManager.currentWord.setLength(0); suggManager.clearSuggestions(); autoCapitalize();
    }
    @Override public void onEmojiSelected(String e) { InputConnection ic = getCurrentInputConnection(); if (ic != null) ic.commitText(e, 1); }
    @Override public void onSwitchToKeyboard() { switchToQwerty(); }

    private void switchToQwerty() {
        currentMode = MODE_QWERTY; flipTo(FLIPPER_KB); keyboardView.setKeyboard(qwertyKeyboard);
        keyboardView.setVisibility(View.VISIBLE); removeEmoji();
        if (qwertyKeyboard != null) { qwertyKeyboard.setShifted(shiftState != SHIFT_OFF); keyboardView.invalidateAllKeys(); }
        gestureHandler.setCurrentMode(currentMode);
        suggManager.setLayer(currentMode);
        updateEnterKey(currentEditorInfo);
    }
    private void switchToSymbols() { currentMode = MODE_SYMBOLS; flipTo(FLIPPER_KB); keyboardView.setKeyboard(symbolsKeyboard); keyboardView.setVisibility(View.VISIBLE); gestureHandler.setCurrentMode(currentMode); suggManager.setLayer(currentMode); updateEnterKey(currentEditorInfo); }
    private void switchToNumpad()  { currentMode = MODE_NUMPAD;  flipTo(FLIPPER_KB); keyboardView.setKeyboard(numpadKeyboard);  keyboardView.setVisibility(View.VISIBLE); gestureHandler.setCurrentMode(currentMode); suggManager.setLayer(currentMode); updateEnterKey(currentEditorInfo); }
    private void switchToSymShift(){ currentMode = MODE_SYM_SHIFT; flipTo(FLIPPER_KB); keyboardView.setKeyboard(symbolsShiftKeyboard); keyboardView.setVisibility(View.VISIBLE); gestureHandler.setCurrentMode(currentMode); suggManager.setLayer(currentMode); updateEnterKey(currentEditorInfo); }
    private void switchToEmoji() {
        currentMode = MODE_EMOJI; removeEmoji();
        emojiKeyboardView = new EmojiKeyboardView(this, this, themeManager.isDark());
        emojiPanel = emojiKeyboardView.getView();
        if (emojiFlipperHost != null) {
            android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT);
            emojiPanel.setLayoutParams(lp); emojiFlipperHost.addView(emojiPanel); flipTo(FLIPPER_EMOJI);
        } else {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
            emojiPanel.setLayoutParams(lp); keyboardView.setVisibility(View.GONE); keyboardRoot.addView(emojiPanel);
        }
        gestureHandler.setCurrentMode(currentMode);
        suggManager.setLayer(currentMode);
    }
    private void removeEmoji() { if (emojiPanel != null) { if (emojiFlipperHost != null) emojiFlipperHost.removeView(emojiPanel); else keyboardRoot.removeView(emojiPanel); emojiPanel = null; emojiKeyboardView = null; } }

    private void setShift(int s) {
        shiftState = s; gestureHandler.setShiftState(s);
        if (currentMode == MODE_QWERTY && qwertyKeyboard != null && keyboardView != null) { qwertyKeyboard.setShifted(s != SHIFT_OFF); keyboardView.invalidateAllKeys(); }
    }
    private void autoCapitalize() {
        if (qwertyKeyboard == null || keyboardView == null) return;
        InputConnection ic = getCurrentInputConnection(); if (ic == null) return;
        CharSequence bef = ic.getTextBeforeCursor(2, 0);
        boolean cap = (bef == null || bef.length() == 0);
        if (!cap && bef != null) { String s = bef.toString(); cap = s.equals(". ") || s.equals("! ") || s.equals("? "); }
        if (cap && shiftState == SHIFT_OFF) setShift(SHIFT_ON);
    }

    private void handleTBClick(String key) {
        // Opening any toolbar feature dismisses the editor and other info panels
        closeTBCustomizer();
        closeInfoPanels();
        switch (key) {
            case "translate":
                if (currentMode == MODE_EMOJI) switchToQwerty();
                suggManager.handleTranslate();
                break;
            case "autocorrect":
                if (currentMode == MODE_EMOJI) switchToQwerty();
                if (currentMode != MODE_QWERTY) {
                    Toast.makeText(this, "Autocorrect is disabled on symbol/numbers layer", Toast.LENGTH_SHORT).show();
                    return;
                }
                suggManager.handleAutoCorrect();
                break;
            case "undo":        suggManager.handleUndo();        break;
            case "emoji":
                if (currentMode == MODE_EMOJI) switchToQwerty(); else switchToEmoji();
                break;
            case "clipboard":
                clipManager.toggleDrawer();
                highlightClipboard(isDrawerOpen());
                break;
            case "phrases":     suggManager.togglePhrases();     break;
            case "stats":       suggManager.toggleStats();       break;
            case "settings":
            case "theme":       launchSettingsActivity();        break;
            case "voice":       voiceManager.handleVoiceTyping(); break;
        }
    }

    /** Hide the small info panels (stats / phrases / AI replies / legacy clipboard row) so only one shows at a time. */
    private void closeInfoPanels() {
        if (statsPanel     != null) statsPanel.setVisibility(View.GONE);
        if (phrasesPanel   != null) phrasesPanel.setVisibility(View.GONE);
        if (aiRepliesPanel != null) aiRepliesPanel.setVisibility(View.GONE);
        if (clipboardPanel != null) clipboardPanel.setVisibility(View.GONE);
    }
    private void hideAllPanels() {
        if (clipboardPanel  != null) clipboardPanel.setVisibility(View.GONE);
        if (phrasesPanel    != null) phrasesPanel.setVisibility(View.GONE);
        if (statsPanel      != null) statsPanel.setVisibility(View.GONE);
        if (settingsPanel   != null) settingsPanel.setVisibility(View.GONE);
        if (grammarBanner   != null) grammarBanner.setVisibility(View.GONE);
        if (aiRepliesPanel  != null) aiRepliesPanel.setVisibility(View.GONE);
        if (clipManager     != null) clipManager.hideDrawer();
        if (symbolPopup     != null) symbolPopup.dismiss();
        closeTBCustomizer();
    }
    private void launchSettingsActivity() {
        try {
            Intent i = new Intent(this, KeyboardSettingsActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        } catch (Exception ex) {
            android.util.Log.e("MyKeyboardService", "Could not start KeyboardSettingsActivity", ex);
            CrashLogger.logError("MyKeyboardService", "launchSettings", ex.getMessage(), ex);
            toggleSettings();
        }
    }
    private void toggleSettings() { if (settingsPanel == null) return; boolean v = settingsPanel.getVisibility() == View.VISIBLE; settingsPanel.setVisibility(v ? View.GONE : View.VISIBLE); }

    private void wireSettingsPanel() {
        if (keyboardRoot == null) return;
        View bl  = keyboardRoot.findViewById(R.id.theme_light);         if (bl  != null) bl.setOnClickListener(v  -> themeManager.setTheme(ThemeManager.THEME_LIGHT));
        View bdk = keyboardRoot.findViewById(R.id.theme_dark);          if (bdk != null) bdk.setOnClickListener(v -> themeManager.setTheme(ThemeManager.THEME_DARK));
        View bb  = keyboardRoot.findViewById(R.id.theme_black);         if (bb  != null) bb.setOnClickListener(v  -> themeManager.setTheme(ThemeManager.THEME_BLACK));
        View ba  = keyboardRoot.findViewById(R.id.theme_amoled);        if (ba  != null) ba.setOnClickListener(v  -> themeManager.setTheme(ThemeManager.THEME_AMOLED));
        View bbl = keyboardRoot.findViewById(R.id.theme_blue);          if (bbl != null) bbl.setOnClickListener(v -> themeManager.setTheme(ThemeManager.THEME_BLUE));
        View bgr = keyboardRoot.findViewById(R.id.theme_green);         if (bgr != null) bgr.setOnClickListener(v -> themeManager.setTheme(ThemeManager.THEME_GREEN));
        View bci = keyboardRoot.findViewById(R.id.theme_custom_image);  if (bci != null) bci.setOnClickListener(v -> themeManager.setTheme(ThemeManager.THEME_CUSTOM_IMAGE));
        View so  = keyboardRoot.findViewById(R.id.sound_off);       if (so  != null) so.setOnClickListener(v  -> { soundManager.setSoundPack(SoundHapticManager.SOUND_OFF);        Toast.makeText(this, "Sound: Off",        Toast.LENGTH_SHORT).show(); });
        View sm  = keyboardRoot.findViewById(R.id.sound_mechanical); if (sm  != null) sm.setOnClickListener(v  -> { soundManager.setSoundPack(SoundHapticManager.SOUND_MECHANICAL); soundManager.playKeySound(); Toast.makeText(this, "Sound: Mechanical", Toast.LENGTH_SHORT).show(); });
        View st  = keyboardRoot.findViewById(R.id.sound_typewriter); if (st  != null) st.setOnClickListener(v  -> { soundManager.setSoundPack(SoundHapticManager.SOUND_TYPEWRITER); soundManager.playKeySound(); Toast.makeText(this, "Sound: Typewriter", Toast.LENGTH_SHORT).show(); });
        View sbu = keyboardRoot.findViewById(R.id.sound_bubble);    if (sbu != null) sbu.setOnClickListener(v -> { soundManager.setSoundPack(SoundHapticManager.SOUND_BUBBLE);    soundManager.playKeySound(); Toast.makeText(this, "Sound: Bubble",     Toast.LENGTH_SHORT).show(); });
        View sga = keyboardRoot.findViewById(R.id.sound_gaming);    if (sga != null) sga.setOnClickListener(v -> { soundManager.setSoundPack(SoundHapticManager.SOUND_GAMING);    soundManager.playKeySound(); Toast.makeText(this, "Sound: Gaming",     Toast.LENGTH_SHORT).show(); });
        View sso = keyboardRoot.findViewById(R.id.sound_soft);      if (sso != null) sso.setOnClickListener(v -> { soundManager.setSoundPack(SoundHapticManager.SOUND_SOFT);      soundManager.playKeySound(); Toast.makeText(this, "Sound: Soft",       Toast.LENGTH_SHORT).show(); });
        View scr = keyboardRoot.findViewById(R.id.sound_crystal);   if (scr != null) scr.setOnClickListener(v -> { soundManager.setSoundPack(SoundHapticManager.SOUND_CRYSTAL);   soundManager.playKeySound(); Toast.makeText(this, "Sound: Crystal",    Toast.LENGTH_SHORT).show(); });
        SeekBar vb = keyboardRoot.findViewById(R.id.volume_seekbar);
        if (vb != null) { vb.setProgress(soundManager.getVolume()); vb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { @Override public void onProgressChanged(SeekBar s, int p, boolean f) { soundManager.setVolume(p); } @Override public void onStartTrackingTouch(SeekBar s) {} @Override public void onStopTrackingTouch(SeekBar s) {} }); }
        View bsw = keyboardRoot.findViewById(R.id.toggle_swipe_typing);
        if (bsw != null) bsw.setOnClickListener(v -> { swipeEnabled = !swipeEnabled; prefs.edit().putBoolean(PREF_SWIPE, swipeEnabled).apply(); Toast.makeText(this, "Swipe typing " + (swipeEnabled ? "ON" : "OFF"), Toast.LENGTH_SHORT).show(); });
        View bai = keyboardRoot.findViewById(R.id.toggle_ai_replies);
        if (bai != null) bai.setOnClickListener(v -> { boolean e = !suggManager.isAiEnabled(); suggManager.setAiEnabled(e); prefs.edit().putBoolean(PREF_AI, e).apply(); if (!e && aiRepliesPanel != null) aiRepliesPanel.setVisibility(View.GONE); Toast.makeText(this, "AI Replies " + (e ? "ON" : "OFF"), Toast.LENGTH_SHORT).show(); });
        View bbg = keyboardRoot.findViewById(R.id.btn_enable_custom_bg), bpk = keyboardRoot.findViewById(R.id.btn_pick_photo);
        updateBgBtn(bbg);
        if (bbg != null) bbg.setOnClickListener(v -> { boolean e = prefs.getBoolean("customBgEnabled", false); e = !e; prefs.edit().putBoolean("customBgEnabled", e).apply(); themeManager.setTheme(e ? ThemeManager.THEME_CUSTOM_IMAGE : ThemeManager.THEME_DARK); updateBgBtn(v); Toast.makeText(this, "Custom photo BG " + (e ? "ON" : "OFF"), Toast.LENGTH_SHORT).show(); });
        if (bpk != null) bpk.setOnClickListener(v -> { try { Intent i = new Intent(this, PickImageActivity.class); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); Toast.makeText(this, "Pick a photo, then reopen the keyboard", Toast.LENGTH_LONG).show(); } catch (Exception ex) { Toast.makeText(this, "No gallery app found", Toast.LENGTH_SHORT).show(); } });
    }
    private void updateBgBtn(View v) { if (v instanceof android.widget.Button) { boolean e = prefs.getBoolean("customBgEnabled", false); ((android.widget.Button) v).setText(e ? "Disable Photo BG" : "Enable Photo BG"); } }

    private void updateEnterKey(EditorInfo info) {
        if (info == null) return;
        currentEditorInfo = info;
        if (qwertyKeyboard == null) return;
        int action = info.imeOptions & EditorInfo.IME_MASK_ACTION;
        boolean ml = (info.inputType & android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
        boolean ne = (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0;
        String lbl = null;
        android.graphics.drawable.Drawable icon = null;
        if (!ml && !ne) {
            switch (action) {
                case EditorInfo.IME_ACTION_SEARCH:
                    lbl = "Search";
                    icon = null;
                    break;
                case EditorInfo.IME_ACTION_GO:
                    lbl = "Go";
                    icon = null;
                    break;
                case EditorInfo.IME_ACTION_SEND:
                    lbl = "Send";
                    icon = null;
                    break;
                case EditorInfo.IME_ACTION_NEXT:
                    lbl = "Next";
                    icon = null;
                    break;
                case EditorInfo.IME_ACTION_DONE:
                    lbl = "Done";
                    icon = null;
                    break;
                case EditorInfo.IME_ACTION_UNSPECIFIED:
                case EditorInfo.IME_ACTION_NONE:
                default:
                    lbl = null;
                    icon = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_enter);
                    break;
            }
        } else {
            lbl = null;
            icon = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_enter);
        }
        Keyboard[] kbs = {qwertyKeyboard, symbolsKeyboard, symbolsShiftKeyboard, numpadKeyboard};
        for (Keyboard kb : kbs) {
            if (kb == null) continue;
            for (Keyboard.Key k : kb.getKeys()) {
                if (k.codes != null && k.codes.length > 0 && k.codes[0] == CODE_DONE) {
                    k.label = lbl;
                    k.icon = icon;
                }
            }
        }
        if (keyboardView != null) keyboardView.invalidateAllKeys();
    }

    /**
     * Rebuilds the visible Gboard-style toolbar strip from the saved
     * toolbarButtonConfig (order + visibility). Each button is tappable
     * (runs its action) and long-pressable (opens the toolbar editor).
     */
    private void rebuildToolbar() {
        if (toolbarStripDynamic == null) return;
        toolbarStripDynamic.removeAllViews();
        tbBtnViews.clear();
        for (String key : tbOrder) {
            if (!tbVisible.contains(key)) continue;
            TBDef def = findDef(key);
            int iconRes = iconResFor(key);
            if (def == null || iconRes == 0) continue;
            android.widget.ImageView btn = new android.widget.ImageView(this);
            btn.setImageResource(iconRes);
            btn.setColorFilter(androidx.core.content.ContextCompat.getColor(this, R.color.toolbar_icon_inactive));
            btn.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
            btn.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
            btn.setClickable(true);
            btn.setContentDescription(def.label);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dpToPx(48), 1f);
            lp.setMargins(dpToPx(2), 0, dpToPx(2), 0);
            btn.setLayoutParams(lp);
            final String k = key;
            btn.setOnClickListener(v -> handleTBClick(k));
            btn.setOnLongClickListener(v -> { openTBCustomizer(); return true; });
            toolbarStripDynamic.addView(btn);
            tbBtnViews.put(key, btn);
        }
    }
    /** Vector icon for a toolbar button key (0 = no icon, button is skipped). */
    private int iconResFor(String key) {
        switch (key) {
            case "clipboard":   return R.drawable.ic_clipboard;
            case "translate":   return R.drawable.ic_tool_translate;
            case "autocorrect": return R.drawable.ic_tool_autocorrect;
            case "emoji":       return R.drawable.ic_sticker;
            case "settings":    return R.drawable.ic_tool_settings;
            case "theme":       return R.drawable.ic_tool_palette;
            case "voice":       return R.drawable.ic_tool_mic;
            case "undo":        return R.drawable.ic_undo;
            case "phrases":     return R.drawable.ic_tool_phrases;
            case "stats":       return R.drawable.ic_tool_stats;
            default:            return 0;
        }
    }
    private boolean isDrawerOpen() {
        return clipboardDrawer != null && clipboardDrawer.getVisibility() == View.VISIBLE;
    }
    private void flipTo(int child) {
        if (contentFlipper == null) return;
        if (keyboardView != null && keyboardView.getHeight() > 0) { android.view.ViewGroup.LayoutParams lp = contentFlipper.getLayoutParams(); lp.height = (child != FLIPPER_KB) ? keyboardView.getHeight() : android.view.ViewGroup.LayoutParams.WRAP_CONTENT; contentFlipper.setLayoutParams(lp); }
        if (child == FLIPPER_KB) { keyboardView.setVisibility(View.VISIBLE); if (emojiFlipperHost != null && emojiPanel != null) { emojiFlipperHost.removeView(emojiPanel); emojiPanel = null; emojiKeyboardView = null; } }
        contentFlipper.setDisplayedChild(child); highlightIcon(child);
    }
    /** Highlight the toolbar icon that corresponds to the currently active panel.
     *  Flipper children:  0=KB  1=Emoji  2=Theme  3=Misc
     *  Icon array order:  0=Grid  1=Emoji  2=Cursor  3=Gif  4=Clipboard  5=Translate  6=Mic  7=More
     */
    /** Highlight the toolbar icon for the currently active panel. */
    private void highlightIcon(int active) {
        if (keyboardRoot == null) return;
        String key = (active == FLIPPER_EMOJI) ? "emoji" : null;
        int ac = androidx.core.content.ContextCompat.getColor(this, R.color.toolbar_icon_active);
        int ic = androidx.core.content.ContextCompat.getColor(this, R.color.toolbar_icon_inactive);
        for (java.util.Map.Entry<String, View> e : tbBtnViews.entrySet()) {
            if (e.getValue() instanceof android.widget.ImageView) {
                ((android.widget.ImageView) e.getValue()).setColorFilter(
                    (key != null && key.equals(e.getKey())) ? ac : ic);
            }
        }
        highlightClipboard(isDrawerOpen());
    }

    /** Highlight or un-highlight the clipboard button when the drawer toggles. */
    private void highlightClipboard(boolean open) {
        View v = tbBtnViews.get("clipboard");
        if (v instanceof android.widget.ImageView) {
            int c = androidx.core.content.ContextCompat.getColor(this,
                open ? R.color.toolbar_icon_active : R.color.toolbar_icon_inactive);
            ((android.widget.ImageView) v).setColorFilter(c);
        }
    }
    private void loadToolbarConfig() {
        tbOrder.clear(); tbVisible.clear();
        String cfg = prefs.getString(PREF_TOOLBAR, "");
        if (!cfg.isEmpty()) { for (String e : cfg.split(",")) { String[] p = e.split(":"); if (p.length == 2) { tbOrder.add(p[0]); if ("1".equals(p[1])) tbVisible.add(p[0]); } } }
        if (tbOrder.isEmpty()) { for (TBDef d : allBtns) { tbOrder.add(d.key); tbVisible.add(d.key); } }
        if (!tbOrder.contains(PERM_KEY)) tbOrder.add(PERM_KEY);
        tbVisible.add(PERM_KEY);
    }
    private void saveTBConfig() { StringBuilder sb = new StringBuilder(); for (String k : tbOrder) sb.append(k).append(":").append(tbVisible.contains(k) ? "1" : "0").append(","); prefs.edit().putString(PREF_TOOLBAR, sb.toString()).apply(); }
    // The legacy scrollable row stays hidden (visibility=gone); the visible
    // toolbar is the dynamic strip built by rebuildToolbar() above.
    private void rebuildLegacyToolbarRow() {
        if (toolbarRow == null) return; toolbarRow.removeAllViews();
        for (String key : tbOrder) { if (!tbVisible.contains(key)) continue; TBDef def = findDef(key); if (def == null) continue; TextView btn = new TextView(this); btn.setText(def.icon); btn.setTextSize(26f); btn.setGravity(android.view.Gravity.CENTER); btn.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8)); btn.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.toolbar_icon_inactive)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.setMargins(4, 0, 4, 0); btn.setLayoutParams(lp); final String k = key; btn.setOnClickListener(v -> handleTBClick(k)); toolbarRow.addView(btn); }
    }
    private void toggleTBCustomizer() {
        if (toolbarCustomizerPanel == null) return;
        boolean open = toolbarCustomizerPanel.getVisibility() != View.VISIBLE;
        if (open) {
            hideAllPanels();
            toolbarCustomizerPanel.setVisibility(View.VISIBLE);
            refreshTBCustomizer();
        } else {
            toolbarCustomizerPanel.setVisibility(View.GONE);
        }
    }
    private void openTBCustomizer() {
        if (toolbarCustomizerPanel == null) return;
        if (toolbarCustomizerPanel.getVisibility() != View.VISIBLE) toggleTBCustomizer();
    }
    private void closeTBCustomizer() {
        if (toolbarCustomizerPanel != null && toolbarCustomizerPanel.getVisibility() == View.VISIBLE)
            toolbarCustomizerPanel.setVisibility(View.GONE);
    }
    /** Gboard-style editor row per button: checkbox (show/hide) + up/down reorder. */
    private void refreshTBCustomizer() {
        if (toolbarCustomizerList == null) return;
        toolbarCustomizerList.removeAllViews();
        android.view.LayoutInflater infl = android.view.LayoutInflater.from(this);
        boolean dark = themeManager.isDark();
        int rowBg = dark ? 0xFF232A31 : 0xFFFFFFFF;
        int labelColor = dark ? 0xFFFFFFFF : 0xFF1A1A1A;
        for (int i = 0; i < tbOrder.size(); i++) {
            final String key = tbOrder.get(i);
            TBDef def = findDef(key);
            if (def == null) continue;
            View row = infl.inflate(R.layout.toolbar_customizer_item, toolbarCustomizerList, false);
            row.setBackgroundColor(rowBg);
            android.widget.CheckBox cb = row.findViewById(R.id.item_checkbox);
            TextView icon = row.findViewById(R.id.item_icon);
            TextView label = row.findViewById(R.id.item_label);
            TextView up = row.findViewById(R.id.item_move_up);
            TextView down = row.findViewById(R.id.item_move_down);
            boolean isCore = PERM_KEY.equals(key);
            icon.setText(def.icon);
            label.setText(def.label + (isCore ? " (core)" : ""));
            label.setTextColor(labelColor);
            cb.setChecked(tbVisible.contains(key));
            cb.setEnabled(!isCore);
            final int pos = i;
            up.setOnClickListener(v -> moveTB(pos, -1));
            down.setOnClickListener(v -> moveTB(pos, 1));
            cb.setOnCheckedChangeListener((b, ch) -> {
                if (isCore) { if (!ch) cb.setChecked(true); return; }
                if (ch) {
                    tbVisible.add(key);
                } else {
                    if (tbVisible.size() <= 1) { cb.setChecked(true); Toast.makeText(this, "Keep at least one toolbar button", Toast.LENGTH_SHORT).show(); return; }
                    tbVisible.remove(key);
                }
                saveTBConfig(); rebuildToolbar();
            });
            toolbarCustomizerList.addView(row);
        }
    }
    private void moveTB(int idx, int dir) {
        int j = idx + dir;
        if (idx < 0 || j < 0 || j >= tbOrder.size()) return;
        String t = tbOrder.get(idx);
        tbOrder.set(idx, tbOrder.get(j));
        tbOrder.set(j, t);
        saveTBConfig(); rebuildToolbar(); refreshTBCustomizer();
    }
    private TBDef findDef(String k) { for (TBDef d : allBtns) if (d.key.equals(k)) return d; return null; }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private void wireResizeOverlay(View root) {
        if (resizeOverlay == null) return;
        View ts = root.findViewById(R.id.toolbar_strip); if (ts != null) ts.setOnLongClickListener(v -> { enterResize(); return true; });
        if (resizeDragHandle != null) resizeDragHandle.setOnTouchListener((v, ev) -> {
            switch (ev.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN: resizeDragStartY = ev.getRawY(); resizeDragStartH = keyboardRoot != null ? keyboardRoot.getHeight() : dpToPx(DEFAULT_DP); return true;
                case android.view.MotionEvent.ACTION_MOVE: float d = resizeDragStartY - ev.getRawY(); int nh = Math.max(dpToPx(180), Math.min(dpToPx(420), resizeDragStartH + (int) d)); setKbH(nh / (int) getResources().getDisplayMetrics().density); return true;
            }
            return false;
        });
        View br = root.findViewById(R.id.btn_resize_reset); if (br != null) br.setOnClickListener(v -> { setKbH(DEFAULT_DP); prefs.edit().putInt(PREF_KB_H, DEFAULT_DP).apply(); });
        View bd = root.findViewById(R.id.btn_resize_done); if (bd != null) bd.setOnClickListener(v -> { int dp = keyboardRoot != null ? (int)(keyboardRoot.getHeight() / getResources().getDisplayMetrics().density) : DEFAULT_DP; prefs.edit().putInt(PREF_KB_H, dp).apply(); exitResize(); });
    }
    private void enterResize() { resizeModeActive = true;  if (resizeOverlay != null) resizeOverlay.setVisibility(View.VISIBLE); }
    private void exitResize()  { resizeModeActive = false; if (resizeOverlay != null) resizeOverlay.setVisibility(View.GONE); }
    private void setKbH(int dp) {
        if (keyboardRoot == null) return; int px = dp > 0 ? dpToPx(dp) : 0;
        keyboardRoot.setMinimumHeight(px);
        if (keyboardView != null) { android.view.ViewGroup.LayoutParams lp = keyboardView.getLayoutParams(); if (lp != null) keyboardView.setLayoutParams(lp); }
        keyboardRoot.requestLayout();
    }
    private int dpToPx(int dp) { return (int)(dp * getResources().getDisplayMetrics().density); }

    public void setTheme(int t) { if (themeManager != null) themeManager.setTheme(t); }
    public void setCustomBackgroundUri(String u) { if (themeManager != null) themeManager.setCustomBgUri(u); }
    public void addToPersonalDict(String w) { if (suggManager != null) suggManager.addToPersonalDict(w); }
}
