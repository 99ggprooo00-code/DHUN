#!/usr/bin/env python3
"""Static gate for the DHUN Android module (no JDK/AGP in this sandbox).

Usage: `python3 scripts/check_widget_refs.py` from anywhere in the repo.
Exits non-zero on the first problem list. CI does not run it — it exists for
toolchain-free sandboxes (and local pre-push sanity) where `assembleDebug`
and `testDebugUnitTest` cannot run, which is exactly where an R.id that only
existed in a deleted layout would otherwise ship broken.

1. Every XML under app-android/src parses and is well-formed.
2. Every `R.<type>.<name>` in Kotlin resolves to a real resource.
3. Every `@<type>/<name>` in app res XML resolves (framework @android:/@id:
   names are skipped, @+id/ definitions are collected).
4. No dangling reference to the deleted Now Playing widget surfaces.
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), os.pardir, "app-android", "src")
ROOT = os.path.normpath(ROOT)
ANDROID_NS = "http://schemas.android.com/apk/res/android"

errors = []
xml_files = []
kt_files = []
for dirpath, dirnames, filenames in os.walk(ROOT):
    dirnames[:] = [d for d in dirnames if d not in ("build", ".git")]
    for fn in filenames:
        p = os.path.join(dirpath, fn)
        if fn.endswith(".xml"):
            xml_files.append(p)
        elif fn.endswith(".kt"):
            kt_files.append(p)

# ---------- 1. well-formedness
parsed = {}
for p in xml_files:
    try:
        parsed[p] = ET.parse(p)
    except Exception as e:  # noqa: BLE001
        errors.append(f"XML PARSE {p}: {e}")

# ---------- 2. resource index
res = {}          # (type, name) -> file
defined_ids = set()
main_res = os.path.join(ROOT, "main", "res")
if not os.path.isdir(main_res):
    sys.exit("no res dir")

FILE_TYPES = {"layout": "layout", "xml": "xml", "drawable": "drawable", "mipmap": "mipmap",
              "raw": "raw", "anim": "anim", "animator": "animator", "transition": "transition"}
VALUE_TAGS = {"color": "color", "string": "string", "dimen": "dimen", "style": "style",
              "bool": "bool", "integer": "integer", "string-array": "array",
              "array": "array", "attr": "attr", "id": "id"}

for dirpath, _dirnames, filenames in os.walk(main_res):
    folder = os.path.basename(dirpath)
    base_type = folder.split("-")[0]
    for fn in filenames:
        p = os.path.join(dirpath, fn)
        if base_type in FILE_TYPES and (fn.endswith(".xml") or "." in fn):
            name = os.path.splitext(fn)[0]
            res[(FILE_TYPES[base_type], name)] = p
        if fn.endswith(".xml") and base_type.startswith("values"):
            tree = parsed.get(p)
            if tree is None:
                continue
            for el in tree.getroot():
                tag = el.tag
                if tag in VALUE_TAGS:
                    nm = el.get("name")
                    if nm:
                        res[(VALUE_TAGS[tag], nm)] = p
                        if tag == "id":
                            defined_ids.add(nm)

# ids declared via @+id/ in any layout
for p, tree in parsed.items():
    for el in tree.iter():
        for k, v in el.attrib.items():
            if isinstance(v, str) and v.startswith("@+id/"):
                nm = v[len("@+id/"):]
                res[("id", nm)] = p
                defined_ids.add(nm)

# ---------- 3. Kotlin R references
kt_ref = re.compile(r"(?<![A-Za-z0-9_.])R\.(layout|drawable|xml|color|string|dimen|id|style|raw|mipmap|array|bool|integer)\.([A-Za-z0-9_]+)")
checked = 0
for p in kt_files:
    text = open(p, encoding="utf-8").read()
    for m in kt_ref.finditer(text):
        typ, name = m.group(1), m.group(2)
        checked += 1
        if (typ, name) not in res:
            line = text[:m.start()].count("\n") + 1
            errors.append(f"UNRESOLVED R.{typ}.{name}  {os.path.relpath(p, ROOT)}:{line}")

# ---------- 4. XML internal references
xml_ref = re.compile(r"@(?!android:)(\+?)(color|drawable|layout|dimen|string|xml|id|style|raw|mipmap|array|bool|integer)/([A-Za-z0-9_.]+)")
for p, tree in parsed.items():
    for el in tree.iter():
        for k, v in el.attrib.items():
            if not isinstance(v, str):
                continue
            for m in xml_ref.finditer(v):
                plus, typ, name = m.group(1), m.group(2), m.group(3)
                if plus == "+" and typ == "id":
                    continue
                if (typ, name) not in res:
                    errors.append(f"UNRESOLVED @{typ}/{name} in {os.path.relpath(p, ROOT)} (attr {k})")

# ---------- 5. Now Playing must be gone
NP = re.compile(r"widget_now_playing|DhunNowPlayingWidgetProvider|widget_preview_now_playing|TALL_MIN_HEIGHT_DP|COMPACT_MAX_WIDTH_DP|layoutForNowPlaying|buildNowPlayingViews|values-night-v31")
for p in kt_files + xml_files:
    if any(seg in p for seg in ("CHANGELOG", "ROADMAP")):
        continue
    text = open(p, encoding="utf-8").read()
    for i, line in enumerate(text.splitlines(), 1):
        if NP.search(line) and not (line.strip().startswith("<!--") or line.strip().startswith("*") or line.strip().startswith("//")):
            errors.append(f"DANGLING Now-Playing reference: {os.path.relpath(p, ROOT)}:{i}: {line.strip()[:110]}")

for f in ("layout/widget_now_playing.xml", "layout/widget_now_playing_compact.xml",
          "layout/widget_now_playing_tall.xml", "layout/widget_preview_now_playing.xml",
          "xml/widget_now_playing_info.xml", "kotlin/dev/dhun/android/widgets/DhunNowPlayingWidgetProvider.kt"):
    if os.path.exists(os.path.join(ROOT, "main", f)):
        errors.append(f"FILE STILL PRESENT: {f}")

print(f"scanned {len(xml_files)} xml, {len(kt_files)} kt, {checked} R refs, {len(res)} resources")
if errors:
    print(f"\n{len(errors)} PROBLEM(S):")
    for e in errors:
        print(" -", e)
    sys.exit(1)
print("OK: XML well-formed, all R refs and @refs resolve, Now Playing fully removed")
