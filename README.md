# Sit or Play

A fantasy football companion app that tracks your roster's weekly stats and
tells you who to **start** and who to **sit**, with the reasoning behind
each call.

## How the recommendation works

`GetSitStartRecommendationsUseCase` (see
`app/src/main/java/com/sitorplay/app/domain/usecase/`) is pure Kotlin with
no Android dependencies, so the logic is fully unit-tested
(`app/src/test/.../GetSitStartRecommendationsUseCaseTest.kt`).

For every player it computes an **adjusted projection**:

```
adjusted = projectedPoints * matchupMultiplier(opponentDefenseRank) * injuryMultiplier(injuryStatus)
```

- `matchupMultiplier`: tougher matchups (defense ranked in the top 8 against
  that position) knock points down; easier matchups (bottom 8) bump them up.
- `injuryMultiplier`: the odds the player actually takes the field, measured
  rather than assumed. This used to be a flat 0.85 for `QUESTIONABLE`; across
  2023-2025 a Questionable player played **60.6%** of the time, and practice
  participation splits that from 40.4% (did not practise) to 65.5% (full
  practice). `OUT` is 0.0 and `HEALTHY` is 1.0 as before. See
  `domain/prediction/Projection.kt` and `model/README.md`.

Within each position group, the top N players by adjusted projection fill
the standard lineup slots (1 QB, 2 RB, 2 WR, 1 TE, 1 K, 1 DEF); everyone
else is a **Sit**. Whichever RB/WR/TE is left with the best adjusted
projection fills the **FLEX** slot. Each recommendation carries a list of
plain-English reasons (matchup quality, injury status, ranking within the
position) shown on the player detail screen.

## The prediction model

Beyond the heuristic above, the app ships a gradient-boosted model trained on
free nflverse data (`model/`, see its README for the backtest). Over 2023-2025 it
ranks start/sit decisions correctly **74.1%** of the time against **71.9%** for
the heuristic alone.

It runs **entirely on device**. `domain/prediction/` holds a tree walker that
evaluates the exported ensemble directly — the model is 299 KB gzipped in
`assets/`, versus the ~15MB an ONNX runtime would add to do the same arithmetic.
`ModelParityTest` asserts the Kotlin output matches Python's to the last bit.

Each player gets a **floor / median / ceiling**, not a single number, because the
real question is not "how many points" but "which of these two do I start" — and
that depends on your matchup. Expected points are `P(plays) x E[points | plays]`,
keeping the two sources of uncertainty separate.

### Start/sit by win probability

The compare screen turns that range into the answer the projection cannot give.
Set **points needed from this slot** to what your matchup actually demands, and
each player is scored on `P(they clear it)` rather than on projected points.

The two disagree often enough to matter. Two players with the same 12-point
median but different spreads are genuinely different starts: needing 4 points,
the steady one wins comfortably; needing 20, the boom-or-bust one is the only
option that can get there at all. Drag the slider and the recommendation flips
at the crossover. When the call contradicts raw projected points the screen says
so explicitly, because that is exactly when the tool is worth having.

Each player's range is fitted to two half-normals joined at the median — fantasy
outcomes are right-skewed, so a single normal would understate ceilings and
overstate floors, and this reproduces p15/p50/p85 exactly.

Features are too heavy to compute on a phone — rolling usage, schedule-adjusted
defence, vacated target share — so `model/build_week.py` publishes a small weekly
bundle (64 KB for a full slate) that the app scores locally. Live injury news can
override a feature and re-score instantly without waiting for a new bundle.

### Setting it up

The model ships in the APK and needs nothing. The weekly bundle does:

1. Run `python3 model/build_week.py` once a week (see `model/README.md`).
2. Upload the resulting `week.json.gz` anywhere the phone can reach it over
   HTTPS — a release asset that the weekly run overwrites works well.
3. Paste that URL into **Settings → Prediction model**.

There is no default URL, because the file is yours and hosted wherever suits
you. Until one is set the app ranks lineups with the projection heuristic, which
is a working fallback rather than an error state; the settings screen says which
of the two is currently driving your recommendations.

`ModelRefreshWorker` then re-fetches every 12 hours. That is deliberately more
often than the bundle changes: the moment that matters is Sunday morning when
inactives land, and a weekly schedule anchored to the wrong day would routinely
be a week stale.

Not modelled: kickers and defenses, which keep the plain projection.

## Live rosters and stats

Adding a player searches Sleeper's free, public NFL player directory
(no API key) instead of typing everything in by hand — pick a real player
and their team/position/injury status auto-fill, along with this week's
live projected points and opponent (matchup pulled from ESPN's public
scoreboard). Tap the refresh icon on the roster screen to re-sync every
live-linked player's projection, matchup, and injury status in one go —
useful right up until kickoff as injury reports update.

A manual "Add player" form is kept as a fallback for anyone the live
directory doesn't have. One thing that *isn't* live yet: opponent defense
rank (matchup difficulty) has no good free data source, so it defaults to
16 (average) and stays a manual, editable input — see
`data/sync/NflDataRepository.kt` for where to plug in a real one (a paid
provider like SportsData.io would give you defense-vs-position ranks
directly).

Both APIs are free and require no signup, but they're unofficial and
undocumented — treat them as best-effort. All the sync code degrades
gracefully (falls back to "no results" / a snackbar error) rather than
crashing if either API changes shape or is unreachable.

## Architecture

MVVM with a repository pattern, built for Jetpack Compose:

```
ui/            Compose screens + ViewModels (Roster, Player Detail, Add Player)
domain/
  model/       Player/Recommendation types (no Android deps)
  prediction/  on-device GBM scorer, weekly feature bundle, play-rate table
  usecase/     the sit/start recommendation engine
data/
  local/       Room entities/DAOs for the roster and the cached NFL player directory
  remote/      Retrofit APIs + DTOs for Sleeper (players/projections) and ESPN (scoreboard)
  sync/        NflDataRepository — orchestrates search, weekly context, and roster sync
  repository/  PlayerRepository interface + Room-backed implementation
  seed/        Sample roster (linked to real Sleeper player IDs) used to seed a fresh install
di/            Hilt modules wiring Room, Retrofit/OkHttp, and the repositories
navigation/    Jetpack Navigation Compose graph
```

## Stack

- Kotlin, Jetpack Compose, Material 3
- MVVM + Hilt for dependency injection
- Room for local persistence (roster + cached player directory)
- Retrofit + kotlinx.serialization + OkHttp for live data
- Navigation Compose
- JUnit for the recommendation engine's unit tests

## Building

Open the project root in Android Studio (Koala or newer) and let it sync,
or from the command line:

```bash
./gradlew assembleDebug   # build the debug APK
./gradlew test            # run unit tests, including the recommendation engine
```

Minimum SDK 26, target/compile SDK 35.
