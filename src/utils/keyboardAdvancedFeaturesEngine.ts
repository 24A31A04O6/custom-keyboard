// ============================================================================
// TAP-DANCE & DUAL-FUNCTION HOLD/TAP ENGINE
// ============================================================================
// Implements:
//   Feature 1 - Tap-Dance Dynamic Layer Toggle
//     single tap  -> momentary / one-shot layer switch
//     double tap  -> persistent sticky layer lock
//     triple tap+ -> hard reset to base layer
//   Feature 2 - Dual-Function Hold/Tap Modifiers
//     short unchorded tap (< TAPPING_TERM)                 -> primary char
//     hold past TAPPING_TERM OR chorded rollover with any
//     other key pressed before release                     -> modifier/layer
//
// This module is UI-framework agnostic. It only calls the two callbacks you
// provide (`emitCharacter`, `setModifierState`) - it never touches the DOM,
// haptics, audio, animation, theme, or suggestion systems directly, so it can
// be dropped alongside existing input-handling code without side effects of
// its own beyond those two callbacks.
// ============================================================================

/** Default hold threshold (ms) for a dual-function key, per QMK-style "tapping term". */
export const DEFAULT_TAPPING_TERM_MS = 200;

/** Default multi-tap resolution window (ms) for a tap-dance key. */
export const DEFAULT_TAP_DANCE_TERM_MS = 250;

/**
 * Minimum time (ms) that must separate a key's own last "up" from its next
 * "down" (and vice versa) before the event is treated as a genuine new
 * physical transition rather than mechanical/capacitive contact bounce.
 */
export const DEBOUNCE_MS = 25;

export interface TapDanceConfig {
  keyId: string;
  /** Multi-tap resolution window in ms. Defaults to DEFAULT_TAP_DANCE_TERM_MS. */
  timeoutMs?: number;
  /** Fired when exactly one tap resolves (momentary/one-shot layer switch). */
  onSingleTap: () => void;
  /** Fired when exactly two taps resolve (persistent sticky layer lock). */
  onDoubleTap: () => void;
  /** Fired when three or more taps resolve (hard reset to base layer). Falls back to onDoubleTap if omitted. */
  onTripleTap?: () => void;
}

export interface DualKeyConfig {
  keyId: string;
  /** Character emitted on a clean, fast, unchorded tap. */
  tapCharacter: string;
  /** Modifier/layer name activated on hold or chord interruption, e.g. 'SHIFT', 'LAYER_SYMBOLS'. */
  holdModifier: string;
  /** Hold threshold in ms. Defaults to DEFAULT_TAPPING_TERM_MS. */
  thresholdMs?: number;
}

interface DebounceRecord {
  lastDownAt: number;
  lastUpAt: number;
}

export class KeyboardAdvancedFeaturesEngine {
  // --- Tap-Dance State ---
  private tdActiveKeyId: string | null = null;
  private tdCount = 0;
  private tdTimer: ReturnType<typeof setTimeout> | null = null;
  private readonly tdConfigs: Map<string, TapDanceConfig> = new Map();

  // --- Dual-Function Key State ---
  private dualActiveKeyId: string | null = null;
  private dualPressedAt = 0;
  private dualTimer: ReturnType<typeof setTimeout> | null = null;
  private isHoldActive = false;
  private hasInterrupted = false;
  private readonly dualConfigs: Map<string, DualKeyConfig> = new Map();

  // --- Rollover / auto-repeat guard ---
  // Tracks keys that are currently physically held down so that OS/browser
  // key-repeat events (which re-fire "down" without an intervening "up")
  // never re-enter the state machines and corrupt timers or tap counts.
  private readonly pressedKeys: Set<string> = new Set();

  // --- Debounce bookkeeping (per key) ---
  private readonly debounceRecords: Map<string, DebounceRecord> = new Map();

  constructor(
    private readonly emitCharacter: (char: string) => void,
    private readonly setModifierState: (modifier: string, isActive: boolean) => void,
    private readonly debounceMs: number = DEBOUNCE_MS
  ) {}

