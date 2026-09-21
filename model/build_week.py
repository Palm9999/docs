#!/usr/bin/env python3
"""Build the feature bundle the app scores for one upcoming week.

The app ships the model but not the data behind it: rolling usage, defence
adjustments and vacated-target shares all need season-long history that a phone
has no business downloading. So this runs weekly on a machine that has the data
and publishes a small file of ready-made feature vectors, which the app scores on
device -- keeping predictions instant, offline, and re-runnable when the user asks
a what-if question.

    python3 build_week.py                  # next unplayed week, per Sleeper
    python3 build_week.py --season 2026 --week 3

Rows are keyed by Sleeper player id, which is what the app's roster already
stores, bridged through the gsis id that Sleeper carries on its own payload.
"""
from __future__ import annotations

import argparse
import gzip
import json
import sys
import urllib.request
from datetime import datetime, timezone

import pandas as pd

from nflpredict import bridge, weekly
from nflpredict.config import OUT
from nflpredict.features import FEATURES
from nflpredict.ingest import build_player_weeks

SLEEPER_PLAYERS = "https://api.sleeper.app/v1/players/nfl"
SLEEPER_STATE = "https://api.sleeper.app/v1/state/nfl"
FORMAT_VERSION = 1

# Distinct from 1 so a scheduled job can tell "no games this week" apart from a
# real failure and skip the commit instead of going red.
NOTHING_TO_BUILD = 78


def _get(url: str):
    with urllib.request.urlopen(url, timeout=90) as response:
        return json.load(response)


def sleeper_directory():
    """Sleeper's player payload, reduced to the id bridges build_week needs."""
    return bridge.build(_get(SLEEPER_PLAYERS))


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--season", type=int)
    ap.add_argument("--week", type=int)
    ap.add_argument("--out", default=None, help="output path (default artifacts/week.json.gz)")
    args = ap.parse_args()

    state = _get(SLEEPER_STATE)
    season = args.season or int(state["season"])
    week = args.week or int(state["week"])

    # A cron runs all year; February through August there is no week to build.
    # That is a normal no-op, not a failure, so say so and exit clean.
    if str(state.get("season_type")) not in ("regular", "post") and not args.season:
        print(f"{season} is in the {state.get('season_type')} phase -- nothing to build",
              file=sys.stderr)
        sys.exit(NOTHING_TO_BUILD)

    print("loading history...", file=sys.stderr)
    history = build_player_weeks()
    by_gsis, teams, by_name = sleeper_directory()

    try:
        rows = weekly.build_week(history, season, week, teams=teams)
    except ValueError as exc:
        print(f"{exc} -- nothing to build", file=sys.stderr)
        sys.exit(NOTHING_TO_BUILD)
    if rows.empty:
        print(f"no rows built for {season} week {week} -- nothing to build", file=sys.stderr)
        sys.exit(NOTHING_TO_BUILD)

    matrix = weekly.feature_matrix(rows)

    players, unmatched = [], []
    for (_, row), features in zip(rows.iterrows(), matrix):
        sleeper_id = bridge.resolve(
            row.player_id, row["name"], row.position, row.team, by_gsis, by_name
        )
        if sleeper_id is None:
            unmatched.append(f"{row['name']} ({row.position}, {row.team})")
            continue
        players.append({
            "sleeper_id": sleeper_id,
            "gsis_id": row.player_id,
            "name": row["name"],
            "position": row.position,
            "team": row.team,
            "opponent": row.opponent,
            "f": features,
        })

    bundle = {
        "format_version": FORMAT_VERSION,
        "season": season,
        "week": week,
        "generated": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        # The app asserts this matches the model's own feature list before scoring
        # positionally, so a stale bundle fails loudly instead of scoring garbage.
        "feature_order": FEATURES,
        "players": players,
    }

    OUT.mkdir(parents=True, exist_ok=True)
    path = args.out or (OUT / "week.json.gz")
    raw = json.dumps(bundle, separators=(",", ":")).encode()
    with gzip.open(path, "wb", compresslevel=9) as handle:
        handle.write(raw)

    matched_pct = 100 * len(players) / max(len(rows), 1)
    print(f"{season} week {week}: {len(players)} of {len(rows)} players "
          f"({matched_pct:.0f}% matched to a Sleeper id)")
    if unmatched:
        print(f"  unmatched ({len(unmatched)}): " + ", ".join(unmatched[:8])
              + (" ..." if len(unmatched) > 8 else ""))
    print(f"  {len(FEATURES)} features | {len(raw) / 1024:.0f} KB raw "
          f"| {path.stat().st_size / 1024:.0f} KB gzipped")
    print(f"  by position: {rows.position.value_counts().to_dict()}")
    print(f"-> {path}")


if __name__ == "__main__":
    main()
