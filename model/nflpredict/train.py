"""Per-position quantile models.

One LightGBM model per (position, quantile). Quantile regression rather than a
point estimate because the product question is "who do I start", which depends on
the shape of a player's range as much as its centre: a 12-point floor and a
12-point ceiling are very different starts in different matchups.
"""
from __future__ import annotations

import lightgbm as lgb
import numpy as np
import pandas as pd

from .config import POSITIONS, QUANTILES
from .features import FEATURES

PARAMS = {
    "objective": "quantile",
    "learning_rate": 0.05,
    "num_leaves": 15,
    "min_data_in_leaf": 40,
    "feature_fraction": 0.8,
    "bagging_fraction": 0.8,
    "bagging_freq": 1,
    "lambda_l2": 1.0,
    "verbose": -1,
    "num_threads": 0,
}
# Tuned by holdout rather than assumed: 400 rounds at 31 leaves overfit, scoring
# worse than this while producing a model 5x larger. Small matters twice here,
# because the export has to fit in an APK asset.
N_ROUNDS = 100


def fit_position(train: pd.DataFrame, quantiles=QUANTILES, n_rounds: int = N_ROUNDS) -> dict:
    x, y = train[FEATURES], train["fp"]
    models = {}
    for q in quantiles:
        ds = lgb.Dataset(x, label=y, free_raw_data=False)
        models[q] = lgb.train({**PARAMS, "alpha": q}, ds, num_boost_round=n_rounds)
    return models


def fit_all(train: pd.DataFrame, **kw) -> dict:
    return {
        pos: fit_position(grp, **kw)
        for pos, grp in train.groupby("position")
        if pos in POSITIONS and len(grp) >= 200
    }


def predict(models: dict, test: pd.DataFrame) -> pd.DataFrame:
    """Returns the input frame with p15 / p50 / p85 columns attached."""
    out = test.copy()
    for q in QUANTILES:
        out[f"p{int(q * 100)}"] = np.nan

    for pos, grp in out.groupby("position"):
        if pos not in models:
            continue
        x = grp[FEATURES]
        for q, model in models[pos].items():
            out.loc[grp.index, f"p{int(q * 100)}"] = model.predict(x)

    # Quantile models are fitted independently and can cross on thin slices; the
    # ordering is a promise the UI relies on, so enforce it here.
    lo, mid, hi = out.p15, out.p50, out.p85
    out["p15"] = np.minimum(lo, mid)
    out["p85"] = np.maximum(hi, mid)
    return out


def feature_importance(models: dict, position: str, quantile: float = 0.50) -> pd.DataFrame:
    model = models[position][quantile]
    return (
        pd.DataFrame({
            "feature": model.feature_name(),
            "gain": model.feature_importance("gain"),
        })
        .sort_values("gain", ascending=False)
        .reset_index(drop=True)
    )


def calibrate(preds: pd.DataFrame) -> dict[str, tuple[float, float]]:
    """Fit per-position widening factors so the intervals mean what they say.

    The three quantile models are fitted independently and each is pulled toward
    the median by regularisation, which leaves p15-p85 too narrow in practice. This
    is the conformal fix: score each observation by how many predicted half-widths
    it actually missed by, then take the 85th percentile of that score as the
    factor the interval needs to be stretched by.

    Fit this on predictions the models did not train on, never in-sample.
    """
    factors = {}
    for pos, grp in preds.groupby("position"):
        lo_width = (grp.p50 - grp.p15).clip(lower=1e-6)
        hi_width = (grp.p85 - grp.p50).clip(lower=1e-6)
        z_lo = ((grp.p50 - grp.fp) / lo_width).clip(lower=0)
        z_hi = ((grp.fp - grp.p50) / hi_width).clip(lower=0)
        factors[pos] = (float(z_lo.quantile(0.85)), float(z_hi.quantile(0.85)))
    return factors


def apply_calibration(preds: pd.DataFrame, factors: dict[str, tuple[float, float]]) -> pd.DataFrame:
    out = preds.copy()
    for pos, (s_lo, s_hi) in factors.items():
        m = out.position == pos
        out.loc[m, "p15"] = out.loc[m, "p50"] - s_lo * (out.loc[m, "p50"] - out.loc[m, "p15"])
        out.loc[m, "p85"] = out.loc[m, "p50"] + s_hi * (out.loc[m, "p85"] - out.loc[m, "p50"])
    # Not clipped at zero: fantasy points really do go negative (a lost fumble is
    # -2), and clipping breaks the p15 <= p50 ordering for players whose median is
    # itself near zero. Clamp at the display edge if a UI wants to.
    return out
