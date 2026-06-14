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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
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
fun SearchScreen(
    contentPadding: PaddingValues,
    onOpenGame: (gameId: String) -> Unit = {},
    vm: SearchViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(contentPadding)) {

        // ── Header ───────────────────────────────────────────────────────────
        Column(
            Modifier.padding(
                horizontal = Spacing.screenH,
                vertical = Spacing.md,
            ),
        ) {
            Text(
                "Browse",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.size(Spacing.xxs))
            Text(
                "Look up compatibility for any game — owned or not.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(Spacing.md))
            OutlinedTextField(
                value = s.query,
                onValueChange = vm::onQueryChange,
                placeholder = { Text("Search any game…") },
                leadingIcon = {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        if (!s.loaded) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }

        // ── Platform chips ────────────────────────────────────────────────────
        if (s.platforms.isNotEmpty()) {
            PlatformChips(
                platforms = s.platforms,
                selected = s.platformFilter,
                onToggle = vm::togglePlatform,
            )
        }

        // ── Sort & filter chips ───────────────────────────────────────────────
        SortAndFilterChips(
            sortOrder = s.sortOrder,
            stabilityFilterActive = s.stabilityFilter != null,
            favoritesFilterActive = s.favoritesFilterActive,
            hasFavorites = s.favoriteGameIds.isNotEmpty(),
            onSetSort = vm::setSortOrder,
            onToggleStability = vm::toggleStabilityFilter,
            onToggleFavorites = vm::toggleFavoritesFilter,
        )

        // ── Result count ──────────────────────────────────────────────────────
        ResultCountBar(
            count = s.results.size,
            total = if (s.favoritesFilterActive) s.favoriteGameIds.size else s.allSummaries.size,
            filterActive = s.query.isNotBlank() || s.platformFilter != null ||
                s.stabilityFilter != null || s.favoritesFilterActive,
        )

        if (s.results.isEmpty() && s.allSummaries.isNotEmpty()) {
            Box(Modifier.fillMaxSize().padding(Spacing.xxl), Alignment.Center) {
                Text(
                    "No matches. Try a shorter query or another platform filter.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // BUGFIX 6b: also gate on at least one Windows game existing in the DB
            // so the card never shows with broken quick-start links on an empty /
            // Android-only database.
            // hasWindowsGames is pre-computed in SearchViewModel (once, when
            // summaries load) to avoid scanning up to 1089 items on every recompose.
            val showQuickStart = s.wizardComplete &&
                !s.windowsQuickStartDismissed &&
                s.query.isBlank() &&
                s.platformFilter == null &&
                s.hasWindowsGames
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = Spacing.screenH),
                verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
                contentPadding = PaddingValues(bottom = Spacing.lg),
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
                    val isVeryWeak = summary.bestConfidence == Confidence.VERY_WEAK
                    val isLowConfidence = isVeryWeak ||
                        summary.bestConfidence == Confidence.WEAK
                    val emuLabelIsEstimated = isLowConfidence

                    val subtitle1 = buildString {
                        summary.game.releaseYear?.let { append("$it") }
                        if (summary.reportCount > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("${summary.reportCount} report")
                            if (summary.reportCount != 1) append("s")
                        } else {
                            if (isNotEmpty()) append(" · ")
                            append("no reports")
                        }
                    }

                    val subtitle2 = summary.bestEmulatorName?.let { emu ->
                        if (emuLabelIsEstimated) "Best: $emu (estimated)" else "Best: $emu"
                    }

                    GameRowCard(
                        platform = summary.game.platform,
                        title = summary.game.title,
                        subtitle1 = subtitle1,
                        subtitle2 = subtitle2,
                        fps = summary.bestFps,
                        stability = summary.bestStability,
                        isEstimated = isVeryWeak,
                        isLowConfidence = isLowConfidence,
                        leadingAccentColor = PlatformColors.primary(summary.game.platform),
                        isFavorite = summary.game.id in s.favoriteGameIds,
                        isTried = summary.game.id in s.triedGameIds,
                        onClick = { onOpenGame(summary.game.id) },
                    )
                }
            }
        }
    }
}

/** Builds the encouraging device line from the detected fingerprint, or a generic fallback. */
private fun quickStartDeviceLine(fp: io.github.mayusi.isitcompatible.hardware.DeviceFingerprint?): String {
    if (fp == null) return "Your device is ready to look up game compatibility."
    val parts = listOfNotNull(
        fp.socFamily.takeIf { it.isNotBlank() },
        fp.gpuModel.takeIf { it.isNotBlank() },
    )
    val prefix = if (parts.isEmpty()) "" else parts.joinToString(" · ") + " — "
    val tier = io.github.mayusi.isitcompatible.hardware.SocCatalog.tier(fp.socFamily)
    val suffix = when (tier) {
        io.github.mayusi.isitcompatible.hardware.SocTier.FLAGSHIP,
        io.github.mayusi.isitcompatible.hardware.SocTier.HIGH_END,
        io.github.mayusi.isitcompatible.hardware.SocTier.MID_RANGE -> "ready for Windows games"
        io.github.mayusi.isitcompatible.hardware.SocTier.BUDGET -> "may run lighter Windows games"
        io.github.mayusi.isitcompatible.hardware.SocTier.UNKNOWN -> {
            return "Your device is ready to look up game compatibility."
        }
    }
    return if (prefix.isEmpty()) suffix else "$prefix$suffix"
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
            .clip(AppShapes.card)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            .padding(Spacing.cardPadding),
    ) {
        Text(
            "Play Windows games on your handheld",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            deviceLine,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            if (iicInstalled)
                "GameNative (IIC) is ready. Pick a game to see the best config for your device:"
            else
                "Pick a game below to see which emulator, driver, and settings work best on your device. Start with one of these:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
        )
        Spacer(Modifier.height(Spacing.sm))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            QUICK_START_PICKS.forEach { pick ->
                Box(
                    Modifier
                        .clip(AppShapes.button)
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onOpenGame(pick.id) }
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
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
        Spacer(Modifier.height(Spacing.xs))
        Box(
            Modifier
                .align(Alignment.End)
                .clip(AppShapes.badge)
                .clickable { onDismiss() }
                .padding(horizontal = Spacing.sm, vertical = Spacing.chipVertical),
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
            .padding(horizontal = Spacing.screenH, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        platforms.forEach { p ->
            SelectableChip(
                label = p,
                selected = selected == p,
                accentColor = PlatformColors.primary(p),
                onClick = { onToggle(p) },
            )
        }
    }
}

@Composable
private fun SortAndFilterChips(
    sortOrder: SortOrder,
    stabilityFilterActive: Boolean,
    favoritesFilterActive: Boolean,
    hasFavorites: Boolean,
    onSetSort: (SortOrder) -> Unit,
    onToggleStability: () -> Unit,
    onToggleFavorites: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenH, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        // Feature B: "Favorites" quick-filter chip — only shown when the user has at least one
        if (hasFavorites || favoritesFilterActive) {
            SelectableChip(
                label = "Favorites",
                selected = favoritesFilterActive,
                leadingIcon = Icons.Outlined.Star,
                accentColor = AppColors.favorite,
                onClick = onToggleFavorites,
            )
        }
        // "Runs great" quick-filter chip
        SelectableChip(
            label = "Runs great",
            selected = stabilityFilterActive,
            accentColor = AppColors.success,
            onClick = onToggleStability,
        )
        // Sort order chips
        SortOrder.entries.forEach { order ->
            SelectableChip(
                label = order.label,
                selected = sortOrder == order,
                onClick = { onSetSort(order) },
            )
        }
    }
}

@Composable
private fun ResultCountBar(count: Int, total: Int, filterActive: Boolean) {
    Text(
        if (filterActive) "$count of $total games" else "$total games in DB",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            horizontal = Spacing.screenH,
            vertical = Spacing.sm,
        ),
    )
}
