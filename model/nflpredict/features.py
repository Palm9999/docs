"""Leakage-safe feature construction.

The contract for this module: every feature on a row for (player, season, week W)
may only use information that existed before that game kicked off. Outcome columns
are carried through untouched so the caller can split them off as the target.

In practice that means each usage statistic is shifted by one game *before* it is
rolled, and every opponent aggregate is built from an expanding window over prior
weeks only. The injury report is the one input read from week W itself, because
the final report is published days ahead of kickoff.
"""
from __future__ import annotations

import numpy as np
import pandas as pd

from .config import DATA, MIN_PRIOR_GAMES, SCORING

# Per-game usage that gets lagged and rolled.
USAGE = [
    "fp", "targets", "carries", "receptions", "receiving_yards", "rushing_yards",
    "passing_yards", "receiving_air_yards", "target_share", "air_yards_share",
    "wopr", "snap_pct", "carry_share", "total_tds", "opportunities",
]
WINDOWS = (3, 6)
SHRINK = 4.0  # pseudo-games of league-average pulled into each defense estimate


def score(df: pd.DataFrame, scoring: str = SCORING) -> pd.Series:
    if scoring == "ppr":
        return df.fantasy_points_ppr
    if scoring == "standard":
        return df.fantasy_points
    if scoring == "half_ppr":
        return df.fantasy_points + 0.5 * df.receptions
    raise ValueError(f"unknown scoring format: {scoring}")


def _base(df: pd.DataFrame, scoring: str) -> pd.DataFrame:
    df = df.copy()
    df["fp"] = score(df, scoring)
    df["total_tds"] = df[["receiving_tds", "rushing_tds", "passing_tds"]].fillna(0).sum(axis=1)
    df["opportunities"] = df[["targets", "carries"]].fillna(0).sum(axis=1)

    team_carries = df.groupby(["season", "week", "team"])["carries"].transform("sum")
    df["carry_share"] = df.carries / team_carries.replace(0, np.nan)
    return df


def _rolling(df: pd.DataFrame) -> pd.DataFrame:
    """Shift-then-roll across a player's whole career timeline.

    Rolling within a season would leave weeks 1-2 with no history at all and drop
    them from the model, which are exactly the weeks a user most wants help with.
    Carrying last season's closing form across the boundary keeps those weeks
    predictable; `games_prior` stays season-local so the model can still learn to
    discount stale cross-season history.
    """
    df = df.sort_values(["player_id", "season", "week"]).reset_index(drop=True)
    career = df.groupby("player_id", sort=False)
    season = df.groupby(["player_id", "season"], sort=False)

    for col in USAGE:
        prior = career[col].shift(1)
        keyed = prior.groupby(df.player_id, sort=False)
        for w in WINDOWS:
            df[f"{col}_r{w}"] = keyed.transform(lambda s, w=w: s.rolling(w, min_periods=1).mean())
        # Season-to-date is deliberately season-local: it measures this year's role.
        df[f"{col}_std"] = (
            season[col].shift(1).groupby([df.player_id, df.season], sort=False)
            .transform(lambda s: s.expanding(min_periods=1).mean())
        )

    df["games_prior"] = season.cumcount()
    df["career_games_prior"] = career.cumcount()
    df["fp_vol"] = (
        career["fp"].shift(1).groupby(df.player_id, sort=False)
        .transform(lambda s: s.rolling(6, min_periods=2).std())
    )
    df["usage_trend"] = df.opportunities_r3 - df.opportunities_std
    return df


def _prior_season(df: pd.DataFrame) -> pd.DataFrame:
    """Last season's per-game average, which carries the early weeks."""
    agg = (
        df.groupby(["player_id", "season"], as_index=False)
        .agg(prev_fp=("fp", "mean"), prev_games=("fp", "size"),
             prev_opps=("opportunities", "mean"))
    )
    agg["season"] = agg.season + 1
    return df.merge(agg, on=["player_id", "season"], how="left")


