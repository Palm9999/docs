package com.sitorplay.app.ui.playerdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sitorplay.app.domain.model.Call
import com.sitorplay.app.domain.model.TrendDirection
import com.sitorplay.app.domain.prediction.WeeklyPlayer
import com.sitorplay.app.ui.theme.SitRed
import com.sitorplay.app.ui.theme.StartGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerDetailScreen(
    onBack: () -> Unit,
    viewModel: PlayerDetailViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val recommendation by viewModel.recommendation.collectAsState()
    val extras by viewModel.extras.collectAsState()
    val whatIf by viewModel.whatIf.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(recommendation?.player?.name ?: "Player") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.removePlayer(onBack) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove player")
                    }
                }
            )
        }
    ) { padding ->
        val current = recommendation
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (current == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                val callColor = if (current.call == Call.START) StartGreen else SitRed
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(color = callColor.copy(alpha = 0.15f), shape = MaterialTheme.shapes.medium) {
                        Text(
                            text = if (current.call == Call.START) "START" else "SIT",
                            color = callColor,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    Text(
                        "${current.player.nflTeam} vs ${current.player.opponent}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Adjusted projection: ${"%.1f".format(current.adjustedProjection)} pts",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    current.projection?.let { projection ->
                        // Floor and ceiling are the 15th and 85th percentiles, so
                        // roughly seven weeks in ten land inside this band.
                        Text(
                            "Floor ${"%.1f".format(projection.range.floor)} · " +
                                "Median ${"%.1f".format(projection.range.median)} · " +
                                "Ceiling ${"%.1f".format(projection.range.ceiling)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (projection.playProbability < 1.0) {
                            Text(
                                "${"%.0f".format(projection.playProbability * 100)}% chance of playing, " +
                                    "based on the injury report",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (whatIf.isAvailable) {
                        WhatIfSection(
                            state = whatIf,
                            onToggle = viewModel::toggleTeammate,
                            onClear = viewModel::clearScenario
                        )
                    }
                    Text("Why:", style = MaterialTheme.typography.titleMedium)
                    current.reasons.forEach { reason ->
                        Text("• $reason", style = MaterialTheme.typography.bodyMedium)
                    }

                    extras.trend?.let { trend ->
                        val verb = if (trend.direction == TrendDirection.ADD) "added" else "dropped"
                        Text(
                            "Trending: $verb by ${trend.count} fantasy teams in the last 24h",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (extras.injuryNotes != null || extras.injuryBodyPart != null) {
                        Text("Injury report:", style = MaterialTheme.typography.titleMedium)
                        extras.injuryBodyPart?.let {
                            Text("Body part: $it", style = MaterialTheme.typography.bodyMedium)
                        }
                        extras.injuryNotes?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}


/**
 * "What if a team-mate sits?"
 *
 * The model already takes vacated target and carry share as inputs, so this is
 * a real re-score rather than a rule of thumb: toggling a team-mate hands his
 * usage to the rest of the offence and the projection is recomputed on the
 * phone. It is the question a static projection cannot answer, and the one
 * worth asking on a Sunday morning when a starter is announced inactive.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhatIfSection(
    state: WhatIfUiState,
    onToggle: (WeeklyPlayer) -> Unit,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("What if a team-mate sits?", style = MaterialTheme.typography.titleMedium)
                if (!state.scenario.isEmpty) {
                    TextButton(onClick = onClear) { Text("Reset") }
                }
            }

            state.adjusted?.let { adjusted ->
                Text(
                    "${"%.1f".format(adjusted.range.median)} pts " +
                        "(${"%.1f".format(adjusted.range.floor)}–" +
                        "${"%.1f".format(adjusted.range.ceiling)})",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (state.scenario.isEmpty) FontWeight.Normal else FontWeight.Bold
                )
            }
            if (!state.scenario.isEmpty) {
                // Under a tenth of a point is not a change a user should act on,
                // and saying so is more useful than showing "+0.0".
                val movement = if (kotlin.math.abs(state.delta) < 0.05) {
                    "Barely moves the projection"
                } else {
                    "${if (state.delta > 0) "+" else ""}${"%.1f".format(state.delta)} pts " +
                        "against the current lineup"
                }
                Text(
                    movement,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.delta > 0.05) StartGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                "Tap anyone to move them in or out of this week's lineup.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Chips wrap instead of stacking: a dozen of them one per row pushes
            // everything else off screen, and they are meant to be scanned as a
            // set rather than read as a list.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                state.teammates.take(12).forEach { teammate ->
                    val sittingOut = state.scenario.isOut(teammate.sleeperId, teammate.ruledOut)
                    FilterChip(
                        selected = sittingOut,
                        onClick = { onToggle(teammate) },
                        label = { Text("${shortName(teammate.name)} · ${teammate.position}") },
                        // The tick is the standard affordance for a selected
                        // filter chip; the old "— out" suffix reflowed the label
                        // on every tap, which made the row jump around.
                        leadingIcon = if (sittingOut) {
                            {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "sitting out",
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        } else {
                            null
                        }
                    )
                }
            }
        }
    }
}


/** "Amon-Ra St. Brown" -> "A. St. Brown", so a chip stays one line. */
private fun shortName(full: String): String {
    val parts = full.trim().split(" ")
    if (parts.size < 2) return full
    return "${parts.first().take(1)}. ${parts.drop(1).joinToString(" ")}"
}
