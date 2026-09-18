package com.sitorplay.app.ui.playerdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sitorplay.app.domain.model.Call
import com.sitorplay.app.ui.theme.SitRed
import com.sitorplay.app.ui.theme.StartGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerDetailScreen(
    onBack: () -> Unit,
    viewModel: PlayerDetailViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val recommendation by viewModel.recommendation.collectAsState()

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
                    Text("Why:", style = MaterialTheme.typography.titleMedium)
                    current.reasons.forEach { reason ->
                        Text("• $reason", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
