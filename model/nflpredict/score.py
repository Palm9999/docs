"""Reference scorer for the exported model, in plain Python.

This is the same arithmetic `DecisionTree.kt` performs, kept deliberately
independent of LightGBM so the exported artifact can be checked without the
training stack, and so there is something to point at when the Kotlin and the
trainer disagree. It is not used in training.
"""
from __future__ import annotations

import gzip
import json
import math
from pathlib import Path

MISSING_ZERO = 1
MISSING_NAN = 2


def load(path: str | Path) -> dict:
    """Reads a bundle written by export.py or build_week.py, gzipped or not."""
    path = Path(path)
    opener = gzip.open if path.suffix == ".gz" else open
    with opener(path, "rt") as handle:
        return json.load(handle)


def _walk(tree: dict, features: list[float | None]) -> float:
    node = 0
    while True:
        value = features[tree["f"][node]]
        flags = tree["g"][node]
        missing, default_left = flags & 0b11, bool(flags & 0b100)

        is_nan = value is None or (isinstance(value, float) and math.isnan(value))
        if is_nan and missing != MISSING_NAN:
            value, is_nan = 0.0, False

        if (missing == MISSING_ZERO and value == 0.0) or (missing == MISSING_NAN and is_nan):
            go_left = default_left
        else:
            go_left = value <= tree["t"][node]

        nxt = tree["l"][node] if go_left else tree["r"][node]
        if nxt < 0:
            return tree["v"][~nxt]
        node = nxt


def raw_quantiles(model: dict, position: str, features: list[float | None]) -> dict[str, float]:
    """Uncalibrated p15/p50/p85, ordered."""
    trees = model["trees"][position]
    scores = {q: sum(_walk(t, features) for t in trees[q]) for q in ("15", "50", "85")}
    median = scores["50"]
    return {
        "15": min(scores["15"], median),
        "50": median,
        "85": max(scores["85"], median),
    }


def calibrated(model: dict, position: str, features: list[float | None]) -> dict[str, float]:
    raw = raw_quantiles(model, position, features)
    lower, upper = model["calibration"].get(position, (1.0, 1.0))
    median = raw["50"]
    return {
        "15": median - lower * (median - raw["15"]),
        "50": median,
        "85": median + upper * (raw["85"] - median),
    }
