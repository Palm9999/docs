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
| **Quantile LightGBM** | **4.28** | **6.17** | **0.686** | **74.1%** |
| Season-to-date average | 4.58 | 6.47 | 0.650 | 72.8% |
| 50/50 blend | 4.57 | 6.43 | 0.658 | 72.8% |
| Last-3 average | 4.75 | 6.67 | 0.638 | 71.6% |
| App heuristic (current rule) | 4.73 | 6.64 | 0.639 | 71.9% |

**Start/sit accuracy** is how often the model ranks the better of two random
same-position players in the same week ahead of the other. It matters more than
MAE here, because the app never asks "how many points" — it asks "which of these
two do I start", and MAE is dominated by a handful of 30-point outlier weeks
nobody can predict.

The model beats the current app rule by **9.4% on MAE** and **2.2 points of
start/sit accuracy**. That second number is the honest one: moving from 71.9% to
74.1% is roughly one extra correct lineup call every other week. Real, but not
the kind of edge that wins a league on its own.

Model size was tuned rather than assumed, and the result was counter-intuitive:
400 trees at 31 leaves **overfit**, scoring worse than 100 trees at 15 leaves
while producing a model five times larger. The small model wins twice, since it
also has to fit in an APK.

### Per position

| Position | n | Model MAE | Baseline MAE | Model acc | Baseline acc |
|---|---|---|---|---|---|
| RB | 4,402 | 4.21 | 4.49 | 76.8% | 76.2% |
| WR | 7,136 | 4.24 | 4.57 | 75.1% | 74.3% |
| TE | 3,557 | 3.41 | 3.69 | 73.2% | 71.7% |
| QB | 1,910 | 6.21 | 6.43 | 67.5% | 67.1% |

**QB remains the weakest position by a wide margin**, and was an outright
negative result until the model was shrunk: at 400 trees it ranked quarterbacks
*worse* than a rolling average (66.7% against 67.1%). The smaller model turns
that around, but only to 67.5% against 67.1% — a margin thin enough that it
should not be treated as settled. Quarterbacks have the fewest rows and almost no
usage variance, since every starter plays every snap, so the opportunity features
that carry the other positions have nothing to say.

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
interval covered the true score only **65.5%** of the time instead of 70% — each
model is pulled toward the median by its own regularisation. `train.calibrate()`
fixes this with a conformal widening factor fit on held-out predictions, which
brings coverage to **71.6%**. Do not ship intervals without it; a floor that is
wrong more often than it claims is worse than no floor. (The 400-tree model
needed far more correction, covering only 56% raw — over-confidence is another
cost of the oversized model.)

A floor is deliberately **not clamped at zero**. Fantasy scoring really does go
negative, and clamping broke the `floor <= median` ordering for players whose
median sits near zero — caught by `WeeklyBundleTest` scoring a full live slate.

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

**Sleeper's player ids are patchy.** Its payload documents a `gsis_id` that would
join straight onto nflverse, but it is populated for only a minority of active
players — 155 of 817 active skill players, with CeeDee Lamb carrying neither a
gsis nor an espn id. `bridge.py` therefore falls back to a normalised
name-and-position match, which lifts coverage from 16% to 96%. The ~4% that
remain unmatched are fullbacks and deep bench, and they are listed by name on
every run rather than silently dropped.

## Phase 1: shipping it to the app

The model runs on the phone. A gradient-boosted ensemble is just summed decision
trees, so rather than add ~15MB of ONNX Runtime to evaluate a pile of
if-statements, `export.py` flattens each tree into parallel primitive arrays and
a ~120-line Kotlin walker evaluates them. The whole model is **299 KB gzipped**.

`ModelParityTest` proves the port is exact: 40 real feature rows scored by Python
at export time, re-scored by the Kotlin walker, **worst delta 0.0**. Half the
fixtures deliberately contain missing features, because NaN routing is where an
independent reimplementation drifts while still looking plausible — LightGBM only
sends a NaN down the default branch when that split was trained with missing
values present, and coerces it to zero otherwise.

Features are not computed on the phone. Rolling usage, defence adjustments and
vacated shares all need season-long history, so `build_week.py` publishes
ready-made feature vectors for one week — **64 KB gzipped for a 552-player
slate** — and the app scores them locally. That keeps projections instant and
offline while still allowing a re-score when the user asks a what-if question or
an injury designation flips on Sunday morning.

```bash
python3 export_model.py                      # model.json.gz + parity fixtures
python3 build_week.py --season 2026 --week 3 # week.json.gz for the app
```

Both artifacts carry their feature list, and the app refuses to score a bundle
whose list disagrees with the model's rather than lining the vectors up
positionally and producing confident nonsense.

`build_week.py` is the only part that needs to run on a schedule, and
`.github/workflows/weekly-bundle.yml` does it — rebuilding the bundle, logging
consensus projections and committing both. See the app README for pointing the
app at the result. Re-run `export_model.py` only when retraining, and ship the
new `model.json.gz` with the app.

`check_artifacts.py` guards the pairing. Bundle and model must come from the
same feature list or the app refuses to score, which is correct but a poor way
to discover it, so CI runs the same check on every push — and re-verifies the
parity fixtures from Python, giving a third implementation that has to agree.

Two things the scheduled job has to get right, both of which cost a wrong answer
rather than an error:

- **Cached data must not go stale.** Completed seasons never change and are
  cached; everything covering the current season is deleted before each run by
  `ingest.purge_live()`, because nflverse rewrites those files as games are
  played and a restored cache would quietly build last week's features.
- **Out of season is not a failure.** `build_week.py` exits 78 when there is no
  week to build, which the workflow treats as a skip.

## Layout

```
nflpredict/
  config.py      seasons, positions, quantiles, paths
  ingest.py      nflverse download + join into one player-week frame
  features.py    leakage-safe feature construction (67 features)
  train.py       per-position quantile LightGBM + conformal calibration
  backtest.py    walk-forward loop, baselines, metrics, play-rate table
  export.py      flattens the trees into the app's JSON format
  weekly.py      feature rows for a week that has not been played yet
  bridge.py      nflverse <-> Sleeper player id matching
run_phase0.py           the whole pipeline end to end
export_model.py         writes the app's model assets
build_week.py           writes one week's feature bundle
collect_projections.py  weekly Sleeper consensus logger
```

Data sources are all free and need no key: nflverse releases for stats, snap
counts, injuries and schedules (which carry `spread_line` and `total_line`, so
Vegas lines cost nothing), and Sleeper for live status. Sleeper's player payload
includes `gsis_id`, which is the join key back to everything here — that is what
lets model output map onto the app's existing Sleeper-keyed roster.
