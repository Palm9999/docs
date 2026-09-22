"""Walk-forward backtest.

For every week in the evaluation seasons the model is retrained on all player-weeks
that happened strictly earlier, then asked to predict that week cold. A random
train/test split would leak the future through the defense and usage aggregates and
report a score the app could never reproduce in-season.
"""
from __future__ import annotations

import sys

import numpy as np
import pandas as pd

from .config import BACKTEST_SEASONS, QUANTILES
from .train import fit_all, predict

# Mirrors GetSitStartRecommendationsUseCase: a matchup multiplier over defense rank
# and a multiplier for injury designation, applied to a base projection.
INJURY_MULT = {3.0: 1.0, 2.0: 0.85, 1.0: 0.5}


def _heuristic(df: pd.DataFrame) -> pd.Series:
    """The app's current rule, using a rolling average where it uses Sleeper."""
    matchup = np.where(df.dvp_rank <= 8, 0.9, np.where(df.dvp_rank >= 25, 1.1, 1.0))
    injury = df.report_code.map(INJURY_MULT).fillna(1.0).to_numpy()
    return df.fp_r3 * matchup * injury


def baselines(df: pd.DataFrame) -> dict[str, pd.Series]:
    # Season-to-date is empty in week 1; fall back to recent form so the baseline
    # is judged on the same rows as the model rather than silently skipping them.
    season_avg = df.fp_std.fillna(df.fp_r3)
    return {
        "last3_avg": df.fp_r3,
        "season_avg": season_avg,
        "blend_3_szn": 0.5 * df.fp_r3 + 0.5 * season_avg,
        "app_heuristic": _heuristic(df),
    }