  // --------------------------------------------------------------------------
  // Configuration Registration
  // --------------------------------------------------------------------------
  public registerTapDance(config: TapDanceConfig): void {
    this.tdConfigs.set(config.keyId, config);
  }

  public registerDualKey(config: DualKeyConfig): void {
    this.dualConfigs.set(config.keyId, config);
  }

  public unregisterTapDance(keyId: string): void {
    if (this.tdActiveKeyId === keyId) {
      this.flushTapDance();
    }
    this.tdConfigs.delete(keyId);
  }

  public unregisterDualKey(keyId: string): void {
    if (this.dualActiveKeyId === keyId) {
      this.reset();
    }
    this.dualConfigs.delete(keyId);
  }

  // --------------------------------------------------------------------------
  // Debounce Gate
  // --------------------------------------------------------------------------
  private passesDownDebounce(keyId: string, now: number): boolean {
    const record = this.debounceRecords.get(keyId);
    if (!record) {
      this.debounceRecords.set(keyId, { lastDownAt: now, lastUpAt: -Infinity });
      return true;
    }
    if (now - record.lastUpAt < this.debounceMs) {
      // A "down" arriving immediately after this same key's last "up" is
      // almost certainly mechanical/capacitive bounce, not a real re-press.
      return false;
    }
    record.lastDownAt = now;
    return true;
  }

  private passesUpDebounce(keyId: string, now: number): boolean {
    const record = this.debounceRecords.get(keyId);
    if (!record) {
      this.debounceRecords.set(keyId, { lastDownAt: -Infinity, lastUpAt: now });
      return true;
    }
    record.lastUpAt = now;
    return true;
  }

  // --------------------------------------------------------------------------
  // POINTER / KEY DOWN DISPATCHER
  // --------------------------------------------------------------------------
  public handleKeyDown(keyId: string): void {
    const now = performance.now();

    // Guard: ignore OS/browser auto-repeat "down" events for a key that is
    // already physically held (no intervening "up" was ever received). Left
    // unguarded, this would restart the dual-key hold timer indefinitely or
    // inflate a tap-dance count on every repeat tick.
    if (this.pressedKeys.has(keyId)) {
      return;
    }

    // Debounce gate: swallow rapid mechanical/capacitive re-triggers.
    if (!this.passesDownDebounce(keyId, now)) {
      return;
    }

    this.pressedKeys.add(keyId);

    // 1. Dual-Key Chord Handling (Permissive Hold / Rolling Chord):
    // If a dual-function key is currently pressed and a *different* key goes
    // down before its hold threshold elapses, immediately promote the
    // dual-function key to HOLD, apply its modifier, and suppress its tap
    // character on eventual release. The incoming key is then routed as
    // normal below, so it is effectively "forwarded" with the modifier
    // already active.
    if (this.dualActiveKeyId && this.dualActiveKeyId !== keyId && !this.isHoldActive) {
      if (this.dualTimer) {
        clearTimeout(this.dualTimer);
        this.dualTimer = null;
      }
      this.isHoldActive = true;
      this.hasInterrupted = true;
      const conf = this.dualConfigs.get(this.dualActiveKeyId);
      if (conf) {
        this.setModifierState(conf.holdModifier, true);
      }
    }

    // 2. Tap-Dance Interruption / Collision Disambiguation:
    // Any other key going down - including a Dual-Function key - flushes a
    // pending tap-dance sequence immediately, before that other key's own
    // pending window (if any) begins.
    if (this.tdActiveKeyId && this.tdActiveKeyId !== keyId) {
      this.flushTapDance();
    }

    // 3. Route Key Type
    if (this.dualConfigs.has(keyId)) {
      this.handleDualKeyDown(keyId, now);
    } else if (this.tdConfigs.has(keyId)) {
      this.handleTapDanceKeyDown(keyId);
    } else {
      // Keys not configured for Dual-Function or Tap-Dance are tracked in pressedKeys
      // for simultaneous multitouch chords, while their characters and shift states
      // are managed by the individual UI Key components.
    }
  }

