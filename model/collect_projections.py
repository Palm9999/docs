#!/usr/bin/env python3
"""Log this week's Sleeper consensus projections so they can be scored later.

Sleeper only serves the current week, and no free source publishes historical
consensus projections. That makes "is this model better than the projection the
app already shows?" a question the backtest cannot answer today -- the baselines
in run_phase0.py are rolling averages, which is a weaker bar.

Running this once a week builds the archive that makes the real comparison
possible. Keyed on gsis_id, which Sleeper carries and nflverse uses, so the rows
join straight onto the backtest output.

    python3 collect_projections.py            # current week, from Sleeper's state
    python3 collect_projections.py --week 5 --season 2026
"""
from __future__ import annotations

import argparse
import csv
import json
import sys
import urllib.request
from pathlib import Path

DEST = Path(__file__).resolve().parent / "data" / "consensus_projections.csv"
POSITIONS = {"QB", "RB", "WR", "TE"}
FIELDS = [
    "season", "week", "sleeper_id", "gsis_id", "name", "position", "team",
    "projected_points", "injury_status", "practice_participation", "depth_chart_order",
]


def _get(url: str):
    with urllib.request.urlopen(url, timeout=60) as r:
        return json.load(r)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--season", type=int)
    ap.add_argument("--week", type=int)
    args = ap.parse_args()

    state = _get("https://api.sleeper.app/v1/state/nfl")
    season = args.season or int(state["season"])
    week = args.week or int(state["week"])

    players = _get("https://api.sleeper.app/v1/players/nfl")
    proj = _get(
        f"https://api.sleeper.app/projections/nfl/{season}/{week}"
        "?season_type=regular&position[]=QB&position[]=RB&position[]=WR&position[]=TE"
    )
    # The projections endpoint has returned both a list and an id-keyed dict.
    entries = proj.values() if isinstance(proj, dict) else proj

    rows = []
    for entry in entries:
        pid = str(entry.get("player_id", ""))
        meta = players.get(pid)
        if not meta or meta.get("position") not in POSITIONS:
            continue
        stats = entry.get("stats") or {}
        rows.append({
            "season": season, "week": week, "sleeper_id": pid,
            "gsis_id": meta.get("gsis_id"), "name": meta.get("full_name"),
            "position": meta.get("position"), "team": meta.get("team"),
            "projected_points": stats.get("pts_ppr"),
            "injury_status": meta.get("injury_status"),
            "practice_participation": meta.get("practice_participation"),
            "depth_chart_order": meta.get("depth_chart_order"),
        })

    if not rows:
        print("no projections returned -- Sleeper may not have posted this week yet",
              file=sys.stderr)
        sys.exit(1)

    DEST.parent.mkdir(parents=True, exist_ok=True)
    existing, seen = [], set()
    if DEST.exists():
        with DEST.open() as f:
            existing = list(csv.DictReader(f))
        seen = {(r["season"], r["week"], r["sleeper_id"]) for r in existing}

    fresh = [r for r in rows if (str(r["season"]), str(r["week"]), r["sleeper_id"]) not in seen]
    with DEST.open("w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=FIELDS)
        w.writeheader()
        w.writerows(existing + fresh)

    print(f"{season} week {week}: {len(fresh)} new rows "
          f"({len(existing) + len(fresh)} total) -> {DEST}")


if __name__ == "__main__":
    main()
