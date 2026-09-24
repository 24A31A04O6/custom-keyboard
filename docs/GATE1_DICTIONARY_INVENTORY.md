# GATE 1 — Dictionary Inventory + Terminology Confirmation

**Repo:** `24A31A04O6/custom-keyboard` (branch `arena/01a0d2b7-custom-keyboard`, base `5ab899f`)  
**Date:** 2026-09-24 (UTC)  
**Scope per Part 0.5:** Locate both dictionary files, report path/format/count/samples/checksum, and confirm terminology ("me" → "nenu" = translation vs transliteration). Do NOT proceed until confirmed.

---

## 1. Dictionary file locations

All dictionaries live under a single directory in the cloned repo:

```
android/app/src/main/res/raw/
```

No other JSON/CSV/SQLite dictionary files exist elsewhere in the repository (verified via recursive walk excluding `raw/`; no additional candidates found).

Seven JSON files were found in `raw/`. Two are the **primary** Part-1 dictionaries (full copy, never reduced); the others are **supporting prefix/autocomplete maps** that the engine loads at runtime. Full inventory below.

---

## 2. Primary dictionaries (Part 1 — must be copied in full, may be expanded, never reduced)

### A) `translations.json` — English → Romanized Telugu (the "me" → "nenu" dictionary)

| Field | Value |
|-------|-------|
| **File path** | `android/app/src/main/res/raw/translations.json` |
| **Format** | JSON object: `string → string` (English word/phrase → Romanized Telugu word/phrase) |
| **Total entries** | **1,199** key-value pairs |
| **File size** | 27,667 bytes |
| **MD5** | `23a0314e13324b027632e5a5c65e7838` |
| **SHA-256** | `77ee7b79582e54fcded6447c5a1c8da61a950026737e8845012c0d8e5c89a5ea` |

**5 sample entries (first 5):**

```json
"i": "nenu"
"we": "memu"
"you": "nuvvu"
"he": "athanu"
"she": "ame"
```

**5 more representative samples (including multi-word phrases):**

```json
"hello": "namaskaram"
"thank you": "dhanyavadalu"
"father": "nanna"
"where": "ekkada"
"there": "akkada"
```

**Critical note on spec example `"me" → "nenu"`:**

- In the actual file, `"me"` → **`"nannu"`** (accusative "me"), not `"nenu"`.
- `"nenu"` is the value for **`"i"`** (first-person singular nominative).
- Full excerpt around `me`:

```json
"me": "nannu",
"us": "mamalni",
...
"i": "nenu"
```

This matters for the terminology ruling below.

**Encoding:** UTF-8, **Latin/Roman only** — zero entries contain Telugu Unicode script (U+0C00–U+0C7F). Verified via scan of all 1,199 values: **0 values contain Telugu script**; exhaustive repo scan confirms no Telugu script anywhere in the codebase. All values are romanized (e.g., `nenu`, `namaskaram`, `dhanyavadalu`), not native script (`నేను`).

### B) `autocorrect_dict.json` — Misspelling → Correction (the "namasthe" → "namaste" dictionary)

| Field | Value |
|-------|-------|
| **File path** | `android/app/src/main/res/raw/autocorrect_dict.json` |
| **Format** | JSON object: `string → string` (misspelled Romanized Telugu → correct Romanized Telugu) |
| **Total entries** | **171** key-value pairs |
| **File size** | 4,129 bytes |
| **MD5** | `5e27b3a85fc6661440d7dccb68fda658` |
| **SHA-256** | `dc1993accc1027a627c513a148ce6eec88d2ac678a318f3de7fd5974bd956b52` |

**5 sample entries:**

```json
"chustru": "chustharu"
"chestru": "chestharu"
"teestru": "theestharu"
"vestru": "vestharu"
"mastru": "mastharu"
```

**5 more representative samples (different patterns):**

