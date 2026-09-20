import React, { useState, useEffect, useRef, useMemo, useCallback } from 'react';
import {
  KeyboardMode,
  ShiftState,
  ThemeId,
  SoundPackId,
  AppSettings,
  KeyboardStats,
  ClipItem,
  EditorInputType,
  ImeAction
} from './types';
import { deletePreviousGrapheme, deletePreviousWord, getAdjacentGraphemePos } from './utils/textUtils';
import { Toolbar } from './components/Toolbar';
import { SuggestionBar } from './components/SuggestionBar';
import { KeyboardLayout } from './components/KeyboardLayout';
import { EmojiKeyboard } from './components/EmojiKeyboard';
import { ClipboardDrawer } from './components/ClipboardDrawer';
import { PhrasesPanel } from './components/PhrasesPanel';
import { StatsPanel } from './components/StatsPanel';
import { VoiceTypingPanel } from './components/VoiceTypingPanel';
import { ToolbarCustomizer } from './components/ToolbarCustomizer';
import { SettingsModal } from './components/SettingsModal';
import { TypingArea, SAMPLE_PROMPTS } from './components/TypingArea';
import { soundController } from './utils/audio';
import {
  getSuggestions,
  checkGrammar,
  getSmartReplies,
  getAutoCorrection,
  autoCorrectSentence,
  translateText,
  detectOtp,
  checkShortcut
} from './utils/engine';
import { Settings, Moon, Sun, Smartphone, RefreshCw, Keyboard, Layers, ChevronLeft, Circle, Square } from 'lucide-react';
import { AccentColorContext } from './context/AccentColorContext';

const DEFAULT_SETTINGS: AppSettings = {
  autoCorrectEnabled: true,
  autoCapsEnabled: true,
  spellCheckEnabled: true,
  grammarCheckEnabled: true,
  smartComposeEnabled: true,
  aiRepliesEnabled: true,
  blockOffensiveEnabled: true,
  swipeTypingEnabled: true,
  voiceTypingEnabled: true,
  soundPack: SoundPackId.MECHANICAL,
  soundVolume: 75,
  hapticEnabled: true,
  selectedTheme: ThemeId.DARK,
  customBackgroundUrl: null,
  keyboardHeightDp: 290,
  keyFontSize: 16,
  toolbarButtons: ['clipboard', 'translate', 'autocorrect', 'emoji', 'theme', 'settings', 'voice', 'undo', 'phrases', 'stats'],
  personalDictionary: ['BabelKey', 'Namaste', 'Hyderabad', 'Bangalore'],
  shortcuts: {
    brb: 'Be right back',
    omw: 'On my way!',
    ty: 'Thank you!',
    np: 'No problem!',
    gn: 'Good night!',
  },
  quickPhrases: [
    'On my way!',
    'I will call you in a few minutes.',
    'Please send over the documents.',
    'Thank you so much!',
    'Can we reschedule for tomorrow?',
  ],
};

const INITIAL_CLIPS: ClipItem[] = [
  { id: 'clip-1', text: 'https://babelkey.app', pinned: true, createdAt: Date.now() - 3600000 },
  { id: 'clip-2', text: 'Welcome to BabelKey smart keyboard!', pinned: false, createdAt: Date.now() - 1800000 },
  { id: 'clip-3', text: 'Verification Code: 729104', pinned: false, createdAt: Date.now() - 900000 },
];

