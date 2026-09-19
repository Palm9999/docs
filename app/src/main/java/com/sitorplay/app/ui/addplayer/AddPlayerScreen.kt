package com.sitorplay.app.ui.addplayer

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sitorplay.app.domain.model.InjuryStatus
import com.sitorplay.app.domain.model.NflPlayer
import com.sitorplay.app.domain.model.Position

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlayerScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddPlayerViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Player") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.manualMode) {
                ManualEntryForm(
                    state = state.manualForm,
                    onChange = viewModel::onManualFormChange,
                    onSave = { viewModel.saveManualPlayer(onSaved) }
                )
                TextButton(onClick = viewModel::toggleManualMode) {
                    Text("Back to player search")
                }
            } else {
                val selected = state.selectedPlayer
                if (selected == null) {
                    PlayerSearch(
                        query = state.searchQuery,
                        isSearching = state.isSearching,
                        results = state.searchResults,
                        favorites = state.favoritePlayers,
                        favoriteIds = state.favoriteIds,
                        positionFilter = state.positionFilter,
                        onPositionFilterChange = viewModel::setPositionFilter,
                        onQueryChange = viewModel::onSearchQueryChange,
                        onSelect = viewModel::selectPlayer,
                        onToggleFavorite = viewModel::toggleFavorite
                    )
                    TextButton(onClick = viewModel::toggleManualMode) {
                        Text("Can't find your player? Add manually")
                    }
                } else {
                    SelectedPlayerCard(
                        player = selected,
                        isLoadingContext = state.isLoadingContext,
                        projectedPoints = state.projectedPoints,
                        opponent = state.opponent,
                        opponentDefenseRank = state.opponentDefenseRank,
                        onDefenseRankChange = viewModel::onDefenseRankChange,
                        onChangePlayer = viewModel::clearSelectedPlayer,
                        onSave = { viewModel.saveSelectedPlayer(onSaved) },
                        canSave = state.canSaveSelected
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSearch(
    query: String,
    isSearching: Boolean,
    results: List<NflPlayer>,
    favorites: List<NflPlayer>,
    favoriteIds: Set<String>,
    positionFilter: Position?,
    onPositionFilterChange: (Position?) -> Unit,
    onQueryChange: (String) -> Unit,
    onSelect: (NflPlayer) -> Unit,
    onToggleFavorite: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        label = { Text("Search NFL players") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = { if (isSearching) CircularProgressIndicator(Modifier.size(20.dp)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = positionFilter == null,
                onClick = { onPositionFilterChange(null) },
                label = { Text("All") }
            )
        }
        items(Position.entries) { position ->
            FilterChip(
                selected = positionFilter == position,
                onClick = { onPositionFilterChange(position) },
                label = { Text(position.name) }
            )
        }
    }

    val listToShow = (if (query.isBlank()) favorites else results)
        .filter { positionFilter == null || it.position == positionFilter }

    if (query.isBlank() && favorites.isEmpty()) {
        Text(
            "Live player data comes from Sleeper's public NFL directory. Star a player to save it here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else if (query.isBlank()) {
        Text("Favorites", style = MaterialTheme.typography.titleMedium)
    } else if (results.isEmpty() && !isSearching) {
        Text(
            "No matches. Check your connection, or add this player manually below.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(listToShow, key = { it.externalId }) { player ->
            Card(modifier = Modifier.fillMaxWidth().clickable { onSelect(player) }) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(player.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${player.position} · ${player.nflTeam}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onToggleFavorite(player.externalId) }) {
                        if (player.externalId in favoriteIds) {
                            Icon(Icons.Filled.Star, contentDescription = "Unfavorite ${player.name}")
                        } else {
                            Icon(Icons.Outlined.StarBorder, contentDescription = "Favorite ${player.name}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedPlayerCard(
    player: NflPlayer,
    isLoadingContext: Boolean,
    projectedPoints: Double,
    opponent: String?,
    opponentDefenseRank: String,
    onDefenseRankChange: (String) -> Unit,
    onChangePlayer: () -> Unit,
    onSave: () -> Unit,
    canSave: Boolean
) {
    Text(player.name, style = MaterialTheme.typography.titleLarge)
    Text(
        "${player.position} · ${player.nflTeam}",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (isLoadingContext) {
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            CircularProgressIndicator(Modifier.size(24.dp))
        }
    } else {
        Text("Projected this week: ${"%.1f".format(projectedPoints)} pts vs ${opponent ?: "TBD"}")
        if (player.injuryStatus != InjuryStatus.HEALTHY) {
            Text(
                "Injury status: ${player.injuryStatus.label}",
                color = MaterialTheme.colorScheme.error
            )
        }
    }

    OutlinedTextField(
        value = opponentDefenseRank,
        onValueChange = onDefenseRankChange,
        label = { Text("Matchup difficulty: opponent defense rank vs ${player.position} (1-32)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
    Text(
        "Live matchup-difficulty data isn't available yet — defaults to average (16); adjust it based on your own read of this week's matchup.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onChangePlayer, modifier = Modifier.weight(1f)) {
            Text("Change player")
        }
        Button(onClick = onSave, enabled = canSave, modifier = Modifier.weight(1f)) {
            Text("Add to roster")
        }
    }
}

@Composable
private fun ManualEntryForm(
    state: ManualPlayerFormState,
    onChange: ((ManualPlayerFormState) -> ManualPlayerFormState) -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = state.name,
            onValueChange = { value -> onChange { it.copy(name = value) } },
            label = { Text("Player name") },
            modifier = Modifier.fillMaxWidth()
        )

        EnumDropdown(
            label = "Position",
            selected = state.position,
            options = Position.entries,
            onSelected = { value -> onChange { it.copy(position = value) } }
        )

        OutlinedTextField(
            value = state.nflTeam,
            onValueChange = { value -> onChange { it.copy(nflTeam = value) } },
            label = { Text("NFL team (e.g. BUF)") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = state.opponent,
            onValueChange = { value -> onChange { it.copy(opponent = value) } },
            label = { Text("Opponent this week (e.g. MIA)") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = state.projectedPoints,
            onValueChange = { value -> onChange { it.copy(projectedPoints = value) } },
            label = { Text("Projected points") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = state.opponentDefenseRank,
            onValueChange = { value -> onChange { it.copy(opponentDefenseRank = value) } },
            label = { Text("Opponent defense rank vs position (1-32)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        EnumDropdown(
            label = "Injury status",
            selected = state.injuryStatus,
            options = InjuryStatus.entries,
            onSelected = { value -> onChange { it.copy(injuryStatus = value) } }
        )

        Button(
            onClick = onSave,
            enabled = state.isValid,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add to roster")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(
    label: String,
    selected: T,
    options: List<T>,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.toString(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.toString()) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
