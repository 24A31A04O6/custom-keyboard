# BabelKey Architecture Document (Phase 1 — layered per Part 0)

**Package:** `com.babeltech.babelkey`  
**Layers:** `ui/` (frontend), `core/` (backend), `data/` (SSoT), `service/` (IME wiring), `security/` (cross-cutting)  
**Spec compliance:** 5 rules enforced, every Part 1–8 feature mapped to its folder, no ad-hoc folders.

This is the canonical architecture doc for the rebuild. For Gate 2 rationale + Gate 3 build delta, see `docs/GATE2_ARCHITECTURE.md` and `docs/PHASE1_BUILD_REPORT.md`.

## Module

Single Android module `android/` (compileSdk 35, minSdk 24, targetSdk 35). Logical layers are package boundaries; physical multi-module split deferred to Phase 2.

## Layer map (full)

```
com.babeltech.babelkey/

  ui/                          // FRONTEND — displays state, forwards actions, no business logic
    keyboard/                  // layouts, rendering, touch → core/input
    toolbar/                   // 3 states (Idle/Typing/Expanded) + editor
    panels/                    // emoji, clipboard, settings, theme picker
    suggestions/               // strip, OTP chip, undo
    themes/                    // definitions, color schemes, (Phase 2: Google Fonts)

  core/                        // BACKEND — pure logic, no UI, unit-testable
    input/                     // key events, gesture path/scorer (Phase 2 Viterbi)
    suggestion/                // autocorrect, ranking, undo
    transliteration/           // EN→romanized Telugu (Gate 1: translation, not script transliteration) + ScriptConverter stub
    spellcheck/                // misspelling correction (autocorrect_dict.json)
    smartreply/                // AI replies (Phase 3)
    otp/                       // SMS Retriever, 4–8 digit OTP, field eligibility, expiry

  data/                        // SSoT — ui/ and core/ read/write here
    dictionaries/              // translations + autocorrect + 5 prefix maps + emoji (never reduced) + MigrationTool
    clipboard/                 // history, pin, 24h auto-expire, never secure-field
    preferences/               // toolbar order, theme, font, toggles (EncryptedSharedPreferences)
    personal/                  // personal dict, learned words (local only, export/delete)

  service/                     // Android integration — thin
    ime/                       // InputMethodService wiring + lifecycle + InputConnection proxy
    accessibility/             // TalkBack / switch access

  security/                    // cross-cutting
    SecureFieldGuard, IncognitoMode, ClipboardPolicy, NetworkPolicy
```

## Data flow

```
ui  --(actions)--> core --(state)--> ui
        \              ^\n         \            | \
          v            |  v
                     data  <- SSoT
                      ^
                      |
                   service/ime  (wires ui+core+data, owns InputConnection)
                      |
                   security (guards all)
```

## Key decisions

- **Dictionary source:** local `assets/dictionaries/` (copied from `res/raw/` in full, checksums verified). No network download for Phase 1.
- **Terminology:** `core/transliteration/` keeps spec name but KDoc explains it is romanized translation today; ScriptConverter is the seam for future Unicode transliteration.
- **Compose:** new UI is Compose; legacy Views retained behind BabelImeService until migrated.
- **OTP:** SMS Retriever helper + OtpDetector + 5-min RAM-only chip (never to clipboard/disk).
- **Privacy:** EncryptedSharedPreferences + backup exclusion + 24h clipboard expiry + secure-field guard.

## Testing

- Unit: `src/test` — core (transliteration/spellcheck/ranking/undo/security/otp/migration) + existing autocorrect/dict tests.
- Instrumentation: `src/androidTest` — IME + Compose UI.
- Regression: `tests/test_source_regressions.py` + `android/tests/test_source_regressions.py`.
- Performance targets: <16ms keystroke-to-render, <1s cold start, 30-min no growth, zero ANRs.

## Roadmap

- Phase 1 (this doc): MVP — typing, transliteration/spellcheck, toolbar 3 states, emoji, clipboard, themes, undo — all offline, no Glide/Fonts/OTP paste/screenshot/AI/GIF/one-handed.
- Phase 2: glide Viterbi, Google Fonts, auto OTP paste, screenshot chip.
- Phase 3: AI smart replies, GIF/sticker, one-handed, true transliteration script mapping.

