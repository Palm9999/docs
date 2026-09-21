#!/usr/bin/env python3
"""Train on all available data and write the app's model assets.

    python3 export_model.py

Writes to artifacts/: model.json.gz (ship this), model.json (inspect this) and
model_fixtures.json (the parity test's golden data).
"""
from __future__ import annotations

import pandas as pd

from nflpredict import export
from nflpredict.config import DATA, OUT
from nflpredict.features import FEATURES

CACHE = DATA / "features.parquet"


def main() -> None:
    if not CACHE.exists():
        raise SystemExit("no data/features.parquet -- run run_phase0.py first")
    df = pd.read_parquet(CACHE)

    # Calibration must be fitted on a season the models did not train on, and on
    # a full one: the current season is two weeks old in September and its
    # residual quantiles would be noise.
    weeks_per_season = df.groupby("season").week.nunique()
    complete = weeks_per_season[weeks_per_season >= 15].index
    holdout = int(max(complete)) if len(complete) else int(df.season.max())
    print(f"calibration holdout season: {holdout}")

    bundle, models = export.build(df, holdout_season=holdout)
    fixtures = export.golden_fixtures(bundle, models, df)
    sizes = export.write(bundle, fixtures)

    trees = sum(len(t) for p in bundle["trees"].values() for t in p.values())
    nodes = sum(
        len(tree["f"]) + len(tree["v"])
        for p in bundle["trees"].values() for q in p.values() for tree in q
    )
    print(f"positions   : {', '.join(bundle['positions'])}")
    print(f"features    : {len(FEATURES)}")
    print(f"trees       : {trees:,} ({nodes:,} nodes)")
    print(f"trained thru: {bundle['trained_through']}  scoring={bundle['scoring']}")
    print(f"calibration : " + ", ".join(
        f"{k}=({v[0]:.2f},{v[1]:.2f})" for k, v in sorted(bundle["calibration"].items())))
    print(f"fixtures    : {len(fixtures['cases'])} cases")
    for name, size in sizes.items():
        print(f"  {name:<22} {size / 1024:>8.0f} KB")
    print(f"\n-> {OUT}")


if __name__ == "__main__":
    main()
