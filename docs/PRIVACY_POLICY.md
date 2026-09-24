# BabelKey Privacy Policy (Phase 1 — matches actual code behavior)

**Last updated:** 2026-09-24  
**App:** BabelKey (com.babeltech.babelkey), Android IME  
**Contact:** See repo owner (24A31A04O6/custom-keyboard)

---

## Summary

BabelKey is an **offline-first** keyboard. Core typing, transliteration/translation
(English→Romanized Telugu), spellcheck, and autocorrect work **100% offline** using
bundled dictionaries. No keystrokes, clipboard contents, or learned words are sent
to any server unless you explicitly opt in to an online feature (and even then,
only over HTTPS, with the provider named).

---

## What we collect (and what we don't)

| Data | Collected? | Where stored | Sent off device? |
|---|---|---|---|
| Keystrokes while typing | No — processed in RAM and via InputConnection to the current app only | Never stored | Never |
| Personal dictionary / learned words | Only words you type and we learn locally | EncryptedSharedPreferences on device | Never (no cloud sync in v1) |
| Clipboard history | Only if you copy while using a non-password field | EncryptedSharedPreferences + in-memory; auto-expires after 24h (configurable) | Never |
| Password / incognito fields | **Never** — suppressed via SecureFieldGuard | Not stored, not learned, not suggested, not clipboarded | Never |
| Telemetry / analytics / crash reporting | **None by default** | — | No. If added later, it will be opt-in, self-hosted or named, disclosed here |
| OTP codes | Held in RAM only, shown as a chip on OTP-eligible fields, expires after ~5 min or once used | Never written to clipboard history or disk | Never |

---

## Online features (opt-in, default OFF)

| Feature | Provider | What is sent | When |
|---|---|---|---|
| Online translation fallback | `api.babeltech.com` (current) — HTTPS only | The sentence you request to translate | Only if you enable "Online translation" in Settings |
| Voice typing | Android's built-in `SpeechRecognizer` | Audio to Google's speech service via the OS | Only when you tap the mic and grant RECORD_AUDIO |
| AI Smart Replies (Phase 3) | ML Kit on-device or named HTTPS API | Context text (if enabled) | Only if you opt in in Settings |
| GIF search / Google Fonts (Phase 2/3) | Provider not yet chosen | — | Will be disclosed before enablement |

You can turn any online feature off at any time in Settings. Turning it off
stops all network calls for that feature.

---

## Permissions

| Permission | Why |
|---|---|
| `RECORD_AUDIO` | Voice typing via Android SpeechRecognizer (only when you tap mic) |
| `INTERNET` | Online translation / GIF / AI — only if you opt in. Core keyboard works offline without it |
| `READ_EXTERNAL_STORAGE` (maxSdk 32) / `READ_MEDIA_IMAGES` (33+) | Picking a custom keyboard background image in Theme picker (only when you open the picker) |
| `BIND_INPUT_METHOD` (service) | Required by Android to bind the IME; not a runtime permission |
| `queries: SpeechRecognizer` | Android 11+ package visibility for voice input detection |

No `RECEIVE_SMS` is needed for OTP: BabelKey uses SMS Retriever API (Play Services)
which does not require SMS permission — an 11-char app hash is embedded in the
SMS sent by your service provider.

---

## Storage & encryption

- Settings, learned words, personal dictionary, clipboard pins are stored via
  **Jetpack Security EncryptedSharedPreferences** (AES256-GCM, MasterKey in AndroidKeyStore).
- Clipboard auto-expires (default 24h; you can choose 1h/6h/24h/never in Settings).
- `allowBackup` is enabled but **sensitive prefs and clipboard files are excluded**
  via `backup_rules.xml` / `data_extraction_rules.xml` (see source).

---

## Your controls

- **Export** personal dictionary / learned words / toolbar order / theme as JSON (Settings → Personal Dictionary → Export).
- **Delete** all learned words / personal dict / clipboard history (same screen).
- **Clipboard expiry:** Settings → Clipboard → expiry choice.
- **Disable** any online feature in Settings — default is OFF.

---

## Children, retention, changes

No special handling for children. Data retention is on-device only until you delete
it or it auto-expires. This policy will be updated when Phase 2/3 online features
are added; the policy text is versioned in the repository (`docs/PRIVACY_POLICY.md`)
and matched against actual code behavior at release time.

---

## Verification

At build time, `data/dictionaries/MigrationTool` verifies bundled dictionaries
against Gate-1 checksums, and `security/SecureFieldGuard` instrumentation tests
verify zero suggestion/learning/clipboard activity on password fields.

