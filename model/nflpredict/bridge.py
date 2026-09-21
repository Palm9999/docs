"""Map nflverse player ids onto Sleeper player ids.

Sleeper documents a `gsis_id` on its player payload, which would make this a
one-line join, but in practice it is populated for only a minority of active
players -- 155 of 817 active skill players when this was written, with stars
like CeeDee Lamb carrying neither gsis nor espn id. Relying on it alone silently
drops most of the roster, so identifiers are tried in order of trustworthiness
and a normalised name match picks up the rest.

Name matching is the fallback, never the first choice: it keys on first initial,
surname and position, and refuses any key that matches more than one player
unless the team breaks the tie.
"""
from __future__ import annotations

import re

import pandas as pd

from .config import DATA

_SUFFIXES = re.compile(r"\b(jr|sr|ii|iii|iv|v)\b")


def name_key(name: str | None, position: str | None) -> str | None:
    """First initial + surname + position, stripped of punctuation and suffixes.

    Full first names are avoided deliberately: the two sources disagree on
    "Josh"/"Joshua" and similar far more often than they disagree on an initial.
    """
    if not name or not position:
        return None
    cleaned = name.lower().replace(".", "").replace("'", "").replace("-", " ")
    cleaned = _SUFFIXES.sub("", cleaned)
    parts = re.sub(r"[^a-z ]", "", cleaned).split()
    if not parts:
        return None
    return f"{parts[0][0]}{parts[-1]}|{position}"


def _espn_to_gsis() -> dict[str, str]:
    path = DATA / "players.csv"
    if not path.exists():
        return {}
    players = pd.read_csv(path, low_memory=False, usecols=["gsis_id", "espn_id"])
    players = players.dropna(subset=["gsis_id", "espn_id"])
    return {
        str(int(espn)): gsis
        for gsis, espn in zip(players.gsis_id, players.espn_id)
    }


def build(sleeper_players: dict[str, dict]) -> tuple[dict[str, str], dict[str, str]]:
    """Returns (gsis_id -> sleeper_id, gsis_id -> current team).

    `sleeper_players` is Sleeper's `/v1/players/nfl` payload keyed by its own id.
    """
    espn_to_gsis = _espn_to_gsis()
    by_gsis: dict[str, str] = {}
    teams: dict[str, str] = {}
    by_name: dict[str, list[dict]] = {}

    for sleeper_id, record in sleeper_players.items():
        position = record.get("position")
        if position not in {"QB", "RB", "WR", "TE"}:
            continue

        gsis = record.get("gsis_id")
        if not gsis:
            espn = record.get("espn_id")
            if espn is not None:
                gsis = espn_to_gsis.get(str(int(espn)))

        if gsis:
            by_gsis.setdefault(gsis, sleeper_id)
            if record.get("team"):
                teams.setdefault(gsis, record["team"])

        key = name_key(record.get("full_name"), position)
        if key:
            by_name.setdefault(key, []).append({"sleeper_id": sleeper_id, **record})

    return by_gsis, teams, by_name


def resolve(
    gsis_id: str,
    name: str,
    position: str,
    team: str | None,
    by_gsis: dict[str, str],
    by_name: dict[str, list[dict]],
) -> str | None:
    """Best Sleeper id for one nflverse player, or None if it cannot be pinned down."""
    direct = by_gsis.get(gsis_id)
    if direct:
        return direct

    candidates = by_name.get(name_key(name, position) or "", [])
    if len(candidates) == 1:
        return candidates[0]["sleeper_id"]
    if len(candidates) > 1 and team:
        on_team = [c for c in candidates if c.get("team") == team]
        if len(on_team) == 1:
            return on_team[0]["sleeper_id"]
    return None
