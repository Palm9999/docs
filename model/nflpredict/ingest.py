"""Download and assemble raw nflverse tables into one player-week frame.

Every row is one (player, season, week) that we may want to predict. Outcome
columns live alongside the raw inputs here; the leakage-safe lagging happens in
features.py, never in this module.
"""
from __future__ import annotations

import sys
import urllib.request

import numpy as np
import pandas as pd

from .config import DATA, NFLVERSE, POSITIONS, SEASONS

# release -> filename template. 2021+ all use the modern stats_player_week files.
FILES = {
    "stats": ("stats_player", "stats_player_week_{year}.csv", "stats_week_{year}.csv"),
    "snaps": ("snap_counts", "snap_counts_{year}.csv", "snap_counts_{year}.csv"),
    "injuries": ("injuries", "injuries_{year}.csv", "injuries_{year}.csv"),
}
STATIC = {
    "games.csv": ("schedules", "games.csv"),
    "players.csv": ("players", "players.csv"),
}


def _fetch(url: str, dest) -> bool:
    """Returns False for a release file that does not exist yet.

    The current season's files appear as the year progresses, so a missing one is
    normal in September and must not abort the whole download.
    """
    if dest.exists() and dest.stat().st_size > 1024:
        return True
    print(f"  downloading {dest.name}", file=sys.stderr)
    try:
        urllib.request.urlretrieve(url, dest)
        return True
    except urllib.error.HTTPError as exc:
        if exc.code == 404:
            print(f"  (not published yet: {dest.name})", file=sys.stderr)
            dest.unlink(missing_ok=True)
            return False
        raise


def download() -> None:
    DATA.mkdir(parents=True, exist_ok=True)
    for year in SEASONS:
        for release, remote, local in FILES.values():
            _fetch(
                f"{NFLVERSE}/{release}/{remote.format(year=year)}",
                DATA / local.format(year=year),
            )
    for local, (release, remote) in STATIC.items():
        _fetch(f"{NFLVERSE}/{release}/{remote}", DATA / local)


def _concat(template: str) -> pd.DataFrame:
    frames = [
        pd.read_csv(DATA / template.format(year=y), low_memory=False)
        for y in SEASONS
        if (DATA / template.format(year=y)).exists()
    ]
    if not frames:
        raise FileNotFoundError(f"no files matching {template} -- run download() first")
    return pd.concat(frames, ignore_index=True)


# nflverse spells practice participation out in full; collapse to an ordinal where
# higher means healthier. 3 == not on the injury report at all.
PRACTICE = {
    "Did Not Participate In Practice": 0,
    "Limited Participation in Practice": 1,
    "Full Participation in Practice": 2,
}
REPORT = {"Out": 0, "Doubtful": 1, "Questionable": 2}


def load_games() -> pd.DataFrame:
    """One row per team-game with the pre-kickoff market and venue context."""
    g = pd.read_csv(DATA / "games.csv", low_memory=False)
    g = g[(g.season.isin(SEASONS)) & (g.game_type == "REG")].copy()

    # spread_line is from the home team's perspective and positive means the home
    # team is favored, so the favored side gets the larger implied total.
    g["home_implied"] = g.total_line / 2 + g.spread_line / 2
    g["away_implied"] = g.total_line / 2 - g.spread_line / 2

    home = pd.DataFrame(
        {
            "game_id": g.game_id, "season": g.season, "week": g.week,
            "team": g.home_team, "opponent": g.away_team, "is_home": 1,
            "implied_total": g.home_implied, "spread": -g.spread_line,
            "game_total": g.total_line, "rest": g.home_rest,
        }
    )
    away = pd.DataFrame(
        {
            "game_id": g.game_id, "season": g.season, "week": g.week,
            "team": g.away_team, "opponent": g.home_team, "is_home": 0,
            "implied_total": g.away_implied, "spread": g.spread_line,
            "game_total": g.total_line, "rest": g.away_rest,
        }
    )
    ctx = pd.concat([home, away], ignore_index=True)

    venue = g[["game_id", "roof", "wind", "temp", "div_game"]].copy()
    ctx = ctx.merge(venue, on="game_id", how="left")
    ctx["is_dome"] = ctx.roof.isin(["dome", "closed"]).astype(int)
    # Wind is only recorded for outdoor games; indoors is genuinely zero wind.
    ctx["wind"] = np.where(ctx.is_dome == 1, 0.0, ctx.wind.fillna(8.0))
    ctx["temp"] = np.where(ctx.is_dome == 1, 70.0, ctx.temp.fillna(60.0))
    return ctx.drop(columns=["roof"])


