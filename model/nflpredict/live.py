"""Injury status from Sleeper, for a week nflverse has not published yet.

nflverse posts an official injury report only once one exists, which means a
bundle built on Monday or Tuesday has no report for the coming Sunday. The
pipeline would then fill every player in as "not on the report" -- the same code
that means healthy -- and the model, having been trained on rows where that code
genuinely meant healthy, would read it as a confident statement that nobody is
hurt. Every vacancy feature would be zero for the same reason.

Sleeper carries the same two fields live, so this converts its payload into the
nflverse coding and the caller prefers it wherever it disagrees. Sleeper is
always at least as current, so "prefer" means "always, where present".
"""
from __future__ import annotations

import pandas as pd

from .ingest import PRACTICE

# Sleeper's designations mapped onto nflverse's report_code. The season-ending
# and roster-exempt statuses all collapse to Out: for the purpose of a single
# week they mean the same thing, which is that he is not playing.
REPORT = {
    "out": 0,
    "ir": 0,
    "pup": 0,
    "sus": 0,
    "nfi": 0,
    "dnr": 0,
    "doubtful": 1,
    "questionable": 2,
}


def injuries_from_sleeper(players: dict[str, dict], season: int, week: int) -> pd.DataFrame:
    """One row per injured player, in the same shape as ingest.load_injuries().

    Only players Sleeper says something about are returned; an absent player is
    left for the nflverse report or for the healthy default, rather than being
    asserted healthy here.
    """
    rows = []
    for record in players.values():
        gsis = record.get("gsis_id")
        if not gsis:
            continue

        status = (record.get("injury_status") or "").strip().lower()
        practice_raw = (record.get("practice_participation") or "").strip()
        if not status and not practice_raw:
            continue

        report_code = REPORT.get(status)
        practice_code = PRACTICE.get(practice_raw)
        if report_code is None and practice_code is None:
            continue

        rows.append({
            "season": season,
            "week": week,
            "player_id": gsis,
            "team": record.get("team"),
            "position": record.get("position"),
            # A player with a practice note but no game designation is on the
            # report without a status yet, which is not the same as healthy.
            "report_code": 3 if report_code is None else report_code,
            "practice_code": 3 if practice_code is None else practice_code,
        })

    if not rows:
        return pd.DataFrame(
            columns=["season", "week", "player_id", "team", "position",
                     "report_code", "practice_code"]
        )
    return pd.DataFrame(rows)


def merge_injuries(official: pd.DataFrame, live: pd.DataFrame) -> pd.DataFrame:
    """Official report plus live status, with live winning any disagreement."""
    if live.empty:
        return official
    key = ["season", "week", "player_id"]
    superseded = official.merge(live[key], on=key, how="left", indicator=True)
    kept = superseded[superseded._merge == "left_only"].drop(columns="_merge")
    return pd.concat([kept, live], ignore_index=True)
