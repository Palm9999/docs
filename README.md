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

## Architecture

MVVM with a repository pattern, built for Jetpack Compose:

```
ui/            Compose screens + ViewModels (Roster, Player Detail, Add Player)
domain/        Player/Recommendation models + the recommendation use case (no Android deps)
data/
  local/       Room entities, DAO, database, type converters
  repository/  PlayerRepository interface + Room-backed implementation
  seed/        Sample roster used to seed a fresh install
di/            Hilt modules wiring Room + the repository
navigation/    Jetpack Navigation Compose graph
```

Data currently comes from an in-memory seed (`SeedPlayers`) written to Room
on first launch, and players can also be added by hand from the app. To
plug in a real stats/projections API, add a remote data source that maps
API responses into the `Player` domain model and have
`PlayerRepositoryImpl` merge it with Room — the use case and UI don't need
to change.

## Stack

- Kotlin, Jetpack Compose, Material 3
- MVVM + Hilt for dependency injection
- Room for local persistence
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
