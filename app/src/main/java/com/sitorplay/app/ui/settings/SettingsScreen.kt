package com.sitorplay.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sitorplay.app.data.settings.ScoringFormat
import com.sitorplay.app.domain.model.Team

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var teamPendingRename by remember { mutableStateOf<Team?>(null) }
    var newTeamName by remember { mutableStateOf("") }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Scoring Format", style = MaterialTheme.typography.titleMedium)
            Text(
                "Which of Sleeper's projection fields to use as \"projected points\" everywhere in the app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ScoringFormat.entries.forEach { format ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = state.scoringFormat == format,
                            onClick = { viewModel.selectScoringFormat(format) }
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = state.scoringFormat == format, onClick = { viewModel.selectScoringFormat(format) })
                    Text(format.label, modifier = Modifier.padding(start = 8.dp))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text("Teams", style = MaterialTheme.typography.titleMedium)
            Text(
                "Switch which team's roster you're viewing, or manage your teams.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            state.teams.forEach { team ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = state.selectedTeamId == team.id,
                            onClick = { viewModel.selectTeam(team.id) }
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = state.selectedTeamId == team.id, onClick = { viewModel.selectTeam(team.id) })
                    Text(team.name, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    IconButton(onClick = { teamPendingRename = team }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Rename ${team.name}")
                    }
                    IconButton(
                        onClick = { viewModel.deleteTeam(team) },
                        enabled = state.teams.size > 1
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete ${team.name}")
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newTeamName,
                    onValueChange = { newTeamName = it },
                    label = { Text("New team name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { viewModel.addTeam(newTeamName); newTeamName = "" },
                    enabled = newTeamName.isNotBlank(),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("Add")
                }
            }
        }
    }

    teamPendingRename?.let { team ->
        var name by remember(team.id) { mutableStateOf(team.name) }
        AlertDialog(
            onDismissRequest = { teamPendingRename = null },
            title = { Text("Rename team") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameTeam(team, name)
                    teamPendingRename = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { teamPendingRename = null }) { Text("Cancel") }
            }
        )
    }
}