export const App: React.FC = () => {
  // Main state
  const [text, setText] = useState('');
  const [cursorPos, setCursorPos] = useState(0);
  const [history, setHistory] = useState<string[]>([]);
  const [historyClearedNotice, setHistoryClearedNotice] = useState(false);
  const [keyboardMode, setKeyboardMode] = useState<KeyboardMode>('qwerty');
  const [shiftState, setShiftState] = useState<ShiftState>(ShiftState.OFF);
  const [activePanel, setActivePanel] = useState<string | null>(null);
  const [showSettingsModal, setShowSettingsModal] = useState(false);
  const [settingsModalTab, setSettingsModalTab] = useState<'theme' | 'sound' | 'corrections' | 'dictionary' | 'shortcuts'>('theme');

  // IME & EditorInfo simulation state (Gboard parity)
  const [editorInputType, setEditorInputType] = useState<EditorInputType>('text');
  const [imeAction, setImeAction] = useState<ImeAction>('IME_ACTION_SEND');
  const [isMultiLine, setIsMultiLine] = useState<boolean>(false);
  const [lastImeActionFeedback, setLastImeActionFeedback] = useState<string | null>(null);

  // Prompt 13: IME process lifecycle restart simulation (killing and recreating InputMethodService)
  const [imeInstanceKey, setImeInstanceKey] = useState<number>(0);
  const [imeRestartNotice, setImeRestartNotice] = useState<string | null>(null);

  // Prompt 14: Hardware keyboard handling (tucking soft keyboard or dual-typing without conflict)
  const [isHardwareKeyboardAttached, setIsHardwareKeyboardAttached] = useState<boolean>(false);
  const [softKeyboardHidden, setSoftKeyboardHidden] = useState<boolean>(false);

  // Prompt 15: App switching / multitasking robustness
  const [isAppSwitchedAway, setIsAppSwitchedAway] = useState<boolean>(false);
  const [lifecycleFeedback, setLifecycleFeedback] = useState<string | null>(null);

  // Always reset to a fresh alphabetic state when opening the keyboard or returning to typing
  const resetKeyboardToAlphabetic = useCallback(() => {
    setActivePanel(null);
    setKeyboardMode('qwerty');
    setShiftState(ShiftState.OFF);
  }, []);

  // Prompt 13: Simulate InputMethodService Process Kill & Recreate
  const handleSimulateImeRestart = useCallback(() => {
    setActivePanel(null);
    setKeyboardMode('qwerty');
    setShiftState(ShiftState.OFF);
    setImeInstanceKey((k) => k + 1);
    setImeRestartNotice('InputMethodService recreated: all pending timers cleared, fresh state');
    setTimeout(() => setImeRestartNotice(null), 3500);
  }, []);

  // Prompt 15: System Back button handling
  const handleSystemBack = useCallback(() => {
    if (showSettingsModal) {
      setShowSettingsModal(false);
      return;
    }
    if (activePanel !== null) {
      setActivePanel(null);
      setLifecycleFeedback('Back button: Closed active panel');
      setTimeout(() => setLifecycleFeedback(null), 2500);
      return;
    }
    if (keyboardMode !== 'qwerty') {
      setKeyboardMode('qwerty');
      setLifecycleFeedback('Back button: Returned to QWERTY');
      setTimeout(() => setLifecycleFeedback(null), 2500);
      return;
    }
    if (!softKeyboardHidden) {
      setSoftKeyboardHidden(true);
      setLifecycleFeedback('Back button: Soft keyboard hidden (tap to restore)');
      setTimeout(() => setLifecycleFeedback(null), 2500);
      return;
    }
    setLifecycleFeedback('Back button: Handled by system');
    setTimeout(() => setLifecycleFeedback(null), 2500);
  }, [showSettingsModal, activePanel, keyboardMode, softKeyboardHidden]);

  // Prompt 15: Simulate App Switch / Multitasking
  const handleSimulateAppSwitch = useCallback(() => {
    setIsAppSwitchedAway(true);
    setLifecycleFeedback('Switched to another app (IME backgrounded)...');
    
    setTimeout(() => {
      setIsAppSwitchedAway(false);
      setActivePanel(null);
      setKeyboardMode('qwerty');
      setShiftState(ShiftState.OFF);
      setLifecycleFeedback('Resumed from multitasking: Keyboard in clean state');
      setTimeout(() => setLifecycleFeedback(null), 3500);
    }, 700);
  }, []);

  const handleEditorInputTypeChange = (type: EditorInputType) => {
    setEditorInputType(type);
    setActivePanel(null);
    if (type === 'number' || type === 'phone') {
      setKeyboardMode('numpad');
      setImeAction('IME_ACTION_DONE');
      setIsMultiLine(false);
    } else {
      // Standard alphabetic fields always start fresh on QWERTY
      setKeyboardMode('qwerty');
      setShiftState(ShiftState.OFF);
      if (type === 'search') {
        setImeAction('IME_ACTION_SEARCH');
        setIsMultiLine(false);
      } else if (type === 'email') {
        setImeAction('IME_ACTION_NEXT');
        setIsMultiLine(false);
      } else if (type === 'password' || type === 'visible_password') {
        setImeAction('IME_ACTION_DONE');
        setIsMultiLine(false);
      }
    }
  };

  // Incoming simulated message context (for testing AI smart replies)
  const [sampleIdx, setSampleIdx] = useState(0);
  const incomingMessage = SAMPLE_PROMPTS[sampleIdx];

  // Settings & Storage (ensures toolbarButtons includes theme and handles local persistence)
  const [settings, setSettings] = useState<AppSettings>(() => {
    try {
      const saved = localStorage.getItem('babelkey_settings');
      if (saved) {
        const parsed = JSON.parse(saved);
        const merged: AppSettings = { ...DEFAULT_SETTINGS, ...parsed };
        if (Array.isArray(merged.toolbarButtons) && !merged.toolbarButtons.includes('theme')) {
          merged.toolbarButtons = [...merged.toolbarButtons, 'theme'];
        }
        return merged;
      }
      return DEFAULT_SETTINGS;
    } catch {
      return DEFAULT_SETTINGS;
    }
  });

  // Dedicated callback to update settings state AND guarantee immediate synchronous persistence in localStorage
  const handleUpdateSettings = useCallback((newVals: Partial<AppSettings>) => {
    setSettings((prev) => {
      const updated = { ...prev, ...newVals };
      try {
        localStorage.setItem('babelkey_settings', JSON.stringify(updated));
      } catch (err) {
        try {
          const fallback = { ...updated, customBackgroundUrl: null };
          localStorage.setItem('babelkey_settings', JSON.stringify(fallback));
        } catch (e) {
          console.error('Failed to save settings to localStorage:', e);
        }
      }
      return updated;
    });
  }, []);

  // Clips
  const [clips, setClips] = useState<ClipItem[]>(() => {
    try {
      const saved = localStorage.getItem('babelkey_clips');
      return saved ? JSON.parse(saved) : INITIAL_CLIPS;
    } catch {
      return INITIAL_CLIPS;
    }
  });

  // Stats
  const [stats, setStats] = useState<KeyboardStats>(() => {
    try {
      const saved = localStorage.getItem('babelkey_stats');
      return saved
        ? JSON.parse(saved)
        : {
            totalWords: 0,
            totalCharacters: 0,
            sessionStartTime: Date.now(),
            correctionCounts: {},
            dailyWords: {},
          };
    } catch {
      return {
        totalWords: 0,
        totalCharacters: 0,
        sessionStartTime: Date.now(),
        correctionCounts: {},
        dailyWords: {},
      };
    }
  });

  // Save settings to localStorage whenever state changes
  useEffect(() => {
    try {
      localStorage.setItem('babelkey_settings', JSON.stringify(settings));
    } catch (err) {
      try {
        const fallback = { ...settings, customBackgroundUrl: null };
        localStorage.setItem('babelkey_settings', JSON.stringify(fallback));
      } catch {}
    }
  }, [settings]);

  // Save clips
  useEffect(() => {
    try {
      localStorage.setItem('babelkey_clips', JSON.stringify(clips));
    } catch {}
  }, [clips]);

  // Save stats
  useEffect(() => {
    try {
      localStorage.setItem('babelkey_stats', JSON.stringify(stats));
    } catch {}
  }, [stats]);

  // Push history snapshot before mutation
  const pushHistory = (current: string) => {
    setHistory((prev) => [...prev.slice(-20), current]);
  };

  // Undo last action
  const handleUndo = () => {
    if (history.length === 0) return;
    const prev = history[history.length - 1];
    setHistory((h) => h.slice(0, -1));
    setText(prev);
    setCursorPos(prev.length);
  };

  // Clear typing history buffer immediately to reset memory and avoid bloat in long sessions
  const handleClearHistory = () => {
    if (history.length === 0) return;
    setHistory([]);
    setHistoryClearedNotice(true);
    setTimeout(() => setHistoryClearedNotice(false), 2200);
    soundController.playKeySound(settings.soundPack, settings.soundVolume);
    soundController.triggerHaptic(settings.hapticEnabled);
  };

  // Track typing activity for real-time WPM, active duration, and session metrics
  const trackTypingActivity = (chars = 1, words = 0) => {
    const now = Date.now();
    setStats((prev) => {
      const prevActiveTime = prev.lastActiveTime || now;
      const elapsedSinceLast = now - prevActiveTime;
      // If activity happened within last 4 seconds, add to active typing time
      const additionalActiveSec = elapsedSinceLast < 4000 ? Math.max(0.2, elapsedSinceLast / 1000) : 1;

      // Keep recent activity within the last 60 seconds for burst/instant WPM
      const recentActivity = [
        ...(prev.recentActivity || []).filter((item) => now - item.timestamp < 60000),
        { timestamp: now, chars, words },
      ];

      return {
        ...prev,
        totalCharacters: prev.totalCharacters + chars,
        totalWords: prev.totalWords + words,
        lastActiveTime: now,
        activeTypingSeconds: (prev.activeTypingSeconds || 0) + additionalActiveSec,
        recentActivity,
      };
    });
  };

  // Extract current word preceding the cursor
  const currentWord = useMemo(() => {
    const beforeCursor = text.substring(0, cursorPos);
    const match = beforeCursor.match(/[\w'-]+$/);
    return match ? match[0] : '';
  }, [text, cursorPos]);

  // Strict Privacy Protocol (Android IME standards: TYPE_TEXT_VARIATION_PASSWORD & TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
  const isStrictPrivacy = editorInputType === 'password' || editorInputType === 'visible_password';

  // Layer check: symbol layer ('symbols', 'symbols_shift') and number layer ('numpad') disable autocorrect & word prediction
  const isSymbolOrNumberLayer = keyboardMode === 'symbols' || keyboardMode === 'symbols_shift' || keyboardMode === 'numpad';

  // Predictive suggestions (strictly disabled on password fields per Android IME security standards, and disabled on symbol/numbers layers)
  const suggestions = useMemo(() => {
    if (isStrictPrivacy || isSymbolOrNumberLayer) return [];
    if (!settings.smartComposeEnabled || !currentWord) return [];
    return getSuggestions(currentWord, settings.personalDictionary);
  }, [currentWord, settings.smartComposeEnabled, settings.personalDictionary, isStrictPrivacy, isSymbolOrNumberLayer]);

  // Grammar check (strictly disabled on password fields and symbol/number layers)
  const grammarError = useMemo(() => {
    if (isStrictPrivacy || isSymbolOrNumberLayer) return null;
    if (!settings.grammarCheckEnabled) return null;
    return checkGrammar(text);
  }, [text, settings.grammarCheckEnabled, isStrictPrivacy, isSymbolOrNumberLayer]);

  // Smart Replies from incoming message (strictly disabled on password fields and symbol/number layers)
  const smartReplies = useMemo(() => {
    if (isStrictPrivacy || isSymbolOrNumberLayer) return [];
    if (!settings.aiRepliesEnabled) return [];
    return getSmartReplies(incomingMessage);
  }, [incomingMessage, settings.aiRepliesEnabled, isStrictPrivacy, isSymbolOrNumberLayer]);

  // OTP detection from text or recent clip
  const otpDetected = useMemo(() => {
    const fromText = detectOtp(text);
    if (fromText) return fromText;
    for (const clip of clips) {
      const code = detectOtp(clip.text);
      if (code) return code;
    }
    return null;
  }, [text, clips]);

  // Key sound trigger
  const playSoundAndHaptic = () => {
    soundController.playKeySound(settings.soundPack, settings.soundVolume);
    soundController.triggerHaptic(settings.hapticEnabled);
  };

  // Handle regular key press
  const handleKeyPress = (char: string) => {
    playSoundAndHaptic();
    pushHistory(text);

    let insertChar = char;

    // Auto-capitalization: if at start of text or following '. ' or '? ' or '! ' (bypassed on password fields)
    if (!isStrictPrivacy && settings.autoCapsEnabled && /^[a-z]$/i.test(insertChar) && shiftState === ShiftState.OFF) {
      const before = text.substring(0, cursorPos);
      if (before.length === 0 || /(?:[.!?]\s*|\n)$/.test(before)) {
        insertChar = insertChar.toUpperCase();
      }
    }

    // Auto-reset single shift after one letter
    if (shiftState === ShiftState.ON) {
      setShiftState(ShiftState.OFF);
    }

    // Insert character
    const newText = text.substring(0, cursorPos) + insertChar + text.substring(cursorPos);
    const newPos = cursorPos + insertChar.length;

    // Check for space press triggers (Autocorrect, Shortcut expansion)
    if (insertChar === ' ') {
      // In strict privacy mode or on symbol/numbers layer, strictly bypass autocorrect & word prediction
      if (isStrictPrivacy || isSymbolOrNumberLayer) {
        setText(newText);
        setCursorPos(newPos);
        trackTypingActivity(1, 1);
        return;
      }

      let modifiedText = newText;
      let modifiedPos = newPos;

      // 1. Text shortcut expansion
      const wordsBefore = modifiedText.substring(0, modifiedPos - 1);
      const lastWordMatch = wordsBefore.match(/([\w'-]+)$/);
      if (lastWordMatch) {
        const word = lastWordMatch[1];
        const expansion = checkShortcut(word, settings.shortcuts);
        if (expansion) {
          const startIdx = lastWordMatch.index!;
          modifiedText = modifiedText.substring(0, startIdx) + expansion + ' ' + modifiedText.substring(modifiedPos);
          modifiedPos = startIdx + expansion.length + 1;
        } else if (settings.autoCorrectEnabled) {
          // 2. Auto-correction
          const fix = getAutoCorrection(word, new Set(), settings.personalDictionary);
          if (fix && fix.toLowerCase() !== word.toLowerCase()) {
            const startIdx = lastWordMatch.index!;
            modifiedText = modifiedText.substring(0, startIdx) + fix + ' ' + modifiedText.substring(modifiedPos);
            modifiedPos = startIdx + fix.length + 1;

            // Update stats
            setStats((prev) => ({
              ...prev,
              correctionCounts: {
                ...prev.correctionCounts,
                [word]: (prev.correctionCounts[word] || 0) + 1,
              },
            }));
          }
        }
      }

      setText(modifiedText);
      setCursorPos(modifiedPos);

      // Track word and character typing activity
      trackTypingActivity(1, 1);
      return;
    }

    setText(newText);
    setCursorPos(newPos);

    // Track character typing activity
    trackTypingActivity(1, 0);
  };

  // Handle Backspace with Gboard parity (UTF-16 surrogate & compound emoji safe)
  const handleBackspace = () => {
    playSoundAndHaptic();
    if (cursorPos === 0) return;
    pushHistory(text);

    const { newText, newPos } = deletePreviousGrapheme(text, cursorPos);
    setText(newText);
    setCursorPos(newPos);
  };

  // Handle Backspace Swipe-Left or Accelerated Repeat: Delete full word
  const handleDeleteWord = () => {
    playSoundAndHaptic();
    if (cursorPos === 0) return;
    pushHistory(text);

    const { newText, newPos } = deletePreviousWord(text, cursorPos);
    setText(newText);
    setCursorPos(newPos);
  };

  // Handle Enter / IME Action (EditorInfo.imeOptions commercial contract)
  const handleEnter = () => {
    playSoundAndHaptic();

    // Fall back to a normal newline Enter key when isMultiLine is set or when IME_ACTION_UNSPECIFIED or IME_ACTION_NONE is set
    if (
      isMultiLine ||
      imeAction === 'IME_ACTION_NONE' ||
      imeAction === 'actionNone' ||
      imeAction === 'IME_ACTION_UNSPECIFIED' ||
      imeAction === 'actionUnspecified'
    ) {
      pushHistory(text);
      const newText = text.substring(0, cursorPos) + '\n' + text.substring(cursorPos);
      setText(newText);
      setCursorPos(cursorPos + 1);
      setLastImeActionFeedback('Newline inserted (Return)');
      setTimeout(() => setLastImeActionFeedback(null), 2000);
      return;
    }

    if (imeAction === 'actionSearch' || imeAction === 'IME_ACTION_SEARCH') {
      setLastImeActionFeedback(`Search executed for: "${text}"`);
      setTimeout(() => setLastImeActionFeedback(null), 3000);
      return;
    }
    if (imeAction === 'actionSend' || imeAction === 'IME_ACTION_SEND') {
      if (text.trim()) {
        setLastImeActionFeedback(`Message sent: "${text}"`);
        pushHistory(text);
        setText('');
        setCursorPos(0);
        setTimeout(() => setLastImeActionFeedback(null), 3000);
      } else {
        setLastImeActionFeedback('Cannot dispatch empty message');
        setTimeout(() => setLastImeActionFeedback(null), 2000);
      }
      return;
    }
    if (imeAction === 'actionDone' || imeAction === 'IME_ACTION_DONE') {
      setLastImeActionFeedback(`Input committed: "${text}" (Dismissed IME)`);
      setTimeout(() => setLastImeActionFeedback(null), 3000);
      return;
    }
    if (imeAction === 'actionGo' || imeAction === 'IME_ACTION_GO') {
      setLastImeActionFeedback(`Navigation dispatched for: "${text}"`);
      setTimeout(() => setLastImeActionFeedback(null), 3000);
      return;
    }
    if (imeAction === 'actionNext' || imeAction === 'IME_ACTION_NEXT') {
      const fieldSequence: EditorInputType[] = ['text', 'email', 'password', 'number'];
      const currentIdx = fieldSequence.indexOf(editorInputType);
      const nextType = fieldSequence[(currentIdx + 1) % fieldSequence.length];
      handleEditorInputTypeChange(nextType);
      setLastImeActionFeedback(`Advanced focus to next field (${nextType})`);
      setTimeout(() => setLastImeActionFeedback(null), 3000);
      return;
    }

    // Default / Return (newline insertion):
    pushHistory(text);
    const newText = text.substring(0, cursorPos) + '\n' + text.substring(cursorPos);
    setText(newText);
    setCursorPos(cursorPos + 1);
  };

  // Shift toggle
  const handleShiftToggle = () => {
    playSoundAndHaptic();
    if (shiftState === ShiftState.OFF) {
      setShiftState(ShiftState.ON);
    } else if (shiftState === ShiftState.ON) {
      setShiftState(ShiftState.CAPS);
    } else {
      setShiftState(ShiftState.OFF);
    }
  };

  // Move Cursor safely across grapheme boundaries (prevents splitting surrogate pairs or emojis)
  const handleMoveCursor = (offset: number) => {
    const newPos = getAdjacentGraphemePos(text, cursorPos, offset);
    setCursorPos(newPos);
  };

  // Prompt 14 & 15: Global hardware keyboard listener & lifecycle listener
  useEffect(() => {
    const handleGlobalKeyDown = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement;
      if (target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable)) {
        return;
      }

      if (e.key === 'Escape') {
        e.preventDefault();
        handleSystemBack();
        return;
      }

      // Automatically register hardware keyboard presence
      if (!isHardwareKeyboardAttached) {
        setIsHardwareKeyboardAttached(true);
      }

      if (e.key === 'Backspace') {
        e.preventDefault();
        handleBackspace();
      } else if (e.key === 'Enter') {
        e.preventDefault();
        handleEnter();
      } else if (e.key === 'Tab') {
        e.preventDefault();
        handleKeyPress('    ');
      } else if (e.key === 'ArrowLeft') {
        e.preventDefault();
        setCursorPos((prev) => Math.max(0, prev - 1));
      } else if (e.key === 'ArrowRight') {
        e.preventDefault();
        setCursorPos((prev) => Math.min(text.length, prev + 1));
      } else if (e.key.length === 1 && !e.ctrlKey && !e.metaKey && !e.altKey) {
        e.preventDefault();
        handleKeyPress(e.key);
      }
    };

    const handleVisibilityChange = () => {
      if (document.visibilityState === 'hidden') {
        // App switching / backgrounding: clean up any pending audio, panels, or transient state
        setActivePanel(null);
      }
    };

    window.addEventListener('keydown', handleGlobalKeyDown);
    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => {
      window.removeEventListener('keydown', handleGlobalKeyDown);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [handleSystemBack, handleBackspace, handleEnter, handleKeyPress, isHardwareKeyboardAttached, text.length]);

  // Select Suggestion
  const handleSelectSuggestion = (suggestedWord: string) => {
    playSoundAndHaptic();
    pushHistory(text);

    const before = text.substring(0, cursorPos);
    const after = text.substring(cursorPos);
    const match = before.match(/[\w'-]+$/);

    if (match) {
      const startIdx = match.index!;
      const newText = before.substring(0, startIdx) + suggestedWord + ' ' + after;
      setText(newText);
      setCursorPos(startIdx + suggestedWord.length + 1);
    } else {
      const newText = before + suggestedWord + ' ' + after;
      setText(newText);
      setCursorPos(before.length + suggestedWord.length + 1);
    }

    trackTypingActivity(suggestedWord.length + 1, 1);
  };

  // Apply Telugu Grammar Fix
  const handleApplyGrammarFix = (fix: { original: string; suggestion: string }) => {
    playSoundAndHaptic();
    pushHistory(text);

    const regex = new RegExp(fix.original, 'gi');
    const newText = text.replace(regex, fix.suggestion);
    setText(newText);
    setCursorPos(newText.length);
  };

  // Paste OTP
  const handlePasteOtp = (otp: string) => {
    playSoundAndHaptic();
    pushHistory(text);

    const newText = text.substring(0, cursorPos) + otp + ' ' + text.substring(cursorPos);
    setText(newText);
    setCursorPos(cursorPos + otp.length + 1);
  };

  // Smart Reply tap
  const handleSelectSmartReply = (reply: string) => {
    playSoundAndHaptic();
    pushHistory(text);

    const separator = text.length > 0 && !text.endsWith(' ') ? ' ' : '';
    const newText = text + separator + reply;
    setText(newText);
    setCursorPos(newText.length);
  };

  // Toolbar Actions
  const handleToolbarAction = (id: string) => {
    playSoundAndHaptic();

    if (id === 'undo') {
      handleUndo();
      return;
    }

    if (id === 'clear_history') {
      handleClearHistory();
      return;
    }

    if (id === 'theme') {
      setActivePanel(null);
      setSettingsModalTab('theme');
      setShowSettingsModal(true);
      return;
    }

    if (id === 'settings') {
      setActivePanel(null);
      setSettingsModalTab('theme');
      setShowSettingsModal(true);
      return;
    }

    if (id === 'autocorrect') {
      if (isStrictPrivacy) {
        setLastImeActionFeedback('Auto-correct bypassed in password field (Strict Privacy Protocol)');
        setTimeout(() => setLastImeActionFeedback(null), 3000);
        return;
      }
      if (isSymbolOrNumberLayer) {
        setLastImeActionFeedback('Autocorrect is disabled on symbol/numbers layer');
        setTimeout(() => setLastImeActionFeedback(null), 3000);
        return;
      }
      // Correct full sentence
      pushHistory(text);
      const { corrected, changedWords } = autoCorrectSentence(
        text,
        new Set(),
        settings.personalDictionary
      );
      setText(corrected);
      setCursorPos(corrected.length);
      if (changedWords.length > 0) {
        setStats((prev) => {
          const newCounts = { ...prev.correctionCounts };
          changedWords.forEach((w) => {
            newCounts[w] = (newCounts[w] || 0) + 1;
          });
          return { ...prev, correctionCounts: newCounts };
        });
      }
      return;
    }

    if (id === 'translate') {
      if (isStrictPrivacy) {
        setLastImeActionFeedback('Translation bypassed in password field (Strict Privacy Protocol)');
        setTimeout(() => setLastImeActionFeedback(null), 3000);
        return;
      }
      if (!text.trim()) {
        setLastImeActionFeedback('Type Telugu or English text first to translate');
        setTimeout(() => setLastImeActionFeedback(null), 2500);
        return;
      }
      // Translate between English and Telugu
      pushHistory(text);
      const { translated, changed } = translateText(text);
      if (changed) {
        setText(translated);
        setCursorPos(translated.length);
        setLastImeActionFeedback(`Translated: "${text}" → "${translated}"`);
      } else {
        setLastImeActionFeedback(`No dictionary match found for "${text.slice(0, 25)}"`);
      }
      setTimeout(() => setLastImeActionFeedback(null), 3000);
      return;
    }

    if (id === 'emoji') {
      setActivePanel(null);
      setKeyboardMode((prev) => (prev === 'emoji' ? 'qwerty' : 'emoji'));
      return;
    }

    // Toggle drawer panels (clipboard, phrases, stats, voice, customizer)
    if (activePanel === id) {
      resetKeyboardToAlphabetic();
    } else {
      setActivePanel(id);
      if (keyboardMode === 'emoji') setKeyboardMode('qwerty');
    }
  };

  // Add clip
  const handleAddClip = (clipText: string) => {
    const newItem: ClipItem = {
      id: `clip-${Date.now()}`,
      text: clipText,
      pinned: false,
      createdAt: Date.now(),
    };
    setClips((prev) => [newItem, ...prev.filter((c) => c.text !== clipText)].slice(0, 15));
  };

  // Toggle clip pin
  const handleTogglePin = (id: string) => {
    setClips((prev) =>
      prev.map((c) => (c.id === id ? { ...c, pinned: !c.pinned } : c))
    );
  };

  // Delete clip
  const handleDeleteClip = (id: string) => {
    setClips((prev) => prev.filter((c) => c.id !== id));
  };

  // Paste clip
  const handlePasteClip = (clipText: string) => {
    playSoundAndHaptic();
    pushHistory(text);
    const newText = text.substring(0, cursorPos) + clipText + text.substring(cursorPos);
    setText(newText);
    setCursorPos(cursorPos + clipText.length);
    setActivePanel(null);
  };

  // Add phrase
  const handleAddPhrase = (newPhrase: string) => {
    setSettings((prev) => ({
      ...prev,
      quickPhrases: [...prev.quickPhrases, newPhrase],
    }));
  };

  // Delete phrase
  const handleDeletePhrase = (index: number) => {
    setSettings((prev) => ({
      ...prev,
      quickPhrases: prev.quickPhrases.filter((_, i) => i !== index),
    }));
  };

  // Theme container styles
  const getKeyboardContainerStyle = () => {
    if (settings.selectedTheme === ThemeId.CUSTOM && settings.customBackgroundUrl) {
      return {
        backgroundImage: `url(${settings.customBackgroundUrl})`,
        backgroundSize: 'cover',
        backgroundPosition: 'center',
      };
    }
    return {};
  };

  const getKeyboardBgClass = () => {
    if (settings.selectedTheme === ThemeId.LIGHT) return 'bg-[#E5E9EC] text-neutral-900 border-t border-neutral-300 shadow-lg';
    if (settings.selectedTheme === ThemeId.BLUE) return 'bg-[#08182B] text-blue-100 border-t border-[#102e50] shadow-lg';
    if (settings.selectedTheme === ThemeId.GREEN) return 'bg-[#071E0C] text-green-100 border-t border-[#103818] shadow-lg';
    if (settings.selectedTheme === ThemeId.AMOLED) return 'bg-black text-white border-t border-neutral-900';
    if (settings.selectedTheme === ThemeId.BLACK) return 'bg-[#101010] text-white border-t border-neutral-800';
    return 'bg-[#20272E] text-neutral-100 border-t border-[#2d3640] shadow-lg';
  };

  return (
    <div id="app_root" className="flex flex-col min-h-screen bg-neutral-100 dark:bg-[#12161A] text-neutral-900 dark:text-neutral-100 selection:bg-blue-500 selection:text-white">
      {/* Top Header Bar */}
      <header className="w-full flex items-center justify-between px-4 py-3 border-b border-neutral-200 dark:border-neutral-800 bg-white/70 dark:bg-[#181D22]/80 backdrop-blur-md sticky top-0 z-40">
        <div className="flex items-center space-x-2.5">
          <div className="w-8 h-8 rounded-xl bg-blue-600 text-white flex items-center justify-center font-bold text-base shadow-sm">
            B
          </div>
          <div>
            <h1 className="font-bold text-sm tracking-tight flex items-center gap-1.5">
              <span>BabelKey</span>
              <span className="text-[10px] px-1.5 py-0.2 rounded-full bg-blue-500/15 text-blue-600 dark:text-blue-400 font-semibold">
                Multilingual
              </span>
            </h1>
            <p className="text-[11px] text-neutral-500 dark:text-neutral-400 leading-none">
              Telugu & English Smart Custom Keyboard
            </p>
          </div>
        </div>

        <div className="flex items-center space-x-2">
          {/* Quick theme switcher */}
          <button
            id="btn_quick_theme"
            onClick={() =>
              handleUpdateSettings({
                selectedTheme: settings.selectedTheme === ThemeId.LIGHT ? ThemeId.DARK : ThemeId.LIGHT,
              })
            }
            title="Toggle Light/Dark Theme"
            className="p-2 rounded-xl border border-neutral-200 dark:border-neutral-800 hover:bg-neutral-100 dark:hover:bg-neutral-800 transition-colors cursor-pointer"
          >
            {settings.selectedTheme === ThemeId.LIGHT ? <Moon size={16} /> : <Sun size={16} />}
          </button>

          {/* Settings button */}
          <button
            id="btn_open_settings"
            onClick={() => {
              setActivePanel(null);
              setSettingsModalTab('theme');
              setShowSettingsModal(true);
            }}
            className="p-2 rounded-xl border border-neutral-200 dark:border-neutral-800 hover:bg-neutral-100 dark:hover:bg-neutral-800 text-blue-600 dark:text-blue-400 transition-colors flex items-center space-x-1.5 text-xs font-semibold cursor-pointer"
          >
            <Settings size={16} />
            <span className="hidden sm:inline">Settings</span>
          </button>
        </div>
      </header>

      {/* Main Content Area: Typing Playground */}
      <main className="flex-1 flex flex-col justify-start pb-4 overflow-y-auto">
        <TypingArea
          text={text}
          cursorPos={cursorPos}
          onTextChange={(newText, newPos) => {
            const charDiff = newText.length - text.length;
            if (charDiff > 0) {
              const oldWords = text.trim() ? text.trim().split(/\s+/).length : 0;
              const newWords = newText.trim() ? newText.trim().split(/\s+/).length : 0;
              const wordDiff = Math.max(0, newWords - oldWords);
              trackTypingActivity(charDiff, wordDiff);
            }
            setText(newText);
            setCursorPos(newPos);
          }}
          incomingMessage={incomingMessage}
          onCycleIncomingMessage={() => setSampleIdx((prev) => (prev + 1) % SAMPLE_PROMPTS.length)}
          onQuickSampleInsert={(sample) => {
            pushHistory(text);
            const newT = text ? `${text} ${sample}` : sample;
            setText(newT);
            setCursorPos(newT.length);
          }}
          historyCount={history.length}
          onUndo={handleUndo}
          onClearHistory={handleClearHistory}
          historyClearedNotice={historyClearedNotice}
          editorInputType={editorInputType}
          onChangeEditorInputType={handleEditorInputTypeChange}
          imeAction={imeAction}
          onChangeImeAction={setImeAction}
          isMultiLine={isMultiLine}
          onChangeIsMultiLine={setIsMultiLine}
          lastImeActionFeedback={lastImeActionFeedback}
          onFocus={resetKeyboardToAlphabetic}
        />

        {/* Prompt 13, 14, 15: IME Robustness, Hardware Keyboard & Multitasking Control Bar */}
        <div className="w-full max-w-2xl mx-auto px-4 py-2 flex flex-wrap items-center justify-between gap-2 text-xs">
          <div className="flex items-center space-x-1.5 flex-wrap gap-1.5">
            {/* Prompt 13: Simulate IME restart */}
            <button
              id="btn_test_ime_restart"
              onClick={handleSimulateImeRestart}
              title="Test behavior across InputMethodService restarts (killing and recreating IME process)"
              className="px-2.5 py-1 rounded-lg border border-neutral-300 dark:border-neutral-700 bg-white dark:bg-neutral-800 hover:bg-neutral-100 dark:hover:bg-neutral-700 text-neutral-700 dark:text-neutral-200 transition-colors flex items-center space-x-1.5 font-medium cursor-pointer shadow-xs"
            >
              <RefreshCw size={13} className="text-blue-500" />
              <span>Kill & Recreate IME</span>
            </button>

            {/* Prompt 14: Hardware Keyboard Toggle */}
            <button
              id="btn_toggle_hw_keyboard"
              onClick={() => {
                const next = !isHardwareKeyboardAttached;
                setIsHardwareKeyboardAttached(next);
                if (next) setSoftKeyboardHidden(true);
                else setSoftKeyboardHidden(false);
              }}
              title="Simulate attaching or detaching an external hardware keyboard"
              className={`px-2.5 py-1 rounded-lg border transition-colors flex items-center space-x-1.5 font-medium cursor-pointer shadow-xs ${
                isHardwareKeyboardAttached
                  ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400'
                  : 'border-neutral-300 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-neutral-700 dark:text-neutral-200 hover:bg-neutral-100 dark:hover:bg-neutral-700'
              }`}
            >
              <Keyboard size={13} />
              <span>HW Keyboard: {isHardwareKeyboardAttached ? 'Attached' : 'None'}</span>
            </button>

            {/* Prompt 15: Multitasking App Switch */}
            <button
              id="btn_test_multitask"
              onClick={handleSimulateAppSwitch}
              title="Simulate switching to another app and returning"
              className="px-2.5 py-1 rounded-lg border border-neutral-300 dark:border-neutral-700 bg-white dark:bg-neutral-800 hover:bg-neutral-100 dark:hover:bg-neutral-700 text-neutral-700 dark:text-neutral-200 transition-colors flex items-center space-x-1.5 font-medium cursor-pointer shadow-xs"
            >
              <Layers size={13} className="text-indigo-500" />
              <span>App Switch</span>
            </button>
          </div>

          {/* Quick toggle to show/hide soft keyboard when hardware keyboard is active */}
          {isHardwareKeyboardAttached && (
            <button
              id="btn_toggle_soft_kb"
              onClick={() => setSoftKeyboardHidden(!softKeyboardHidden)}
              className="text-[11px] px-2 py-0.5 rounded-md bg-neutral-200 dark:bg-neutral-700 hover:bg-neutral-300 dark:hover:bg-neutral-600 text-neutral-700 dark:text-neutral-200 font-medium transition-colors cursor-pointer"
            >
              {softKeyboardHidden ? 'Show Soft KB' : 'Hide Soft KB'}
            </button>
          )}
        </div>

        {/* Live Feedback Notifier for IME Restarts and Multitasking */}
        {(imeRestartNotice || lifecycleFeedback) && (
          <div className="w-full max-w-2xl mx-auto px-4 pb-1">
            <div className="px-3 py-1.5 rounded-lg bg-blue-600/10 dark:bg-blue-400/10 border border-blue-500/20 text-blue-700 dark:text-blue-300 text-xs font-medium flex items-center justify-between">
              <span>{imeRestartNotice || lifecycleFeedback}</span>
            </div>
          </div>
        )}
      </main>

      {/* Bottom Floating Keyboard Container */}
      <AccentColorContext.Provider value={settings.customAccentColor ?? null}>
        <div
          id="custom_keyboard_container"
          style={getKeyboardContainerStyle()}
          className={`w-full max-w-2xl mx-auto rounded-t-3xl overflow-hidden transition-all duration-150 flex flex-col justify-end ${getKeyboardBgClass()}`}
        >
          {/* Quick Toolbar */}
          <Toolbar
            themeId={settings.selectedTheme}
            enabledButtons={settings.toolbarButtons}
            activePanel={activePanel || (keyboardMode === 'emoji' ? 'emoji' : null)}
            onButtonClick={handleToolbarAction}
            onToggleCustomizer={() => handleToolbarAction('customizer')}
            canUndo={history.length > 0}
            historyCount={history.length}
            onClearHistory={handleClearHistory}
            customAccentColor={settings.customAccentColor}
          />

        {/* Suggestion Bar */}
        <SuggestionBar
          themeId={settings.selectedTheme}
          suggestions={suggestions}
          smartReplies={smartReplies}
          grammarError={grammarError}
          otpDetected={otpDetected}
          onSelectSuggestion={handleSelectSuggestion}
          onApplyGrammarFix={handleApplyGrammarFix}
          onPasteOtp={handlePasteOtp}
          onSelectSmartReply={handleSelectSmartReply}
          currentWord={currentWord}
          editorInputType={editorInputType}
          isSymbolOrNumberLayer={isSymbolOrNumberLayer}
        />

        {/* Drawer Panels or Active Keyboard Layout */}
        <div style={{ height: `${settings.keyboardHeightDp}px` }} className="flex flex-col justify-center items-center w-full overflow-hidden">
          {activePanel === 'clipboard' && (
            <ClipboardDrawer
              themeId={settings.selectedTheme}
              clips={clips}
              onPasteClip={handlePasteClip}
              onTogglePin={handleTogglePin}
              onDeleteClip={handleDeleteClip}
              onAddClip={handleAddClip}
              onClose={resetKeyboardToAlphabetic}
            />
          )}

          {activePanel === 'phrases' && (
            <PhrasesPanel
              themeId={settings.selectedTheme}
              phrases={settings.quickPhrases}
              onSelectPhrase={(phrase) => {
                handlePasteClip(phrase);
                resetKeyboardToAlphabetic();
              }}
              onAddPhrase={handleAddPhrase}
              onDeletePhrase={handleDeletePhrase}
              onClose={resetKeyboardToAlphabetic}
            />
          )}

          {activePanel === 'stats' && (
            <StatsPanel
              themeId={settings.selectedTheme}
              stats={stats}
              historyCount={history.length}
              onClearHistory={handleClearHistory}
              onResetStats={() =>
                setStats({
                  totalWords: 0,
                  totalCharacters: 0,
                  sessionStartTime: Date.now(),
                  lastActiveTime: Date.now(),
                  activeTypingSeconds: 0,
                  recentActivity: [],
                  correctionCounts: {},
                  dailyWords: {},
                })
              }
              onClose={resetKeyboardToAlphabetic}
            />
          )}

          {activePanel === 'voice' && (
            <VoiceTypingPanel
              themeId={settings.selectedTheme}
              onTranscriptReceived={(spokenText) => {
                pushHistory(text);
                const separator = text.length > 0 && !text.endsWith(' ') ? ' ' : '';
                const newText = text + separator + spokenText;
                setText(newText);
                setCursorPos(newText.length);
              }}
              onClose={resetKeyboardToAlphabetic}
            />
          )}

          {activePanel === 'customizer' && (
            <ToolbarCustomizer
              themeId={settings.selectedTheme}
              enabledButtons={settings.toolbarButtons}
              onUpdateToolbar={(newButtons) => handleUpdateSettings({ toolbarButtons: newButtons })}
              onClose={resetKeyboardToAlphabetic}
            />
          )}

          {/* When no drawer panel is active, show the virtual keyboard, app switcher card, or hardware keyboard card */}
          {!activePanel && (
            <>
              {isAppSwitchedAway ? (
                <div className="flex flex-col items-center justify-center p-6 text-center space-y-2">
                  <div className="w-10 h-10 rounded-2xl bg-indigo-500/20 text-indigo-500 flex items-center justify-center">
                    <Layers size={22} className="animate-pulse" />
                  </div>
                  <p className="text-sm font-semibold text-neutral-800 dark:text-neutral-200">
                    App Backgrounded (Multitasking)
                  </p>
                  <p className="text-xs text-neutral-500 dark:text-neutral-400 max-w-xs">
                    InputMethodService paused. Pending timers and transient touch states cleanly flushed.
                  </p>
                </div>
              ) : softKeyboardHidden ? (
                <div className="flex flex-col items-center justify-center p-6 text-center space-y-3">
                  <div className="flex items-center space-x-2 text-sm font-medium text-neutral-700 dark:text-neutral-200">
                    <Keyboard size={20} className="text-blue-500" />
                    <span>Hardware keyboard active</span>
                  </div>
                  <p className="text-xs text-neutral-500 dark:text-neutral-400 max-w-sm">
                    The soft keyboard is hidden to preserve screen real estate while an external hardware keyboard is attached. Type on your physical keyboard or restore soft keyboard below.
                  </p>
                  <button
                    id="btn_show_soft_kb_inline"
                    onClick={() => setSoftKeyboardHidden(false)}
                    className="px-3.5 py-1.5 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold shadow-xs transition-colors cursor-pointer"
                  >
                    Show Soft Keyboard
                  </button>
                </div>
              ) : keyboardMode === 'emoji' ? (
                <EmojiKeyboard
                  themeId={settings.selectedTheme}
                  onSelectEmoji={(emoji) => handleKeyPress(emoji)}
                  onBackspace={handleBackspace}
                  onClose={resetKeyboardToAlphabetic}
                />
              ) : (
                <KeyboardLayout
                  key={`ime-layout-${imeInstanceKey}`}
                  mode={keyboardMode}
                  themeId={settings.selectedTheme}
                  customAccentColor={settings.customAccentColor}
                  shiftState={shiftState}
                  fontSize={settings.keyFontSize ?? 16}
                  height={settings.keyboardHeightDp}
                  imeAction={imeAction}
                  isMultiLine={isMultiLine}
                  onKeyPress={handleKeyPress}
                  onBackspace={handleBackspace}
                  onDeleteWord={handleDeleteWord}
                  onEnter={handleEnter}
                  onShiftToggle={handleShiftToggle}
                  onSetShiftState={setShiftState}
                  onSwitchMode={(mode) => {
                    setActivePanel(null);
                    setKeyboardMode(mode);
                  }}
                  onMoveCursor={handleMoveCursor}
                />
              )}
            </>
          )}
        </div>

        {/* Android System 3-Button Navigation Bar (Prompt 15) */}
        <div className="w-full flex items-center justify-around py-1.5 px-8 bg-black/40 dark:bg-black/60 border-t border-neutral-700/30">
          <button
            id="nav_btn_back"
            onClick={handleSystemBack}
            title="System Back Button (dismisses popups, panels, or hides keyboard)"
            className="p-1.5 text-neutral-400 hover:text-white transition-colors cursor-pointer active:scale-95"
          >
            <ChevronLeft size={18} />
          </button>
          <button
            id="nav_btn_home"
            onClick={() => {
              handleSystemBack();
              setLifecycleFeedback('Home button pressed: minimized to home');
              setTimeout(() => setLifecycleFeedback(null), 2500);
            }}
            title="System Home Button"
            className="p-1.5 text-neutral-400 hover:text-white transition-colors cursor-pointer active:scale-95"
          >
            <Circle size={13} />
          </button>
          <button
            id="nav_btn_recents"
            onClick={handleSimulateAppSwitch}
            title="System Recents / App Switcher (tests multitasking state robustness)"
            className="p-1.5 text-neutral-400 hover:text-white transition-colors cursor-pointer active:scale-95"
          >
            <Square size={13} />
          </button>
        </div>
      </div>

      {/* Settings Hub Modal */}
      {showSettingsModal && (
        <SettingsModal
          settings={settings}
          initialTab={settingsModalTab}
          onUpdateSettings={handleUpdateSettings}
          onClose={() => {
            setShowSettingsModal(false);
            resetKeyboardToAlphabetic();
          }}
        />
      )}
      </AccentColorContext.Provider>
    </div>
  );
};
