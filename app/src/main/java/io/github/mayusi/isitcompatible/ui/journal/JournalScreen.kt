package io.github.mayusi.isitcompatible.ui.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mayusi.isitcompatible.compatdb.room.JournalEntryEntity
import io.github.mayusi.isitcompatible.ui.common.GameRowCard
import io.github.mayusi.isitcompatible.ui.common.PlatformColors
import io.github.mayusi.isitcompatible.ui.theme.AppColors
import io.github.mayusi.isitcompatible.ui.theme.AppShapes
import io.github.mayusi.isitcompatible.ui.theme.Spacing
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

    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        // ── Header ──────────────────────────────────────────────────────────────
        Column(
            Modifier.padding(
                horizontal = Spacing.screenH,
                vertical = Spacing.lg,
            ),
        ) {
            Text(
                "Journal",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                "Every game you've logged, with what worked and what didn't.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ── Loading ─────────────────────────────────────────────────────────────
        if (s.loading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Column
        }

        // ── In-progress section ─────────────────────────────────────────────────
        val inProgress = s.inProgressSetups
        if (!inProgress.isNullOrEmpty()) {
            InProgressSection(
                setups = inProgress,
                onResume = onOpenGame,
                modifier = Modifier.padding(
                    horizontal = Spacing.screenH,
                    vertical = Spacing.xs,
                ),
            )
            Spacer(Modifier.height(Spacing.sm))
        }

        // ── Empty state ─────────────────────────────────────────────────────────
        if (s.entries.isEmpty()) {
            EmptyJournalState()
            return@Column
        }

        // ── Stats strip ─────────────────────────────────────────────────────────
        s.stats?.let { stats ->
            StatsStrip(
                stats = stats,
                modifier = Modifier.padding(
                    horizontal = Spacing.screenH,
                    vertical = Spacing.sm,
                ),
            )
            Spacer(Modifier.height(Spacing.sm))
        }

        // ── Entry list ──────────────────────────────────────────────────────────
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.screenH),
            verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
            contentPadding = PaddingValues(
                top = Spacing.xs,
                bottom = Spacing.lg,
            ),
        ) {
            items(s.entries, key = { it.id }) { entry ->
                val gameTitle = s.gamesById[entry.gameId]?.title ?: entry.gameId
                val gamePlatform = s.gamesById[entry.gameId]?.platform ?: "—"
                val emulatorName = entry.emulatorId?.let { s.emulatorsById[it]?.name }
                // QW4: resolve driver name for this run
                val driverName = entry.driverIdAtTimeOfRun?.let { s.driversById[it]?.name }

                JournalEntryRowCard(
                    entry = entry,
                    gameTitle = gameTitle,
                    gamePlatform = gamePlatform,
                    emulatorName = emulatorName,
                    driverName = driverName,
                    onOpen = { onOpenGame(entry.gameId) },
                    onDelete = { pendingDelete = entry.id },
                )
            }
        }
    }

    // ── Delete confirmation dialog ───────────────────────────────────────────
    pendingDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this entry?") },
            text = {
                Text("This can't be undone. The community recommendations stay the same.")
            },
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

// ── Stats strip ───────────────────────────────────────────────────────────────

@Composable
private fun StatsStrip(stats: JournalStats, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        // "Working" is the key gamer metric — gets success color + most weight
        StatCard(
            number = "${stats.workingCount}",
            label = "working",
            accentColor = AppColors.success,
            modifier = Modifier.weight(1.1f),
        )
        StatCard(
            number = "${stats.gameCount}",
            label = "tried",
            accentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        val hrs = stats.sessionHrs
        val hrsLabel = if (hrs < 1f) "${(hrs * 60).toInt()}m" else "${"%.1f".format(hrs)}h"
        StatCard(
            number = hrsLabel,
            label = "played",
            accentColor = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCard(
    number: String,
    label: String,
    accentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = AppShapes.card,
        colors = CardDefaults.cardColors(
            containerColor = accentColor.copy(alpha = 0.12f),
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            accentColor.copy(alpha = 0.25f),
        ),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.md, horizontal = Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Text(
                number,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accentColor,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor.copy(alpha = 0.8f),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ── Journal entry row ─────────────────────────────────────────────────────────

@Composable
private fun JournalEntryRowCard(
    entry: JournalEntryEntity,
    gameTitle: String,
    gamePlatform: String,
    emulatorName: String?,
    driverName: String? = null,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val platformColor = PlatformColors.primary(gamePlatform)

    // Build subtitle1: emulator + date + optional micro-chips (session, shared)
    val subtitle1 = buildString {
        emulatorName?.let { append(it); append(" · ") }
        append(formatDate(entry.createdAt))
        entry.sessionMinutes?.takeIf { it > 0 }?.let { append("  ${it}m") }
        if (entry.shareWithCommunity) append("  shared")
        // QW4: peak thermal reading if available
        entry.peakTempC?.let { append("  · Peak ${it}°C") }
        // QW4: driver name used at time of run
        driverName?.let { append("  · $it") }
    }

    // Subtitle2: notes snippet when available
    val subtitle2: String? = entry.notes?.takeIf { it.isNotBlank() }?.let { "\"$it\"" }

    GameRowCard(
        platform = gamePlatform,
        title = gameTitle,
        subtitle1 = subtitle1,
        subtitle2 = subtitle2,
        fps = entry.avgFps,
        stability = entry.stability,
        isEstimated = false,
        isLowConfidence = false,
        leadingAccentColor = platformColor,
        onClick = onOpen,
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete entry",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
    )
}

private fun formatDate(epochMs: Long): String =
    DateFormat.getDateInstance(DateFormat.SHORT).format(Date(epochMs))

// ── In-progress section ───────────────────────────────────────────────────────

@Composable
private fun InProgressSection(
    setups: List<InProgressSetup>,
    onResume: (gameId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            "In progress",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        setups.forEach { setup ->
            InProgressCard(setup = setup, onResume = { onResume(setup.gameId) })
        }
    }
}

@Composable
private fun InProgressCard(
    setup: InProgressSetup,
    onResume: () -> Unit,
) {
    val fraction = if (setup.totalSteps > 0)
        setup.doneSteps.toFloat() / setup.totalSteps
    else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.card),
        onClick = onResume,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
        ),
        shape = AppShapes.card,
    ) {
        Column(
            Modifier.padding(Spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Resume: ${setup.gameTitle}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        "${setup.emulatorName} · step ${setup.doneSteps} of ${setup.totalSteps}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = "Resume setup",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }

            // Rounded progress bar — primary track, muted trail
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(AppShapes.pill),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            // Progress label e.g. "2 / 5 steps done"
            Text(
                "${setup.doneSteps} / ${setup.totalSteps} steps done",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyJournalState() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(Spacing.xxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.EditNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.size(Spacing.md))
        Text(
            "No entries yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(Spacing.xs))
        Text(
            "Open any game from Browse and tap \"Apply this preset\" → \"Log my result\" " +
                "after trying it. Your entries appear here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
