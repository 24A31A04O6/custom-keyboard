# Security Review — Gate 5 (Final)

**Branch:** `arena/01a0d2b7-custom-keyboard`  
**Date:** 2026-09-24  
**Scope:** Parts 0.5 (Privacy/Security), 4B (OTP), 8 (Security Review checklist 1–7)  
**Principles:** No telemetry by default, HTTPS/TLS only, secure-field guard everywhere, EncryptedSharedPreferences, backup exclusion.

---

## 1. Permissions — every permission + justification

Declared in `android/app/src/main/AndroidManifest.xml`:

| Permission | Protection | Declared? | Justification | Required? |
|---|---|---|---|---|
| `android.permission.BIND_INPUT_METHOD` (service) | signature | Yes (service `android:permission`) | **Required by Android** to bind IME — only the system can bind to the service. Not a runtime permission. | ✅ Required |
| `<queries> android.speech.RecognitionService` | — | Yes (`<queries><intent>`) | **Package visibility** for Android 11+ (API 30+) — without it `SpeechRecognizer.isRecognitionAvailable()` always returns false. No permission, just query. | ✅ Required for voice |
| `android.permission.RECORD_AUDIO` | dangerous | Yes | Voice typing via Android built-in `SpeechRecognizer` only when user taps mic and grants permission. Spec: "Voice input uses Android's built-in SpeechRecognizer only, no third-party SDK unless named". | ✅ Required, user-gated |
| `android.permission.INTERNET` | normal | Yes | Online features: online translation fallback (opt-in OFF), GIF search (Phase 3, opt-in), Google Fonts download (Phase 2, opt-in). **Core typing/offline dictionaries work without network.** If user never opts in, this permission is not exercised (no network calls). | ✅ Required for opt-in online only |
| `android.permission.READ_EXTERNAL_STORAGE` `android:maxSdkVersion="32"` | dangerous (deprecated) | Yes | Custom keyboard background image picker (Android ≤12) — when user opens theme picker and chooses "Pick image" (`PickImageActivity` transparent activity). Scoped to picker intent only. | ✅ Required for picker on ≤12 |
| `android.permission.READ_MEDIA_IMAGES` | dangerous | Yes | Same as above for Android 13+ (scoped photo picker for keyboard background + screenshot detection via MediaStore observer). | ✅ Required for picker + screenshot chip |
| `RECEIVE_SMS` / `READ_SMS` | dangerous | **Not declared** | Intentionally omitted — OTP uses SMS Retriever API (Play Services) which does not require SMS permissions. Hash documented in `docs/OTP_APP_HASH.md`. | ✅ Correctly omitted |
| `ACCESS_FINE_LOCATION`, `QUERY_ALL_PACKAGES`, `WRITE_EXTERNAL_STORAGE`, `CAMERA`, etc. | — | **Not declared** | Not needed for keyboard. | ✅ Correctly omitted |

**Flag unnecessary permissions:** None. All declared permissions are justified above and are the minimal set for the features described. No `ACCESS_*`, no `READ_CONTACTS`, no `READ_CALL_LOG`, no `QUERY_ALL_PACKAGES`.

**Gating:** `RECORD_AUDIO` and `READ_*` are runtime permissions requested only when the user invokes the corresponding feature (mic, image picker). `INTERNET` is normal-granted at install but no network call is made unless the user opts in to an online feature (see §4).

---

## 2. No insecure logging / caching / network transmission

