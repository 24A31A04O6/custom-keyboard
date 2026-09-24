#!/usr/bin/env python3
"""
print_otp_hash.py — compute SMS Retriever app hash (11-char) from package + cert.

Mirrors core/otp/SmsRetrieverHelper.computeHash logic in Python for CI/provider use.

Usage:
  python3 scripts/print_otp_hash.py --package com.babeltech.babelkey --cert-hex <hex>
  python3 scripts/print_otp_hash.py --keystore babelkey.jks --alias babelkey [--store-pass PASS]

If --keystore is given, the script runs keytool to extract the cert hex.
Otherwise --cert-hex should be the hex of the signing cert (from keytool -exportcert | xxd -p).
"""
import argparse, base64, hashlib, subprocess, sys

def compute_hash(package_name: str, cert_hex: str) -> str:
    # Aligns with SmsRetrieverHelper.computeHash: SHA-256(package + " " + hex) -> base64 -> 11 chars
    # Note: SmsRetrieverHelper uses SHA-256 of "package hex" string bytes; this mirrors it.
    app_info = f"{package_name} {cert_hex}".encode("utf-8")
    digest = hashlib.sha256(app_info).digest()
    b64 = base64.b64encode(digest).decode("utf-8")
    # SMS Retriever hash is first 11 chars of base64 without padding
    b64 = b64.replace("=", "")
    return b64[:11]

def cert_hex_from_keystore(keystore: str, alias: str, store_pass: str = "") -> str:
    cmd = ["keytool", "-exportcert", "-alias", alias, "-keystore", keystore, "-rfc"]
    if store_pass:
        cmd += ["-storepass", store_pass]
    # Use -exportcert without -rfc piped to xxd for hex; but -rfc gives PEM, easier to use non-rfc via xxd
    cmd = ["keytool", "-exportcert", "-alias", alias, "-keystore", keystore]
    if store_pass:
        cmd += ["-storepass", store_pass]
    proc = subprocess.run(cmd, capture_output=True)
    if proc.returncode != 0:
        print(f"keytool failed: {proc.stderr.decode()}", file=sys.stderr)
        sys.exit(1)
    return proc.stdout.hex()

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--package", default="com.babeltech.babelkey", help="App package name")
    ap.add_argument("--cert-hex", help="Hex of signing cert (from keytool | xxd -p)")
    ap.add_argument("--keystore", help="Path to keystore (alternative to --cert-hex)")
    ap.add_argument("--alias", default="babelkey", help="Key alias (with --keystore)")
    ap.add_argument("--store-pass", default="", help="Keystore password (if needed)")
    args = ap.parse_args()
    if args.keystore:
        cert_hex = cert_hex_from_keystore(args.keystore, args.alias, args.store_pass)
    elif args.cert_hex:
        cert_hex = args.cert_hex.strip().lower()
    else:
        ap.error("Provide --cert-hex or --keystore")
        return
    h = compute_hash(args.package, cert_hex)
    print(h)
    # Also print sample SMS
    print(f"\nSample SMS:\n<#> Your BabelKey verification code is: 123456\n{h}", file=sys.stderr)

if __name__ == "__main__":
    main()