def pairwise_accuracy(df: pd.DataFrame, pred_col: str, seed: int = 0) -> float:
    """How often the prediction ranks the better of two same-position players first.

    This is the metric that matches what the app is actually asked to do, and
    unlike MAE it is not dominated by the handful of 30-point outlier weeks.
    """
    rng = np.random.default_rng(seed)
    hits = total = 0
    for _, grp in df.groupby(["season", "week", "position"], sort=False):
        if len(grp) < 2:
            continue
        idx = rng.permutation(len(grp))
        a, b = grp.iloc[idx[: len(idx) // 2]], grp.iloc[idx[len(idx) // 2: len(idx) // 2 * 2]]
        same = a.fp.to_numpy() == b.fp.to_numpy()
        pred_wins = (a[pred_col].to_numpy() > b[pred_col].to_numpy())
        true_wins = (a.fp.to_numpy() > b.fp.to_numpy())
        hits += int(((pred_wins == true_wins) & ~same).sum())
        total += int((~same).sum())
    return hits / total if total else float("nan")


def metrics(df: pd.DataFrame, pred_col: str) -> dict:
    err = df[pred_col] - df.fp
    return {
        "MAE": float(err.abs().mean()),
        "RMSE": float(np.sqrt((err ** 2).mean())),
        "spearman": float(df[pred_col].corr(df.fp, method="spearman")),
        "pairwise_acc": pairwise_accuracy(df, pred_col),
    }


def run(df: pd.DataFrame, seasons=BACKTEST_SEASONS, retrain_every: int = 1) -> pd.DataFrame:
    df = df.sort_values(["season", "week"]).reset_index(drop=True)
    df["abs_week"] = df.season * 100 + df.week

    preds, models, trained_at = [], None, None
    targets = sorted(df[df.season.isin(seasons)].abs_week.unique())

    for i, aw in enumerate(targets):
        train = df[df.abs_week < aw]
        test = df[df.abs_week == aw]
        if len(train) < 2000 or test.empty:
            continue
        if models is None or (i - trained_at) >= retrain_every:
            models = fit_all(train)
            trained_at = i
        preds.append(predict(models, test))
        print(f"  week {aw // 100}-{aw % 100:02d}  train={len(train):>6,}  test={len(test):>4,}",
              file=sys.stderr)

    return pd.concat(preds, ignore_index=True)


def coverage(df: pd.DataFrame) -> dict:
    """Do the intervals contain the truth as often as they claim to?"""
    inside = ((df.fp >= df.p15) & (df.fp <= df.p85)).mean()
    return {
        "p15_below_actual": float((df.fp >= df.p15).mean()),  # target 0.85
        "p85_above_actual": float((df.fp <= df.p85).mean()),  # target 0.85
        "interval_coverage": float(inside),                    # target 0.70
    }


def play_rate_table(seasons=BACKTEST_SEASONS) -> pd.DataFrame:
    """P(plays | injury designation), the other half of an expected-points estimate.

    The points model is conditional on playing. Multiplying its output by this gives
    the number a lineup decision actually needs.
    """
    from .config import POSITIONS, SEASONS
    from .ingest import _concat, load_injuries

    inj = load_injuries()
    stats = _concat("stats_week_{year}.csv")
    stats = stats[(stats.season_type == "REG") & (stats.position.isin(POSITIONS))]
    played = stats[["season", "week", "player_id"]].drop_duplicates().assign(played=1)

    # The injury report covers the whole roster but the stat table only holds
    # skill players, so linemen and defenders must be filtered out or they count
    # as "did not play" and sink every rate.
    inj = inj[inj.position.isin(POSITIONS)]
    merged = inj.merge(played, on=["season", "week", "player_id"], how="left")
    merged["played"] = merged.played.fillna(0)
    labels = {0.0: "Out", 1.0: "Doubtful", 2.0: "Questionable", 3.0: "Not listed"}
    practice = {0.0: "DNP", 1.0: "Limited", 2.0: "Full", 3.0: "No report"}
    merged["report"] = merged.report_code.map(labels)
    merged["practice"] = merged.practice_code.map(practice)

    return (
        merged.groupby(["report", "practice"], as_index=False)
        .agg(play_rate=("played", "mean"), n=("played", "size"))
        .query("n >= 50")
        .sort_values("play_rate")
        .reset_index(drop=True)
    )


def paired_accuracy(df: pd.DataFrame, pred_col: str, base_col: str, seed: int = 0):
    """Score the *same* random pairs with two predictors.

    Comparing two independently-sampled accuracies throws away the fact that both
    face identical match-ups, which is most of the shared noise. Pairing them
    makes the difference far easier to resolve at these sample sizes.
    """
    rng = np.random.default_rng(seed)
    hits_pred, hits_base = [], []
    for _, grp in df.groupby(["season", "week", "position"], sort=False):
        if len(grp) < 2:
            continue
        idx = rng.permutation(len(grp))
        half = len(idx) // 2
        a, b = grp.iloc[idx[:half]], grp.iloc[idx[half: half * 2]]
        tied = a.fp.to_numpy() == b.fp.to_numpy()
        truth = a.fp.to_numpy() > b.fp.to_numpy()
        for column, out in ((pred_col, hits_pred), (base_col, hits_base)):
            out.extend(((a[column].to_numpy() > b[column].to_numpy()) == truth)[~tied])
    return np.array(hits_pred), np.array(hits_base)


def accuracy_advantage(
    df: pd.DataFrame,
    pred_col: str = "p50",
    baseline: str = "blend_3_szn",
    resamples: int = 2000,
    seed: int = 0,
) -> dict:
    """How much the model beats a baseline at ranking, with a bootstrap interval.

    A point estimate on its own invites reading noise as a result. Per position
    there are only a few thousand match-ups -- under a thousand for quarterbacks
    -- and at that size a difference of one or two points is not distinguishable
    from zero. Reporting the interval is what stops a difference that small being
    quoted as a finding, which is a mistake this project already made once.
    """
    scored = df.copy()
    scored["_base"] = baselines(scored)[baseline]
    model_hits, base_hits = paired_accuracy(scored, pred_col, "_base", seed)
    if len(model_hits) == 0:
        return {"pairs": 0}

    rng = np.random.default_rng(seed + 1)
    size = len(model_hits)
    draws = [
        model_hits[pick].mean() - base_hits[pick].mean()
        for pick in (rng.integers(0, size, size) for _ in range(resamples))
    ]
    low, high = np.percentile(draws, [2.5, 97.5])
    return {
        "pairs": size,
        "model": float(model_hits.mean()),
        "baseline": float(base_hits.mean()),
        "advantage": float(model_hits.mean() - base_hits.mean()),
        "ci_low": float(low),
        "ci_high": float(high),
        "significant": bool(low > 0 or high < 0),
    }