  // --------------------------------------------------------------------------
  // POINTER / KEY UP DISPATCHER
  // --------------------------------------------------------------------------
  public handleKeyUp(keyId: string): void {
    const now = performance.now();

    // An "up" for a key we never saw go down (e.g. focus was regained
    // mid-gesture, or it was already flushed by reset()) is ignored rather
    // than mutating unrelated state.
    if (!this.pressedKeys.has(keyId)) {
      return;
    }
    this.pressedKeys.delete(keyId);

    if (!this.passesUpDebounce(keyId, now)) {
      return;
    }

    if (keyId === this.dualActiveKeyId) {
      this.handleDualKeyUp();
    } else if (keyId === this.tdActiveKeyId) {
      this.handleTapDanceKeyUp();
    }
  }

  // ==========================================================================
  // FEATURE 1: Tap-Dance Dynamic Layer Toggle Logic
  // ==========================================================================
  private handleTapDanceKeyDown(keyId: string): void {
    if (this.tdTimer) {
      clearTimeout(this.tdTimer);
      this.tdTimer = null;
    }
    this.tdActiveKeyId = keyId;
    this.tdCount += 1;
  }

  private handleTapDanceKeyUp(): void {
    if (!this.tdActiveKeyId) return;
    const conf = this.tdConfigs.get(this.tdActiveKeyId);
    const timeout = conf?.timeoutMs ?? DEFAULT_TAP_DANCE_TERM_MS;

    if (this.tdTimer) {
      clearTimeout(this.tdTimer);
    }
    this.tdTimer = setTimeout(() => {
      this.flushTapDance();
    }, timeout);
  }

  /**
   * Resolves whatever tap count has accumulated so far and returns the
   * machine to IDLE. Safe to call at any time (timer expiry, an interrupting
   * key, or an external reset) - it is a no-op if no dance is pending.
   */
  public flushTapDance(): void {
    if (!this.tdActiveKeyId) return;

    if (this.tdTimer) {
      clearTimeout(this.tdTimer);
      this.tdTimer = null;
    }

    const conf = this.tdConfigs.get(this.tdActiveKeyId);
    const count = this.tdCount;

    // Reset state before invoking the callback so a callback that itself
    // triggers key events (e.g. programmatic input) always sees a clean
    // machine rather than a half-flushed one.
    this.tdActiveKeyId = null;
    this.tdCount = 0;

    if (!conf) return;

    if (count === 1) {
      conf.onSingleTap();
    } else if (count === 2) {
      conf.onDoubleTap();
    } else if (count >= 3) {
      if (conf.onTripleTap) {
        conf.onTripleTap();
      } else {
        conf.onDoubleTap();
      }
    }
  }

  /**
   * Clears any active tap dance timers and state without emitting callbacks.
   * Used during process kill/recreate, app backgrounding, or hard resets.
   */
  public clearTapDanceState(): void {
    if (this.tdTimer) {
      clearTimeout(this.tdTimer);
      this.tdTimer = null;
    }
    this.tdActiveKeyId = null;
    this.tdCount = 0;
  }

  // ==========================================================================
  // FEATURE 2: Dual-Function Hold / Tap Modifier Logic
  // ==========================================================================
  private handleDualKeyDown(keyId: string, timestamp: number): void {
    const conf = this.dualConfigs.get(keyId);
    if (!conf) return;

    this.dualActiveKeyId = keyId;
    this.dualPressedAt = timestamp;
    this.isHoldActive = false;
    this.hasInterrupted = false;

    const threshold = conf.thresholdMs ?? DEFAULT_TAPPING_TERM_MS;

    this.dualTimer = setTimeout(() => {
      this.dualTimer = null;
      this.isHoldActive = true;
      this.setModifierState(conf.holdModifier, true);
    }, threshold);
  }

  private handleDualKeyUp(): void {
    if (!this.dualActiveKeyId) return;

    if (this.dualTimer) {
      clearTimeout(this.dualTimer);
      this.dualTimer = null;
    }

    const conf = this.dualConfigs.get(this.dualActiveKeyId);
    const duration = performance.now() - this.dualPressedAt;
    const threshold = conf?.thresholdMs ?? DEFAULT_TAPPING_TERM_MS;
    const wasHoldActive = this.isHoldActive;
    const wasInterrupted = this.hasInterrupted;

    this.dualActiveKeyId = null;
    this.isHoldActive = false;
    this.hasInterrupted = false;

    if (!conf) return;

    if (!wasInterrupted && !wasHoldActive && duration < threshold) {
      // Clean, fast, unchorded release -> primary character.
      this.emitCharacter(conf.tapCharacter);
    } else if (wasHoldActive) {
      // Held past threshold, or promoted early by a chorded key -> release
      // whatever modifier/layer was applied.
      this.setModifierState(conf.holdModifier, false);
    }
  }

