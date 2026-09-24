# GATE 2 — Architecture & Folder Plan (mapped onto Phase 1)

**Repo:** `24A31A04O6/custom-keyboard` — branch `arena/01a0d2b7-custom-keyboard`  
**Date:** 2026-09-24  
**Master spec:** Part 0–0.5 layered architecture; Phase 1 = MVP.  
**Gate status:** Gate 1 approved. This document must be approved before any Phase-1 build begins (per BUILD PROCESS approval gates).

---

## 1. Target package & module topology

**Root namespace:** `com.babeltech.babelkey` (keep existing `com.babeltech.babelkey` to avoid Play Store / settings migration breakage; spec's `com.<yourpackage>.babelkey` maps to `com.babeltech.babelkey`).

**Gradle modules (single `android` module retained for v1 to avoid fission):**

```
android/                     // single Android app module (compileSdk 35, minSdk 24)
  src/main/
    java/com/babeltech/babelkey/
      ui/                    // FRONTEND — Compose + Views, no business logic
      core/                  // BACKEND — pure Kotlin/Java, no android.view / Compose
      data/                  // Shared data layer — Single Source of Truth
      service/               // Android system integration (thin)
      security/              // Cross-cutting policy
    res/
      xml/   (Keyboard XML, method.xml)
      layout/ (Compose hosts + legacy keyboard_view.xml during migration)
      raw/   (deprecated after migration → data/dictionaries/assets/)
      color/, drawable/, values/, values-night/
```

**Why single module?** Spec requires logical separation, not Gradle fission for v1. Logical layers enforced via package boundaries + lint + code review; physical multi-module split is a Phase-2 stretch (adds Hilt complexity without MVP benefit).

---

## 2. Full layered folder spec (verbatim spec + concrete mapping)

```
com.babeltech.babelkey/

  ui/                          <- "FRONTEND" — only displays state, forwards actions to core/
    keyboard/                  <- key layouts, key rendering, touch handling
      QwertyLayout.kt          // Compose + Keyboard XML renderer
      SymbolLayout.kt
      NumpadLayout.kt
      KeyRenderer.kt           // key background / preview / ripple
      TouchHandler.kt          // delegates to core/input/GestureHandler
      KeyboardViewModel.kt     // UI state holder (Compose StateFlow)
    toolbar/                   <- adaptive toolbar row (3 states) + editor
      ToolbarState.kt          // sealed class Idle/Typing/Expanded
      ToolbarRow.kt            // Compose row, 3-state, no flicker
      ToolbarEditor.kt         // long-press reorder/add/remove (Phase 1)
      ToolbarController.kt     // Forwarder → core + data/preferences
    panels/                    <- emoji, clipboard, settings, theme picker
      EmojiPanel.kt / EmojiViewModel.kt
      ClipboardPanel.kt        // history + pin/unpin + auto-clear badge
      SettingsPanel.kt
      ThemePicker.kt
    suggestions/               <- suggestion strip, OTP chip, undo UI
      SuggestionStrip.kt       // 3-chip row, a11y
      OtpChip.kt               // only on OTP-eligible fields
      UndoChip.kt              // one-tap revert
    themes/                    <- theme definitions, color schemes, font picker UI
      Theme.kt                 // data class + presets
      ThemeProvider.kt         // light/dark + follows system
      ColorSchemes.kt
      // FontPickerUi.kt deferred to Phase 2

  core/                        <- "BACKEND" — pure logic, testable in isolation
    input/                     <- key event processing, gesture tracking + scoring
      KeyEventProcessor.kt     // QWERTY/symbol/number → commit/delete
      EditorCompat.kt          // composing region, batch edits, RTL, surrogate pairs
      GesturePath.kt           // (Phase 1 stub) → Phase 2 full Viterbi
      GestureScorer.kt         // (Phase 1 stub) → Phase 2 scoring
    suggestion/                <- autocorrect, ranking, undo
      SuggestionEngine.kt      // prefix lookup + ranking
      AutocorrectEngine.kt     // autocorrect_dict.json lookup
      UndoManager.kt           // lastOrig/lastCorr + canUndo
      RankingPolicy.kt         // personal dict boost, frequency
    transliteration/           <- English→Telugu engine (see terminology note)
      Transliterator.kt        // NOTE: currently romanized TRANSLATION; keeps folder name for spec compat
      TranslationEngine.kt     // translations.json lookup + phrase handling
      ScriptConverter.kt       // stub for future Unicode transliteration (Phase 3)
    spellcheck/                <- misspelling correction (autocorrect_dict.json)
      SpellChecker.kt
    smartreply/                <- AI reply logic
      SmartReplyEngine.kt      // Phase 1: disabled; Phase 3: ML Kit / opt-in API
    otp/                       <- SMS Retriever, detection
      OtpDetector.kt           // 4-8 digit regex + expiry + field eligibility
      SmsRetrieverHelper.kt    // app hash generation + broadcast

  data/                        <- shared data layer — SSoT for ui/ & core/
    dictionaries/              <- migrated, never reduced
      DictionaryRepository.kt  // loads from data/dictionaries/assets
      Assets:
        translations.json      // 1199 pairs, sha256 77ee7b79...
        autocorrect_dict.json  // 171 pairs, sha256 dc1993ac...
        tel_eng_dict.json      // 1757 prefixes / 1313 words
        telugu_dict.json       // 62 / 163
        eng_dict.json          // 2010 / 1428
        suggestions_dict.json  // 19 / 70
        emoji_suggestions.json // 29 entries
      MigrationTool.kt         // verifies checksums, copies atomically
    clipboard/                 <- history storage (in-memory + EncryptedPrefs)
      ClipboardRepository.kt   // 24h auto-expire, pin, no secure-field writes
      ClipboardEntry.kt
    preferences/               <- user settings, toolbar layout, theme, font
      PreferencesRepository.kt // EncryptedSharedPreferences
      ToolbarPrefs.kt
      ThemePrefs.kt
    personal/                  <- personal dictionary, learned words
      PersonalDictionary.kt
      LearnedWordsStore.kt

  service/                     <- Android system integration (thin)
    ime/                       <- InputMethodService — delegates only
      BabelImeService.kt       // (rename from MyKeyboardService) — replaces 1000-line god class
      ImeLifecycle.kt          // bind/unbind, process death, config change
      InputConnectionProxy.kt  // composing/batch/RTL/emoji/password fallback
    accessibility/             <- a11y hooks
      AccessibilityDelegate.kt

  security/                    <- cross-cutting
    SecureFieldGuard.kt        // isPasswordField, isIncognito → suppress learn/suggest/clipboard
    IncognitoMode.kt
    ClipboardPolicy.kt
    NetworkPolicy.kt           // HTTPS-only, opt-in gates for online features
```

**Enforcement rules (per spec § Rules):**

1. `ui/` never contains business logic — only renders `StateFlow`/`LiveData` from `core/` via `data/`.
2. `core/` never imports `android.view`, `androidx.compose.*`, `android.widget.*`. Pure Kotlin/Java, JUnit-testable.
3. `data/` is Single Source of Truth — `ui/` and `core/` read/write via repositories, never duplicate `Map` copies.
4. `service/ime/` stays thin: wires `ui/` + `core/` + `data/` together, handles `InputConnection`, lifecycle, config changes, editor actions (`Done/Next/Search/Send`), multi-field switching. No feature logic inside.
5. Every feature file goes into its matching folder per Parts 1–8 — lint rule `noAdHocPackage` fails build if a file lives outside the 5 top folders.
6. New `core/transliteration/` folder retains spec name but its KDoc states: "Currently implements romanized TRANSLATION (per Gate 1 finding); ScriptConverter is a stub for future Unicode transliteration."

---

## 3. Phase mapping (stabilize each phase before next)

### Phase 1 — MVP (this Gate's build target)

| Spec feature | Folder | Deliverable | Offline? |
|---|---|---|---|
| QWERTY / symbol / number layouts + key event handling | `ui/keyboard` + `core/input/KeyEventProcessor` + `service/ime` | Keyboard XMLs + Compose renderer, key handler with EditorCompat | ✅ always |
| Suggestion strip + autocorrect pipeline (Part 2 LatinIME reference, no research module) | `ui/suggestions` + `core/suggestion` + `core/spellcheck` + `data/dictionaries` | `SuggestionEngine`, `AutocorrectEngine`, `RankingPolicy`, `SuggestionStrip` wired to dictionaries | ✅ |
| Transliteration (romanized translation) + spellcheck | `core/transliteration` + `core/spellcheck` + `data/dictionaries` | `Transliterator`/`TranslationEngine` + `SpellChecker` loading 7 JSON files full, never reduced | ✅ |
| Personal dictionary + learned words | `data/personal` + `data/preferences` | `PersonalDictionary`, `LearnedWordsStore` (EncryptedSharedPreferences) | ✅ |
| Adaptive toolbar — 3 states minus contextual smart suggestions | `ui/toolbar` + `data/preferences/ToolbarPrefs` | `Idle: grid | hint | mic`; `Typing: grid | word chips | mic`; `Expanded: full row`; instant transitions, no flicker | ✅ |
| Toolbar editor (long-press reorder/add/remove) | `ui/toolbar/ToolbarEditor` | Persist order in EncryptedSharedPreferences | ✅ |
| Undo | `core/suggestion/UndoManager` + `ui/suggestions/UndoChip` | One-tap revert of autocorrect/autocomplete; also undoes swipe word (stub in Phase 1) | ✅ |
| Basic emoji panel (categories/search) | `ui/panels/EmojiPanel` + `data/dictionaries/emoji_suggestions.json` | Picker grid, verified categories (8 × ≥20, existing `EmojiKeyboardView` migrated) | ✅ |
| Clipboard manager (history, pin/unpin, auto-clear, never secure-field) | `ui/panels/ClipboardPanel` + `data/clipboard` + `security/` | History ≤24h (configurable), pin, auto-expire, SecureFieldGuard prevents writes | ✅ (in-memory) |
| Themes: light/dark + 2 presets, follows system | `ui/themes` + `data/preferences/ThemePrefs` | Light/Dark + 2 presets (current `ThemeManager` themes migrated), day/night resources | ✅ |
| Undo UI | `ui/suggestions/UndoChip` | Visual chip + gesture | ✅ |

**Phase 1 explicitly excludes (deferred):** glide path tracking/scoring (Phase 2), Google Fonts (Phase 2), auto OTP paste (Phase 2), screenshot-suggestion chip (Phase 2), AI/smart replies (Phase 3), GIF/sticker (Phase 3), one-handed mode (Phase 3).

### Phase 2 — Glide + Fonts + OTP + Screenshot chip

| Feature | Folder | Notes |
|---|---|---|
| Gesture/glide typing (finger path, Viterbi/similar scoring, smooth) | `core/input/GesturePath` + `GestureScorer` + `ui/keyboard/TouchHandler` | Built from scratch (not in LatinIME); integrates suggestion strip + dictionaries; fuzz/property tests |
| Google Fonts (opt-in) | `ui/themes/FontPickerUi` + `data/preferences` | Downloads over HTTPS only, cached, respects offline core |
| Auto OTP paste (SMS Retriever, 4–8 digits, field-eligible only, 5-min expiry, never clipboard/disk) | `core/otp` + `ui/suggestions/OtpChip` + `service/ime` | Requires app hash; see § 7 |
| Screenshot-suggestion chip | `ui/toolbar` + `service/accessibility` | Accessibility hook or MediaProjection — opt-in |

### Phase 3 — Stretch

| Feature | Folder |
|---|---|
| AI/smart replies (opt-in, provider named, HTTPS) | `core/smartreply` + `ui/panels` |
| GIF/sticker support (opt-in SDK) | `ui/panels` + `data/preferences` |
| One-handed mode | `ui/keyboard` + `data/preferences` |

---

## 4. Rules & dependency graph

```
ui/  ──reads──►  data/  ◄──reads──  core/
 │                              ▲
 └──────forwards actions─────────┘
              ▲
              │ wires
         service/ime
              │
          security (guards all)
```

- **No cycles:** `data/` has no dependency on `ui/` or `core/`; `core/` depends only on `data/` interfaces; `ui/` depends on `data/` + `core/` state via `StateFlow`.
- **Lint gates:** custom Lint checks — `UiLogicLeak` (ui/ imports banned `core` business logic), `CoreUiLeak` (core/ bans `androidx.compose`), `DataDuplication` (detects duplicate Maps outside `data/`).
- **God-class fix:** `MyKeyboardService` (1007 lines) split into `service/ime/BabelImeService` (~150 lines, lifecycle + InputConnection delegation) + dedicated managers moved to their layer folders. `ServiceCallback` removed in favor of `StateFlow`/`Repository` pattern.

---

## 5. Licensing inventory (before any third-party code use)

| Component | License | Required attribution | Use in Phase 1? | Action |
|---|---|---|---|---|
| **Current proprietary code** `com.babeltech.babelkey` (no header found in any of 13 Java files) | Unknown / proprietary — treat as all-rights-reserved until owner confirms | Retain existing headers (none) → add SPDX to each new file | ✅ — migrated internally | Add `LICENSE` file clarifying original code license; do not publish until owner chooses license |
| **AOSP LatinIME** (`github.com/aosp-mirror/platform_packages_inputmethods_LatinIME`) — architecture reference only, fresh implementation | **Apache 2.0** | Must include Apache 2.0 `LICENSE` copy + `NOTICE` attribution if any code is ever copied; here: reference only, so attribution in docs | ✅ reference | Add `NOTICE` section "This product includes architecture derived from AOSP LatinIME, Apache 2.0" even if no code copied; retain Apache 2.0 text in `LICENSE` |
| **AndroidX AppCompat** `androidx.appcompat:appcompat:1.7.1` | Apache 2.0 | Included via Gradle | ✅ | List in `NOTICE` |
| **Material Components** `com.google.android.material:material:1.14.0` | Apache 2.0 | Included via Gradle | ✅ | List in `NOTICE` |
| **RecyclerView** `androidx.recyclerview:recyclerview:1.3.2` | Apache 2.0 | Included via Gradle | ✅ | List in `NOTICE` |
| **Google ML Kit Smart Reply** `com.google.mlkit:smart-reply:17.0.4` | **ML Kit Terms** (proprietary Google, on-device) | Must disclose Google ML Kit usage; no Apache obligation but Google terms apply | ❌ Phase 3 only (Phase 1 keeps stub disabled) | Do **not** include in Phase 1 build; keep dependency commented with gate check |
| **org.json** `org.json:json:20231013` (test only) | **JSON License** (MIT-style but with "The Software shall be used for Good, not Evil" clause) — incompatible with some strict policies | Keep as `testImplementation` only; not shipped in APK | ✅ test only | Note in `NOTICE`: test dependency not distributed |
| **Emoji dataset** (embedded via `EmojiKeyboardView.java` `CATEGORY_EMOJIS` codePoints — hardcoded Unicode code points, not image assets) | **Unicode / emoji is public domain code points**; if replacing with Noto Emoji font later, Apache 2.0 | If we later ship Noto Color Emoji, add SIL-adjacent Apache 2.0 notice | ✅ existing code points | Verify `EmojiKeyboardView` codePoints are raw Unicode, no font file to attribute yet |
| **Font files / GIF-SDK / AI model** | Not present in Phase 1 | — | ❌ Phase 2/3 | Any addition requires new row + approval before use |
| **Jetpack Security / EncryptedSharedPreferences** (planned) | Apache 2.0 | — | ✅ Phase 1 | List in `NOTICE` once added |
| **Jetpack Compose** (planned) | Apache 2.0 | — | ✅ Phase 1 | List in `NOTICE` |

**Deliverable:** `LICENSE` (Apache 2.0 copy + proprietary placeholder for original code) + `NOTICE` file in repo root enumerating every row above, added **before** any copy-paste from LatinIME. No GIF/sticker/AI SDK may be added without updating this table and getting approval.

---

## 6. External integrations & offline guarantee (per Part 0.5)

| Feature | Network? | Default | Disclosure | Implementation |
|---|---|---|---|---|
| Core typing, transliteration (`translations.json`), spellcheck, autocorrect | **100% offline** | always on | none needed | Bundled JSONs in `data/dictionaries/assets`; zero network calls |
| Online translation fallback (`api.babeltech.com/translate` — current `TranslationService`) | Online | **OFF by default**, opt-in switch in Settings | Settings must name provider (`BabelTech API`) + show "Sends typed text to …" warning | Gated behind `PreferencesRepository.onlineTranslationEnabled`; if ON, `NetworkPolicy` enforces HTTPS/TLS; current URL to be reviewed for production readiness |
| Voice input | Via Android `SpeechRecognizer` only | system permission `RECORD_AUDIO` via user opt-in | Standard Android permission dialog | No third-party SDK unless named/approved |
| AI replies / GIF search / Google Fonts (Phase 2/3) | Online | OFF by default, opt-in | Provider named per feature | HTTPS only |

No telemetry / analytics / crash-reporting SDK in Phase 1. `CrashLogger.java` currently exists — Phase 1 will either remove it or keep it **local-only** (logcat/file, never network) unless user explicitly opts into self-hosted reporting.

---

## 7. OTP architecture (per spec)

- **API:** Android **SMS Retriever API** (`com.google.android.gms.auth.api.phone.SmsRetriever` Play Services; no additional `RECEIVE_SMS` permission needed).
- **App hash:** 11-char base64 string computed from `keystore + packageName`. Must be generated and documented.
  - Generation: `keytool -exportcert -alias <alias> -keystore <keystore> | xxd -p | tr -d "[:space:]" | echo -n <package> <hex> | sha256sum | xxd -r -p | base64 | cut -c1-11`
  - Or via `SmsRetrieverHelper.getAppSignatureHash(context)` at runtime (Play Services helper). Helper will be in `core/otp/SmsRetrieverHelper.kt` and its output shown in Settings → OTP for developer verification.
  - Spec appId: `com.babeltech.babelkey` → hash to be computed once signing config is chosen (see release build).
  - SMS format expected: `... 123456 ... <11-char-hash>` (4–8 contiguous digits).
- **Eligibility:** `OtpDetector.isEligible(EditorInfo)` returns true only for:
  - `InputType.TYPE_CLASS_NUMBER` OR `TYPE_CLASS_TEXT` with `TYPE_TEXT_VARIATION_PASSWORD` excluded, plus
  - `maxLength` ≤ 8 (if reported) OR autofill hint contains `otp`/`verificationCode`/`smsOTP`, OR `inputType` is numeric.
  - **Never** shows OTP chip on arbitrary text fields / password fields.
- **Lifecycle:** Chip appears → user taps to commit → `InputConnection.commitText()` → entry **never written to `data/clipboard` nor disk** → entry kept in RAM only in `core/otp/OtpChipState` with 5-minute `Handler` expiry (`postDelayed(5*60*1000) { expire }`). If app process dies, chip is lost (acceptable).
- **No clipboard leak:** `ClipboardPolicy` enforces `!OtpDetector.isOtp(expiredOrUsed)` before any `ClipboardRepository.write()`.

---

## 8. Privacy / security (per Part 0.5)

- **No telemetry by default.** No analytics / crash SDK.
- **HTTPS/TLS:** `NetworkPolicy` enforces `HttpsURLConnection` / `OkHttp` with TLS; plain `HttpURLConnection` to non-https fails lint.
- **Clipboard:** `ClipboardRepository` auto-expires after **24 hours** (configurable 1h/6h/24h/never) via `WorkManager` or `Handler`; expiry sweeps on IME start + periodic. Stored via `EncryptedSharedPreferences` (Jetpack Security `MasterKey` + `EncryptedSharedPreferences.create()`).
- **Typed words / personal dict:** also `EncryptedSharedPreferences`, never plaintext `SharedPreferences`.
- **Auto-backup exclusion:** `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml` will contain:
  ```xml
  <exclude domain="sharedpref" path="encrypted_prefs.xml"/>
  <exclude domain="sharedpref" path="MyBoardPrefs.xml"/> <!-- legacy, migrated then excluded -->
  <exclude domain="file" path="clipboard/"/>
  ```
  Plus `AndroidManifest.xml` `android:allowBackup="true"` retained but sensitive prefs excluded; `android:dataExtractionRules` for API 31+.
- **Export/delete controls:** Settings → Personal Dictionary → `Export` (plain JSON share) + `Delete all learned words` (wipes `data/personal/*` + `data/preferences` keys).
- **Secure storage:** Jetpack Security `androidx.security:security-crypto`.
- **Incognito / secure fields:** `SecureFieldGuard` checks `EditorInfo.inputType & TYPE_TEXT_VARIATION_PASSWORD`, `TYPE_TEXT_VARIATION_VISIBLE_PASSWORD`, `TYPE_TEXT_FLAG_NO_SUGGESTIONS`; when true → suppress `SuggestionEngine`, `PersonalDictionary.learn()`, and `ClipboardRepository` writes.

---

## 9. Keyboard lifecycle & input compatibility (per spec)

- **IME bind/unbind:** `BabelImeService.onCreate()` loads `data/` repos; `onCreateInputView()` inflates Compose host; `onStartInput(EditorInfo, boolean)` refreshes `SecureFieldGuard` + eligible-field detection; `onFinishInput()` clears `core/suggestion/currentWord`, dismisses `SymbolPopupWindow`, cancels `OtpChip` timer.
- **Process death/restart:** All critical state (`toolbar order`, `theme`, `personal dict`, `clipboard pinned`) in `EncryptedSharedPreferences`; UI state (`currentWord`, `canUndo`, `OtpChip`) is transient and rebuilt from `InputConnection` on restart.
- **Config changes:** Rotation, dark/light, font size → `onConfigurationChanged()` delegates to `ui/themes/ThemeProvider`; no dedicated tablet/foldable layouts, but `ResponsiveKeyboardLayout` keeps `%p` widths ≤100% (existing regression test enforces this).
- **Multi-field & mid-session IME switch:** `onUnbindInput()` cleans up `ClipboardManager` listener, `VoiceTypingManager` recognizer, `GestureHandler` pointers.
- **Editor actions:** `onEditorAction()` forwards `IME_ACTION_DONE/NEXT/SEARCH/SEND` to `InputConnection.performEditorAction()`.
- **Input compatibility:** `core/input/EditorCompat` handles `InputConnection.getTextBeforeCursor()` composing regions, `beginBatchEdit`/`endBatchEdit`, selection/deletion/cursor, RTL (`layoutDirection`), emoji/surrogate pairs (codePoint-aware delete), password/visible-password fields (graceful fallback: no suggestions/learning), `TYPE_NUMBER`/`EMAIL`/`URI`/`PHONE` (switch to appropriate keyboard layout), and broken `EditorInfo` (null checks, fallback to `commitText`).

---

## 10. Testing & acceptance criteria (per spec, Phase 1)

| Category | Tool | Location | Targets |
|---|---|---|---|
| Unit tests — transliteration, spellcheck, ranking, undo | JUnit 4 / Robolectric | `src/test/java/.../core/` `core/suggestion`, `core/transliteration`, `core/spellcheck` | Already have `AutocorrectTest`, `DictionaryLoaderTest`; add `TransliteratorTest`, `RankingTest`, `UndoTest`, `SecureFieldGuardTest` |
| Instrumentation tests — UI + IME | Espresso + `androidx.test` | `src/androidTest/java/.../ui/` `service/ime` | Keyboard render, toolbar state transitions, suggestion strip, theme switch |
| Fuzz/property — glide scorer | Kotest property + JUnit QuickCheck | `src/test/.../core/input/GestureScorerTest` | Phase 2; Phase 1 stub still has property scaffold |
| Performance | Macrobenchmark + manual | CI | keystroke-to-render <16ms (1 frame @60Hz), cold-start <1s (from `onCreate` to `onCreateInputView` return), 30-min typing with no heap growth (LeakCanary in debug), 0 ANRs |
| A11y | TalkBack manual + `AccessibilityChecks` | `ui/` all panels | TalkBack announcements, touch exploration, adjustable key-repeat, system large-text, high-contrast theme, switch access, reduced-motion |
| Security | Manual + instrumentation | `security/` | Secure-field: zero suggestion/learning/clipboard activity; verified via `SecureFieldGuardTest` + manual password-field typing |
| Compatibility | Manual smoke | 3+ messaging apps (WhatsApp, Telegram, Messages) | Correct commit/delete/replace across apps with different `EditorInfo` |

---

## 11. Dictionary migration / import tool (Phase 1 deliverable)

- **Tool:** `data/dictionaries/MigrationTool.kt` + CLI `scripts/migrate_dictionaries.py` (or Gradle task `migrateDictionaries`).
- **Steps:**
  1. Read from `android/app/src/main/res/raw/*.json` (source of truth per Gate 1) and verify checksums match the Gate 1 table (fail closed on mismatch, prompt for Gate-1 re-approval).
  2. Atomically copy to `src/main/assets/dictionaries/` (new location for layered architecture; `res/raw` is deprecated because `assets` allows subfolder `dictionaries/`).
  3. Validate JSON shape (KV vs prefix) + count matches Gate 1 counts; write `checksums.sha256` alongside.
  4. Generate `DictionaryManifest.json` listing each file + sha256 + entry counts.
- **"Never reduced" enforcement:** `MigrationToolTest` asserts `newCount >= oldCount` for each file; CI fails if a PR deletes entries.
- **Spec URL for future drift:** Source is the cloned repo's `raw/`; if later fetching from `github.com/24A31A04O6/custom-keyboard/tree/main`, the tool can optionally fetch + diff, but Phase 1 uses local copy to guarantee offline build.

---

## 12. What we will NOT do in Phase 1 (to keep the build review clean)

- No table/foldable/ChromeOS dedicated layouts (just no-crash on larger screens, as spec allows).
- No cloud sync for toolbar order / theme / font / personal dict (local-only via EncryptedSharedPreferences).
- No network translation unless user opts in (setter defaults OFF).
- No GIF/sticker/one-handed packages created in Phase 1 (avoid dead code).
- No `research` module from LatinIME (explicitly excluded per Part 2).

---

## 13. Flags / open questions for approval

- **Q1:** Confirm keeping `core/transliteration/` name (with KDoc disclaimer) vs. renaming to `core/translation/`. Recommendation: **keep `transliteration/`** to satisfy spec's exact folder name, but document the romanized-translation reality.
- **Q2:** Confirm keeping legacy `MyKeyboardService` → `BabelImeService` rename (or keep `MyKeyboardService` name for lower churn). Recommendation: **rename to `BabelImeService`** with `android:name=".service.ime.BabelImeService"` and keep a deprecated alias for one release if needed.
- **Q3:** Confirm `ML Kit Smart Reply` stays **out** of Phase 1 dependencies (keeps APK smaller, keeps `NOTICE` clean). Phase 1 `core/smartreply` will be a no-op stub.
- **Q4:** Confirm theme presets: keep current light/dark defaults + add 2 more presets (e.g., `Amoled` + `Pastel`) inside `ui/themes/ColorSchemes.kt`.
- **Q5:** Confirm EncryptedSharedPreferences is acceptable (adds `androidx.security:security-crypto` ~200KB) vs. plain prefs with obfuscation. Recommendation: **use EncryptedSharedPreferences** per spec's "Use EncryptedSharedPreferences" MUST.

---

## 14. Approval gate

**Gate 2 is ready for review.** Please confirm:

- [ ] Layered folder plan above satisfies Part 0's 5 rules and maps every Phase-1 feature to its correct layer
- [ ] Licensing table is complete enough to add `LICENSE`/`NOTICE` before any code is written
- [ ] Phase 1 / 2 / 3 split is correct (especially that glide/OTP/Fonts are out of Phase 1)
- [ ] Any answers to Q1–Q5 (or "approve as recommended")

Reply **"Gate 2 approved — build Phase 1 MVP"** (or note corrections) to unblock the Phase 1 build. Per spec, I will not start coding Phase 1 until you approve.

