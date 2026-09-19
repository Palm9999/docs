package com.sitorplay.app.ui.waiver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sitorplay.app.domain.model.Position
import com.sitorplay.app.domain.model.WaiverSuggestion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaiverWireScreen(
    onAddPlayer: (externalId: String) -> Unit,
    viewModel: WaiverWireViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Waiver Wire") },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.isLoading) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PositionFilterRow(
                selected = state.positionFilter,
                onSelect = viewModel::setPositionFilter
            )
            SortRow(selected = state.sortBy, onSelect = viewModel::setSortBy)
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    state.errorMessage != null -> Text(
                        state.errorMessage!!,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp)
                    )
                    state.suggestions.isEmpty() -> Text(
                        "No trending pickups right now for this filter.",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp)
                    )
                    else -> LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.suggestions, key = { it.player.externalId }) { suggestion ->
                            WaiverSuggestionCard(
                                suggestion,
                                isFavorite = suggestion.player.externalId in state.favoriteIds,
                                onAdd = { onAddPlayer(suggestion.player.externalId) },
                                onToggleFavorite = { viewModel.toggleFavorite(suggestion.player.externalId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PositionFilterRow(selected: Position?, onSelect: (Position?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("All") })
        }
        items(Position.entries) { position ->
            FilterChip(
                selected = selected == position,
                onClick = { onSelect(position) },
                label = { Text(position.name) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortRow(selected: WaiverSort, onSelect: (WaiverSort) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected == WaiverSort.TREND,
            onClick = { onSelect(WaiverSort.TREND) },
            label = { Text("Sort: Trending") }
        )
        FilterChip(
            selected = selected == WaiverSort.PROJECTED_POINTS,
            onClick = { onSelect(WaiverSort.PROJECTED_POINTS) },
            label = { Text("Sort: Points") }
        )
    }
}

@Composable
private fun WaiverSuggestionCard(
    suggestion: WaiverSuggestion,
    isFavorite: Boolean,
    onAdd: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(suggestion.player.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${suggestion.player.position} · ${suggestion.player.nflTeam} vs ${suggestion.opponent ?: "TBD"} · " +
                        "${"%.1f".format(suggestion.projectedPoints)} pts",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Added by ${suggestion.trendCount} teams in the last 24h",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onToggleFavorite) {
                if (isFavorite) {
                    Icon(Icons.Filled.Star, contentDescription = "Unfavorite ${suggestion.player.name}")
                } else {
                    Icon(Icons.Outlined.StarBorder, contentDescription = "Favorite ${suggestion.player.name}")
                }
            }
            IconButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "Add ${suggestion.player.name}")
            }
        }
    }
}