def _defense_vs_position(df: pd.DataFrame) -> pd.DataFrame:
    """How generous each defense has been to each position, prior weeks only.

    Raw points-allowed is noisy early in a season and contaminated by schedule, so
    the estimate is shrunk toward the league mean by SHRINK pseudo-games and
    expressed as a multiplier where 1.0 is exactly average.
    """
    per_game = (
        df.groupby(["season", "week", "opponent", "position"], as_index=False)
        .agg(allowed=("fp", "sum"))
        .rename(columns={"opponent": "def_team"})
        .sort_values(["season", "position", "def_team", "week"])
    )

    grp = per_game.groupby(["season", "position", "def_team"], sort=False)
    per_game["prior_mean"] = grp["allowed"].transform(lambda s: s.shift(1).expanding().mean())
    per_game["prior_n"] = grp.cumcount()

    # League baseline, also restricted to prior weeks.
    league = (
        per_game.groupby(["season", "position", "week"], as_index=False)
        .agg(week_mean=("allowed", "mean"))
        .sort_values(["season", "position", "week"])
    )
    league["league_prior"] = (
        league.groupby(["season", "position"], sort=False)["week_mean"]
        .transform(lambda s: s.shift(1).expanding().mean())
    )
    per_game = per_game.merge(
        league[["season", "position", "week", "league_prior"]],
        on=["season", "position", "week"], how="left",
    )

    n, prior, lg = per_game.prior_n, per_game.prior_mean.fillna(0), per_game.league_prior
    shrunk = (n * prior + SHRINK * lg) / (n + SHRINK)
    per_game["dvp_mult"] = (shrunk / lg).replace([np.inf, -np.inf], np.nan).fillna(1.0)
    # 1 = toughest, 32 = easiest, matching how the app already models defense rank.
    per_game["dvp_rank"] = (
        per_game.groupby(["season", "week", "position"])["dvp_mult"]
        .rank(method="first", ascending=True).astype("Int64")
    )

    cols = ["season", "week", "def_team", "position", "dvp_mult", "dvp_rank"]
    return df.merge(
        per_game[cols].rename(columns={"def_team": "opponent"}),
        on=["season", "week", "opponent", "position"], how="left",
    )


def _vacancy(df: pd.DataFrame) -> pd.DataFrame:
    """Usage freed up by teammates who are Out or Doubtful this week.

    This is the feature consensus projections are slowest to reflect, so it is
    where a homemade model has the most room to add value. Injured players are
    absent from the week's stat table by definition, so their usage is carried
    forward from the last week they played.
    """
    from .ingest import load_injuries

    usage = df[["player_id", "season", "week", "team", "target_share_r3", "carry_share_r3"]]

    # Forward-fill each player's last known usage across every week of the season
    # so an injured player still has a usage estimate attached.
    weeks = pd.DataFrame({"week": range(1, 19), "key": 1})
    spine = (
        df[["player_id", "season"]].drop_duplicates().assign(key=1)
        .merge(weeks, on="key").drop(columns="key")
    )
    filled = (
        spine.merge(usage, on=["player_id", "season", "week"], how="left")
        .sort_values(["player_id", "season", "week"])
    )
    filled[["team", "target_share_r3", "carry_share_r3"]] = (
        filled.groupby(["player_id", "season"], sort=False)[
            ["team", "target_share_r3", "carry_share_r3"]
        ].ffill()
    )

    inj = load_injuries()
    sidelined = inj[inj.report_code <= 1][["season", "week", "player_id"]]
    sidelined = sidelined.merge(filled, on=["season", "week", "player_id"], how="inner")

    vacated = (
        sidelined.groupby(["season", "week", "team"], as_index=False)
        .agg(vacated_target_share=("target_share_r3", "sum"),
             vacated_carry_share=("carry_share_r3", "sum"))
    )
    df = df.merge(vacated, on=["season", "week", "team"], how="left")
    df[["vacated_target_share", "vacated_carry_share"]] = df[
        ["vacated_target_share", "vacated_carry_share"]
    ].fillna(0.0)
    return df


FEATURES = (
    [f"{c}_r{w}" for c in USAGE for w in WINDOWS]
    + [f"{c}_std" for c in USAGE]
    + [
        "games_prior", "career_games_prior", "fp_vol", "usage_trend", "prev_fp", "prev_games", "prev_opps",
        "implied_total", "spread", "game_total", "is_home", "rest", "div_game",
        "is_dome", "wind", "temp", "dvp_mult", "dvp_rank",
        "practice_code", "report_code",
        "vacated_target_share", "vacated_carry_share",
    ]
)


def pipeline(df: pd.DataFrame, scoring: str = SCORING) -> pd.DataFrame:
    """Feature construction with no row filtering.

    Rows for a week that has not been played yet can be appended to `df` before
    calling this: the shift-then-roll logic puts them last in each player's
    timeline, so their features are built from completed weeks and nothing else.
    """
    df = _base(df, scoring)
    df = _rolling(df)
    df = _prior_season(df)
    df = _defense_vs_position(df)
    df = _vacancy(df)
    df["dvp_rank"] = df.dvp_rank.astype("float64")
    return df


def build(df: pd.DataFrame, scoring: str = SCORING) -> pd.DataFrame:
    df = pipeline(df, scoring)

    # Players ruled Out are a hard zero in the app, not a prediction problem, and
    # training on them would teach the model to shade everyone down.
    df = df[df.report_code > 0]
    # Below this the rolling inputs are mostly empty and the naive baselines we
    # compare against have nothing to work with either. Counted over the career, so
    # week 1 of a returning player's season still qualifies.
    df = df[df.career_games_prior >= MIN_PRIOR_GAMES]
    # Outcome must exist: rows for an unplayed week are for prediction, not training.
    df = df[df.fp.notna()]
    return df.reset_index(drop=True)
