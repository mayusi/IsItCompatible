package io.github.mayusi.isitcompatible.ui.library

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mayusi.isitcompatible.recommend.Confidence
import io.github.mayusi.isitcompatible.ui.common.PlatformColors

@Composable
fun LibraryScreen(
    contentPadding: PaddingValues,
    onOpenGame: (gameId: String) -> Unit = {},
    vm: LibraryViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().padding(contentPadding),
    ) {
        // Header row: title + rescan button
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("My Library", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Games you own — with compatibility on your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = vm::rescan) { Text("Rescan") }
        }

        when {
            s.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

            !s.romPicked && !s.pcPicked -> EmptyState(
                title = "No folders picked yet",
                body = "Open Settings to pick your ROM folder and/or Windows-games folder.",
            )

            s.items.isEmpty() -> EmptyState(
                title = "No games found",
                body = "We scanned but didn't recognise anything. " +
                    "Use the Browse tab to look up games manually.",
            )

            else -> {
                SortChips(
                    current = s.sortOrder,
                    onSelect = vm::setSortOrder,
                )
                Text(
                    "${s.items.size} game${if (s.items.size != 1) "s" else ""} found",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(s.items, key = { it.game.gameId ?: it.game.fileName }) { item ->
                        LibraryGameCard(item = item) {
                            item.game.gameId?.let(onOpenGame)
                        }
                    }
                }
            }
        }
    }
}

// ── Sort chips ────────────────────────────────────────────────────────────────

@Composable
private fun SortChips(current: LibrarySortOrder, onSelect: (LibrarySortOrder) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LibrarySortOrder.entries.forEach { order ->
            val isSel = current == order
            Box(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                    )
                    .clickable { onSelect(order) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    order.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Game card — mirrors Browse GameCard styling ───────────────────────────────

/**
 * Library game card. Bundled params into [LibraryGameItem] to keep the
 * composable param count well under the VerifyError threshold.
 */
@Composable
private fun LibraryGameCard(item: LibraryGameItem, onClick: () -> Unit) {
    val platformColor = PlatformColors.primary(item.game.platformGuess)
    val isVeryWeak = item.bestConfidence == Confidence.VERY_WEAK
    val isLowConf = isVeryWeak || item.bestConfidence == Confidence.WEAK

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = item.game.gameId != null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left accent bar — platform color
        Box(
            Modifier
                .width(4.dp)
                .height(72.dp)
                .background(platformColor),
        )

        // Platform badge
        Box(
            Modifier
                .padding(start = 12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(platformColor.copy(alpha = 0.18f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                item.game.platformGuess,
                style = MaterialTheme.typography.labelSmall,
                color = platformColor,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.width(12.dp))

        // Title + compatibility subtitle
        Column(
            Modifier.weight(1f).padding(vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Dot: same color logic as before
                Box(
                    Modifier
                        .size(8.dp)
                        .background(item.dot.color(), CircleShape),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    item.game.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Spacer(Modifier.height(2.dp))
            if (item.game.gameId == null) {
                Text(
                    "Not in DB yet — use Browse to look it up",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (item.reportCount == 0) {
                Text(
                    "No reports yet",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // Report count line
                Text(
                    "${item.reportCount} report${if (item.reportCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Best emulator line — honest about confidence
                if (item.bestEmulatorName != null) {
                    val emuLabel = if (isLowConf)
                        "Best: ${item.bestEmulatorName} (estimated)"
                    else
                        "Best: ${item.bestEmulatorName}"
                    Text(
                        emuLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isLowConf)
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // FPS pill — right side, matches Browse pill exactly
        FpsPill(
            fps = item.bestFps,
            stability = item.bestStability,
            isVeryWeak = isVeryWeak,
            isLowConf = isLowConf,
        )
    }
}

/**
 * Extracted FPS pill so the card composable stays well under 12 params.
 * Rendering is identical to the Browse GameCard pill.
 */
@Composable
private fun FpsPill(
    fps: Int?,
    stability: String?,
    isVeryWeak: Boolean,
    isLowConf: Boolean,
) {
    if (fps != null) {
        val baseColor = PlatformColors.stability(stability)
        val pillTextColor = if (isLowConf) baseColor.copy(alpha = 0.55f) else baseColor
        val pillBg = if (isLowConf) baseColor.copy(alpha = 0.09f) else baseColor.copy(alpha = 0.18f)
        Box(
            Modifier
                .padding(end = 12.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(pillBg)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "$fps",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                    fontWeight = FontWeight.Bold,
                    color = pillTextColor,
                )
                Text(
                    when {
                        isVeryWeak -> "est."
                        isLowConf -> "fps?"
                        else -> "fps"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = pillTextColor,
                )
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
            Text(
                "—",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Color helpers ─────────────────────────────────────────────────────────────

private fun DotColor.color(): Color = when (this) {
    DotColor.GREEN -> Color(0xFF4CAF50)
    DotColor.YELLOW -> Color(0xFFFFC107)
    DotColor.RED -> Color(0xFFEF5350)
    DotColor.GRAY -> Color(0xFF9E9E9E)
}
