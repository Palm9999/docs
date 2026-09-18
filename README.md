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
- `injuryMultiplier`: `HEALTHY` = 1.0, `QUESTIONABLE` = 0.85,
  `DOUBTFUL` = 0.5, `OUT` = 0.0.

Within each position group, the top N players by adjusted projection fill
the standard lineup slots (1 QB, 2 RB, 2 WR, 1 TE, 1 K, 1 DEF); everyone
else is a **Sit**. Whichever RB/WR/TE is left with the best adjusted
projection fills the **FLEX** slot. Each recommendation carries a list of
plain-English reasons (matchup quality, injury status, ranking within the
position) shown on the player detail screen.

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
domain/        Player/Recommendation models + the recommendation use case (no Android deps)
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
