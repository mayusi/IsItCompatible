package io.github.mayusi.isitcompatible.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mayusi.isitcompatible.compatdb.room.JournalEntryEntity
import io.github.mayusi.isitcompatible.ui.common.PlatformColors
import java.text.DateFormat
import java.util.Date

@Composable
fun JournalScreen(
    contentPadding: PaddingValues,
    onOpenGame: (gameId: String) -> Unit = {},
    vm: JournalViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(contentPadding)) {

        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("Journal", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text(
                "Every game you've logged, with what worked and what didn't.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (s.loading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }

        if (s.entries.isEmpty()) {
            EmptyJournalState()
            return@Column
        }

        // Stats strip
        s.stats?.let { stats ->
            StatsStrip(stats = stats)
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(s.entries, key = { it.id }) { entry ->
                JournalEntryRow(
                    entry = entry,
                    gameTitle = s.gamesById[entry.gameId]?.title ?: entry.gameId,
                    gamePlatform = s.gamesById[entry.gameId]?.platform ?: "—",
                    emulatorName = entry.emulatorId?.let { s.emulatorsById[it]?.name },
                    onOpen = { onOpenGame(entry.gameId) },
                    onDelete = { pendingDelete = entry.id },
                )
            }
        }
    }

    pendingDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this entry?") },
            text = { Text("This can't be undone. The community recommendations stay the same.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(id)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun StatsStrip(stats: JournalStats) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatChip(
            label = "${stats.gameCount} game${if (stats.gameCount == 1) "" else "s"} tried",
            color = MaterialTheme.colorScheme.primary,
        )
        StatChip(
            label = "${stats.workingCount} working",
            color = androidx.compose.ui.graphics.Color(0xFF4CAF50),
        )
        val hrs = stats.sessionHrs
        val hrsLabel = if (hrs < 1f) "${(hrs * 60).toInt()}m" else "${"%.1f".format(hrs)} hrs"
        StatChip(
            label = "$hrsLabel session",
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
private fun StatChip(label: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

@Composable
private fun EmptyJournalState() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.EditNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.size(12.dp))
        Text("No entries yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.size(6.dp))
        Text(
            "Open any game from Browse and tap \"Apply this preset\" → \"Log my result\" " +
                "after trying it. Your entries appear here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun JournalEntryRow(
    entry: JournalEntryEntity,
    gameTitle: String,
    gamePlatform: String,
    emulatorName: String?,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val platformColor = PlatformColors.primary(gamePlatform)
    val stabColor = PlatformColors.stability(entry.stability)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left accent — platform color
        Box(Modifier.width(4.dp).size(78.dp).background(platformColor))
        Spacer(Modifier.width(12.dp))
        // Platform badge
        Box(
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(platformColor.copy(alpha = 0.18f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(gamePlatform,
                style = MaterialTheme.typography.labelSmall,
                color = platformColor,
                fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        // Title + meta
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(gameTitle,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildString {
                        emulatorName?.let { append(it); append(" · ") }
                        append(formatDate(entry.createdAt))
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Session duration chip
                entry.sessionMinutes?.takeIf { it > 0 }?.let { mins ->
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text("${mins}m",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.SemiBold)
                    }
                }
                // v0.6: tiny "shared" chip when the user opted into community sharing
                if (entry.shareWithCommunity) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(androidx.compose.ui.graphics.Color(0xFF1976D2).copy(alpha = 0.18f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text("shared",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = androidx.compose.ui.graphics.Color(0xFF0D47A1),
                            fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            entry.notes?.takeIf { it.isNotBlank() }?.let {
                Text("“$it”",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1)
            }
        }
        // FPS pill
        Box(
            Modifier
                .padding(end = 6.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(stabColor.copy(alpha = 0.18f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(entry.avgFps?.toString() ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = stabColor)
                Text(if (entry.avgFps != null) "fps" else "—",
                    style = MaterialTheme.typography.labelSmall,
                    color = stabColor)
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.DeleteOutline,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp))
        }
    }
}

private fun formatDate(epochMs: Long): String =
    DateFormat.getDateInstance(DateFormat.SHORT).format(Date(epochMs))
