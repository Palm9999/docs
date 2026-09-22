"""Build feature rows for a week that has not been played yet.

The training pipeline only ever sees player-weeks that happened. To project an
upcoming week the same features have to exist for players with no stat line yet,
so this appends one placeholder row per active player -- identity and game context
filled in, every outcome column left empty -- and lets the ordinary pipeline build
its features. Because rolling windows shift before they roll, those rows see only
completed weeks, which is exactly the leakage guarantee the backtest relies on.
"""
from __future__ import annotations

import numpy as np
import pandas as pd

from .config import POSITIONS
from .features import FEATURES, pipeline
from .ingest import load_games, load_injuries
from .live import merge_injuries

# Identity and context columns that a placeholder row must carry itself; anything
# else is derived from history by the pipeline.
CARRIED = ["player_id", "name", "position"]


def active_players(history: pd.DataFrame, lookback: int = 6) -> pd.DataFrame:
    """Players who appeared in any of the last `lookback` completed weeks.

    Indexed by absolute week order rather than arithmetic, so the lookback spans
    the season boundary correctly in September.
    """
    hist = history.copy()
    hist["abs_week"] = hist.season * 100 + hist.week
    ordered = sorted(hist.abs_week.unique())
    cutoff = ordered[max(0, len(ordered) - lookback)]
    recent = hist[hist.abs_week >= cutoff].sort_values("abs_week")
    return recent.groupby("player_id", as_index=False).last()


def upcoming_rows(
    history: pd.DataFrame,
    season: int,
    week: int,
    teams: dict[str, str] | None = None,
    lookback: int = 6,
    live_injuries: pd.DataFrame | None = None,
) -> pd.DataFrame:
    """One placeholder row per active player with a game scheduled that week.

    `teams` optionally overrides each player's team by gsis id, which matters in
    September: a player's last appearance may have been for the team that just
    traded or released them.
    """
    latest = active_players(history, lookback)
    latest = latest[latest.position.isin(POSITIONS)]
    if teams:
        latest["team"] = latest.player_id.map(teams).fillna(latest.team)

    context = load_games()
    context = context[(context.season == season) & (context.week == week)]
    if context.empty:
        raise ValueError(f"no scheduled games found for {season} week {week}")

    rows = latest[CARRIED + ["team"]].merge(
        context.drop(columns=["season", "week"]), on="team", how="inner"
    )
    rows["season"] = season
    rows["week"] = week

    # The injury report for the target week is published before kickoff, so it is
    # legitimate input rather than leakage. Early in the week it does not exist
    # yet, and `live_injuries` covers the gap -- see live.py for why filling in
    # "not on the report" instead would be actively wrong.
    injuries = load_injuries()
    if live_injuries is not None and not live_injuries.empty:
        injuries = merge_injuries(injuries, live_injuries)
    injuries = injuries[(injuries.season == season) & (injuries.week == week)]
    rows = rows.merge(
        injuries[["player_id", "practice_code", "report_code"]],
        on="player_id", how="left",
    )
    rows["practice_code"] = rows.practice_code.fillna(3)
    rows["report_code"] = rows.report_code.fillna(3)

    # Every outcome column stays empty: these weeks have not happened.
    for column in history.columns:
        if column not in rows.columns:
            rows[column] = np.nan
    return rows[history.columns]


def build_week(
    history: pd.DataFrame,
    season: int,
    week: int,
    live_injuries: pd.DataFrame | None = None,
    **kwargs,
) -> pd.DataFrame:
    """Feature rows for `season`/`week`, built from completed weeks only."""
    pending = upcoming_rows(history, season, week, live_injuries=live_injuries, **kwargs)
    combined = pd.concat([history, pending], ignore_index=True)
    featured = pipeline(combined, extra_injuries=live_injuries)

    rows = featured[(featured.season == season) & (featured.week == week)].copy()
    # A player with no prior game anywhere has nothing to predict from; the model
    # would be extrapolating from an all-empty vector.
    rows = rows[rows.career_games_prior >= 1]
    return rows.reset_index(drop=True)


def feature_matrix(rows: pd.DataFrame) -> list[list[float | None]]:
    """Feature values in FEATURES order, with NaN rendered as JSON null."""
    values = rows[FEATURES].to_numpy(dtype=float)
    return [[None if np.isnan(v) else round(float(v), 6) for v in row] for row in values]
