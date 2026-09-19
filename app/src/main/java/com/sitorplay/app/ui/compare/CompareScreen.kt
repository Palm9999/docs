package com.sitorplay.app.ui.compare

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sitorplay.app.domain.model.NflPlayer
import com.sitorplay.app.ui.theme.StartGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(
    viewModel: CompareViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Compare Players") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
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

            Text(
                "Tap a slot above, then search to fill it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                label = { Text("Search NFL players for ${if (state.activeSlot == CompareSlot.A) "Player A" else "Player B"}") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (state.isSearching) {
                CircularProgressIndicator(Modifier.padding(top = 16.dp).size(20.dp))
            }

            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.searchResults, key = { it.externalId }) { player ->
                    SearchResultRow(player, onClick = { viewModel.selectPlayer(player) })
                }
            }
        }
    }
}

private fun isWinner(mine: ComparisonPlayer?, other: ComparisonPlayer?): Boolean {
    if (mine == null || other == null) return false
    return mine.adjustedProjection > other.adjustedProjection
}

@Composable
private fun SearchResultRow(player: NflPlayer, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(player.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${player.position} · ${player.nflTeam}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
