#!/usr/bin/env python3
"""Cross-check all R.<type>.<name> references in Java against res/ files."""
import os, re, sys, glob

ROOT = sys.argv[1] if len(sys.argv) > 1 else "app/src/main"
JAVA = os.path.join(ROOT, "java")
RES = os.path.join(ROOT, "res")

refs = {}  # type -> set(names)
ref_re = re.compile(r"\bR\.(id|drawable|color|xml|layout|string|array|raw|style|mipmap|dimen)\.([A-Za-z0-9_]+)")
for f in glob.glob(os.path.join(JAVA, "**", "*.java"), recursive=True):
    src = open(f, encoding="utf-8", errors="replace").read()
    for m in ref_re.finditer(src):
        refs.setdefault(m.group(1), set()).add(m.group(2))

# Collect existing resources
existing = {}
for d in os.listdir(RES):
    full = os.path.join(RES, d)
    if not os.path.isdir(full):
        continue
    rtype = {"layout": "layout", "drawable": "drawable", "drawable-v24": "drawable",
             "values": "values", "values-night": "values", "xml": "xml",
             "color": "color", "raw": "raw", "mipmap-anydpi-v26": "mipmap",
             "mipmap-hdpi": "mipmap", "mipmap-mdpi": "mipmap", "mipmap-xhdpi": "mipmap",
             "mipmap-xxhdpi": "mipmap", "mipmap-xxxhdpi": "mipmap",
             "anim": "anim"}.get(d)
    if rtype is None:
        continue
    for fn in os.listdir(full):
        if fn.endswith((".xml", ".png", ".jpg", ".webp", ".9.png", ".wav", ".json")):
            name = os.path.splitext(fn)[0]
            existing.setdefault(rtype, set()).add(name)

# values files define colors, strings, styles, arrays, dimens
values_names = {"color": set(), "string": set(), "style": set(), "array": set(), "dimen": set(), "bool": set()}
vfile_re = re.compile(r"<(color|string|style|array|dimen|bool)([^>]*)>(.*?)</\1>", re.S)
sfile_re = re.compile(r"<(color|string|style|array|dimen|bool)\s+([^>]*)/>")
for vf in glob.glob(os.path.join(RES, "values*", "*.xml")):
    src = open(vf, encoding="utf-8", errors="replace").read()
    for m in vfile_re.finditer(src):
        tag = m.group(1)
        nm = re.search(r'name="([^"]+)"', m.group(2))
        if nm:
            values_names.setdefault(tag, set()).add(nm.group(1))
    for m in sfile_re.finditer(src):
        tag = m.group(1)
        nm = re.search(r'name="([^"]+)"', m.group(2))
        if nm:
            values_names.setdefault(tag, set()).add(nm.group(1))

def lookups(rtype, names):
    pool = set(existing.get(rtype, set()))
    for v in ("color", "string", "style", "array", "dimen", "bool"):
        if v in values_names:
            pool |= values_names[v]
    # android: builtins
    pool |= {"id", "parent"}
    return pool

problems = 0
# IDs defined in any layout are valid R.id resources
layout_ids = set()
for lf in glob.glob(os.path.join(RES, "**", "*.xml"), recursive=True):
    if "/values" in lf or "/values-night" in lf or "/xml" in lf or "/color" in lf:
        continue
    src = open(lf, encoding="utf-8", errors="replace").read()
    layout_ids |= {m[1] for m in re.findall(r'@(\+)?id/([A-Za-z0-9_]+)', src)}

for rtype in sorted(refs):
    pool = lookups(rtype, refs[rtype])
    if rtype == "id":
        pool |= layout_ids
        # framework ids (android.R.id.*) are not referenced as R.id anyway
    missing = sorted(n for n in refs[rtype] if n not in pool and not n.startswith("android"))
    if missing:
        problems += len(missing)
        print(f"MISSING R.{rtype}: {', '.join(missing)}")
if problems == 0:
    print("OK: all R.* references resolve to existing resources")
else:
    print(f"\n{problems} missing resource(s)")
