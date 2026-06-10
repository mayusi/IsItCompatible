package io.github.mayusi.isitcompatible.ui.updates

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
        Modifier.fillMaxSize().padding(contentPadding).padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Database", style = MaterialTheme.typography.headlineSmall)

        // Last-synced timestamp card. The honest version: shows when we last
        // tried, whether the remote came through, and a chip if we're running
        // on bundled-only data.
        LastSyncCard(
            epochMs = s.lastSyncEpochMs,
            remoteReached = s.lastSyncRemoteReached,
        )

        // Totals card: games + reports + journal entries side by side.
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Catalog", style = MaterialTheme.typography.titleMedium)
                StatRow("Games", s.games.toString())
                StatRow("Reports", s.reports.toString())
                StatRow("Your journal entries", s.journalEntries.toString())
            }
        }

        // Per-source breakdown. Shows exactly where each report came from so the
        // user can see "1700 of these are heuristic estimates" honestly.
        if (s.breakdown.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Where reports come from", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    s.breakdown.sortedByDescending { it.count }.forEach { sc ->
                        StatRow(friendlySourceName(sc.source), sc.count.toString())
                    }
                }
            }
        }

        Button(
            onClick = vm::refreshNow,
            enabled = !s.syncing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (s.syncing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Syncing…")
                }
            } else {
                Text("Refresh now")
            }
        }
        s.lastResult?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        Text("Sources", style = MaterialTheme.typography.titleSmall)
        Text(
            "• Bundled seed — always available, ships with the APK.\n" +
                "• mayusi/IsItCompatible-DB on GitHub — community-submitted reports and preset tweaks, pulled when online.\n" +
                "• Heuristic estimates — rules-engine generated when no real report exists for your device class; clearly labelled in-app.\n" +
                "• Your journal — local-only by default; ranks above community data for games you've personally tried.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        // "just now" reads naturally on its own; everything else gets " ago"
        if (raw == "just now") raw else "$raw ago"
    }
    val absoluteText = if (haveSynced) {
        SimpleDateFormat("MMM d, yyyy 'at' HH:mm", Locale.getDefault()).format(Date(epochMs))
    } else "—"

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Last synced", style = MaterialTheme.typography.titleMedium)
            Text(
                ageText,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                absoluteText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            if (!haveSynced) {
                Pill(
                    text = "Not synced yet — bundled seed only",
                    bg = Color(0xFFFEF3C7),
                    fg = Color(0xFF92400E),
                )
            } else if (!remoteReached) {
                Pill(
                    text = "Bundled only — remote unreachable",
                    bg = Color(0xFFFEF3C7),
                    fg = Color(0xFF92400E),
                )
            } else {
                Pill(
                    text = "Remote reached",
                    bg = Color(0xFFD1FAE5),
                    fg = Color(0xFF065F46),
                )
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Pill(text: String, bg: Color, fg: Color) {
    Surface(
        color = bg,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
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
