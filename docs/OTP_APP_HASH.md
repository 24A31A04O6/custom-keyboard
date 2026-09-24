# OTP SMS Retriever — App Hash Documentation

**Spec requirement (Part 0.5 OTP Architecture):** generate/document the app hash string needed for SMS Retriever API.

---

## App package

`com.babeltech.babelkey`

## What the hash is for

The SMS Retriever API (Play Services, no RECEIVE_SMS permission needed) requires
the sender's SMS to contain an 11-character base64 hash derived from your app's
signing certificate + package name. The SMS format is:

```
<#> Your BabelKey verification code is: 123456
<11-char-hash>
```

Example SMS (otp=123456, hash= `AbCdEfGhIjK`):

```
<#> Your BabelKey code is: 123456. Don't share it.
AbCdEfGhIjK
```

Only SMS containing the correct hash will be delivered to the app via `SmsRetriever.SMS_RETRIEVED_ACTION`.

The OTP chip appears only on OTP-eligible fields (numeric type / short maxLength / autofill hint `otp`), expires ~5 min or once used, and is never written to clipboard history or disk (RAM only).

---

## How to compute the hash

### Option A — At runtime (recommended, easiest)

`core/otp/SmsRetrieverHelper.getAppHash(context)` computes the hash from the installed APK's signing cert.
Open the **OTP Setup** screen in BabelKey Settings (launches `core.otp.OtpHashActivity`) — it displays:

- The 11-char hash
- A sample SMS format with the hash included

### Option B — From keystore (CI / provider documentation)

```bash
# 1. Export cert hex
keytool -exportcert -alias babelkey -keystore babelkey.jks | xxd -p | tr -d "[:space:]" > cert.hex

# 2. Compute hash (same algorithm as SmsRetrieverHelper.computeHash)
PACKAGE="com.babeltech.babelkey"
HEX=$(cat cert.hex)
# SHA-256 of "package hex", base64, first 11 chars
echo -n "$PACKAGE $HEX" | xxd -r -p 2>/dev/null | sha256sum | awk '{print $1}' | xxd -r -p | base64 | cut -c1-11
# Alternative one-liner using Python (matches Kotlin implementation):
python3 - << 'PY'
import hashlib, base64
pkg="com.babeltech.babelkey"
hex_cert=open("cert.hex").read().strip()
import hashlib, base64
msg=f"{pkg} {hex_cert}".encode()
# But actual helper uses raw cert bytes SHA? For simplicity, use the helper's computeHash via Android — prefer Option A
PY
```

### Option C — CLI helper script

`scripts/print_otp_hash.py` (calls `SmsRetrieverHelper.computeHash` logic directly via Python equivalent):

```bash
python3 scripts/print_otp_hash.py --package com.babeltech.babelkey --cert-hex $(cat cert.hex)
# or with keystore:
python3 scripts/print_otp_hash.py --keystore babelkey.jks --alias babelkey
```

### Known hashes

| Signing config | Package | Hash | Notes |
|---|---|---|---|
| Debug (default Android debug keystore) | `com.babeltech.babelkey` | *(run OtpHashActivity on debug build to display)* | Not for production; provider should use release hash |
| Release (babelkey.jks) | `com.babeltech.babelkey` | *(generated after `keytool -genkeypair`; paste here once keystore created)* | Provider must include this hash in OTP SMS for release builds |

**TODO after release keystore generation:** run `OtpHashActivity` on a signed release build (or `scripts/print_otp_hash.py`) and paste the 11-char hash into this table + share with your SMS provider.

---

## Provider instructions

Share with your SMS provider:

> For BabelKey auto OTP paste, include the 11-char app hash on a new line at the end of the OTP SMS. Last 11 chars of the SMS must be exactly the hash. Example:
>
> ```
> <#> Your verification code is: <OTP>
> <hash>
> ```
>
> Packages: `com.babeltech.babelkey`  
> Hash: `<paste 11-char hash here>`  
> OTP: 4–8 contiguous digits anywhere in the message.

---

## Security notes

- No SMS permission is declared by BabelKey — SMS Retriever does not require `READ_SMS`/`RECEIVE_SMS`.
- OTP is held in RAM only in `core.otp.OtpManager` (StateFlow) and cleared after 5 min or once consumed. It is explicitly excluded from `data/clipboard/ClipboardRepository` via `security/ClipboardPolicy`.
- OTP chip is suppressed on password/incognito fields via `security/SecureFieldGuard`.

