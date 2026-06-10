package io.github.mayusi.isitcompatible.ui.search

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mayusi.isitcompatible.ui.common.PlatformColors

@Composable
fun SearchScreen(
    contentPadding: PaddingValues,
    onOpenGame: (gameId: String) -> Unit = {},
    vm: SearchViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(contentPadding)) {

        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("Browse", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Look up compatibility for any game — owned or not.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(12.dp))
            OutlinedTextField(
                value = s.query,
                onValueChange = vm::onQueryChange,
                placeholder = { Text("Search any game…") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        if (!s.loaded) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }

        if (s.platforms.isNotEmpty()) {
            PlatformChips(
                platforms = s.platforms,
                selected = s.platformFilter,
                onToggle = vm::togglePlatform,
            )
        }

        SortAndFilterChips(
            sortOrder = s.sortOrder,
            stabilityFilterActive = s.stabilityFilter != null,
            onSetSort = vm::setSortOrder,
            onToggleStability = vm::toggleStabilityFilter,
        )

        ResultCountBar(count = s.results.size, total = s.allSummaries.size,
            filterActive = s.query.isNotBlank() || s.platformFilter != null || s.stabilityFilter != null)

        if (s.results.isEmpty() && s.allSummaries.isNotEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                Text(
                    "No matches. Try a shorter query or another platform filter.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val showQuickStart = s.wizardComplete &&
                !s.windowsQuickStartDismissed &&
                s.query.isBlank() &&
                s.platformFilter == null
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                if (showQuickStart) {
                    item(key = "windows-quick-start") {
                        WindowsQuickStartCard(
                            deviceLine = quickStartDeviceLine(s.deviceFingerprint),
                            iicInstalled = s.iicInstalled,
                            onOpenGame = onOpenGame,
                            onDismiss = vm::dismissWindowsQuickStart,
                        )
                    }
                }
                items(s.results, key = { it.game.id }) { summary ->
                    GameCard(
                        s = summary,
                        tried = summary.game.id in s.triedGameIds,
                    ) { onOpenGame(summary.game.id) }
                }
            }
        }
    }
}

/** Builds the encouraging device line from the detected fingerprint, or a generic fallback. */
private fun quickStartDeviceLine(fp: io.github.mayusi.isitcompatible.hardware.DeviceFingerprint?): String {
    if (fp == null) return "Your device is ready for Windows games."
    val parts = listOfNotNull(
        fp.socFamily.takeIf { it.isNotBlank() },
        fp.gpuModel.takeIf { it.isNotBlank() },
    )
    return if (parts.isEmpty()) "Your device is ready for Windows games."
    else parts.joinToString(" · ") + " — great for Windows games"
}

private data class QuickStartPick(val id: String, val title: String)

private val QUICK_START_PICKS = listOf(
    QuickStartPick("win:hollow-knight", "Hollow Knight"),
    QuickStartPick("win:hades", "Hades"),
    QuickStartPick("win:stardew", "Stardew Valley"),
    QuickStartPick("win:portal-2", "Portal 2"),
)

