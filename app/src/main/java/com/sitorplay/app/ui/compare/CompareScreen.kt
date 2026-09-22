package com.sitorplay.app.ui.compare

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sitorplay.app.domain.model.NflPlayer
import com.sitorplay.app.domain.prediction.FormComparison
import com.sitorplay.app.domain.prediction.PlayerForm
import com.sitorplay.app.domain.prediction.StartSitComparison
import com.sitorplay.app.ui.theme.StartGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(
    viewModel: CompareViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Compare Players") }) }) { padding ->
        val formRows = PlayerForm.align(
            state.slotA?.form.orEmpty(),
            state.slotB?.form.orEmpty()
        )
        val listToShow =
            if (state.searchQuery.isBlank()) state.favoritePlayers else state.searchResults

        // Everything is an item of one list rather than a fixed header above a
        // scrolling one. With the stat table added, a fixed header leaves the
        // search results a few pixels tall on a phone, and the obvious fix --
        // wrapping the Column in verticalScroll -- crashes Compose, because a
        // LazyColumn cannot be measured inside an infinite-height parent.
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CompareSlotCard(
                        label = "Player A",
                        comparison = state.slotA,
                        isActive = state.activeSlot == CompareSlot.A,
                        isWinner = isWinner(state.slotA, state.slotB),
                        onClick = { viewModel.setActiveSlot(CompareSlot.A) },
                        onClear = { viewModel.clearSlot(CompareSlot.A) },
                        onDefenseRankChange = { viewModel.onDefenseRankChange(CompareSlot.A, it) },
                        modifier = Modifier.weight(1f)
                    )
                    CompareSlotCard(
                        label = "Player B",
                        comparison = state.slotB,
                        isActive = state.activeSlot == CompareSlot.B,
                        isWinner = isWinner(state.slotB, state.slotA),
                        onClick = { viewModel.setActiveSlot(CompareSlot.B) },
                        onClear = { viewModel.clearSlot(CompareSlot.B) },
                        onDefenseRankChange = { viewModel.onDefenseRankChange(CompareSlot.B, it) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            state.comparison?.let { comparison ->
                item {
                    StartSitVerdict(
                        comparison = comparison,
                        rationale = state.rationale.orEmpty(),
                        nameA = state.slotA?.player?.name.orEmpty(),
                        nameB = state.slotB?.player?.name.orEmpty(),
                        pointsNeeded = state.pointsNeeded,
                        onPointsNeededChange = viewModel::onPointsNeededChange
                    )
                }
            }

            if (formRows.isNotEmpty()) {
                item {
                    FormComparisonSection(
                        rows = formRows,
                        nameA = state.slotA?.player?.name.orEmpty(),
                        nameB = state.slotB?.player?.name.orEmpty()
                    )
                }
            }

            item {
                Text(
                    "Tap a slot above, then search to fill it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::onSearchQueryChange,
                    label = {
                        Text(
                            "Search NFL players for " +
                                if (state.activeSlot == CompareSlot.A) "Player A" else "Player B"
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (state.isSearching) {
                item { CircularProgressIndicator(Modifier.padding(top = 16.dp).size(20.dp)) }
            }

            if (state.searchQuery.isBlank() && state.favoritePlayers.isNotEmpty()) {
                item {
                    Text(
                        "Favorites",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            items(listToShow, key = { it.externalId }) { player ->
                SearchResultRow(
                    player,
                    isFavorite = player.externalId in state.favoriteIds,
                    onClick = { viewModel.selectPlayer(player) },
                    onToggleFavorite = { viewModel.toggleFavorite(player.externalId) }
                )
            }
        }
    }
}

/**
 * The two stat lines side by side.
 *
 * The verdict above is one number per player, which is the answer but not the
 * argument. This is the argument: the volume and the share of the offence each
 * of them has actually been getting, which is what a start/sit call turns on
 * once the projections are close.
 *
 * Last three games only. The season column earns its place on a single player's
 * screen, but four figures across a phone would be unreadable, and the recent
 * window is the one that decides a close call.
 */
@Composable
private fun FormComparisonSection(rows: List<FormComparison>, nameA: String, nameB: String) {
    Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Last 3 games, per game", style = MaterialTheme.typography.titleMedium)
            ComparisonRow(
                label = "",
                left = shortName(nameA),
                right = shortName(nameB),
                emphasis = FontWeight.SemiBold
            )
            HorizontalDivider()
            rows.forEach { row ->
                ComparisonRow(row.label, row.leftText, row.rightText)
            }
        }
    }
}

@Composable
private fun ComparisonRow(
    label: String,
    left: String,
    right: String,
    emphasis: FontWeight = FontWeight.Normal
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = emphasis)
        Spacer(Modifier.weight(1f))
        Text(
            left,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = emphasis,
            textAlign = TextAlign.End,
            modifier = Modifier.width(72.dp)
        )
        Text(
            right,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = emphasis,
            textAlign = TextAlign.End,
            modifier = Modifier.width(72.dp)
        )
    }
}

/** "Amon-Ra St. Brown" will not fit a column header; "A. St. Brown" will. */
private fun shortName(full: String): String {
    val parts = full.trim().split(" ")
    if (parts.size < 2) return full
    return "${parts.first().take(1)}. ${parts.drop(1).joinToString(" ")}"
}


@Composable
private fun StartSitVerdict(
    comparison: StartSitComparison,
    rationale: String,
    nameA: String,
    nameB: String,
    pointsNeeded: Float,
    onPointsNeededChange: (Float) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Points needed from this slot: ${"%.0f".format(pointsNeeded)}",
                style = MaterialTheme.typography.titleSmall
            )
            Slider(
                value = pointsNeeded,
                onValueChange = onPointsNeededChange,
                valueRange = 0f..40f,
                steps = 39
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                WinChance(nameA, comparison.probabilityA, comparison.favoursA)
                WinChance(nameB, comparison.probabilityB, !comparison.favoursA)
            }
            Spacer(Modifier.height(8.dp))
            Text(rationale, style = MaterialTheme.typography.bodyMedium)
            if (comparison.disagreesWithProjection) {
                Text(
                    "This is the opposite of what projected points alone would tell you.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun WinChance(name: String, probability: Double, isPick: Boolean) {
    Column {
        Text(name, style = MaterialTheme.typography.bodySmall)
        Text(
            "${"%.0f".format(probability * 100)}% to cover",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isPick) FontWeight.Bold else FontWeight.Normal,
            color = if (isPick) StartGreen else MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun SearchResultRow(player: NflPlayer, isFavorite: Boolean, onClick: () -> Unit, onToggleFavorite: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(player.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${player.position} · ${player.nflTeam}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onToggleFavorite) {
                if (isFavorite) {
                    Icon(Icons.Filled.Star, contentDescription = "Unfavorite ${player.name}")
                } else {
                    Icon(Icons.Outlined.StarBorder, contentDescription = "Favorite ${player.name}")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompareSlotCard(
    label: String,
    comparison: ComparisonPlayer?,
    isActive: Boolean,
    isWinner: Boolean,
    onClick: () -> Unit,
    onClear: () -> Unit,
    onDefenseRankChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = if (isActive) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            if (comparison == null) {
                Text(
                    "Tap to search",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                Text(
                    comparison.player.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal,
                    color = if (isWinner) StartGreen else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${comparison.player.position} · ${comparison.player.nflTeam}",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (comparison.isLoadingContext) {
                    CircularProgressIndicator(Modifier.padding(top = 8.dp).size(16.dp))
                } else {
                    Text(
                        "${"%.1f".format(comparison.projectedPoints)} pts vs ${comparison.opponent ?: "TBD"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Adjusted: ${"%.1f".format(comparison.adjustedProjection)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal
                    )
                    comparison.projection?.let { projection ->
                        Text(
                            "Model ${"%.1f".format(projection.range.floor)} – " +
                                "${"%.1f".format(projection.range.ceiling)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (projection.playProbability < 1.0) {
                            Text(
                                "${"%.0f".format(projection.playProbability * 100)}% to play",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    OutlinedTextField(
                        value = comparison.opponentDefenseRank,
                        onValueChange = onDefenseRankChange,
                        label = { Text("Def rank") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
                TextButton(onClick = onClear, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Clear")
                }
            }
        }
    }
}