```json
"bagundhi": "bagundi"
"ledhu": "ledu"
"kaadhu": "kaadu"
"ekada": "ekkada"
"namaskrm": "namaskaram"
```

**Note on spec example `"namasthe" → "namaste"`:** Neither `"namasthe"` nor `"namaste"` appears as a key or value in the current file. Closest entries are the `namaskaram` typo variants above. The spec example is illustrative, not literal; the real file corrects Romanized Telugu verb/word-form variants, not English "namaste" romanization variants. The engine should support the file as-is and allow adding `"namasthe" → "namaste"` if desired (expansion permitted).

**Encoding:** UTF-8, Latin/Roman only (same as above, verified zero Telugu script).

---

## 3. Supporting dictionaries (autocomplete / suggestion indexes — loaded by `SuggestionManager`)

These are **prefix maps** (`string prefix → string[] word list`) used for suggestion-strip autocomplete. They are derived from the primary word lists but are required by the current engine; per Part 1's "never reduced" rule, they must also be migrated in full.

### C) `tel_eng_dict.json` — Romanized Telugu autocomplete index

| Field | Value |
|-------|-------|
| **Path** | `android/app/src/main/res/raw/tel_eng_dict.json` |
| **Format** | JSON object: `prefix → string[]` (e.g., `"me" → ["meda","mee","meeru",...]`) |
| **Total prefixes (keys)** | **1,757** |
| **Unique words (across all value arrays)** | **1,313** |
| **File size** | 81,265 bytes |
| **MD5** | `023b3e46936b6aac6f5e2a85779d83a6` |
| **SHA-256** | `0b23934af311b6539afe4a30c50ec8593ba1a5da0f04ee5ac88e83ccebc72f84` |

Samples:

```json
"a": ["aadivaram","aaduko","aaduthunnanu","aagandi",...]
"me": ["meda","mee","meeda","meeru","meeruki","meerukosam",...]
"ma": ["maa","maadi","maaku","maanam","maata",...]
"nenu": ["nenu"]
"man": ["manager","manasu","mancham","manchi",...]
```

### D) `telugu_dict.json` — Smaller Romanized Telugu prefix map (subset of tel_eng)

| Field | Value |
|-------|-------|
| **Path** | `android/app/src/main/res/raw/telugu_dict.json` |
| **Format** | JSON object: `prefix → string[]` |
| **Total prefixes** | **62** |
| **Unique words** | **163** |
| **File size** | 3,089 bytes |
| **MD5** | `cd78cdffcd4537d0c073c599c5b87ca8` |
| **SHA-256** | `e05b1c1034bc457c8a39cf2aa04ec688e650bca3873b7635941d0633fca4ac42` |

Samples:

```json
"n": ["nenu","naaku","nuvvu","naanna","nijam","nidra"]
"me": ["meeru","meeruki","meerukosam"]
"ch": ["chesanu","chestanu","chusanu","chustharu","chestharu"]
"tel": (not a key — uses "te","telugu" etc.)
"sa": ["santosham","sahaayam","samayam","sare"]
```

### E) `eng_dict.json` — English autocomplete index

| Field | Value |
|-------|-------|
| **Path** | `android/app/src/main/res/raw/eng_dict.json` |
| **Format** | JSON object: `prefix → string[]` |
| **Total prefixes** | **2,010** |
| **Unique words** | **1,428** |
| **File size** | 85,750 bytes |
| **MD5** | `1c682d0dacaa06a8042def93a19e6277` |
| **SHA-256** | `60792451300d1948489f3ed1dd5f616ad202527619941a8f16b2a46e9217dd25` |

Samples:

```json
"a": ["a","about","above","accident","action",...]
"th": ["than","thank","thanks","that","the","their",...]
"hel": ["held","hell","hello","help"]
"wor": ["word","work","worker","world","worry"]
"you": ["you","young","your","yours","yourself","youth"]
```

### F) `suggestions_dict.json` — Small English prefix map (19 prefixes, 70 unique words)