@Composable
private fun WindowsQuickStartCard(
    deviceLine: String,
    iicInstalled: Boolean,
    onOpenGame: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            .padding(16.dp),
    ) {
        Text(
            "Play Windows games on your handheld",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            deviceLine,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            if (iicInstalled)
                "GameNative (IIC) is installed. Pick a game to set it up and play:"
            else
                "Open a Windows game below to install GameNative (IIC) and play. Start with one of these:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QUICK_START_PICKS.forEach { pick ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onOpenGame(pick.id) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        pick.title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .align(Alignment.End)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onDismiss() }
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                "Got it",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PlatformChips(
    platforms: List<String>,
    selected: String?,
    onToggle: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        platforms.forEach { p ->
            val isSel = selected == p
            val color = PlatformColors.primary(p)
            Box(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isSel) color.copy(alpha = 0.85f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onToggle(p) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    text = p,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSel) Color.White
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SortAndFilterChips(
    sortOrder: SortOrder,
    stabilityFilterActive: Boolean,
    onSetSort: (SortOrder) -> Unit,
    onToggleStability: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // "Runs great" quick-filter chip
        val runGreatColor = Color(0xFF4CAF50)
        Box(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (stabilityFilterActive) runGreatColor.copy(alpha = 0.85f)
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .clickable { onToggleStability() }
                .padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
            Text(
                "Runs great",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (stabilityFilterActive) FontWeight.Bold else FontWeight.Normal,
                color = if (stabilityFilterActive) Color.White
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Sort order chips
        SortOrder.entries.forEach { order ->
            val isSel = sortOrder == order
            Box(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onSetSort(order) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    order.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSel) Color.White
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ResultCountBar(count: Int, total: Int, filterActive: Boolean) {
    Text(
        if (filterActive) "$count of $total games" else "$total games in DB",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun GameCard(s: GameSummary, tried: Boolean = false, onClick: () -> Unit) {
    val color = PlatformColors.primary(s.game.platform)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left accent bar matching platform color
        Box(
            Modifier
                .width(4.dp)
                .height(64.dp)
                .background(color),
        )
        // Platform badge
        Box(
            Modifier
                .padding(start = 12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(color.copy(alpha = 0.18f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(s.game.platform,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        // Title + subtitle
        Column(
            Modifier.weight(1f).padding(vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.game.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false))
                if (tried) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(
                            "✓ tried",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            val subtitle = buildString {
                s.game.releaseYear?.let { append("$it") }
                if (s.reportCount > 0) {
                    if (isNotEmpty()) append(" · ")
                    append("${s.reportCount} report")
                    if (s.reportCount != 1) append("s")
                } else {
                    if (isNotEmpty()) append(" · ")
                    append("no reports")
                }
            }
            Text(subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (s.bestEmulatorName != null) {
                // PART 2 honesty fix: at WEAK/VERY_WEAK confidence the "best" emulator
                // is a heuristic extrapolation — not confirmed on this device.
                val emuLabelIsEstimated =
                    s.bestConfidence == io.github.mayusi.isitcompatible.recommend.Confidence.WEAK ||
                    s.bestConfidence == io.github.mayusi.isitcompatible.recommend.Confidence.VERY_WEAK
                val emuLabel = if (emuLabelIsEstimated)
                    "Best: ${s.bestEmulatorName} (estimated)"
                else
                    "Best: ${s.bestEmulatorName}"
                Text(emuLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (emuLabelIsEstimated)
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // FPS pill (right) — dims when match confidence is WEAK / VERY_WEAK.
        // VERY_WEAK shows "est." (no real same-device data at all — a heuristic).
        // WEAK shows "fps?" (some real data but from a wider hardware bucket).
        if (s.bestFps != null) {
            val baseColor = PlatformColors.stability(s.bestStability)
            val isVeryWeak = s.bestConfidence == io.github.mayusi.isitcompatible.recommend.Confidence.VERY_WEAK
            val isLowConfidence = isVeryWeak ||
                                  s.bestConfidence == io.github.mayusi.isitcompatible.recommend.Confidence.WEAK
            val pillTextColor = if (isLowConfidence) baseColor.copy(alpha = 0.55f) else baseColor
            val pillBg = if (isLowConfidence) baseColor.copy(alpha = 0.09f) else baseColor.copy(alpha = 0.18f)
            Box(
                Modifier
                    .padding(end = 12.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(pillBg)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${s.bestFps}",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                        fontWeight = FontWeight.Bold,
                        color = pillTextColor)
                    Text(
                        // "est." = no real same-device data; "fps?" = extrapolated from wider bucket
                        text = when {
                            isVeryWeak     -> "est."
                            isLowConfidence -> "fps?"
                            else            -> "fps"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = pillTextColor)
                }
            }
        } else {
            Box(
                Modifier
                    .padding(end = 12.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text("—",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
