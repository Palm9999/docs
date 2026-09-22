#!/usr/bin/env python3
"""Check that a built APK still contains a model the app can actually read.

Packaging is the one place this can break with nothing noticing. aapt reads a
gzipped asset as a request to store it deflated: it strips the `.gz` and lets
AssetManager inflate it on the way out, so `app/src/main/assets/model.json.gz`
in the repository arrives on the device as `assets/model.json` in plain text.
Opening the wrong name throws inside a runCatching, leaves the model null, and
silently disables every projection -- no crash, nothing in the UI beyond a line
on the settings screen, and no unit test that can reach it, because the JVM
tests never see a packaged APK. That shipped once.

The names come out of PredictionRepository rather than being repeated here, so
this cannot quietly start checking for something the app no longer opens.

Usage: tools/check_apk.py [path/to/app-debug.apk]
"""

from __future__ import annotations

import glob
import gzip
import json
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REPOSITORY = ROOT / (
    "app/src/main/java/com/sitorplay/app/data/prediction/PredictionRepository.kt"
)
DEFAULT_APK = "app/build/outputs/apk/debug/*.apk"
GZIP_MAGIC = b"\x1f\x8b"


def expected_names() -> list[str]:
    """The asset names PredictionRepository will try, in its own order."""
    source = REPOSITORY.read_text()
    match = re.search(r"val MODEL_ASSETS = listOf\(([^)]*)\)", source)
    if not match:
        raise SystemExit(f"Could not find MODEL_ASSETS in {REPOSITORY}")
    names = re.findall(r'"([^"]+)"', match.group(1))
    if not names:
        raise SystemExit(f"MODEL_ASSETS is empty in {REPOSITORY}")
    return names


def main() -> int:
    if len(sys.argv) > 1:
        apk_path = sys.argv[1]
    else:
        matches = sorted(glob.glob(str(ROOT / DEFAULT_APK)))
        if not matches:
            raise SystemExit(f"No APK at {DEFAULT_APK}; build one first")
        apk_path = matches[0]

    names = expected_names()
    with zipfile.ZipFile(apk_path) as apk:
        entries = set(apk.namelist())
        found = next((n for n in names if f"assets/{n}" in entries), None)
        if found is None:
            shipped = sorted(n for n in entries if n.startswith("assets/"))
            print(f"FAIL: {apk_path} has no model the app would open.")
            print(f"  PredictionRepository tries: {', '.join(names)}")
            print(f"  the APK ships: {', '.join(shipped) or '(nothing under assets/)'}")
            return 1
        raw = apk.read(f"assets/{found}")

    # Exactly what readModelAsset does with the bytes, so a mismatch between the
    # name surviving and the content being readable still fails here.
    data = gzip.decompress(raw) if raw[:2] == GZIP_MAGIC else raw
    try:
        model = json.loads(data)
    except (json.JSONDecodeError, UnicodeDecodeError) as exc:
        print(f"FAIL: assets/{found} is not JSON the app could parse: {exc}")
        return 1

    features = model.get("feature_order") or model.get("features") or []
    if not features:
        print(f"FAIL: assets/{found} parsed but carries no feature order.")
        return 1

    stored = "gzipped" if raw[:2] == GZIP_MAGIC else "plain"
    print(
        f"OK: assets/{found} -- {len(data):,} bytes, {len(features)} features, "
        f"{stored} in the APK."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
