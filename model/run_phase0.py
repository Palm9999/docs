#!/usr/bin/env python3
"""Phase 0: does a model trained on free data beat the rules the app ships today?

Builds features from nflverse, runs a walk-forward backtest over 2023-2025, and
prints the model against four baselines. Nothing here touches the Android app; the
point is to find out whether the modelling is worth wiring up at all.

    python3 run_phase0.py [--scoring ppr|half_ppr|standard] [--rebuild]
"""
from __future__ import annotations

import argparse
import json
import sys

import pandas as pd

from nflpredict import backtest, features, train
from nflpredict.config import BACKTEST_SEASONS, DATA, OUT, SCORING

CACHE = DATA / "features.parquet"


def get_features(scoring: str, rebuild: bool) -> pd.DataFrame:
    if CACHE.exists() and not rebuild and scoring == SCORING:
        return pd.read_parquet(CACHE)
    from nflpredict.ingest import build_player_weeks
    print("building features from nflverse...", file=sys.stderr)
    df = features.build(build_player_weeks(), scoring)
    if scoring == SCORING:
        df.to_parquet(CACHE)
    return df


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--scoring", default=SCORING, choices=["ppr", "half_ppr", "standard"])
    ap.add_argument("--rebuild", action="store_true")
    ap.add_argument("--retrain-every", type=int, default=1,
                    help="weeks between refits; 1 retrains before every week")
    args = ap.parse_args()

    OUT.mkdir(parents=True, exist_ok=True)
    df = get_features(args.scoring, args.rebuild)
    print(f"{len(df):,} player-weeks, {len(features.FEATURES)} features, "
          f"scoring={args.scoring}", file=sys.stderr)

    preds = backtest.run(df, retrain_every=args.retrain_every)
    preds.to_parquet(OUT / "backtest_predictions.parquet")

    rows = [{"model": "quantile_lgbm", **backtest.metrics(preds, "p50")}]
    for name, series in backtest.baselines(preds).items():
        tmp = preds.copy()
        tmp["b"] = series
        rows.append({"model": name, **backtest.metrics(tmp, "b")})
    table = pd.DataFrame(rows).set_index("model").round(4)

    print(f"\n=== Walk-forward backtest, {BACKTEST_SEASONS[0]}-{BACKTEST_SEASONS[-1]} "
          f"({len(preds):,} predictions) ===")
    print(table.to_string())

    print("\n=== By position (MAE / pairwise start-sit accuracy) ===")
    per_pos = []
    for pos, grp in preds.groupby("position"):
        row = {"position": pos, "n": len(grp)}
        row["model_MAE"] = round(backtest.metrics(grp, "p50")["MAE"], 3)
        row["model_acc"] = round(backtest.pairwise_accuracy(grp, "p50"), 4)
        tmp = grp.copy()
        tmp["b"] = backtest.baselines(grp)["blend_3_szn"]
        row["base_MAE"] = round(backtest.metrics(tmp, "b")["MAE"], 3)
        row["base_acc"] = round(backtest.pairwise_accuracy(tmp, "b"), 4)
        per_pos.append(row)
    print(pd.DataFrame(per_pos).set_index("position").to_string())

    print("\n=== Interval calibration (p15-p85 should cover ~0.70) ===")
    fit_season, eval_seasons = BACKTEST_SEASONS[0], BACKTEST_SEASONS[1:]
    fit_rows = preds[preds.season == fit_season]
    eval_rows = preds[preds.season.isin(eval_seasons)]
    factors = train.calibrate(fit_rows)
    calibrated = train.apply_calibration(eval_rows, factors)
    print(f"raw        ({eval_seasons[0]}-{eval_seasons[-1]}): "
          f"{json.dumps(backtest.coverage(eval_rows))}")
    print(f"calibrated ({eval_seasons[0]}-{eval_seasons[-1]}): "
          f"{json.dumps(backtest.coverage(calibrated))}")
    print(f"widening factors fit on {fit_season} (lo, hi): "
          + ", ".join(f"{k}=({v[0]:.2f}, {v[1]:.2f})" for k, v in sorted(factors.items())))
    json.dump(factors, open(OUT / "calibration.json", "w"), indent=2)

    print("\n=== P(plays | injury report) ===")
    print(backtest.play_rate_table().to_string(index=False))

    print("\n=== Top features (median model) ===")
    final = train.fit_all(df)
    for pos in ["QB", "RB", "WR", "TE"]:
        if pos in final:
            top = train.feature_importance(final, pos).head(8)
            print(f"\n{pos}: " + ", ".join(f"{r.feature}" for r in top.itertuples()))

    table.to_csv(OUT / "backtest_metrics.csv")
    print(f"\nartifacts -> {OUT}")


if __name__ == "__main__":
    main()
