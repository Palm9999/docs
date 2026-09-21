# Phase 0 — does a model beat the rules the app ships today?

A Python pipeline that trains per-position fantasy-point models on free nflverse
data and backtests them against the heuristic in
`GetSitStartRecommendationsUseCase`. Nothing here is wired into the Android app.
The point of Phase 0 is to find out whether the modelling earns its place before
any Kotlin gets written.

```bash
pip install -r requirements.txt
python3 run_phase0.py                 # ~8 min: downloads, builds, backtests
python3 run_phase0.py --scoring half_ppr --rebuild
```

## Result

Walk-forward over 2023–2025, 17,005 predictions. Every model is trained only on
player-weeks that happened strictly earlier than the week it predicts.

| Model | MAE | RMSE | Spearman | Start/sit accuracy |
|---|---|---|---|---|
| **Quantile LightGBM** | **4.32** | **6.19** | **0.681** | **73.4%** |
| Season-to-date average | 4.58 | 6.47 | 0.650 | 72.8% |
| 50/50 blend | 4.57 | 6.43 | 0.658 | 72.8% |
| Last-3 average | 4.75 | 6.67 | 0.638 | 71.6% |
| App heuristic (current rule) | 4.73 | 6.64 | 0.639 | 71.9% |

**Start/sit accuracy** is how often the model ranks the better of two random
same-position players in the same week ahead of the other. It matters more than
MAE here, because the app never asks "how many points" — it asks "which of these
two do I start", and MAE is dominated by a handful of 30-point outlier weeks
nobody can predict.

The model beats the current app rule by **8.6% on MAE** and **1.5 points of
start/sit accuracy**. That second number is the honest one: moving from 71.9% to
73.4% is roughly one extra correct lineup call every three weeks. Real, but not
the kind of edge that wins a league on its own.

### Per position

| Position | n | Model MAE | Baseline MAE | Model acc | Baseline acc |
|---|---|---|---|---|---|
| RB | 4,402 | 4.26 | 4.49 | 76.6% | 76.2% |
| WR | 7,136 | 4.27 | 4.57 | 75.0% | 74.3% |
| TE | 3,557 | 3.46 | 3.69 | 72.6% | 71.7% |
| QB | 1,910 | 6.25 | 6.43 | **66.7%** | **67.1%** |

**QB is where the model fails.** It edges the baseline on MAE but is *worse* at
the ranking question, which is the one that matters. QB has the fewest rows and
the least usage variance — every starter plays every snap, so the usage features
that carry RB and WR have nothing to say. Until that is fixed, the app should
keep showing the plain projection for quarterbacks rather than a model number.

## What the model actually learned

Feature importance is consistent with the premise that **opportunity predicts
better than efficiency**:

- **RB/WR/TE**: `opportunities` (targets + carries) and `snap_pct` rolling
  averages dominate, above any yardage or touchdown feature.
- **QB**: `implied_total` — the Vegas-derived points a team is expected to score
  — ranks second overall, behind only recent scoring.

`dvp_mult`, the shrunk defense-vs-position multiplier, lands in the top 8 for QB
and TE but not RB or WR. Matchup matters less than the app's current ±10%
multiplier assumes.

## Two findings the app can use immediately

**The Questionable multiplier is wrong.** The app multiplies projected points by
`0.85` for a Questionable player. Measured over 2023–2025:

| Designation | Practice | P(plays) | n |
|---|---|---|---|
| Questionable | Full | 65.5% | 411 |
| Questionable | Limited | 60.6% | 1,362 |
| Questionable | DNP | 40.4% | 339 |
| Doubtful | Limited | 1.5% | 68 |
| Out | any | 0.0% | 1,609 |

A Questionable player misses roughly **two games in five**, so `0.85` is far too
generous — and the spread between Full and DNP practice is 25 points of play
probability that the single `QUESTIONABLE` enum currently throws away. Splitting
injury handling into `P(play) × E[points | play]`, with practice participation as
the input, is a change worth making regardless of whether the model ships.
Sleeper already returns `practice_participation` on the same payload the app
reads.

**Raw quantile models lie about their range.** Fitted independently, the p15–p85
interval covered the true score only **56%** of the time instead of 70% — each
model is pulled toward the median by its own regularisation. `train.calibrate()`
fixes this with a conformal widening factor fit on held-out predictions, which
brings coverage to **73%**. Do not ship intervals without it; a floor that is
wrong half the time is worse than no floor.

## Leakage control

The single thing most likely to make these numbers fiction:

- Every usage statistic is **shifted one game before it is rolled**, so a week's
  own production can never reach its own features.
- Defense-vs-position and the league baseline it is shrunk toward are **expanding
  windows over prior weeks only**.
- The backtest **retrains before every week** on strictly earlier data. A random
  train/test split would leak the future through the defense aggregates.
- The injury report is the one input read from the target week, because the final
  report is published days before kickoff. `load_injuries()` keeps the **last**
  report of the week, which is what a user would see.

Rolling windows deliberately cross the season boundary. Resetting them each
September left weeks 1–2 with no history and dropped 3,600 rows — including the
weeks users most want help with.

## Known gaps

**The consensus baseline is missing.** The baselines above are rolling averages,
which is a weaker bar than the Sleeper projection the app actually displays.
Sleeper only serves the current week and no free source publishes historical
consensus, so this comparison cannot be run retroactively.
`collect_projections.py` logs each week's projections keyed on `gsis_id` so the
comparison becomes possible going forward — run it weekly, and after a season
there is enough to answer the real question. **Until then, treat "beats the app
heuristic" as proven and "beats consensus" as untested.**

Also open: kickers and defenses are not modelled; `vacated_target_share` is built
from injury reports rather than confirmed inactives, so it misses late scratches;
and no weather feature beyond wind and temperature.

## Layout

```
nflpredict/
  config.py      seasons, positions, quantiles, paths
  ingest.py      nflverse download + join into one player-week frame
  features.py    leakage-safe feature construction (67 features)
  train.py       per-position quantile LightGBM + conformal calibration
  backtest.py    walk-forward loop, baselines, metrics, play-rate table
run_phase0.py           the whole pipeline end to end
collect_projections.py  weekly Sleeper consensus logger
```

Data sources are all free and need no key: nflverse releases for stats, snap
counts, injuries and schedules (which carry `spread_line` and `total_line`, so
Vegas lines cost nothing), and Sleeper for live status. Sleeper's player payload
includes `gsis_id`, which is the join key back to everything here — that is what
lets model output map onto the app's existing Sleeper-keyed roster.