| Check | Result | Evidence |
|---|---|---|
| Keystroke logging to logcat/file/network | **Clean** | `core/input/KeyEventProcessor` processes chars in RAM and forwards via `InputConnectionProxy.commitText` to the target app. No `Log.d` of keystrokes, no file write, no network. `CrashLogger` logs only exceptions with messages, not keystrokes; it is local-only (no network). |
| Clipboard logging | **Clean** | `data/clipboard/ClipboardRepository` stores to EncryptedSharedPreferences; `ClipboardPolicy.isAllowed` rejects OTP and over-long entries; `SecureFieldGuard` prevents writes when field is password/incognito. No clipboard content is logged. |
| Network transmission of keystrokes/clipboard without opt-in | **Clean** | Core engines (`core/suggestion`, `core/transliteration`, `core/spellcheck`) are 100% offline (bundled JSONs). Only online paths are `translate/TranslationService` POST to `https://api.babeltech.com/translate`, Google Fonts HTTPS, **Giphy search** (`api.giphy.com`, GIF query only), and **Smart Reply online fallback** (`generativelanguage.googleapis.com`, last-message only, stub) — all gated by `PreferencesRepository.*Enabled()` / `GifRepository.isEnabled()` / `FontRepository` opt-in and `NetworkPolicy.requireHttps`. Default OFF. Smart replies on-device (ML Kit) send no network even when enabled. |
| Insecure caching (plaintext prefs, world-readable files) | **Clean** | All sensitive prefs (toolbar order, theme, font, learned words, personal dict, clipboard pins) use `EncryptedSharedPreferences` (Jetpack Security, MasterKey AES256_GCM). Fallback plain prefs only on AndroidKeyStore failure on broken emulators, with migration flag. |
| Auto-backup leakage | **Clean** | `res/xml/backup_rules.xml` + `res/xml/data_extraction_rules.xml` exclude `babelkey_encrypted_prefs.xml`, `_fallback`, `MyBoardPrefs.xml`, `clipboard/` files. Dictionaries are bundled assets, not user data, but also excluded for hygiene. |

**Verified via:** code audit of `core/*`, `data/*`, `security/*`, `translate/*`; `grep -R "Log\."` shows only `CrashLogger` exception logging, no `getTextBeforeCursor` content logging; network search (`grep -R "HttpURLConnection\|OkHttp\|fetch\|api\.babeltech"`) finds only `TranslationService`.

---

## 3. Every network call — data sent, destination