def load_injuries() -> pd.DataFrame:
    inj = _concat("injuries_{year}.csv")
    inj = inj[inj.game_type == "REG"].copy()
    inj["practice_code"] = inj.practice_status.map(PRACTICE)
    inj["report_code"] = inj.report_status.map(REPORT)
    inj = inj.rename(columns={"gsis_id": "player_id"})
    inj = inj.dropna(subset=["player_id"])
    # A player appears on several daily reports in a week (Wed through Fri). The
    # last one is the final pre-game designation and the only one a user would ever
    # see, so sort by report date and keep it.
    inj = inj.sort_values(["season", "week", "player_id", "date_modified"])
    inj = (
        inj.groupby(["season", "week", "player_id"], as_index=False)
        .agg(
            team=("team", "last"),
            position=("position", "last"),
            practice_code=("practice_code", "last"),
            report_code=("report_code", "last"),
        )
    )
    return inj


def load_snaps() -> pd.DataFrame:
    """Snap share, bridged from PFR ids to gsis ids via the players table."""
    snaps = _concat("snap_counts_{year}.csv")
    snaps = snaps[snaps.game_type == "REG"]
    players = pd.read_csv(DATA / "players.csv", low_memory=False)
    bridge = players[["gsis_id", "pfr_id"]].dropna().rename(columns={"pfr_id": "pfr_player_id"})
    snaps = snaps.merge(bridge, on="pfr_player_id", how="inner")
    return (
        snaps.rename(columns={"gsis_id": "player_id", "offense_pct": "snap_pct"})
        .groupby(["season", "week", "player_id"], as_index=False)
        .agg(snap_pct=("snap_pct", "max"))
    )


def build_player_weeks() -> pd.DataFrame:
    """The raw fact table: one row per offensive player-week that was played."""
    download()
    stats = _concat("stats_week_{year}.csv")
    stats = stats[(stats.season_type == "REG") & (stats.position.isin(POSITIONS))].copy()

    keep = [
        "player_id", "player_display_name", "position", "season", "week", "team",
        "opponent_team", "fantasy_points", "fantasy_points_ppr", "receptions",
        "targets", "carries", "receiving_yards", "rushing_yards", "passing_yards",
        "receiving_air_yards", "target_share", "air_yards_share", "wopr",
        "receiving_tds", "rushing_tds", "passing_tds", "attempts", "completions",
        "receiving_epa", "rushing_epa", "passing_epa",
    ]
    stats = stats[[c for c in keep if c in stats.columns]]
    stats = stats.rename(columns={"player_display_name": "name", "opponent_team": "opponent"})

    df = stats.merge(load_snaps(), on=["season", "week", "player_id"], how="left")
    df = df.merge(
        load_injuries().drop(columns=["team", "position"]),
        on=["season", "week", "player_id"], how="left"
    )
    # Absent from the injury report means healthy, which is strictly better than
    # any listed practice status, so it sits one step above "Full".
    df["practice_code"] = df.practice_code.fillna(3)
    df["report_code"] = df.report_code.fillna(3)

    ctx = load_games()
    df = df.merge(
        ctx.drop(columns=["opponent"]), on=["season", "week", "team"], how="left"
    )
    df = df.dropna(subset=["implied_total"])
    return df.sort_values(["player_id", "season", "week"]).reset_index(drop=True)
