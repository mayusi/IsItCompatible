package io.github.mayusi.isitcompatible.ui.updates

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mayusi.isitcompatible.ui.common.SectionCard
import io.github.mayusi.isitcompatible.ui.theme.AppColors
import io.github.mayusi.isitcompatible.ui.theme.AppShapes
import io.github.mayusi.isitcompatible.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UpdatesScreen(
    contentPadding: PaddingValues,
    vm: UpdatesViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = Spacing.screenH, vertical = Spacing.screenV)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.sectionGap),
    ) {
        Text(
            "Database",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        // ── Last-synced card ──────────────────────────────────────────────────
        LastSyncCard(
            epochMs = s.lastSyncEpochMs,
            remoteReached = s.lastSyncRemoteReached,
        )

        // ── Catalog stats card ────────────────────────────────────────────────
        SectionCard(
            title = "Catalog",
            icon = Icons.Outlined.Storage,
            accentColor = AppColors.sourceEmuReady,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatRow("Games", s.games.toString())
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                StatRow("Reports", s.reports.toString())
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                StatRow("Your journal entries", s.journalEntries.toString())
            }
        }

        // ── Per-source breakdown ──────────────────────────────────────────────
        if (s.breakdown.isNotEmpty()) {
            SectionCard(
                title = "Where reports come from",
                icon = Icons.Outlined.Info,
                accentColor = AppColors.sourceBundled,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    s.breakdown.sortedByDescending { it.count }.forEachIndexed { index, sc ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        StatRow(friendlySourceName(sc.source), sc.count.toString())
                    }
                }
            }
        }

        // ── Refresh button ────────────────────────────────────────────────────
        Button(
            onClick = vm::refreshNow,
            enabled = !s.syncing,
            modifier = Modifier
                .fillMaxWidth()
                .height(Spacing.xxl + Spacing.md),
            shape = AppShapes.button,
        ) {
            if (s.syncing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(Spacing.lg + Spacing.xxs),
                        strokeWidth = Spacing.xxs,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Syncing…")
                }
            } else {
                Text("Refresh now", fontWeight = FontWeight.SemiBold)
            }
        }

        s.lastResult?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ── Sources section ───────────────────────────────────────────────────
        SectionCard(
            title = "Sources",
            icon = Icons.Outlined.CloudSync,
            accentColor = AppColors.sourceCommunity,
        ) {
            val sourceBullets = listOf(
                "Bundled seed — always available, ships with the APK.",
                "mayusi/IsItCompatible-DB on GitHub — community-submitted reports and preset tweaks, pulled when online.",
                "Heuristic estimates — rules-engine generated when no real report exists for your device class; clearly labelled in-app.",
                "Your journal — local-only by default; ranks above community data for games you've personally tried.",
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                sourceBullets.forEach { bullet ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.sourceCommunity,
                            modifier = Modifier.width(Spacing.md),
                        )
                        Text(
                            bullet,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.md))
    }
}

@Composable
private fun LastSyncCard(epochMs: Long, remoteReached: Boolean) {
    val now = System.currentTimeMillis()
    val haveSynced = epochMs > 0
    val ageText = if (!haveSynced) {
        "never"
    } else {
        val raw = friendlyAge(now - epochMs)
        if (raw == "just now") raw else "$raw ago"
    }
    val absoluteText = if (haveSynced) {
        SimpleDateFormat("MMM d, yyyy 'at' HH:mm", Locale.getDefault()).format(Date(epochMs))
    } else "—"

    val syncAccent = if (!haveSynced || !remoteReached) AppColors.warning else AppColors.success

    SectionCard(
        title = "Last synced",
        icon = Icons.Outlined.CloudSync,
        accentColor = syncAccent,
    ) {
        Text(
            ageText,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            absoluteText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.sm))
        if (!haveSynced) {
            StatusPill(
                text = "Not synced yet — bundled seed only",
                accentColor = AppColors.warning,
            )
        } else if (!remoteReached) {
            StatusPill(
                text = "Bundled only — remote unreachable",
                accentColor = AppColors.warning,
            )
        } else {
            StatusPill(
                text = "Remote reached",
                accentColor = AppColors.success,
            )
        }
    }
}

@Composable
private fun StatusPill(text: String, accentColor: androidx.compose.ui.graphics.Color) {
    Surface(
        color = accentColor.copy(alpha = 0.18f),
        shape = AppShapes.pill,
    ) {
        Text(
            text,
            color = accentColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(
                horizontal = Spacing.chipHorizontal,
                vertical = Spacing.chipVertical,
            ),
        )
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Maps the internal `source` enum tag to something friendly for users.
 *
 * Note: most "OUR_GITHUB" reports today actually live in the bundled seed
 * (they're hand-written entries we maintain in the repo). Once the live
 * GitHub fetch is wired and pulling deltas, the same tag covers both —
 * the user-visible label is the same either way.
 */
private fun friendlySourceName(source: String): String = when (source) {
    "OUR_GITHUB" -> "Hand-written reports"
    "EMUREADY_SNAPSHOT" -> "EmuReady snapshot"
    "EMUREADY_LIVE" -> "EmuReady (live)"
    "GENERATED_HEURISTIC" -> "Heuristic estimates"
    "BUNDLED" -> "Bundled seed"
    "JOURNAL" -> "Your journal"
    "YOU" -> "Your journal"
    else -> source.lowercase().replaceFirstChar { it.uppercaseChar() }
}

/** Compact "3 hours" / "2 days" / "8 minutes" formatter. */
private fun friendlyAge(deltaMs: Long): String {
    val seconds = deltaMs / 1000L
    val minutes = seconds / 60L
    val hours = minutes / 60L
    val days = hours / 24L
    return when {
        days >= 1 -> if (days == 1L) "1 day" else "$days days"
        hours >= 1 -> if (hours == 1L) "1 hour" else "$hours hours"
        minutes >= 1 -> if (minutes == 1L) "1 minute" else "$minutes minutes"
        else -> "just now"
    }
}