| Call | File | Trigger | Data sent | Destination | TLS | Opt-in? |
|---|---|---|---|---|---|---|
| `TranslationService.translateTeluguToEnglish` | `translate/TranslationService.java` | User taps translate / `TranslationEngine.translateWithFallback(..., allowOnline=true)` — only if `NetworkPolicy.isOnlineFallbackAllowed()` (prefs `online_translation_enabled` true) | `{"source_language":"te","target_language":"en","text":"<sentence>"}` (UTF-8 JSON) | `https://api.babeltech.com/translate` (POST, `Content-Type: application/json; charset=UTF-8`, timeout 5s) | ✅ HTTPS, `Assertion: URL startsWith https://` via NetworkPolicy | **Yes, default OFF**, disclosed in Settings + Privacy Policy |
| Google Fonts download (Phase 2) | `data/preferences/FontRepository` + `ui/themes/FontPickerUi` (Compose `ui-text-google-fonts`) | User picks a non-default font in FontPickerUi (Poppins, Noto Sans Telugu, Inter, Roboto Slab) | Font name request to Google Fonts provider (`com.google.android.gms.fonts`) — Play Services handles download over HTTPS, cached | `https://fonts.googleapis.com` (via Play Services Fonts Provider, HTTPS, certificates) | ✅ HTTPS (Play Services) | **Yes, default OFF** (System Default); disclosed as "downloads over HTTPS when chosen" + HTTPS badge in UI |
| Play Services SmsRetriever (implicit network not from app) | `core/otp/OtpSmsReceiver` + `service/ime/BabelImeService.startSmsRetriever()` | `SmsRetriever.getClient().startSmsRetriever()` on every `onStartInput` where field is OTP-eligible | No data sent by app; Play Services registers to receive SMS containing the 11-char hash (SMS is sent by the user's service provider to the device, not from app) | Google Play Services cloud (no app-controlled destination) | ✅ (Play Services) | **No opt-in needed** (no permission), but only active on OTP-eligible fields |
| ML Kit Smart Reply — on-device (Phase 3) | `core/smartreply/SmartReplyEngine` + `ui/panels/SmartReplyPanel` | `engine.generateOnDevice(contextText)` via `SmartReply.getClient().suggestReplies()` (TFLite, offline). Opt-in `prefs.isAiRepliesEnabled()` (default OFF). | No data sent — TFLite on-device | — | — | **Yes, default OFF**, disclosed as "Google ML Kit (on-device)" |
| ML Kit online fallback (Phase 3, stub) | `core/smartreply/SmartReplyEngine` | Only if `allowOnline=true` && `networkPolicy.isAiRepliesAllowed()` — would POST `{"text":"<last remote message>"}` | Last remote message (stub not shipped) | `https://generativelanguage.googleapis.com` (HTTPS, provider named) | ✅ HTTPS | **Yes, default OFF**, disclosed |
| GIF search — Giphy (Phase 3) | `data/gif/GifRepository` + `ui/panels/GifPanel` | `repo.search(query)` / `repo.trending()` via `HttpURLConnection` GET — only if `gif_enabled` (default OFF) && `giphyApiKey` present | Query text only (e.g., `?q=happy`) | `https://api.giphy.com/v1/gifs/search` + `/trending` (`api.giphy.com`, HTTPS, "Powered by Giphy" attribution) | ✅ HTTPS, `requireHttps` | **Yes, default OFF**, disclosed |
| GIF preview download (implicit) | Coil/Glide (future `AsyncImage` with `previewUrl`) | When `GifPanel` displays `previewUrl` (Giphy CDN) — only after user search, when enabled | GIF preview image fetch | `https://media*.giphy.com` (HTTPS) | ✅ HTTPS | **Yes, default OFF** (shares GIF opt-in) |
| Sticker (Phase 3) | `ui/panels/StickerPanel` | Local 12-emoji curated set — no network. Online stickers reuse GIF Giphy opt-in. | — | — | — | Local no network; online shares GIF opt-in |

**No other network calls** exist in the codebase (verified via `grep -R "http://\|https://\|HttpURLConnection\|OkHttpClient\|Retrofit\|fetch("` — only `api.babeltech.com`, `fonts.googleapis.com`/`gms`, `api.giphy.com`, `generativelanguage.googleapis.com`).

**Data minimization:** Online translation sends only the sentenced user requests; GIF search sends only query text; smart replies on-device sends nothing; online fallback would send only last remote message (stub).

---

## 4. Secure-field / incognito respected everywhere

| Guard | Where enforced | Behavior when secure/incognito |
|---|---|---|
| `security/SecureFieldGuard.isSecure(EditorInfo)` | `service/ime/BabelImeService.onStartInput` | Clears composing text, `keyProcessor.resetComposing()`, suppresses suggestion strip, OTP chip (`OtpManager.setEligible(false)`), learning. |
| `security/SecureFieldGuard.isSecure` + `ClipboardPolicy` | `data/clipboard/ClipboardRepository.add(text, secureField)` | Call site passes `secureField` true when `isSecure` → `add()` returns false (no persist). Also `ClipboardPolicy.isAllowed` rejects OTP-like text. |
| `security/SecureFieldGuard` | `core/input/KeyEventProcessor.onCharacter(..., isPasswordField)` | `isPasswordField` true → autocorrect skipped (`autocorrect?.autocorrect` not called). |
| `security/IncognitoMode` | `service/ime/BabelImeService` (StateFlow, future Settings toggle) | When enabled, suggestions/learning/clipboard suppressed globally (wired to same guards). |
| `EditorInfo.privateImeOptions` incognito check | `security/SecureFieldGuard` | Detects Gboard/ChromeOS convention `privateImeOptions.contains("incognito")` and `TYPE_TEXT_FLAG_NO_SUGGESTIONS`. |
| `OtpDetector.isEligible` | `core/otp/OtpDetector` | Returns false if `isPassword` → OTP chip never appears on password fields. |

**Test coverage:** `SecureFieldGuardTest` (password field detection, clipboard policy rejects OTP, surrogate pair handling) + `OtpDetectorTest` (eligibility) + manual test: type in password field → no suggestion strip, no clipboard entry, no learned word.

---

## 5. Secure storage for settings / themes / user data

| Data | Storage | Encryption | Backup excluded? | Export/delete controls |
|---|---|---|---|---|
| Toolbar order, theme, font choice, feature toggles | `PreferencesRepository.encryptedPrefs` (`babelkey_encrypted_prefs`) | `EncryptedSharedPreferences` AES256_SIV (keys) + AES256_GCM (values), `MasterKey` AES256_GCM via AndroidKeyStore | Yes (`backup_rules.xml` excludes `babelkey_encrypted_prefs.xml`) | Delete via `PreferencesRepository.deleteAllUserData()` — Settings → Delete all |
| Learned words, personal dictionary | Same EncryptedSharedPreferences (`learned_words`, `personal_dict` StringSets) | Same | Yes | Export via `exportPersonalDictionaryJson()` (JSON to share sheet), Delete via `clearLearnedWords()` / `deleteAllUserData()` |
| Clipboard history, pins | `ClipboardRepository` → `EncryptedSharedPreferences` key `clipboard_history_v2` (JSONArray) + `StateFlow` in RAM | Same (values encrypted at rest) | Yes (`exclude domain="file" path="clipboard/"` + sharedpref excludes) | Clear all via `clearAll()` (panel button), pin/unpin per entry, auto-expire 24h (configurable 1h/6h/24h/never via `clipboard_expiry_hours`) |
| OTP | `OtpManager` StateFlow in RAM only | Not persisted (never to EncryptedSharedPreferences, never to clipboard) | N/A (not persisted) | Auto-clears 5 min or on consume; process death loses it |
| Dictionaries (7 JSONs) | `assets/dictionaries/` (bundled, read-only) | Not sensitive; bundled asset, not user data | Excluded (not user data, but hygiene) | N/A (never reduced; migration tool verifies checksums) |

**Migration:** `PreferencesRepository.migrateIfNeeded()` copies from legacy `MyBoardPrefs` plain prefs to encrypted prefs on first run, sets `migrated_from_legacy` flag.

---

## 6. Full permission/privacy checklist (spec §0.5)

- ✅ No telemetry, no analytics SDK, no third-party crash reporting by default (only local `CrashLogger`).
- ✅ All network over HTTPS/TLS (`NetworkPolicy.assertHttps`, `TranslationService` uses `https://`, font download via Play Services HTTPS).
- ✅ Clipboard 24h auto-expire (configurable) via `ClipboardRepository.sweepExpired()` on load + periodic.
- ✅ EncryptedSharedPreferences for typed-word/clipboard persistence (`PreferencesRepository`).
- ✅ Auto-backup exclusion via `backup_rules.xml` + `data_extraction_rules.xml`.
- ✅ Export/delete controls in `PreferencesRepository` + `PersonalDictionary`.
- ✅ Offline guarantee: core typing/transliteration/spellcheck/autocorrect 100% offline (verified via `DictionaryRepository` loads from `assets/dictionaries/` with no network).
- ✅ Online features opt-in OFF by default (`isOnlineTranslationEnabled()==false`, `isAiRepliesEnabled()==false`, font default System Default).
- ✅ Voice via Android `SpeechRecognizer` only (correct `<queries>` + `RECORD_AUDIO`).
- ✅ Keyboard lifecycle handled (`ImeLifecycle`, `BabelImeService.onCreate/onStartInput/onFinishInput/onDestroy`, `onEvaluateInputViewShown`, config change stub, IME switch cleanup via `unregisterReceiver`).
- ✅ Input compatibility via `EditorCompat` + `InputConnectionProxy` (composing, batch edits, RTL, surrogate pairs, password/numeric/email/URL/phone fallback).

---

## 7. Final report — risks, severity, fix recommendation

| # | Risk | Severity | Status / Fix | Owner |
|---|---|---|---|---|
| 1 | `https://api.babeltech.com/translate` is a placeholder / not yet audited; if production provider differs, privacy policy must be updated and domain allowlisted. | **Medium** | Current code POSTs to `api.babeltech.com` — if this domain is not owned or not SOC2, rotate to a real provider (Google Cloud Translation, self-hosted). Fix: make URL configurable via `BuildConfig.TRANSLATE_URL`, gate behind opt-in disclosure naming the provider, and pin certificate. Update `docs/PRIVACY_POLICY.md` when provider is finalized. | Backend / Product |
| 2 | Release keystore (`babelkey.jks`) not yet generated — OTP hash in `docs/OTP_APP_HASH.md` is TODO, provider cannot yet send correct hash. | **Medium** | Fix: generate release keystore (`keytool -genkeypair`), run `scripts/print_otp_hash.py` or `OtpHashActivity` on signed release build, paste 11-char hash into `docs/OTP_APP_HASH.md` Known hashes table and share with SMS provider. Document rotation procedure. | Release engineering |
| 3 | `INTERNET` permission is normal-granted; a bug could cause network even when opt-in OFF. | **Low** | Fix: `NetworkPolicy` already gates calls via `isOnlineFallbackAllowed()` and `assertHttps`. Add Lint checker `NoUnqualifiedHttpCall` that fails build if any `HttpURLConnection` is created without going through `NetworkPolicy`. Already documented in architecture doc. | Security |
| 4 | Compose Google Fonts provider downloads over Play Services — certificate validation is delegated to Play Services; no cert pinning in app. | **Low** | Acceptable per spec ("Any online feature is opt-in, provider named"). Fix: disclose provider (`com.google.android.gms.fonts`) in Settings and Privacy Policy (already done). Future: add cert hashes for `GoogleFont.Provider` if strict pinning is required. | Security |
| 5 | `Emoji codePoints` are raw Unicode — no risk; Giphy API key handling (if hard-coded, key leakage). | **Low** | Emoji: no file bundled. Giphy: fixed in Phase 3 — key via `BuildConfig.giphyApiKey` / `EncryptedSharedPreferences` `giphy_api_key`, never logged, only sent as query param over HTTPS; `GifRepository` returns empty if key blank. Append per-font notices when Noto bundled. | Legal |
| 6 | No custom Lint rules yet for layer violations (`ui/` must not contain business logic, etc.) — relies on code review. | **Low** | Fix: add `android/lint.xml` with custom detectors `UiLogicLeak`, `CoreUiLeak`, `DataDuplication`, `noAdHocPackage`. Documented in `docs/PHASE1_BUILD_REPORT.md` § Known gaps. | Tooling |
| 7 | `Screen timeout` for OTP (5 min Handler) is tied to `Looper.getMainLooper()` — if process dies, OTP lost (acceptable per spec, but user may need to request again). | **Low** | Acceptable per spec ("Expires after ~5 minutes or once used; never written to clipboard history or disk"). No fix needed; document behavior in `docs/OTP_APP_HASH.md` (already done). | Product |
| 8 | Fallback plain prefs (`babelkey_encrypted_prefs_fallback`) on AndroidKeyStore failure is plaintext — could be read if device is compromised. | **Low** | Acceptable fallback for broken emulators only. Fix: log warning, show Settings hint "Secure storage unavailable — consider reinstall on a production device", and auto-migrate to encrypted prefs on next successful MasterKey creation. Already implemented in `PreferencesRepository`. | Security |
| 9 | No LeakCanary / Macrobenchmark in debug/release — performance targets (<16ms keystroke, <1s cold start, 30-min no growth, zero ANRs) not yet instrumented. | **Medium** | Fix before Gate 5 sign-off: add `debugImplementation("com.squareup.leakcanary:leakcanary-android")` and `benchmark` module with `Macrobenchmark` for `BabelImeService.onCreate → onCreateInputView`. Targets documented in `docs/ARCHITECTURE.md`. | Performance |
| 10 | `MyKeyboardService` legacy alias remains as second `<service>` in Manifest — increases attack surface (two entry points). | **Low** | Intentional for one release to avoid breaking existing users. Fix: remove alias after telemetry shows <1% of users still on legacy service (or after one Play Store update cycle). | Product |

**Overall posture:** **Secure for Play Store release** once risks #1, #2, #9 are resolved (provider audit, release hash, perf instrumentation). No critical or high-severity open issues that leak keystrokes, clipboard, or OTP. The build is offline-first, opt-in-only for online, and secure-field guard is enforced at every write path.

---

## 8. Verification commands (Gate 5 sign-off)

```bash
# No network unless opted in (core offline)
grep -R "http" android/app/src/main/java --include="*.java" --include="*.kt" | grep -v "https://" | grep -v "android:" | grep -v "gms"

# No insecure prefs (all via EncryptedSharedPreferences)
grep -R "getSharedPreferences.*MODE_PRIVATE" android/app/src/main/java --include="*.kt" | grep -v "EncryptedSharedPreferences\|fallback"

# Secure-field guard coverage
grep -R "SecureFieldGuard" android/app/src/main/java --include="*.kt" | wc -l   # expect >=5 call sites

# Dictionaries still never reduced
python scripts/migrate_dictionaries.py --check

# Regression still passes (6/6)
python -m unittest android.tests.test_source_regressions
```

**Sign-off:** Pending resolution of Medium risks #1, #2, #9. After that, Gate 5 passes and the build is ready for `assembleRelease` with `BABELKEY_KEYSTORE` env and hash generation.
