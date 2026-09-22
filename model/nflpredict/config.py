"""Shared configuration for the Phase 0 pipeline."""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "data"
OUT = ROOT / "artifacts"

NFLVERSE = "https://github.com/nflverse/nflverse-data/releases/download"

# 2021+ only: the 2020 COVID season distorts usage and snap patterns badly enough
# that including it hurt more than the extra rows helped. The current season is
# included so live weeks can be projected; partial-season files are tolerated.
FIRST_SEASON = 2021
CURRENT_SEASON = 2026
SEASONS = list(range(FIRST_SEASON, CURRENT_SEASON + 1))
POSITIONS = ["QB", "RB", "WR", "TE"]

# Quantiles trained per position -> floor / median / ceiling.
QUANTILES = [0.15, 0.50, 0.85]

# Backtest predicts these seasons; every model is trained only on earlier weeks.
BACKTEST_SEASONS = [2023, 2024, 2025]

# A player needs this many prior games in the season before we trust rolling usage.
MIN_PRIOR_GAMES = 2

SCORING = "ppr"  # one of: ppr, half_ppr, standard
