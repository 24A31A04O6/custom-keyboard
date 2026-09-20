import React, { useState, useRef, useEffect, useContext } from 'react';
import { ThemeId, KeyboardMode, ShiftState, ImeAction } from '../types';
import { Key } from './Key';
import { EmojiKeyboard } from './EmojiKeyboard';
import { KeyFontSizeContext } from '../context/KeyFontSizeContext';
import { AccentColorContext } from '../context/AccentColorContext';
import { hexToRgba, getContrastTextColor } from '../utils/color';
import {
  KeyboardAdvancedFeaturesEngine,
  attachKeyboardAdvancedFeatures,
} from '../utils/keyboardAdvancedFeaturesEngine';
import {
  Delete,
  CornerDownLeft,
  ArrowBigUp,
  Smile,
  Search,
  Send,
  ArrowRight,
  CornerDownRight,
  Check,
  ChevronLeft,
  ChevronRight,
} from 'lucide-react';

/**
 * =========================================================================================
 * LAYOUT CONFIGURATION FOR NUMERIC & SYMBOLS VIEWS (?123 / numpad / symbols_shift)
 * -----------------------------------------------------------------------------------------
 * • Key Height: Set NUM_SYM_KEY_HEIGHT to any desired height (e.g. 'h-[60px]', 'h-[64px]').
 *   Currently set to 'h-[60px]' (60dp) for taller, vertically elongated keys that are easy to tap.
 * • Row Gap: Set NUM_SYM_ROW_GAP to adjust vertical spacing between rows (e.g. 'space-y-2.5' = 10dp).
 * • Key Gap: Set NUM_PAD_KEY_GAP / SYM_KEY_GAP for distinct horizontal spacing.
 * =========================================================================================
 */
export const NUM_SYM_KEY_HEIGHT = 'h-[60px]'; // 60dp: taller key height for numeric and symbols views
export const NUM_SYM_ROW_GAP = 'space-y-2.5'; // 10dp: increased row gap between each row
export const NUM_PAD_KEY_GAP = 'gap-x-2 sm:gap-x-2.5'; // 8dp-10dp: horizontal distance between keys in numpad
export const NUM_PAD_SPECIAL_LEFT_MARGIN = 'mr-1.5 sm:mr-2'; // extra horizontal distance separating left special keys from numpad
export const NUM_PAD_SPECIAL_RIGHT_MARGIN = 'ml-1.5 sm:ml-2'; // extra horizontal distance separating right special keys from numpad
export const SYM_KEY_GAP = 'gap-x-1 sm:gap-x-1.5'; // 4dp-6dp: horizontal distance between keys in symbols views

interface KeyboardLayoutProps {
  mode: KeyboardMode;
  themeId: ThemeId;
  shiftState: ShiftState;
  fontSize?: number;
  height?: number;
  imeAction?: ImeAction;
  isMultiLine?: boolean;
  customAccentColor?: string | null;
  onKeyPress: (char: string) => void;
  onBackspace: () => void;
  onDeleteWord: () => void;
  onEnter: () => void;
  onShiftToggle: () => void;
  onSetShiftState?: (state: ShiftState) => void;
  onSwitchMode: (mode: KeyboardMode) => void;
  onMoveCursor: (offset: number) => void;
}

