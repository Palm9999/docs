"""Export trained models to a compact JSON the Android app can score directly.

ONNX Runtime Mobile would add ~15MB to the APK to evaluate what is, in the end,
a pile of if-statements. A gradient-boosted ensemble is just summed decision
trees, so the trees ship as flat arrays and a ~120-line Kotlin walker evaluates
them. The format is deliberately boring: parallel arrays of primitives, no nested
objects, so parsing is a single pass with no allocation per node.

Prediction is the plain sum of leaf values across trees -- verified against
LightGBM's own predict(), including the NaN paths -- so there is no bias term or
link function for the client to reproduce.
"""
from __future__ import annotations

import gzip
import json
from datetime import date

import numpy as np
import pandas as pd

from .config import OUT, POSITIONS, QUANTILES, SCORING
from .features import FEATURES
from .train import calibrate, fit_all, predict

FORMAT_VERSION = 1

# Mirrors LightGBM's MissingType enum. The client must branch on this, not just on
# default_left: for a node whose feature was never missing during training,
# LightGBM coerces a NaN input to 0.0 and compares normally, rather than sending
# it down the default branch.
MISSING = {"None": 0, "Zero": 1, "NaN": 2}


def _flatten_tree(root: dict) -> dict:
    """Depth-first flatten into parallel arrays.

    Children are encoded in one int array: a non-negative value is an internal
    node index, a negative value is the leaf at ~value (bitwise complement), which
    keeps the hot loop branch-light on the client.
    """
    feat: list[int] = []
    thr: list[float] = []
    flags: list[int] = []
    left: list[int] = []
    right: list[int] = []
    leaves: list[float] = []

    def visit(node: dict) -> int:
        if "split_feature" not in node:
            # Deliberately not rounded. Python emits the shortest round-trip
            # repr, so the client parses back bit-identical doubles and the parity
            # test can demand exact equality instead of a tolerance that would
            # hide a real divergence behind "close enough".
            leaves.append(float(node["leaf_value"]))
            return ~(len(leaves) - 1)

        idx = len(feat)
        feat.append(int(node["split_feature"]))
        thr.append(float(node["threshold"]))
        missing = MISSING[node.get("missing_type", "None")]
        flags.append(missing | (int(bool(node["default_left"])) << 2))
        left.append(0)
        right.append(0)

        left[idx] = visit(node["left_child"])
        right[idx] = visit(node["right_child"])
        return idx

    visit(root)
    return {"f": feat, "t": thr, "g": flags, "l": left, "r": right, "v": leaves}


def _export_models(models: dict) -> dict:
    out = {}
    for pos, by_quantile in models.items():
        out[pos] = {}
        for q, booster in by_quantile.items():
            dump = booster.dump_model()
            assert dump["objective"].startswith("quantile"), dump["objective"]
            out[pos][str(int(q * 100))] = [
                _flatten_tree(t["tree_structure"]) for t in dump["tree_info"]
            ]
    return out


def build(df: pd.DataFrame, holdout_season: int | None = None, scoring: str = SCORING) -> dict:
    """Train on everything and package it, with calibration fit out-of-sample.

    The widening factors must come from predictions the models did not train on,
    so they are fitted on `holdout_season` using models trained without it, then
    carried over to the final full-data models.
    """
    if holdout_season is None:
        holdout_season = int(df.season.max())

    pre = df[df.season < holdout_season]
    held = df[df.season == holdout_season]
    factors = calibrate(predict(fit_all(pre), held))

    final = fit_all(df)
    medians = (
        df.groupby("position")[FEATURES].median().round(6)
        .apply(lambda r: {c: (None if pd.isna(v) else float(v)) for c, v in r.items()}, axis=1)
        .to_dict()
    )

    return {
        "format_version": FORMAT_VERSION,
        "created": date.today().isoformat(),
        "scoring": scoring,
        "trained_through": f"{int(df.season.max())}-{int(df[df.season == df.season.max()].week.max())}",
        "features": FEATURES,
        "quantiles": [int(q * 100) for q in QUANTILES],
        "positions": sorted(final),
        # (lower, upper) multipliers applied to each half-width; see train.calibrate.
        "calibration": {pos: list(f) for pos, f in factors.items()},
        # P(plays | final report, practice participation), measured not assumed.
        # Keyed "<report>|<practice>" so the client can look up both together.
        "play_rates": _play_rates(),
        "feature_medians": medians,
        "trees": _export_models(final),
    }, final


def _play_rates() -> dict[str, float]:
    from .backtest import play_rate_table

    table = play_rate_table()
    rates = {f"{r.report}|{r.practice}": round(float(r.play_rate), 4)
             for r in table.itertuples()}
    # A player absent from the injury report is simply available.
    rates["Not listed|No report"] = 1.0
    return rates


def golden_fixtures(bundle: dict, models: dict, df: pd.DataFrame, n: int = 40) -> dict:
    """Rows plus the exact Python output, so the Kotlin port can be proven equal.

    Deliberately biased toward rows with missing features, because that is where
    an independent reimplementation of the walker is most likely to drift.
    """
    rng = np.random.default_rng(7)
    has_nan = df[df[FEATURES].isna().any(axis=1)]
    clean = df[~df[FEATURES].isna().any(axis=1)]
    take = pd.concat([
        has_nan.iloc[rng.choice(len(has_nan), size=min(n // 2, len(has_nan)), replace=False)],
        clean.iloc[rng.choice(len(clean), size=min(n - n // 2, len(clean)), replace=False)],
    ])

    scored = predict(models, take)
    cases = []
    for _, row in scored.iterrows():
        cases.append({
            "position": row.position,
            "features": [None if pd.isna(row[f]) else float(row[f]) for f in FEATURES],
            "expected": {q: float(row[f"p{q}"]) for q in (15, 50, 85)},
        })
    return {"format_version": FORMAT_VERSION, "cases": cases}


def write(bundle: dict, fixtures: dict) -> dict[str, int]:
    OUT.mkdir(parents=True, exist_ok=True)
    sizes = {}

    raw = json.dumps(bundle, separators=(",", ":")).encode()
    (OUT / "model.json").write_bytes(raw)
    packed = gzip.compress(raw, 9)
    (OUT / "model.json.gz").write_bytes(packed)
    sizes["model.json"] = len(raw)
    sizes["model.json.gz"] = len(packed)

    fx = json.dumps(fixtures, separators=(",", ":")).encode()
    (OUT / "model_fixtures.json").write_bytes(fx)
    sizes["model_fixtures.json"] = len(fx)
    return sizes
