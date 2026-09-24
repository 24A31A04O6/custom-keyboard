#!/usr/bin/env python3
"""
Dictionary Migration / Import Tool — BabelKey

Per Master Spec Part 1 + Gate 1: copies dictionaries from
  android/app/src/main/res/raw/*.json
to
  android/app/src/main/assets/dictionaries/
in full (never reduced), verifies checksums, validates shape, and
writes DictionaryManifest.json + checksums.sha256.

This is the Phase-1 deliverable for dictionary migration. Run:
  python scripts/migrate_dictionaries.py              # verify + copy
  python scripts/migrate_dictionaries.py --verify-only
  python scripts/migrate_dictionaries.py --check      # CI: fail if any file reduced
"""
import argparse, hashlib, json, sys, pathlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
SRC = ROOT / "android/app/src/main/res/raw"
DST = ROOT / "android/app/src/main/assets/dictionaries"

# Gate 1 checksums — source of truth snapshot before any edits
EXPECTED_SHA256 = {
    "translations.json":      "77ee7b79582e54fcded6447c5a1c8da61a950026737e8845012c0d8e5c89a5ea",
    "autocorrect_dict.json":  "dc1993accc1027a627c513a148ce6eec88d2ac678a318f3de7fd5974bd956b52",
    "tel_eng_dict.json":      "0b23934af311b6539afe4a30c50ec8593ba1a5da0f04ee5ac88e83ccebc72f84",
    "telugu_dict.json":       "e05b1c1034bc457c8a39cf2aa04ec688e650bca3873b7635941d0633fca4ac42",
    "eng_dict.json":          "60792451300d1948489f3ed1dd5f616ad202527619941a8f16b2a46e9217dd25",
    "suggestions_dict.json":  "efd90b29d447ca42f8e7e71ed8b653280a02ec5fc815acde2c22fcbea6174c88",
    "emoji_suggestions.json": "36cd8a60ec2926b278ddf0516a86f6be6a50dabf6408d2263daefb9f949933e0",
}
EXPECTED_COUNTS = {
    "translations.json": 1199,
    "autocorrect_dict.json": 171,
    "tel_eng_dict.json": 1757,
    "telugu_dict.json": 62,
    "eng_dict.json": 2010,
    "suggestions_dict.json": 19,
    "emoji_suggestions.json": 29,
}

def sha256(p: pathlib.Path) -> str:
    return hashlib.sha256(p.read_bytes()).hexdigest()

def validate_shape(path: pathlib.Path):
    obj = json.loads(path.read_bytes().decode("utf-8"))
    assert isinstance(obj, dict), f"{path.name}: expected JSON object"
    if all(isinstance(v, str) for v in obj.values()):
        kind = "kv"
    elif all(isinstance(v, list) for v in obj.values()):
        kind = "prefix"
        for k, lst in obj.items():
            assert lst, f"{path.name}: empty list for prefix '{k}'"
            for w in lst:
                assert isinstance(w, str) and w.strip(), f"{path.name}: empty word in prefix '{k}'"
    else:
        raise AssertionError(f"{path.name}: mixed value types — expected all str or all list")
    return kind, len(obj)

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--verify-only", action="store_true", help="only verify, do not copy")
    ap.add_argument("--check", action="store_true", help="CI check: never reduced (new >= old)")
    args = ap.parse_args()

    ok = True
    for name, exp_sha in EXPECTED_SHA256.items():
        src = SRC / name
        if not src.exists():
            print(f"FAIL: missing source {src}", file=sys.stderr)
            ok = False; continue
        got = sha256(src)
        if got != exp_sha:
            print(f"FAIL: checksum mismatch {name}\n  expected {exp_sha}\n  got      {got}", file=sys.stderr)
            print("  -> Gate 1 snapshot violated. Re-approve Gate 1 if this change is intentional.", file=sys.stderr)
            ok = False
        else:
            kind, cnt = validate_shape(src)
            exp_cnt = EXPECTED_COUNTS[name]
            if cnt < exp_cnt:
                print(f"FAIL: {name} reduced: {cnt} < expected {exp_cnt} (never reduced rule)", file=sys.stderr)
                ok = False
            else:
                status = "OK" if cnt == exp_cnt else f"OK (expanded {exp_cnt} -> {cnt})"
                print(f"{status}: {name} [{kind}] entries={cnt} sha256={got[:8]}…")

    if not ok:
        sys.exit(1)

    if args.verify_only or args.check:
        print("All dictionaries verified (no copy).")
        return

    DST.mkdir(parents=True, exist_ok=True)
    manifest = {}
    for name in EXPECTED_SHA256:
        src = SRC / name
        dst = DST / name
        dst.write_bytes(src.read_bytes())
        info_sha = sha256(dst)
        kind, cnt = validate_shape(dst)
        manifest[name] = {"bytes": dst.stat().st_size, "sha256": info_sha, "entries": cnt, "kind": kind}
        print(f"Copied {name} -> {dst.relative_to(ROOT)}")

    with open(DST / "DictionaryManifest.json", "w") as f:
        json.dump(manifest, f, indent=2)
    with open(DST / "checksums.sha256", "w") as f:
        for name, info in manifest.items():
            f.write(f"{info['sha256']}  {name}\n")
    print(f"Wrote {DST/'DictionaryManifest.json'} and {DST/'checksums.sha256'}")

if __name__ == "__main__":
    main()
