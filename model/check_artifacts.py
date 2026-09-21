#!/usr/bin/env python3
"""Verify the committed model, fixtures and weekly bundle still agree.

The app refuses at runtime to score a bundle whose feature list disagrees with
the model's, which is the right behaviour but a bad way to find out. This is the
same check run in CI, plus the parity fixtures, so a mismatch is caught when the
artifact is committed rather than on someone's phone on a Sunday morning.

    python3 model/check_artifacts.py
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

from nflpredict import score

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
MODEL = REPO / "app/src/main/assets/model.json.gz"
FIXTURES = REPO / "app/src/test/resources/model_fixtures.json"
BUNDLE = ROOT / "artifacts/week.json.gz"

# Anything outside this is not a football score and means something is wrong.
PLAUSIBLE_MEDIAN = (-5.0, 45.0)


def main() -> None:
    failures: list[str] = []

    if not MODEL.exists():
        raise SystemExit(f"missing {MODEL.relative_to(REPO)} -- run export_model.py")
    model = score.load(MODEL)
    print(f"model      : {len(model['features'])} features, "
          f"positions {', '.join(model['positions'])}, "
          f"trained through {model['trained_through']}")

    # 1. The parity fixtures must still reproduce exactly. This is the same
    #    assertion ModelParityTest makes in Kotlin, from the other language.
    if FIXTURES.exists():
        fixtures = score.load(FIXTURES)
        worst = 0.0
        for case in fixtures["cases"]:
            got = score.raw_quantiles(model, case["position"], case["features"])
            for quantile, expected in case["expected"].items():
                worst = max(worst, abs(expected - got[str(quantile)]))
        if worst > 0.0:
            failures.append(f"parity fixtures drifted from the model: worst delta {worst}")
        print(f"fixtures   : {len(fixtures['cases'])} cases, worst delta {worst}")
    else:
        print("fixtures   : absent, skipped")

    # 2. The bundle must be scoreable by this exact model.
    if BUNDLE.exists():
        bundle = score.load(BUNDLE)
        print(f"bundle     : {bundle['season']} week {bundle['week']}, "
              f"{len(bundle['players'])} players, generated {bundle['generated']}")

        if bundle["feature_order"] != model["features"]:
            failures.append(
                "bundle feature_order does not match the model -- the app would "
                "refuse this. Rebuild both: export_model.py then build_week.py"
            )
        else:
            width = len(model["features"])
            unmodelled, bad_width, implausible = set(), 0, []
            for player in bundle["players"]:
                if player["position"] not in model["trees"]:
                    unmodelled.add(player["position"])
                    continue
                if len(player["f"]) != width:
                    bad_width += 1
                    continue
                result = score.calibrated(model, player["position"], player["f"])
                if not (result["15"] <= result["50"] <= result["85"]):
                    implausible.append(f"{player['name']}: quantiles out of order")
                elif not (PLAUSIBLE_MEDIAN[0] <= result["50"] <= PLAUSIBLE_MEDIAN[1]):
                    implausible.append(f"{player['name']}: median {result['50']:.1f}")
                elif any(math.isnan(v) for v in result.values()):
                    implausible.append(f"{player['name']}: produced NaN")

            if unmodelled:
                failures.append(f"bundle has unmodelled positions: {sorted(unmodelled)}")
            if bad_width:
                failures.append(f"{bad_width} players have the wrong feature count")
            if implausible:
                failures.append(
                    f"{len(implausible)} implausible predictions, e.g. {implausible[:3]}"
                )
            if not (unmodelled or bad_width or implausible):
                print(f"scoring    : all {len(bundle['players'])} players scored cleanly")
    else:
        print("bundle     : absent, skipped (build_week.py has not run)")

    if failures:
        print("\nFAILED:", file=sys.stderr)
        for failure in failures:
            print(f"  - {failure}", file=sys.stderr)
        sys.exit(1)
    print("\nall artifacts agree")


if __name__ == "__main__":
    main()