  // --------------------------------------------------------------------------
  // Cleanup: window blur, pointercancel/touchcancel, app switch, or IME restart
  // --------------------------------------------------------------------------
  public reset(cancelPending: boolean = true): void {
    if (cancelPending) {
      this.clearTapDanceState();
    } else {
      this.flushTapDance();
    }

    if (this.dualActiveKeyId) {
      const conf = this.dualConfigs.get(this.dualActiveKeyId);
      if (conf && this.isHoldActive) {
        this.setModifierState(conf.holdModifier, false);
      }
    }

    if (this.dualTimer) {
      clearTimeout(this.dualTimer);
      this.dualTimer = null;
    }
    if (this.tdTimer) {
      clearTimeout(this.tdTimer);
      this.tdTimer = null;
    }

    this.dualActiveKeyId = null;
    this.isHoldActive = false;
    this.hasInterrupted = false;
    this.pressedKeys.clear();
    this.debounceRecords.clear();
  }

  public destroy(): void {
    this.reset(true);
    this.tdConfigs.clear();
    this.dualConfigs.clear();
    this.debounceRecords.clear();
  }
}

// ============================================================================
// KEYBOARD INPUT BINDING
// ============================================================================
export interface KeyboardBindingOptions {
  /** Root element containing the individual key elements. */
  root: HTMLElement;
  /** The engine instance to dispatch events into. */
  engine: KeyboardAdvancedFeaturesEngine;
  /**
   * Attribute used to look up a key element's logical id, e.g.
   * `<button data-key-id="space">`. Defaults to 'data-key-id'.
   */
  keyIdAttribute?: string;
}

export type TeardownFn = () => void;

export function attachKeyboardAdvancedFeatures(
  options: KeyboardBindingOptions
): TeardownFn {
  const { root, engine } = options;
  const keyIdAttribute = options.keyIdAttribute ?? 'data-key-id';

  const resolveKeyId = (target: EventTarget | null): string | null => {
    if (!(target instanceof Element)) return null;
    const el = target.closest(`[${keyIdAttribute}]`);
    return el ? el.getAttribute(keyIdAttribute) : null;
  };

  const onPointerDown = (event: PointerEvent): void => {
    const keyId = resolveKeyId(event.target);
    if (keyId) {
      engine.handleKeyDown(keyId);
    }
  };

  const onPointerUp = (event: PointerEvent): void => {
    const keyId = resolveKeyId(event.target);
    if (keyId) {
      engine.handleKeyUp(keyId);
    }
  };

  const onPointerCancel = (): void => {
    engine.reset(true);
  };

  const onTouchCancel = (): void => {
    engine.reset(true);
  };

  const onWindowBlur = (): void => {
    engine.reset(true);
  };

  const onVisibilityChange = (): void => {
    if (document.visibilityState === 'hidden') {
      engine.reset(true);
    }
  };

  root.addEventListener('pointerdown', onPointerDown);
  root.addEventListener('pointerup', onPointerUp);
  root.addEventListener('pointercancel', onPointerCancel);
  root.addEventListener('touchcancel', onTouchCancel);
  window.addEventListener('blur', onWindowBlur);
  document.addEventListener('visibilitychange', onVisibilityChange);

  return function teardown(): void {
    root.removeEventListener('pointerdown', onPointerDown);
    root.removeEventListener('pointerup', onPointerUp);
    root.removeEventListener('pointercancel', onPointerCancel);
    root.removeEventListener('touchcancel', onTouchCancel);
    window.removeEventListener('blur', onWindowBlur);
    document.removeEventListener('visibilitychange', onVisibilityChange);
    engine.destroy();
  };
}