| Field | Value |
|-------|-------|
| **Path** | `android/app/src/main/res/raw/suggestions_dict.json` |
| **Format** | JSON object: `prefix → string[]` |
| **Total prefixes** | **19** |
| **Unique words** | **70** |
| **File size** | 856 bytes |
| **MD5** | `cb216139548f913d3fb4681d77ba8d36` |
| **SHA-256** | `efd90b29d447ca42f8e7e71ed8b653280a02ec5fc815acde2c22fcbea6174c88` |

Samples:

```json
"hel": ["hello","help","held"]
"wor": ["world","word","work","worry"]
"the": ["the","them","then","they","there"]
"goo": ["good","google","goodbye","goodness"]
"kno": ["know","knowledge","known","knows"]
```

### G) `emoji_suggestions.json` — Keyword → emoji string

| Field | Value |
|-------|-------|
| **Path** | `android/app/src/main/res/raw/emoji_suggestions.json` |
| **Format** | JSON object: `keyword → string` (space-separated emoji) |
| **Total entries** | **29** |
| **File size** | 996 bytes |
| **MD5** | `019752535e5d20d333c1ca6f17431837` |
| **SHA-256** | `36cd8a60ec2926b278ddf0516a86f6be6a50dabf6408d2263daefb9f949933e0` |

Samples:

```json
"happy": "😊 😄 🎉 🥳 😁"
"love": "❤️ 🥰 😍 💕 💖"
"thanks": "🙏 😊 ❤️ 👍"
"hello": "👋 😊 🤗 ✨"
"food": "🍕 🍔 🍛 😋 🤤"
```

---

## 4. Checksums — record of source data before copying/editing

| File | Bytes | MD5 | SHA-256 |
|------|-------|-----|---------|
| `translations.json` | 27667 | `23a0314e13324b027632e5a5c65e7838` | `77ee7b79582e54fcded6447c5a1c8da61a950026737e8845012c0d8e5c89a5ea` |
| `autocorrect_dict.json` | 4129 | `5e27b3a85fc6661440d7dccb68fda658` | `dc1993accc1027a627c513a148ce6eec88d2ac678a318f3de7fd5974bd956b52` |
| `tel_eng_dict.json` | 81265 | `023b3e46936b6aac6f5e2a85779d83a6` | `0b23934af311b6539afe4a30c50ec8593ba1a5da0f04ee5ac88e83ccebc72f84` |
| `telugu_dict.json` | 3089 | `cd78cdffcd4537d0c073c599c5b87ca8` | `e05b1c1034bc457c8a39cf2aa04ec688e650bca3873b7635941d0633fca4ac42` |
| `eng_dict.json` | 85750 | `1c682d0dacaa06a8042def93a19e6277` | `60792451300d1948489f3ed1dd5f616ad202527619941a8f16b2a46e9217dd25` |
| `suggestions_dict.json` | 856 | `cb216139548f913d3fb4681d77ba8d36` | `efd90b29d447ca42f8e7e71ed8b653280a02ec5fc815acde2c22fcbea6174c88` |
| `emoji_suggestions.json` | 996 | `019752535e5d20d333c1ca6f17431837` | `36cd8a60ec2926b278ddf0516a86f6be6a50dabf6408d2263daefb9f949933e0` |

Recomputed with `md5sum` + `sha256sum` on raw bytes (Python `hashlib`). All files are UTF-8 JSON.

---

## 5. Terminology correction — "me" → "nenu"

**Spec says:** `"me" → "nenu"` is *TRANSLATION* if written in English letters, or *TRANSLITERATION* only if rendered in actual Telugu script (`నేను`).

**Finding: The repository implements TRANSLATION (romanized), not transliteration.**

Evidence:

