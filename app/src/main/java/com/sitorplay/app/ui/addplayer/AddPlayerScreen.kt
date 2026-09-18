package com.sitorplay.app.ui.addplayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.sitorplay.app.domain.model.Position

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlayerScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddPlayerViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val state by viewModel.formState.collectAsState()

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
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Player name") },
                modifier = Modifier.fillMaxWidth()
            )

            EnumDropdown(
                label = "Position",
                selected = state.position,
                options = Position.entries,
                onSelected = viewModel::onPositionChange
            )

            OutlinedTextField(
                value = state.nflTeam,
                onValueChange = viewModel::onTeamChange,
                label = { Text("NFL team (e.g. BUF)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.opponent,
                onValueChange = viewModel::onOpponentChange,
                label = { Text("Opponent this week (e.g. MIA)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.projectedPoints,
                onValueChange = viewModel::onProjectedPointsChange,
                label = { Text("Projected points") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.opponentDefenseRank,
                onValueChange = viewModel::onDefenseRankChange,
                label = { Text("Opponent defense rank vs position (1-32)") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            EnumDropdown(
                label = "Injury status",
                selected = state.injuryStatus,
                options = InjuryStatus.entries,
                onSelected = viewModel::onInjuryStatusChange
            )

            Button(
                onClick = { viewModel.savePlayer(onSaved) },
                enabled = state.isValid,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add to roster")
            }
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
