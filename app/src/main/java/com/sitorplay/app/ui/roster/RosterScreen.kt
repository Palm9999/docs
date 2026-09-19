package com.sitorplay.app.ui.roster

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sitorplay.app.domain.model.Call
import com.sitorplay.app.domain.model.Recommendation
import com.sitorplay.app.ui.theme.SitRed
import com.sitorplay.app.ui.theme.StartGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RosterScreen(
    onAddPlayer: () -> Unit,
    onPlayerClick: (Long) -> Unit,
    viewModel: RosterViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()
    val lastRemovedPlayer by viewModel.lastRemovedPlayer.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(syncMessage) {
        syncMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeSyncMessage()
        }
    }

    LaunchedEffect(lastRemovedPlayer) {
        lastRemovedPlayer?.let { player ->
            val result = snackbarHostState.showSnackbar(
                message = "Removed ${player.name}",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoRemovePlayer()
            } else {
                viewModel.consumeRemovedPlayerState()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("This Week's Lineup")
                        if (uiState.teamName.isNotBlank()) {
                            Text(
                                uiState.teamName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::syncLiveData, enabled = !isSyncing) {
                        if (isSyncing) {
                            CircularProgressIndicator(Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "Sync live stats")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddPlayer) {
                Icon(Icons.Filled.Add, contentDescription = "Add player")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                uiState.recommendations.isEmpty() -> Text(
                    "No players yet. Tap + to add your roster.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
                else -> RecommendationList(
                    recommendations = uiState.recommendations,
                    onPlayerClick = onPlayerClick,
                    onRemove = viewModel::removePlayer
                )
            }
        }
    }
}

@Composable
private fun RecommendationList(
    recommendations: List<Recommendation>,
    onPlayerClick: (Long) -> Unit,
    onRemove: (Recommendation) -> Unit
) {
    val grouped = recommendations.groupBy { it.player.position }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        grouped.forEach { (position, group) ->
            item {
                Text(
                    text = position.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }
            items(group, key = { it.player.id }) { recommendation ->
                RecommendationCard(
                    recommendation,
                    onClick = { onPlayerClick(recommendation.player.id) },
                    onRemove = { onRemove(recommendation) }
                )
            }
        }
    }
}

@Composable
private fun RecommendationCard(recommendation: Recommendation, onClick: () -> Unit, onRemove: () -> Unit) {
    val callColor = if (recommendation.call == Call.START) StartGreen else SitRed
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(recommendation.player.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${recommendation.player.nflTeam} vs ${recommendation.player.opponent} · ${"%.1f".format(recommendation.adjustedProjection)} pts",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            CallBadge(call = recommendation.call, color = callColor)
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Remove ${recommendation.player.name}")
            }
        }
    }
}

@Composable
private fun CallBadge(call: Call, color: Color) {
    Surface(color = color.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
        Text(
            text = if (call == Call.START) "START" else "SIT",
            color = color,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