1. **No Telugu Unicode in any dictionary or source file.** Exhaustive scan for codepoints U+0C00–U+0C7F across `android/app/src/main/res/raw/*.json` and all `*.java`/`*.xml` finds **zero matches**. Every value is Latin letters (e.g., `nenu`, `nannu`, `namaskaram`, `chustharu`), never `నేను`.
2. **`translations.json` is an English→Romanized-Telugu translation dictionary** (1,199 entries), not a script-conversion table. It maps English words to their Telugu equivalents written in Roman letters: `hello → namaskaram`, `thank you → dhanyavadalu`, `i → nenu`. This is by definition **translation** (semantic mapping), even though the target happens to be phonetically spelled.
3. **True transliteration** would be a reversible script mapping, e.g., Roman `nenu` → Telugu script `నేను`, or ideally character-level rules (`na` → `న`, etc.). No such table exists in the repo.
4. The online fallback (`translate/TranslationService.java`) currently POSTs to `https://api.babeltech.com/translate` with `source_language: te, target_language: en` and expects Telugu Unicode in the payload comment ("Ensure correct UTF-8 encoding for Telugu Unicode script"), suggesting the *intended* future path is Unicode — but the bundled offline dictionaries are all Roman.

**Conclusion & recommendation:**

- Describe the current offline engine as a **romanized translation + autocorrect engine**, not a transliteration engine.
- If a future milestone requires real transliteration (Roman → `నేను`), it must be built as a **new transliteration layer** (ISO 15919-style or custom mapping) on top of the existing romanized dictionary, and the spec language should be updated to distinguish:
  - **Translation (EN → romanized Telugu):** `translations.json` (offline, already exists)
  - **Transliteration (romanized Telugu → Telugu script):** not yet present; requires new mapping table and is not satisfied by current files.
- For Gate 2/Phase 1, I propose keeping the engine name `transliteration/` (to match the requested folder structure) but documenting inside that it currently performs **romanized translation** with a pluggable script-converter stub for future Unicode output. Alternatively, rename to `translation/` if you prefer strict accuracy — awaiting your call.

---

## 6. What will be migrated (and the "never reduced" guarantee)

For **Part 1** rebuild, the following will be copied **in full** into `data/dictionaries/` (new layered architecture):

- `translations.json` (1,199 pairs) — primary translation dict
- `autocorrect_dict.json` (171 pairs) — primary spellcheck dict
- `tel_eng_dict.json` (1,757 prefixes / 1,313 words) + `telugu_dict.json` + `eng_dict.json` + `suggestions_dict.json` + `emoji_suggestions.json` — supporting indexes (required for current `SuggestionManager` behavior; also never reduced)

A migration/import tool will verify checksums before and after copy; any expansion will be additive (new entries appended, existing entries preserved).

---

## 7. Licensing snapshot (preliminary, for Gate 2)

No `LICENSE`/`NOTICE` file exists at repo root today. Identified components needing attribution before any code reuse:

- Current code: proprietary `com.babeltech.babelkey` (no license header found)
- Planned reference: AOSP LatinIME (Apache 2.0) — architecture reference only, fresh implementation
- Dependencies: `androidx.appcompat`, `material`, `recyclerview`, `com.google.mlkit:smart-reply` (17.0.4), `org.json` — each to be inventoried with full license + attribution in Gate 2's `NOTICE` file

Full table will be delivered at Gate 2 before any third-party code is integrated.

---

## 8. Approval gate

**Gate 1 is ready for your review.** Please confirm:

- [ ] The 7-file inventory and counts above match your expectation (especially that the two Part-1 dictionaries are `translations.json` + `autocorrect_dict.json`)
- [ ] You agree with the terminology finding (current data is **translation / romanized**, not Unicode transliteration) and the proposed naming for `core/transliteration/`
- [ ] Checksums are recorded as the source-of-truth snapshot

Reply **"Gate 1 approved — proceed to architecture plan"** (or note corrections) and I will deliver Gate 2 (architecture/folder plan mapped onto Phase 1) before writing any feature code.

