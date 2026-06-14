package io.github.mayusi.isitcompatible.ui.library

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mayusi.isitcompatible.recommend.Confidence
import io.github.mayusi.isitcompatible.ui.common.GameRowCard
import io.github.mayusi.isitcompatible.ui.common.PlatformColors
import io.github.mayusi.isitcompatible.ui.common.SelectableChip
import io.github.mayusi.isitcompatible.ui.theme.AppColors
import io.github.mayusi.isitcompatible.ui.theme.AppShapes
import io.github.mayusi.isitcompatible.ui.theme.Spacing

@Composable
fun LibraryScreen(
    contentPadding: PaddingValues,
    onOpenGame: (gameId: String) -> Unit = {},
    vm: LibraryViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        // ── Header ──────────────────────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenH, vertical = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "My Library",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    "Games you own — with compatibility on your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            OutlinedButton(
                onClick = vm::rescan,
                shape = AppShapes.button,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(
                    "Rescan",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // ── Body ────────────────────────────────────────────────────────────────
        when {
            s.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

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
                // Sort chips
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.screenH, vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    LibrarySortOrder.entries.forEach { order ->
                        SelectableChip(
                            label = order.label,
                            selected = s.sortOrder == order,
                            onClick = { vm.setSortOrder(order) },
                        )
                    }
                }

                // Count label
                Text(
                    "${s.items.size} game${if (s.items.size != 1) "s" else ""} found",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        horizontal = Spacing.screenH,
                        vertical = Spacing.xs,
                    ),
                )

                // Game list
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
                    items(s.items, key = { it.game.gameId ?: it.game.fileName }) { item ->
                        LibraryGameCardRow(item = item) {
                            item.game.gameId?.let(onOpenGame)
                        }
                    }
                }
            }
        }
    }
}

// ── Library row card ──────────────────────────────────────────────────────────

/**
 * Thin wrapper around [GameRowCard] that injects the status dot at the START
 * of [subtitle1]. The dot is rendered as an inline Unicode bullet colored via
 * a SpanStyle — but since Compose Text doesn't support per-char inline color in
 * the simple API, we instead build the subtitle string with the dot signal in
 * a separate [Row] above the title so the dot remains a real colored circle.
 *
 * Strategy chosen: pass a custom [subtitle1] that embeds the textual dot signal
 * ("● ") and use [leadingAccentColor] = dot color so the accent bar visually
 * echoes the status. The actual colored dot is rendered by placing a small
 * [DotIndicator] as the very first element via a local composable, which we
 * inject into [GameRowCard]'s title column by composing a wrapper Column
 * that shows [DotIndicator] + [GameRowCard].
 *
 * Cleanest approach: keep [GameRowCard] unmodified and render a small Row that
 * puts the dot then the GameRowCard *sharing* the row space — but GameRowCard
 * is a full-width Row internally. So the neatest zero-param-change approach is:
 * put the dot at the start of subtitle1 as a colored prefix symbol, with the
 * dot color carried by AppColors tokens. This reads fine at 12 sp label size.
 */
@Composable
private fun LibraryGameCardRow(item: LibraryGameItem, onClick: () -> Unit) {
    val platformColor = PlatformColors.primary(item.game.platformGuess)
    val isVeryWeak = item.bestConfidence == Confidence.VERY_WEAK
    val isLowConf = isVeryWeak || item.bestConfidence == Confidence.WEAK

    // Status dot color mapped to AppColors tokens
    val dotColor = when (item.dot) {
        DotColor.GREEN  -> AppColors.success
        DotColor.YELLOW -> AppColors.warning
        DotColor.RED    -> AppColors.danger
        DotColor.GRAY   -> AppColors.neutral
    }

    // Subtitle1 = "● report count or status message"
    // The dot is a Unicode filled circle (●) prepended to give color signal
    // in a single line; the accent bar on the left also echoes the dot color.
    val dotPrefix = "●  "   // ● + two spaces
    val subtitle1: String = dotPrefix + when {
        item.game.gameId == null -> "Not in DB yet — use Browse to look it up"
        item.reportCount == 0    -> "No reports yet"
        else -> "${item.reportCount} report${if (item.reportCount != 1) "s" else ""}"
    }

    // Subtitle2 = best emulator line (optional)
    val subtitle2: String? = if (item.bestEmulatorName != null && item.reportCount > 0) {
        if (isLowConf) "Best: ${item.bestEmulatorName} (estimated)"
        else "Best: ${item.bestEmulatorName}"
    } else null

    // We pass dotColor as leadingAccentColor so the left bar reflects game status
    GameRowCard(
        platform = item.game.platformGuess,
        title = item.game.displayName,
        subtitle1 = subtitle1,
        subtitle2 = subtitle2,
        fps = item.bestFps,
        stability = item.bestStability,
        isEstimated = isVeryWeak,
        isLowConfidence = isLowConf,
        leadingAccentColor = dotColor,
        onClick = onClick,
    )
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(title: String, body: String) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(Spacing.xxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