export const KeyboardLayout: React.FC<KeyboardLayoutProps> = ({
  mode,
  themeId,
  shiftState,
  fontSize = 16,
  height,
  imeAction = 'actionSend',
  isMultiLine = false,
  customAccentColor,
  onKeyPress,
  onBackspace,
  onDeleteWord,
  onEnter,
  onShiftToggle,
  onSetShiftState,
  onSwitchMode,
  onMoveCursor,
}) => {
  const contextAccent = useContext(AccentColorContext);
  const effectiveAccent = customAccentColor !== undefined ? customAccentColor : contextAccent;
  // Long press popup state
  const [symbolPopup, setSymbolPopup] = useState<{
    symbols: string[];
    x: number;
    y: number;
  } | null>(null);

  // Spacebar cursor glide state (Commercial Gboard Trackpad Contract)
  const [isGlidingSpace, setIsGlidingSpace] = useState(false);
  const [glideDirection, setGlideDirection] = useState<'left' | 'right' | null>(null);
  const spaceInitialXRef = useRef<number | null>(null);
  const spaceLastStepXRef = useRef<number | null>(null);
  const spaceThresholdExceededRef = useRef<boolean>(false);
  const spacePointerDownRef = useRef<boolean>(false);
  const lastSpaceTapMsRef = useRef<number>(0);

  // Backspace hold-to-repeat & swipe-to-delete state (Gboard parity)
  const bsStartXRef = useRef<number | null>(null);
  const bsLastDragXRef = useRef<number | null>(null);
  const bsHoldingRef = useRef<boolean>(false);
  const bsDragActiveRef = useRef<boolean>(false);
  const bsTimerRef = useRef<number | null>(null);
  const bsRepeatCountRef = useRef<number>(0);
  const bsDragIntervalRef = useRef<number | null>(null);
  const [bsScrubState, setBsScrubState] = useState<{ active: boolean; count: number } | null>(null);
  const bsScrubCountRef = useRef<number>(0);

  // Track active physical/virtual keys for multi-touch and simultaneous key transitions
  const [activeKeys, setActiveKeys] = useState<Set<string>>(new Set());
  const isKeyActive = (k: string) => activeKeys.has(k);
  const pressedKeyIdsRef = useRef<Set<string>>(new Set());

  const stopBsRepeat = () => {
    if (bsTimerRef.current) {
      clearTimeout(bsTimerRef.current);
      bsTimerRef.current = null;
    }
    if (bsDragIntervalRef.current) {
      clearInterval(bsDragIntervalRef.current);
      bsDragIntervalRef.current = null;
    }
    bsHoldingRef.current = false;
    bsRepeatCountRef.current = 0;
    bsDragActiveRef.current = false;
    bsScrubCountRef.current = 0;
    setBsScrubState(null);
  };

  // Ensure keyboard layout initializes fresh on alphabetic keys whenever opened/mounted
  useEffect(() => {
    if (mode !== 'qwerty') {
      onSwitchMode('qwerty');
    }
    setSymbolPopup(null);
    pressedKeyIdsRef.current.clear();
    setActiveKeys(new Set());
    stopBsRepeat();
    engineRef.current?.reset();
    return () => {
      stopBsRepeat();
      setSymbolPopup(null);
      pressedKeyIdsRef.current.clear();
      setActiveKeys(new Set());
      engineRef.current?.reset();
    };
  }, []);

  const containerRef = useRef<HTMLDivElement>(null);
  const engineRef = useRef<KeyboardAdvancedFeaturesEngine | null>(null);

  // Initialize Tap-Dance Dynamic Layer Toggle & Dual-Function Hold/Tap Engine
  useEffect(() => {
    if (!containerRef.current) return;

    const engine = new KeyboardAdvancedFeaturesEngine(
      (char) => {
        onKeyPress(char);
      },
      (modifier, isActive) => {
        if (modifier === 'LAYER_SYMBOLS') {
          if (isActive) {
            onSwitchMode('symbols');
          } else {
            onSwitchMode('qwerty');
          }
        }
      }
    );

    // Feature 1: Tap-Dance Dynamic Layer Toggle on Shift
    // single tap  -> momentary/one-shot shift (ShiftState.ON)
    // double tap  -> persistent sticky layer lock (ShiftState.CAPS)
    // triple tap+ -> hard reset to base layer (ShiftState.OFF)
    engine.registerTapDance({
      keyId: 'shift',
      timeoutMs: 260,
      onSingleTap: () => {
        if (onSetShiftState) onSetShiftState(ShiftState.ON);
        else onShiftToggle();
      },
      onDoubleTap: () => {
        if (onSetShiftState) onSetShiftState(ShiftState.CAPS);
        else onShiftToggle();
      },
      onTripleTap: () => {
        if (onSetShiftState) onSetShiftState(ShiftState.OFF);
        else onShiftToggle();
      },
    });

    // Feature 2: Dual-Function Hold/Tap Modifiers on Space
    // short unchorded tap (< 200ms) -> ' '
    // hold past 200ms OR chorded rollover with another key -> LAYER_SYMBOLS
    engine.registerDualKey({
      keyId: 'space',
      tapCharacter: ' ',
      holdModifier: 'LAYER_SYMBOLS',
      thresholdMs: 200,
    });

    engineRef.current = engine;

    const teardown = attachKeyboardAdvancedFeatures({
      root: containerRef.current,
      engine,
    });

    return () => {
      teardown();
      engineRef.current = null;
    };
  }, [onKeyPress, onSetShiftState, onShiftToggle, onSwitchMode]);

  useEffect(() => {
    const handleResetAll = () => {
      pressedKeyIdsRef.current.clear();
      setActiveKeys(new Set());
      engineRef.current?.reset(true);
      setSymbolPopup(null);
      setIsGlidingSpace(false);
      spacePointerDownRef.current = false;
      stopBsRepeat();
    };

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        if (symbolPopup) {
          setSymbolPopup(null);
          e.preventDefault();
          return;
        }
        if (mode !== 'qwerty') {
          onSwitchMode('qwerty');
          e.preventDefault();
          return;
        }
      }

      let keyId = e.key.toLowerCase();
      if (e.code === 'Space' || e.key === ' ') keyId = 'space';
      else if (e.key === 'Backspace') keyId = 'backspace';
      else if (e.key === 'Enter') keyId = 'enter';
      else if (e.key === 'Shift') keyId = 'shift';

      pressedKeyIdsRef.current.add(keyId);
      setActiveKeys(new Set(pressedKeyIdsRef.current));

      window.setTimeout(() => {
        pressedKeyIdsRef.current.delete(keyId);
        setActiveKeys(new Set(pressedKeyIdsRef.current));
      }, 150);
    };

    const handleKeyUp = (e: KeyboardEvent) => {
      let keyId = e.key.toLowerCase();
      if (e.code === 'Space' || e.key === ' ') keyId = 'space';
      else if (e.key === 'Backspace') keyId = 'backspace';
      else if (e.key === 'Enter') keyId = 'enter';
      else if (e.key === 'Shift') keyId = 'shift';

      pressedKeyIdsRef.current.delete(keyId);
      setActiveKeys(new Set(pressedKeyIdsRef.current));
    };

    const handleVisibilityChange = () => {
      if (document.visibilityState === 'hidden') {
        handleResetAll();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    window.addEventListener('keyup', handleKeyUp);
    window.addEventListener('blur', handleResetAll);
    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
      window.removeEventListener('keyup', handleKeyUp);
      window.removeEventListener('blur', handleResetAll);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      handleResetAll();
    };
  }, [symbolPopup, mode, onSwitchMode]);

  const isLight = themeId === ThemeId.LIGHT;

  const handleLongPressSymbol = (symbols: string[], x: number, y: number) => {
    setSymbolPopup({ symbols, x, y });
  };

  const handleSelectSymbol = (sym: string) => {
    onKeyPress(sym);
    setSymbolPopup(null);
  };

  // Spacebar glide handlers (Commercial Gboard Trackpad Contract)
  const handleSpacePointerDown = (e: React.PointerEvent) => {
    e.preventDefault();
    try {
      (e.currentTarget as HTMLElement).setPointerCapture?.(e.pointerId);
    } catch {
      // Ignore if setPointerCapture is unsupported
    }
    spacePointerDownRef.current = true;
    spaceInitialXRef.current = e.clientX;
    spaceLastStepXRef.current = e.clientX;
    spaceThresholdExceededRef.current = false;
    setGlideDirection(null);
  };

  const handleSpacePointerMove = (e: React.PointerEvent) => {
    if (!spacePointerDownRef.current || spaceInitialXRef.current === null) return;
    const totalDx = Math.abs(e.clientX - spaceInitialXRef.current);

    // Strict 10px Displacement Threshold:
    // Once horizontal displacement exceeds 10px, permanently lock into trackpad mode
    // and completely decouple character insertion so releasing will NEVER insert a space!
    if (totalDx > 10) {
      spaceThresholdExceededRef.current = true;
      if (!isGlidingSpace) {
        setIsGlidingSpace(true);
      }
    }

    if (spaceThresholdExceededRef.current && spaceLastStepXRef.current !== null) {
      const stepDiff = e.clientX - spaceLastStepXRef.current;
      // Scrub 1 grapheme/character hop per 12px of slide distance
      if (Math.abs(stepDiff) >= 12) {
        const dir = stepDiff > 0 ? 1 : -1;
        setGlideDirection(dir > 0 ? 'right' : 'left');
        onMoveCursor(dir);
        spaceLastStepXRef.current = e.clientX;
      }
    }
  };

  const handleSpacePointerUp = (e?: React.PointerEvent) => {
    if (e) {
      try {
        (e.currentTarget as HTMLElement).releasePointerCapture?.(e.pointerId);
      } catch {
        // Ignore
      }
    }
    const hadPointer = spacePointerDownRef.current;
    const wasThresholdExceeded = spaceThresholdExceededRef.current;

    // Reset trackpad states
    setIsGlidingSpace(false);
    setGlideDirection(null);
    spacePointerDownRef.current = false;
    spaceThresholdExceededRef.current = false;
    spaceInitialXRef.current = null;
    spaceLastStepXRef.current = null;

    if (!hadPointer) return;

    // COMMERCIAL STANDARD DECOUPLING:
    // If horizontal displacement exceeded 10px threshold, character commitment is strictly disabled!
    if (wasThresholdExceeded) {
      engineRef.current?.reset();
      return; // Do NOT insert space or period
    }

    // Clean tap without horizontal scrubbing:
    const now = Date.now();
    if (now - lastSpaceTapMsRef.current < 450) {
      // Double-tap space: Rapid period (.) insertion contract
      onBackspace();
      onKeyPress('. ');
      lastSpaceTapMsRef.current = 0;
    } else {
      lastSpaceTapMsRef.current = now;
    }
  };

  // Backspace multi-stage cadence & leftward drag gesture (Gboard commercial contract)
  const handleBsPointerDown = (e: React.PointerEvent) => {
    e.preventDefault();
    try {
      (e.currentTarget as HTMLElement).setPointerCapture?.(e.pointerId);
    } catch {
      // Ignore if setPointerCapture is unsupported
    }

    stopBsRepeat();
    bsStartXRef.current = e.clientX;
    bsLastDragXRef.current = e.clientX;
    bsHoldingRef.current = true;
    bsDragActiveRef.current = false;
    bsRepeatCountRef.current = 0;
    bsScrubCountRef.current = 0;

    // Stage 0: Immediate deletion on pointer down
    onBackspace();

    // Stage 1: 350ms initial delay before continuous repeat begins
    bsTimerRef.current = window.setTimeout(() => {
      if (!bsHoldingRef.current || bsDragActiveRef.current) return;

      const runAcceleratedDelete = () => {
        if (!bsHoldingRef.current || bsDragActiveRef.current) return;
        bsRepeatCountRef.current += 1;
        const count = bsRepeatCountRef.current;

        if (count > 20) {
          // Stage 4: Extended holds (>20 deletions) switch to whole-word deletions
          onDeleteWord();
        } else {
          // Stages 2 & 3: Single grapheme cluster deletions
          onBackspace();
        }

        // Dynamic multi-stage cadence:
        // • count 1..10: 85ms linear repeat
        // • count 11..20: 45ms accelerated repeat
        // • count > 20: 45ms accelerated whole-word repeat
        const nextInterval = count > 10 ? 45 : 85;
        bsTimerRef.current = window.setTimeout(runAcceleratedDelete, nextInterval);
      };

      runAcceleratedDelete();
    }, 350);
  };

  const handleBsPointerMove = (e: React.PointerEvent) => {
    if (bsStartXRef.current === null || !bsHoldingRef.current) return;
    const diffX = e.clientX - bsStartXRef.current;

    // Leftward drag gesture threshold: triggers rapid word-level deletion
    if (diffX < -18) {
      if (!bsDragActiveRef.current) {
        bsDragActiveRef.current = true;
        // Halt char-by-char repeat timer so it doesn't delete chars while scrubbing words
        if (bsTimerRef.current) {
          clearTimeout(bsTimerRef.current);
          bsTimerRef.current = null;
        }

        // Immediate word deletion upon entering drag gesture
        onDeleteWord();
        bsScrubCountRef.current = 1;
        bsLastDragXRef.current = e.clientX;
        setBsScrubState({ active: true, count: 1 });

        // Continuous rapid word-level deletion loop while held in leftward drag position
        if (!bsDragIntervalRef.current) {
          bsDragIntervalRef.current = window.setInterval(() => {
            if (!bsHoldingRef.current || !bsDragActiveRef.current) return;
            onDeleteWord();
            bsScrubCountRef.current += 1;
            setBsScrubState({ active: true, count: bsScrubCountRef.current });
          }, 80);
        }
      } else {
        // Distance scrubbing: as finger scrubs further left, trigger deletions for every ~24px
        if (bsLastDragXRef.current !== null) {
          const stepDiff = e.clientX - bsLastDragXRef.current;
          if (stepDiff < -24) {
            onDeleteWord();
            bsScrubCountRef.current += 1;
            bsLastDragXRef.current = e.clientX;
            setBsScrubState({ active: true, count: bsScrubCountRef.current });
          }
        }
      }
    }
  };

  const handleBsPointerUp = (e?: React.PointerEvent) => {
    if (e) {
      try {
        (e.currentTarget as HTMLElement).releasePointerCapture?.(e.pointerId);
      } catch {
        // Ignore
      }
    }
    stopBsRepeat();
    bsStartXRef.current = null;
    bsLastDragXRef.current = null;
  };

  // Check whether multi-line or no enter action is set, forcing fallback to standard Enter (newline)
  const isFallbackEnter =
    Boolean(isMultiLine) ||
    imeAction === 'IME_ACTION_NONE' ||
    imeAction === 'actionNone' ||
    imeAction === 'IME_ACTION_UNSPECIFIED' ||
    imeAction === 'actionUnspecified';

  const getEnterKeyLabel = () => {
    if (isFallbackEnter) return 'Enter';
    switch (imeAction) {
      case 'actionSearch':
      case 'IME_ACTION_SEARCH':
        return 'Search';
      case 'actionSend':
      case 'IME_ACTION_SEND':
        return 'Send';
      case 'actionGo':
      case 'IME_ACTION_GO':
        return 'Go';
      case 'actionNext':
      case 'IME_ACTION_NEXT':
        return 'Next';
      case 'actionDone':
      case 'IME_ACTION_DONE':
        return 'Done';
      default:
        return 'Enter';
    }
  };

  // Render dynamic IME Action icon and label for Enter key (EditorInfo.imeOptions parity with Gboard)
  const renderEnterKeyContent = () => {
    if (isFallbackEnter) {
      return (
        <div className="flex items-center justify-center space-x-1">
          <CornerDownLeft size={16} className="stroke-[2.4]" />
          <span className="text-[11px] font-medium tracking-tight">Enter</span>
        </div>
      );
    }
    switch (imeAction) {
      case 'actionSearch':
      case 'IME_ACTION_SEARCH':
        return (
          <div className="flex items-center justify-center space-x-1">
            <Search size={15} className="stroke-[2.6]" />
            <span className="text-[11px] font-bold tracking-tight">Search</span>
          </div>
        );
      case 'actionSend':
      case 'IME_ACTION_SEND':
        return (
          <div className="flex items-center justify-center space-x-1">
            <Send size={14} className="stroke-[2.5] -translate-y-[0.5px] translate-x-[0.5px]" />
            <span className="text-[11px] font-bold tracking-tight">Send</span>
          </div>
        );
      case 'actionGo':
      case 'IME_ACTION_GO':
        return (
          <div className="flex items-center justify-center space-x-1">
            <ArrowRight size={15} className="stroke-[2.6]" />
            <span className="text-[11px] font-bold tracking-tight">Go</span>
          </div>
        );
      case 'actionNext':
      case 'IME_ACTION_NEXT':
        return (
          <div className="flex items-center justify-center space-x-1">
            <CornerDownRight size={15} className="stroke-[2.6]" />
            <span className="text-[11px] font-bold tracking-tight">Next</span>
          </div>
        );
      case 'actionDone':
      case 'IME_ACTION_DONE':
        return (
          <div className="flex items-center justify-center space-x-1">
            <Check size={16} className="stroke-[3]" />
            <span className="text-[11px] font-bold tracking-tight">Done</span>
          </div>
        );
      default:
        return (
          <div className="flex items-center justify-center space-x-1">
            <CornerDownLeft size={16} className="stroke-[2.4]" />
            <span className="text-[11px] font-medium tracking-tight">Enter</span>
          </div>
        );
    }
  };

  const getEnterColorClass = () => {
    if (isFallbackEnter) {
      return isLight
        ? 'bg-neutral-300/90 hover:bg-neutral-300 text-neutral-800 active:bg-blue-200 border-b-2 border-neutral-400/50'
        : 'bg-[#2E353D] hover:bg-[#38404a] text-neutral-200 active:bg-neutral-700 border-b-2 border-[#1e2329]';
    }
    switch (imeAction) {
      case 'actionSearch':
      case 'IME_ACTION_SEARCH':
        return 'bg-blue-600 hover:bg-blue-500 active:bg-blue-700 text-white shadow-xs font-semibold';
      case 'actionSend':
      case 'IME_ACTION_SEND':
        return 'bg-emerald-600 hover:bg-emerald-500 active:bg-emerald-700 text-white shadow-xs font-semibold';
      case 'actionGo':
      case 'IME_ACTION_GO':
        return 'bg-indigo-600 hover:bg-indigo-500 active:bg-indigo-700 text-white shadow-xs font-semibold';
      case 'actionNext':
      case 'IME_ACTION_NEXT':
        return 'bg-sky-600 hover:bg-sky-500 active:bg-sky-700 text-white shadow-xs font-semibold';
      case 'actionDone':
      case 'IME_ACTION_DONE':
        return 'bg-teal-600 hover:bg-teal-500 active:bg-teal-700 text-white shadow-xs font-semibold';
      default:
        return isLight
          ? 'bg-neutral-300/90 hover:bg-neutral-300 text-neutral-800 active:bg-blue-200 border-b-2 border-neutral-400/50'
          : 'bg-[#2E353D] hover:bg-[#38404a] text-neutral-200 active:bg-neutral-700 border-b-2 border-[#1e2329]';
    }
  };

  const getSpacebarStyle = (baseFontSize: number): React.CSSProperties => {
    const defaultFontSize = `${Math.max(10, Math.round(baseFontSize * 0.72))}px`;
    if (isGlidingSpace && effectiveAccent) {
      return {
        fontSize: defaultFontSize,
        boxShadow: `0 0 0 2px ${effectiveAccent}, inset 0 0 14px ${hexToRgba(effectiveAccent, 0.35)}`,
        backgroundColor: hexToRgba(effectiveAccent, isLight ? 0.2 : 0.3),
        color: isLight ? '#0f172a' : '#ffffff',
      };
    }
    if (isKeyActive('space') && effectiveAccent) {
      return {
        fontSize: defaultFontSize,
        borderColor: effectiveAccent,
        backgroundColor: hexToRgba(effectiveAccent, isLight ? 0.25 : 0.4),
        color: isLight ? '#0f172a' : '#ffffff',
        boxShadow: `inset 0 0 10px ${hexToRgba(effectiveAccent, 0.3)}`,
      };
    }
    return { fontSize: defaultFontSize };
  };

  const getEnterStyle = (): React.CSSProperties | undefined => {
    if (isKeyActive('enter') && effectiveAccent) {
      return {
        backgroundColor: effectiveAccent,
        color: getContrastTextColor(effectiveAccent),
        borderBottomColor: effectiveAccent,
        boxShadow: `0 0 12px ${hexToRgba(effectiveAccent, 0.45)} inset`,
      };
    }
    return undefined;
  };

  return (
    <AccentColorContext.Provider value={effectiveAccent ?? null}>
      <KeyFontSizeContext.Provider value={fontSize}>
        <div
          ref={containerRef}
          id="keyboard_view"
          onClick={() => setSymbolPopup(null)}
          style={height ? { height: `${height}px` } : undefined}
          className="w-full h-full flex flex-col items-center justify-center p-1.5 select-none relative"
        >
          {/* Spacebar Glide / Cursor Trackpad Badge with real-time direction feedback */}
          {isGlidingSpace && (
            <div
              id="space_trackpad_feedback"
              className="absolute top-1.5 z-50 px-4 py-1.5 text-xs font-semibold rounded-full shadow-2xl flex items-center space-x-2 border backdrop-blur-md animate-in fade-in zoom-in-95 pointer-events-none"
              style={
                effectiveAccent
                  ? {
                      backgroundColor: effectiveAccent,
                      color: getContrastTextColor(effectiveAccent),
                      borderColor: hexToRgba(effectiveAccent, 0.4),
                    }
                  : {
                      backgroundColor: '#2563eb',
                      color: '#ffffff',
                      borderColor: 'rgba(96, 165, 250, 0.4)',
                    }
              }
            >
            <ChevronLeft
              size={15}
              className={`transition-all duration-75 ${
                glideDirection === 'left' ? 'text-cyan-200 scale-125 stroke-[3]' : 'opacity-60'
              }`}
            />
            <span className="tracking-wide uppercase text-[11px] font-bold">
              {glideDirection === 'left'
                ? '◄ Scrubbing Left'
                : glideDirection === 'right'
                ? 'Scrubbing Right ►'
                : 'Cursor Trackpad Active'}
            </span>
            <ChevronRight
              size={15}
              className={`transition-all duration-75 ${
                glideDirection === 'right' ? 'text-cyan-200 scale-125 stroke-[3]' : 'opacity-60'
              }`}
            />
          </div>
        )}

        {/* Backspace Swipe-to-Delete Scrub Badge */}
        {bsScrubState?.active && (
          <div className="absolute top-1 z-50 px-3.5 py-1 bg-red-600 text-white text-xs font-semibold rounded-full shadow-xl flex items-center space-x-1.5 animate-pulse pointer-events-none">
            <Delete size={14} />
            <span>Scrubbing words: {bsScrubState.count} {bsScrubState.count === 1 ? 'word' : 'words'} deleted</span>
          </div>
        )}

        {/* Long-press Symbol Popup (Accents, Fractions, Currency, Math) */}
        {symbolPopup && (
          <div
            id="symbol_popup_bubble"
            style={{
              left: Math.max(10, Math.min(window.innerWidth - 240, symbolPopup.x - 120)),
              bottom: '64px',
            }}
            className="absolute z-50 flex items-center bg-neutral-900/95 text-white border border-neutral-700 shadow-2xl rounded-xl p-1.5 backdrop-blur-md animate-in fade-in zoom-in-95 duration-100"
          >
            {symbolPopup.symbols.map((sym, idx) => (
              <button
                key={`${sym}-${idx}`}
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  handleSelectSymbol(sym);
                }}
                className="w-8 h-9 flex items-center justify-center text-lg font-medium hover:bg-blue-600 rounded-xl transition-colors cursor-pointer"
              >
                {sym}
              </button>
            ))}
          </div>
        )}

        {/* Mode: QWERTY */}
        {mode === 'qwerty' && (
          <div className="keyboard-grid-container w-full h-full flex flex-col items-center justify-center space-y-1.5 py-1">
            {/* Row 1: Number Row */}
            <div className="keyboard-row w-full h-[fit-content] min-h-fit flex-none flex items-center justify-center">
              {['1', '2', '3', '4', '5', '6', '7', '8', '9', '0'].map((num) => (
                <Key
                  key={num}
                  label={num}
                  themeId={themeId}
                  shiftState={shiftState}
                  onKeyPress={onKeyPress}
                  onLongPressSymbol={handleLongPressSymbol}
                  isExternallyPressed={isKeyActive(num)}
                />
              ))}
            </div>

            {/* Row 2 */}
            <div className="keyboard-row w-full h-[fit-content] min-h-fit flex-none flex items-center justify-center">
              {['q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'].map((char) => (
                <Key
                  key={char}
                  label={char}
                  themeId={themeId}
                  shiftState={shiftState}
                  onKeyPress={onKeyPress}
                  onLongPressSymbol={handleLongPressSymbol}
                  isExternallyPressed={isKeyActive(char)}
                />
              ))}
            </div>

            {/* Row 3 */}
            <div className="keyboard-row w-full h-[fit-content] min-h-fit flex-none flex items-center justify-center px-3">
              {['a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l'].map((char) => (
                <Key
                  key={char}
                  label={char}
                  themeId={themeId}
                  shiftState={shiftState}
                  onKeyPress={onKeyPress}
                  onLongPressSymbol={handleLongPressSymbol}
                  isExternallyPressed={isKeyActive(char)}
                />
              ))}
            </div>

            {/* Row 4: Shift, Z-M, Backspace */}
            <div className="keyboard-row w-full h-[fit-content] min-h-fit flex-none flex items-center justify-center">
              <button
                id="key_shift"
                data-key-id="shift"
                type="button"
                onClick={onShiftToggle}
                style={
                  (shiftState === ShiftState.CAPS || shiftState === ShiftState.ON) && effectiveAccent
                    ? {
                        backgroundColor: effectiveAccent,
                        color: getContrastTextColor(effectiveAccent),
                        borderBottomColor: effectiveAccent,
                        boxShadow: `0 0 10px ${hexToRgba(effectiveAccent, 0.4)} inset`,
                      }
                    : undefined
                }
                className={`h-11 w-[14%] m-[2px] rounded-xl flex items-center justify-center cursor-pointer transform will-change-transform transition-[transform,background-color,border-color,box-shadow,color] duration-100 ease-out active:scale-90 active:duration-75 shadow-xs ${
                  isKeyActive('shift') ? 'scale-90 brightness-110 shadow-inner' : 'hover:-translate-y-[1px]'
                } ${
                  shiftState === ShiftState.CAPS
                    ? effectiveAccent ? '' : 'bg-blue-600 text-white'
                    : shiftState === ShiftState.ON
                    ? effectiveAccent ? '' : 'bg-blue-500 text-white'
                    : isLight
                    ? 'bg-neutral-300/80 hover:bg-neutral-300 text-neutral-800 active:bg-blue-200'
                    : 'bg-[#2E353D] hover:bg-[#38404a] text-neutral-200 active:bg-neutral-700'
                }`}
              >
                <ArrowBigUp size={20} className={shiftState === ShiftState.CAPS ? 'fill-current' : ''} />
              </button>

              {['z', 'x', 'c', 'v', 'b', 'n', 'm'].map((char) => (
                <Key
                  key={char}
                  label={char}
                  width="w-[10%]"
                  themeId={themeId}
                  shiftState={shiftState}
                  onKeyPress={onKeyPress}
                  onLongPressSymbol={handleLongPressSymbol}
                  isExternallyPressed={isKeyActive(char)}
                />
              ))}

              <button
                id="key_backspace"
                type="button"
                onPointerDown={handleBsPointerDown}
                onPointerMove={handleBsPointerMove}
                onPointerUp={handleBsPointerUp}
                onPointerLeave={handleBsPointerUp}
                title="Tap to delete, hold to accelerate, swipe left to delete word"
                className={`h-11 w-[14%] m-[2px] rounded-xl flex items-center justify-center cursor-pointer transform will-change-transform transition-[transform,background-color,border-color,box-shadow,color] duration-100 ease-out active:scale-90 active:duration-75 shadow-xs ${
                  isKeyActive('backspace')
                    ? 'scale-90 bg-red-500/25 text-red-500 shadow-inner border border-red-500/30'
                    : 'hover:-translate-y-[1px]'
                } ${
                  isLight
                    ? 'bg-neutral-300/80 hover:bg-neutral-300 text-neutral-800 active:bg-red-500/20 active:text-red-600'
                    : 'bg-[#2E353D] hover:bg-[#38404a] text-neutral-200 active:bg-red-500/20 active:text-red-400'
                }`}
              >
                <Delete size={20} />
              </button>
            </div>

            {/* Row 5: Bottom Navigation Row */}
            <div className="keyboard-row w-full h-[fit-content] min-h-fit flex-none flex items-center justify-center">
              <button
                id="key_sym_switch"
                type="button"
                onClick={() => onSwitchMode('symbols')}
                style={{ fontSize: `${Math.max(10, Math.round(fontSize * 0.75))}px` }}
                className={`h-11 w-[14%] m-[2px] rounded-xl flex items-center justify-center font-semibold cursor-pointer transform will-change-transform transition-[transform,background-color,border-color,box-shadow,color] duration-100 ease-out active:scale-90 active:duration-75 shadow-xs hover:-translate-y-[1px] ${
                  isLight
                    ? 'bg-neutral-300/80 hover:bg-neutral-300 text-neutral-800 active:bg-blue-200 active:text-blue-900'
                    : 'bg-[#2E353D] hover:bg-[#38404a] text-neutral-200 active:bg-blue-900/40 active:text-blue-200'
                }`}
              >
                ?123
              </button>

              <Key
                label=","
                width="w-[9%]"
                themeId={themeId}
                shiftState={shiftState}
                onKeyPress={onKeyPress}
                onLongPressSymbol={handleLongPressSymbol}
                isExternallyPressed={isKeyActive(',')}
              />

              <button
                id="key_emoji_switch"
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  onSwitchMode('emoji');
                }}
                className={`h-11 w-[9%] m-[2px] rounded-xl flex items-center justify-center cursor-pointer transform will-change-transform transition-[transform,background-color,border-color,box-shadow,color] duration-100 ease-out active:scale-90 active:duration-75 shadow-xs hover:-translate-y-[1px] ${
                  isLight
                    ? 'bg-neutral-300/80 hover:bg-neutral-300 text-neutral-800 active:bg-blue-200 active:text-blue-900'
                    : 'bg-[#2E353D] hover:bg-[#38404a] text-neutral-200 active:bg-blue-900/40 active:text-blue-200'
                }`}
                aria-label="Emoji keyboard"
              >
                <Smile size={19} />
              </button>

              {/* Spacebar with Trackpad Slide */}
              <div
                id="key_spacebar"
                data-key-id="space"
                onPointerDown={handleSpacePointerDown}
                onPointerMove={handleSpacePointerMove}
                onPointerUp={handleSpacePointerUp}
                onPointerCancel={handleSpacePointerUp}
                onPointerLeave={(e) => {
                  if (!e.currentTarget.hasPointerCapture?.(e.pointerId)) handleSpacePointerUp(e);
                }}
                title="Slide across space to move cursor (Trackpad mode)"
                style={getSpacebarStyle(fontSize)}
                className={`h-11 w-[38%] m-[2px] rounded-xl flex items-center justify-center font-medium tracking-wider cursor-pointer transform will-change-transform transition-[transform,background-color,border-color,box-shadow,color] duration-100 ease-out active:scale-[0.98] active:duration-75 shadow-xs select-none ${
                  isGlidingSpace
                    ? 'scale-[0.98] ring-2 ring-blue-500 bg-blue-500/25 dark:bg-blue-600/35 text-blue-600 dark:text-blue-300 font-bold shadow-inner'
                    : isKeyActive('space')
                    ? 'scale-[0.98] ring-2 ring-blue-500/50 bg-blue-100/90 dark:bg-blue-900/50 text-blue-600 dark:text-blue-300 shadow-inner'
                    : 'hover:-translate-y-[1px]'
                } ${
                  isLight
                    ? 'bg-white hover:bg-neutral-50 text-neutral-500 border-b-2 border-neutral-300 active:bg-blue-100'
                    : themeId === ThemeId.BLUE
                    ? 'bg-[#1a4473] hover:bg-[#20528a] text-blue-300 border-b-2 border-[#102d4f] active:bg-blue-600'
                    : themeId === ThemeId.GREEN
                    ? 'bg-[#1a5222] hover:bg-[#22632b] text-green-300 border-b-2 border-[#103816] active:bg-emerald-600'
                    : 'bg-[#3A434C] hover:bg-[#434d57] text-neutral-400 border-b-2 border-[#262c33] active:bg-blue-600/50'
                }`}
              >
                {isGlidingSpace ? (
                  <span className="flex items-center space-x-1 font-mono text-[11px] text-blue-600 dark:text-blue-300 animate-pulse font-bold">
                    <span>◄</span>
                    <span>CURSOR TRACKPAD</span>
                    <span>►</span>
                  </span>
                ) : (
                  'English (US)'
                )}
              </div>

              <Key
                label="."
                width="w-[9%]"
                themeId={themeId}
                shiftState={shiftState}
                onKeyPress={onKeyPress}
                onLongPressSymbol={handleLongPressSymbol}
                isExternallyPressed={isKeyActive('.')}
              />

              <button
                id="key_enter"
                type="button"
                onClick={onEnter}
                style={getEnterStyle()}
                title={`IME Action: ${getEnterKeyLabel()} (${imeAction}${isMultiLine ? ' - MultiLine' : ''})`}
                className={`h-11 w-[16%] m-[2px] rounded-xl flex items-center justify-center cursor-pointer transform will-change-transform transition-[transform,background-color,border-color,box-shadow,color] duration-100 ease-out active:scale-90 active:duration-75 shadow-xs ${getEnterColorClass()} ${
                  isKeyActive('enter') ? 'scale-90 brightness-110 shadow-inner' : 'hover:-translate-y-[1px]'
                }`}
              >
                {renderEnterKeyContent()}
              </button>
            </div>
          </div>
        )}

        {/* Mode: Symbols (?123) */}
        {mode === 'symbols' && (
          <div className={`keyboard-grid-container w-full h-full flex flex-col items-center justify-center ${NUM_SYM_ROW_GAP} py-1 px-1`}>
            {/* Row 1: Numbers */}
            <div className={`keyboard-row w-full h-[fit-content] min-h-fit flex-none flex items-center justify-center ${SYM_KEY_GAP}`}>
              {['1', '2', '3', '4', '5', '6', '7', '8', '9', '0'].map((num) => (
                <Key
                  key={num}
                  label={num}
                  width="w-[9.2%]"
                  heightClass={NUM_SYM_KEY_HEIGHT}
                  marginClass="my-0.5"
                  themeId={themeId}
                  shiftState={shiftState}
                  onKeyPress={onKeyPress}
                  onLongPressSymbol={handleLongPressSymbol}
                  isExternallyPressed={isKeyActive(num)}
                />
              ))}
            </div>

            {/* Row 2: Standard Symbols */}
            <div className={`keyboard-row w-full h-[fit-content] min-h-fit flex-none flex items-center justify-center ${SYM_KEY_GAP}`}>
              {['@', '#', '₹', '_', '&', '-', '+', '(', ')', '/'].map((char) => (
                <Key
                  key={char}
                  label={char}
                  width="w-[9.2%]"
                  heightClass={NUM_SYM_KEY_HEIGHT}
                  marginClass="my-0.5"
                  themeId={themeId}
                  shiftState={shiftState}
                  onKeyPress={onKeyPress}
                  onLongPressSymbol={handleLongPressSymbol}
                  isExternallyPressed={isKeyActive(char)}
                />
              ))}
            </div>

            {/* Row 3: Symbols with Mode Switch, 8 Symbol slots (no dead slots), & Hold-to-Repeat Backspace */}
            <div className={`keyboard-row w-full h-[fit-content] min-h-fit flex-none flex items-center justify-center ${SYM_KEY_GAP}`}>
              <button
                type="button"
                onClick={() => onSwitchMode('symbols_shift')}
                style={{ fontSize: `${Math.max(10, Math.round(fontSize * 0.75))}px` }}
                className={`${NUM_SYM_KEY_HEIGHT} w-[11%] my-0.5 rounded-xl flex items-center justify-center font-semibold cursor-pointer transform will-change-transform transition-[transform,background-color,border-color,box-shadow,color] duration-100 ease-out active:scale-90 active:duration-75 shadow-xs hover:-translate-y-[1px] ${
                  isLight
                    ? 'bg-neutral-300/80 hover:bg-neutral-300 text-neutral-800 active:bg-blue-200'
                    : 'bg-[#2E353D] hover:bg-[#38404a] text-neutral-200 active:bg-neutral-700'
                }`}
              >
                =\&lt;
              </button>
              {['*', '"', "'", ':', ';', '!', '?', '\\'].map((char) => (
                <Key
                  key={char}
                  label={char}
                  width="w-[8.8%]"
                  heightClass={NUM_SYM_KEY_HEIGHT}
                  marginClass="my-0.5"
                  themeId={themeId}
                  shiftState={shiftState}
                  onKeyPress={onKeyPress}
                  onLongPressSymbol={handleLongPressSymbol}
                  isExternallyPressed={isKeyActive(char)}
                />
              ))}
              <button
                type="button"
                onPointerDown={handleBsPointerDown}
                onPointerMove={handleBsPointerMove}
                onPointerUp={handleBsPointerUp}
                onPointerLeave={handleBsPointerUp}
                title="Tap to delete, hold to accelerate"
                className={`${NUM_SYM_KEY_HEIGHT} w-[11%] my-0.5 rounded-xl flex items-center justify-center cursor-pointer transform will-change-transform transition-[transform,background-color,border-color,box-shadow,color] duration-100 ease-out active:scale-90 active:duration-75 shadow-xs ${
                  isKeyActive('backspace')
                    ? 'scale-90 bg-red-500/25 text-red-500 shadow-inner'
                    : 'hover:-translate-y-[1px]'
                } ${
                  isLight
                    ? 'bg-neutral-300/80 hover:bg-neutral-300 text-neutral-800 active:bg-red-500/20 active:text-red-600'
                    : 'bg-[#2E353D] hover:bg-[#38404a] text-neutral-200 active:bg-red-500/20 active:text-red-400'
                }`}
              >
                <Delete size={20} />
              </button>
            </div>

            {/* Row 4: Navigation Row */}
            <div className={`keyboard-row w-full h-[fit-content] min-h-fit flex-none flex items-center justify-center ${SYM_KEY_GAP}`}>
              <button
                type="button"
                onClick={() => onSwitchMode('qwerty')}
                style={{ fontSize: `${Math.max(10, Math.round(fontSize * 0.75))}px` }}
                className={`${NUM_SYM_KEY_HEIGHT} w-[13.5%] my-0.5 rounded-xl flex items-center justify-center font-semibold cursor-pointer transform will-change-transform transition-[transform,background-color,border-color,box-shadow,color] duration-100 ease-out active:scale-90 active:duration-75 shadow-xs hover:-translate-y-[1px] ${
                  isLight
                    ? 'bg-neutral-300/80 hover:bg-neutral-300 text-neutral-800 active:bg-blue-200'
                    : 'bg-[#2E353D] hover:bg-[#38404a] text-neutral-200 active:bg-neutral-700'
                }`}
              >
                ABC
              </button>
              <Key
                label=","
                width="w-[9.2%]"
                heightClass={NUM_SYM_KEY_HEIGHT}
                marginClass="my-0.5"
                themeId={themeId}
                shiftState={shiftState}
                onKeyPress={onKeyPress}
                onLongPressSymbol={handleLongPressSymbol}
                isExternallyPressed={isKeyActive(',')}
              />
              <button
                type="button"
                onClick={() => onSwitchMode('numpad')}
                className={`${NUM_SYM_KEY_HEIGHT} w-[9.2%] my-0.5 rounded-xl flex flex-col items-center justify-center text-[11px] font-bold leading-none cursor-pointer transform will-change-transform transition-[transform,background-color,border-color,box-shadow,color] duration-100 ease-out active:scale-90 active:duration-75 shadow-xs hover:-translate-y-[1px] ${
                  isLight
                    ? 'bg-neutral-300/80 hover:bg-neutral-300 text-neutral-800 active:bg-blue-200'
                    : 'bg-[#2E353D] hover:bg-[#38404a] text-neutral-200 active:bg-neutral-700'
                }`}
              >
                <span>12</span>
                <span>34</span>
              </button>
              <div
                onPointerDown={handleSpacePointerDown}
                onPointerMove={handleSpacePointerMove}
                onPointerUp={handleSpacePointerUp}
                onPointerCancel={handleSpacePointerUp}
                onPointerLeave={(e) => {
                  if (!e.currentTarget.hasPointerCapture?.(e.pointerId)) handleSpacePointerUp(e);
                }}
                style={getSpacebarStyle(fontSize)}
                className={`${NUM_SYM_KEY_HEIGHT} w-[34%] my-0.5 rounded-xl flex items-center justify-center cursor-pointer transform will-change-transform transition-[transform,background-color,border-color,box-shadow,color] duration-100 ease-out active:scale-[0.98] active:duration-75 shadow-xs select-none ${
                  isGlidingSpace
                    ? 'scale-[0.98] ring-2 ring-blue-500 bg-blue-500/25 dark:bg-blue-600/35 text-blue-600 dark:text-blue-300 font-bold shadow-inner'
                    : isKeyActive('space')
                    ? 'scale-[0.98] ring-2 ring-blue-500/50 bg-blue-100/90 dark:bg-blue-900/50 shadow-inner'
                    : 'hover:-translate-y-[1px]'
                } ${
                  isLight ? 'bg-white hover:bg-neutral-50 active:bg-blue-100' : 'bg-[#3A434C] hover:bg-[#434d57] active:bg-blue-600/50'
                }`}
              >
                {isGlidingSpace && (
                  <span className="flex items-center space-x-1 font-mono text-[10px] text-blue-600 dark:text-blue-300 animate-pulse font-bold">
                    <span>◄ TRACKPAD ►</span>
                  </span>
                )}
              </div>
              <Key
                label="."
                width="w-[9.2%]"
                heightClass={NUM_SYM_KEY_HEIGHT}
                marginClass="my-0.5"
                themeId={themeId}
                shiftState={shiftState}
                onKeyPress={onKeyPress}
                onLongPressSymbol={handleLongPressSymbol}
                isExternallyPressed={isKeyActive('.')}
              />
              <button
                type="button"
                onClick={onEnter}
                style={getEnterStyle()}
                title={`IME Action: ${getEnterKeyLabel()} (${imeAction}${isMultiLine ? ' - MultiLine' : ''})`}
                className={`${NUM_SYM_KEY_HEIGHT} w-[15%] my-0.5 rounded-xl flex items-center justify-center cursor-pointer transform will-change-transform transition-[transform,background-color,border-color,box-shadow,color] duration-100 ease-out active:scale-90 active:duration-75 shadow-xs ${getEnterColorClass()} ${
                  isKeyActive('enter') ? 'scale-90 brightness-110 shadow-inner' : 'hover:-translate-y-[1px]'
                }`}
              >
                {renderEnterKeyContent()}
              </button>
            </div>
          </div>
        )}

        {/* Mode: Symbols Shift (=\<) */}
        {mode === 'symbols_shift' && (
          <div className={`keyboard-grid-container w-full h-full flex flex-col items-center justify-center ${NUM_SYM_ROW_GAP} py-1 px-1`}>
            {/* Row 1: Special Math & Typography Symbols */}
            <div className={`keyboard-row w-full h-[fit-content] min-h-fit flex-none flex items-center justify-center ${SYM_KEY_GAP}`}>
              {['~', '`', '|', '•', '√', 'π', '÷', '×', '§', '∆'].map((char) => (
                <Key
                  key={char}
                  label={char}
                  width="w-[9.2%]"
                  heightClass={NUM_SYM_KEY_HEIGHT}
                  marginClass="my-0.5"
                  themeId={themeId}
                  shiftState={shiftState}
                  onKeyPress={onKeyPress}
                  onLongPressSymbol={handleLongPressSymbol}
                  isExternallyPressed={isKeyActive(char)}
                />
              ))}
            </div>

            {/* Row 2: Currencies & Delimiters */}
            <div className={`keyboard-row w-full h-[fit-content] min-h-fit flex-none flex items-center justify-center ${SYM_KEY_GAP}`}>
              {['€', '¥', '$', '¢', '^', '°', '=', '{', '}', '\\'].map((char) => (
                <Key
                  key={char}
                  label={char}
                  width="w-[9.2%]"
                  heightClass={NUM_SYM_KEY_HEIGHT}
                  marginClass="my-0.5"
                  themeId={themeId}
                  shiftState={shiftState}
                  onKeyPress={onKeyPress}
                  onLongPressSymbol={handleLongPressSymbol}
                  isExternallyPressed={isKeyActive(char)}
                />
              ))}
            </div>

            {/* Row 3: Symbols Shift Row with Mode Switch & Backspace */}
            <div className={`keyboard-row w-full h-[fit-content] min-h-fit flex-none flex items-center justify-center ${SYM_KEY_GAP}`}>
              <button
                type="button"
                onClick={() => onSwitchMode('symbols')}
                style={{ fontSize: `${Math.max(10, Math.round(fontSize * 0.75))}px` }}
                className={`${NUM_SYM_KEY_HEIGHT} w-[11%] my-0.5 rounded-xl flex items-center justify-center font-semibold cursor-pointer transform will-change-transform transition-[transform,background-color,border-color,box-shadow,color] duration-100 ease-out active:scale-90 active:duration-75 shadow-xs hover:-translate-y-[1px] ${
                  isLight
                    ? 'bg-neutral-300/80 hover:bg-neutral-300 text-neutral-800 active:bg-blue-200'
                    : 'bg-[#2E353D] hover:bg-[#38404a] text-neutral-200 active:bg-neutral-700'
                }`}
              >
                123
              </button>
              {['%', '©', '®', '™', '✓', '[', ']', '•'].map((char) => (
                <Key
                  key={char}
                  label={char}
                  width="w-[8.8%]"
                  heightClass={NUM_SYM_KEY_HEIGHT}
                  marginClass="my-0.5"
                  themeId={themeId}
                  shiftState={shiftState}
                  onKeyPress={onKeyPress}
                  onLongPressSymbol={handleLongPressSymbol}
                  isExternallyPressed={isKeyActive(char)}
                />
              ))}
              <button
                type="button"
                onPointerDown={handleBsPointerDown}
                onPointerMove={handleBsPointerMove}
                onPointerUp={handleBsPointerUp}
                onPointerLeave={handleBsPointerUp}
                className={`${NUM_SYM_KEY_HEIGHT} w-[11%] my-0.5 rounded-xl flex items-center justify-center cursor-pointer transform will-change-transform transition-[transform,background-color,border-color,box-shadow,color] duration-100 ease-out active:scale-90 active:duration-75 shadow-xs ${
                  isKeyActive('backspace')
                    ? 'scale-90 bg-red-500/25 text-red-500 shadow-inner'
                    : 'hover:-translate-y-[1px]'
                } ${
                  isLight
                    ? 'bg-neutral-300/80 hover:bg-neutral-300 text-neutral-800 active:bg-red-500/20 active:text-red-600'
                    : 'bg-[#2E353D] hover:bg-[#38404a] text-neutral-200 active:bg-red-500/20 active:text-red-400'
                }`}
              >
                <Delete size={20} />
              </button>
            </div>

            {/* Row 4: Navigation Row */}
            <div className={`keyboard-row w-full h-[fit-content] min-h-fit flex-none flex items-center justify-center ${SYM_KEY_GAP}`}>
              <button
                type="button"
                onClick={() => onSwitchMode('qwerty')}
                style={{ fontSize: `${Math.max(10, Math.round(fontSize * 0.75))}px` }}
                className={`${NUM_SYM_KEY_HEIGHT} w-[13.5%] my-0.5 rounded-xl flex items-center justify-center font-semibold cursor-pointer transform will-change-transform transition-[transform,background-color,border-color,box-shadow,color] duration-100 ease-out active:scale-90 active:duration-75 shadow-xs hover:-translate-y-[1px] ${
                  isLight
                    ? 'bg-neutral-300/80 hover:bg-neutral-300 text-neutral-800 active:bg-blue-200'
                    : 'bg-[#2E353D] hover:bg-[#38404a] text-neutral-200 active:bg-neutral-700'
                }`}
              >
                ABC
              </button>
              <Key
                label="<"
                width="w-[9.2%]"
                heightClass={NUM_SYM_KEY_HEIGHT}
                marginClass="my-0.5"
                themeId={themeId}
                shiftState={shiftState}
                onKeyPress={onKeyPress}
                onLongPressSymbol={handleLongPressSymbol}
                isExternallyPressed={isKeyActive('<')}
              />
              <button
                type="button"
                onClick={() => onSwitchMode('numpad')}
                className={`${NUM_SYM_KEY_HEIGHT} w-[9.2%] my-0.5 rounded-xl flex flex-col items-center justify-center text-[11px] font-bold leading-none cursor-pointer transform will-change-transform transition-[transform,background-color,border-color,box-shadow,color] duration-100 ease-out active:scale-90 active:duration-75 shadow-xs hover:-translate-y-[1px] select-none ${
                  isLight
                    ? 'bg-neutral-300/80 hover:bg-neutral-300 text-neutral-800 active:bg-blue-200'
                    : 'bg-[#2E353D] hover:bg-[#38404a] text-neutral-200 active:bg-neutral-700'
                }`}
                title="Switch to numeric keypad (12/34)"
              >
                <span>12</span>
                <span>34</span>
              </button>
              <div
                onPointerDown={handleSpacePointerDown}
                onPointerMove={handleSpacePointerMove}
                onPointerUp={handleSpacePointerUp}
                onPointerCancel={handleSpacePointerUp}
                onPointerLeave={(e) => {
                  if (!e.currentTarget.hasPointerCapture?.(e.pointerId)) handleSpacePointerUp(e);
                }}
                style={getSpacebarStyle(fontSize)}
                className={`${NUM_SYM_KEY_HEIGHT} w-[34%] my-0.5 rounded-xl flex items-center justify-center cursor-pointer transform will-change-transform transition-[transform,background-color,border-color,box-shadow,color] duration-100 ease-out active:scale-[0.98] active:duration-75 shadow-xs select-none ${
                  isGlidingSpace
                    ? 'scale-[0.98] ring-2 ring-blue-500 bg-blue-500/25 dark:bg-blue-600/35 text-blue-600 dark:text-blue-300 font-bold shadow-inner'
                    : isKeyActive('space')
                    ? 'scale-[0.98] ring-2 ring-blue-500/50 bg-blue-100/90 dark:bg-blue-900/50 shadow-inner'
                    : 'hover:-translate-y-[1px]'
                } ${
                  isLight ? 'bg-white hover:bg-neutral-50 active:bg-blue-100' : 'bg-[#3A434C] hover:bg-[#434d57] active:bg-blue-600/50'
                }`}
              >
                {isGlidingSpace && (
                  <span className="flex items-center space-x-1 font-mono text-[10px] text-blue-600 dark:text-blue-300 animate-pulse font-bold">
                    <span>◄ TRACKPAD ►</span>
                  </span>
                )}
              </div>
              <Key
                label=">"
                width="w-[9.2%]"
                heightClass={NUM_SYM_KEY_HEIGHT}
                marginClass="my-0.5"
                themeId={themeId}
                shiftState={shiftState}
                onKeyPress={onKeyPress}
                onLongPressSymbol={handleLongPressSymbol}
                isExternallyPressed={isKeyActive('>')}
              />
              <button
                type="button"
                onClick={onEnter}
                style={getEnterStyle()}
                title={`IME Action: ${getEnterKeyLabel()} (${imeAction}${isMultiLine ? ' - MultiLine' : ''})`}
                className={`${NUM_SYM_KEY_HEIGHT} w-[15%] my-0.5 rounded-xl flex items-center justify-center cursor-pointer transform will-change-transform transition-[transform,background-color,border-color,box-shadow,color] duration-100 ease-out active:scale-90 active:duration-75 shadow-xs ${getEnterColorClass()} ${
                  isKeyActive('enter') ? 'scale-90 brightness-110 shadow-inner' : 'hover:-translate-y-[1px]'
                }`}
              >
                {renderEnterKeyContent()}
              </button>
            </div>
          </div>
        )}

        {/* Mode: Numpad */}
        {mode === 'numpad' && (
          <div className={`keyboard-grid-container w-full h-full flex flex-col items-center justify-center ${NUM_SYM_ROW_GAP} py-1 px-1`}>
            {/* Row 1: [+] [1] [2] [3] [*] */}
            <div className={`keyboard-row w-full h-[fit-content] min-h-fit flex-none flex items-center justify-center ${NUM_PAD_KEY_GAP}`}>
              <Key
                label="+"
                width="w-[13.5%]"
                heightClass={NUM_SYM_KEY_HEIGHT}
                marginClass="my-0.5"
                className={NUM_PAD_SPECIAL_LEFT_MARGIN}
                isSpecial
                themeId={themeId}
                shiftState={shiftState}
                onKeyPress={onKeyPress}
                onLongPressSymbol={handleLongPressSymbol}
                isExternallyPressed={isKeyActive('+')}
              />
              <Key
                label="1"
                width="w-[21%]"
                heightClass={NUM_SYM_KEY_HEIGHT}
                marginClass="my-0.5"
                themeId={themeId}
                shiftState={shiftState}
                onKeyPress={onKeyPress}
                onLongPressSymbol={handleLongPressSymbol}
                isExternallyPressed={isKeyActive('1')}
              />
              <Key
                label="2"
                width="w-[21%]"
                heightClass={NUM_SYM_KEY_HEIGHT}
                marginClass="my-0.5"
                themeId={themeId}
                shiftState={shiftState}
                onKeyPress={onKeyPress}
                onLongPressSymbol={handleLongPressSymbol}
                isExternallyPressed={isKeyActive('2')}
              />
              <Key
                label="3"
                width="w-[21%]"
                heightClass={NUM_SYM_KEY_HEIGHT}
                marginClass="my-0.5"
                themeId={themeId}
                shiftState={shiftState}
                onKeyPress={onKeyPress}
                onLongPressSymbol={handleLongPressSymbol}
                isExternallyPressed={isKeyActive('3')}
              />
              <Key
                label="*"
                width="w-[13.5%]"
                heightClass={NUM_SYM_KEY_HEIGHT}
                marginClass="my-0.5"
                className={NUM_PAD_SPECIAL_RIGHT_MARGIN}
                isSpecial
                themeId={themeId}
                shiftState={shiftState}
                onKeyPress={onKeyPress}
                onLongPressSymbol={handleLongPressSymbol}
                isExternallyPressed={isKeyActive('*')}
              />
            </div>

            {/* Row 2: [-] [4] [5] [6] [/] */}
            <div className={`keyboard-row w-full h-[fit-content] min-h-fit flex-none flex items-center justify-center ${NUM_PAD_KEY_GAP}`}>
              <Key
                label="-"
                width="w-[13.5%]"
                heightClass={NUM_SYM_KEY_HEIGHT}
                marginClass="my-0.5"
                className={NUM_PAD_SPECIAL_LEFT_MARGIN}
                isSpecial
                themeId={themeId}
                shiftState={shiftState}
                onKeyPress={onKeyPress}
                onLongPressSymbol={handleLongPressSymbol}
                isExternallyPressed={isKeyActive('-')}
              />
              <Key
                label="4"
                width="w-[21%]"
                heightClass={NUM_SYM_KEY_HEIGHT}
                marginClass="my-0.5"
                themeId={themeId}
                shiftState={shiftState}
                onKeyPress={onKeyPress}
                onLongPressSymbol={handleLongPressSymbol}
                isExternallyPressed={isKeyActive('4')}
              />
              <Key
                label="5"
                width="w-[21%]"
                heightClass={NUM_SYM_KEY_HEIGHT}
                marginClass="my-0.5"
                themeId={themeId}
                shiftState={shiftState}
                onKeyPress={onKeyPress}
                onLongPressSymbol={handleLongPressSymbol}
                isExternallyPressed={isKeyActive('5')}
              />
              <Key
                label="6"
                width="w-[21%]"
                heightClass={NUM_SYM_KEY_HEIGHT}
                marginClass="my-0.5"
                themeId={themeId}
                shiftState={shiftState}
                onKeyPress={onKeyPress}
                onLongPressSymbol={handleLongPressSymbol}
                isExternallyPressed={isKeyActive('6')}
              />
              <Key
                label="/"
                width="w-[13.5%]"
                heightClass={NUM_SYM_KEY_HEIGHT}
                marginClass="my-0.5"
                className={NUM_PAD_SPECIAL_RIGHT_MARGIN}
                isSpecial
                themeId={themeId}
                shiftState={shiftState}
                onKeyPress={onKeyPress}
                onLongPressSymbol={handleLongPressSymbol}
                isExternallyPressed={isKeyActive('/')}
              />
            </div>

            {/* Row 3: [%] [7] [8] [9] [⌫] */}
            <div className={`keyboard-row w-full h-[fit-content] min-h-fit flex-none flex items-center justify-center ${NUM_PAD_KEY_GAP}`}>
              <Key
                label="%"
                width="w-[13.5%]"
                heightClass={NUM_SYM_KEY_HEIGHT}
                marginClass="my-0.5"
                className={NUM_PAD_SPECIAL_LEFT_MARGIN}
                isSpecial
                themeId={themeId}
                shiftState={shiftState}
                onKeyPress={onKeyPress}
                onLongPressSymbol={handleLongPressSymbol}
                isExternallyPressed={isKeyActive('%')}
              />
              <Key
                label="7"
                width="w-[21%]"
                heightClass={NUM_SYM_KEY_HEIGHT}
                marginClass="my-0.5"
                themeId={themeId}
                shiftState={shiftState}
                onKeyPress={onKeyPress}
                onLongPressSymbol={handleLongPressSymbol}
                isExternallyPressed={isKeyActive('7')}
              />
              <Key
                label="8"
                width="w-[21%]"
                heightClass={NUM_SYM_KEY_HEIGHT}
                marginClass="my-0.5"
                themeId={themeId}
                shiftState={shiftState}
                onKeyPress={onKeyPress}
                onLongPressSymbol={handleLongPressSymbol}
                isExternallyPressed={isKeyActive('8')}
              />
              <Key
                label="9"
                width="w-[21%]"
                heightClass={NUM_SYM_KEY_HEIGHT}
                marginClass="my-0.5"
                themeId={themeId}
                shiftState={shiftState}
                onKeyPress={onKeyPress}
                onLongPressSymbol={handleLongPressSymbol}
                isExternallyPressed={isKeyActive('9')}
              />
              <button
                type="button"
                onPointerDown={handleBsPointerDown}
                onPointerMove={handleBsPointerMove}
                onPointerUp={handleBsPointerUp}
                onPointerLeave={handleBsPointerUp}
                title="Tap to delete, hold to accelerate"
                className={`${NUM_SYM_KEY_HEIGHT} w-[13.5%] ${NUM_PAD_SPECIAL_RIGHT_MARGIN} my-0.5 rounded-xl flex items-center justify-center cursor-pointer transform will-change-transform transition-[transform,background-color,border-color,box-shadow,color] duration-100 ease-out active:scale-90 active:duration-75 shadow-xs ${
                  isKeyActive('backspace')
                    ? 'scale-90 bg-red-500/25 text-red-500 shadow-inner'
                    : 'hover:-translate-y-[1px]'
                } ${
                  isLight
                    ? 'bg-neutral-300/80 hover:bg-neutral-300 text-neutral-800 active:bg-red-500/20 active:text-red-600'
                    : 'bg-[#2E353D] hover:bg-[#38404a] text-neutral-200 active:bg-red-500/20 active:text-red-400'
                }`}
              >
                <Delete size={20} />
              </button>
            </div>

            {/* Row 4: [ABC] [!?#] [,] [0] [.] [␣] [↵] */}
            <div className={`keyboard-row w-full h-[fit-content] min-h-fit flex-none flex items-center justify-center ${NUM_PAD_KEY_GAP}`}>
              <button
                type="button"
                onClick={() => onSwitchMode('qwerty')}
                className={`${NUM_SYM_KEY_HEIGHT} w-[13.5%] ${NUM_PAD_SPECIAL_LEFT_MARGIN} my-0.5 rounded-xl flex items-center justify-center text-xs font-bold cursor-pointer transform will-change-transform transition-[transform,background-color,border-color,box-shadow,color] duration-100 ease-out active:scale-90 active:duration-75 shadow-xs hover:-translate-y-[1px] ${
                  isLight
                    ? 'bg-neutral-300/80 hover:bg-neutral-300 text-neutral-800 active:bg-blue-200'
                    : 'bg-[#2E353D] hover:bg-[#38404a] text-neutral-200 active:bg-neutral-700'
                }`}
              >
                ABC
              </button>
              <button
                type="button"
                onClick={() => onSwitchMode('symbols')}
                className={`${NUM_SYM_KEY_HEIGHT} w-[10%] my-0.5 rounded-xl flex items-center justify-center text-xs font-bold cursor-pointer transform will-change-transform transition-[transform,background-color,border-color,box-shadow,color] duration-100 ease-out active:scale-90 active:duration-75 shadow-xs hover:-translate-y-[1px] ${
                  isLight
                    ? 'bg-neutral-300/80 hover:bg-neutral-300 text-neutral-800 active:bg-blue-200'
                    : 'bg-[#2E353D] hover:bg-[#38404a] text-neutral-200 active:bg-neutral-700'
                }`}
              >
                !?#
              </button>
              <Key
                label=","
                width="w-[10.5%]"
                heightClass={NUM_SYM_KEY_HEIGHT}
                marginClass="my-0.5"
                themeId={themeId}
                shiftState={shiftState}
                onKeyPress={onKeyPress}
                onLongPressSymbol={handleLongPressSymbol}
                isExternallyPressed={isKeyActive(',')}
              />
              <Key
                label="0"
                width="w-[21%]"
                heightClass={NUM_SYM_KEY_HEIGHT}
                marginClass="my-0.5"
                themeId={themeId}
                shiftState={shiftState}
                onKeyPress={onKeyPress}
                onLongPressSymbol={handleLongPressSymbol}
                isExternallyPressed={isKeyActive('0')}
              />
              <Key
                label="."
                width="w-[10.5%]"
                heightClass={NUM_SYM_KEY_HEIGHT}
                marginClass="my-0.5"
                themeId={themeId}
                shiftState={shiftState}
                onKeyPress={onKeyPress}
                onLongPressSymbol={handleLongPressSymbol}
                isExternallyPressed={isKeyActive('.')}
              />
              <div
                onPointerDown={handleSpacePointerDown}
                onPointerMove={handleSpacePointerMove}
                onPointerUp={handleSpacePointerUp}
                onPointerCancel={handleSpacePointerUp}
                onPointerLeave={(e) => {
                  if (!e.currentTarget.hasPointerCapture?.(e.pointerId)) handleSpacePointerUp(e);
                }}
                aria-label="Space"
                style={getSpacebarStyle(fontSize)}
                className={`${NUM_SYM_KEY_HEIGHT} w-[10%] my-0.5 rounded-xl flex items-center justify-center cursor-pointer transform will-change-transform transition-[transform,background-color,border-color,box-shadow,color] duration-100 ease-out active:scale-[0.98] active:duration-75 shadow-xs select-none ${
                  isGlidingSpace
                    ? 'scale-[0.98] ring-2 ring-blue-500 bg-blue-500/25 dark:bg-blue-600/35 text-blue-600 dark:text-blue-300 font-bold shadow-inner'
                    : isKeyActive('space')
                    ? 'scale-[0.98] ring-2 ring-blue-500/50 bg-blue-100/90 dark:bg-blue-900/50 text-blue-600 dark:text-blue-300 shadow-inner'
                    : 'hover:-translate-y-[1px]'
                } ${
                  isLight
                    ? 'bg-neutral-300/80 hover:bg-neutral-300 text-neutral-800 active:bg-blue-200'
                    : 'bg-[#2E353D] hover:bg-[#38404a] text-neutral-200 active:bg-neutral-700'
                }`}
              >
                <svg width="18" height="12" viewBox="0 0 24 16" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M4 6v6h16V6" />
                </svg>
              </div>
              <button
                type="button"
                onClick={onEnter}
                style={getEnterStyle()}
                title={`IME Action: ${getEnterKeyLabel()} (${imeAction}${isMultiLine ? ' - MultiLine' : ''})`}
                className={`${NUM_SYM_KEY_HEIGHT} w-[13.5%] ${NUM_PAD_SPECIAL_RIGHT_MARGIN} my-0.5 rounded-xl flex items-center justify-center cursor-pointer transform will-change-transform transition-[transform,background-color,border-color,box-shadow,color] duration-100 ease-out active:scale-90 active:duration-75 shadow-xs ${getEnterColorClass()} ${
                  isKeyActive('enter') ? 'scale-90 brightness-110 shadow-inner' : 'hover:-translate-y-[1px]'
                }`}
              >
                {renderEnterKeyContent()}
              </button>
            </div>
          </div>
        )}

        {/* Mode: Emoji */}
        {mode === 'emoji' && (
          <div className="w-full h-full flex-1 min-h-0 flex flex-col">
            <EmojiKeyboard
              themeId={themeId}
              onSelectEmoji={onKeyPress}
              onBackspace={onBackspace}
              onClose={() => onSwitchMode('qwerty')}
            />
          </div>
        )}
      </div>
    </KeyFontSizeContext.Provider>
    </AccentColorContext.Provider>
  );
};
