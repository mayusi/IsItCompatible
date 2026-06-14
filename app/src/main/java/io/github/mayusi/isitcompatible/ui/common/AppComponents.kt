package io.github.mayusi.isitcompatible.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mayusi.isitcompatible.ui.theme.AppColors
import io.github.mayusi.isitcompatible.ui.theme.AppShapes
import io.github.mayusi.isitcompatible.ui.theme.Spacing

/* =============================================================================
   AppComponents.kt — "Handheld Forge" shared UI primitives
   Replaces inline duplicates across Search / Library / Journal / GameDetail.

   Every color reference goes through MaterialTheme, AppColors, or
   PlatformColors. Zero raw hex. Zero Color.White / Color.Black.
   Every spacing uses Spacing tokens. Every shape uses AppShapes.
   ============================================================================= */

// ── 1. FpsPill ───────────────────────────────────────────────────────────────

/**
 * Tinted pill showing an FPS number + unit label.
 *
 * Replaces three inline copies:
 *   - SearchScreen.kt GameCard FPS block (~lines 479–521)
 *   - LibraryScreen.kt private FpsPill (~lines 267–317)
 *   - JournalScreen.kt JournalEntryRow FPS block (~lines 296–313)
 *
 * @param fps            The frames-per-second value. Null → dash pill (surfaceVariant bg).
 * @param stability      Raw stability string ("PERFECT","PLAYABLE","GLITCHY","CRASHES",other).
 *                       Drives background and text color via [PlatformColors.stability].
 * @param isEstimated    True  → label "est."  (VERY_WEAK confidence — no same-device data)
 *                       False and low-conf → label "fps?"  (WEAK confidence — wider bucket)
 *                       False and normal   → label "fps"
 * @param isLowConfidence True when confidence is WEAK or VERY_WEAK; dims colors to 55%/9%.
 *                        Search & Library compute this from Confidence; Journal always uses
 *                        normal confidence so pass false there.
 */
@Composable
fun FpsPill(
    fps: Int?,
    stability: String?,
    isEstimated: Boolean,
    modifier: Modifier = Modifier,
    isLowConfidence: Boolean = false,
) {
    if (fps != null) {
        val baseColor = PlatformColors.stability(stability)
        val textColor = if (isLowConfidence) baseColor.copy(alpha = 0.55f) else baseColor
        val bgColor   = if (isLowConfidence) baseColor.copy(alpha = 0.09f) else baseColor.copy(alpha = 0.18f)
        val unitLabel = when {
            isEstimated      -> "est."
            isLowConfidence  -> "fps?"
            else             -> "fps"
        }
        Box(
            modifier
                .clip(AppShapes.pill)
                .background(bgColor)
                .padding(horizontal = Spacing.chipHorizontal, vertical = Spacing.sm),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$fps",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                )
                Text(
                    text = unitLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = textColor,
                )
            }
        }
    } else {
        // Null / no-data state: dash in a muted surfaceVariant pill
        Box(
            modifier
                .clip(AppShapes.pill)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = Spacing.chipHorizontal, vertical = Spacing.sm),
        ) {
            Text(
                text = "—",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── 2. PlatformBadge ─────────────────────────────────────────────────────────

/**
 * Compact colored badge showing a platform name (e.g. "SWITCH", "PS4").
 *
 * Replaces four inline copies:
 *   - SearchScreen.kt GameCard platform badge (~lines 397–408)
 *   - LibraryScreen.kt LibraryGameCard platform badge (~lines 175–189)
 *   - JournalScreen.kt JournalEntryRow platform badge (~lines 231–242)
 *   - GameDetailScreen.kt private PlatformBadge (~lines 621–634)
 *
 * FIX vs. originals: GameDetailScreen hardcoded `Color.White` for text on the
 * full-strength (alpha=0.85) bg — illegible on light platforms. This component
 * uses a tinted bg (0.18f) with the platform color at full strength as text,
 * which is always readable on the dark surface. Same pattern the Search /
 * Library / Journal inline copies already used (those three were already correct).
 *
 * @param platform  Raw platform string; forwarded to [PlatformColors.primary].
 */
@Composable
fun PlatformBadge(
    platform: String,
    modifier: Modifier = Modifier,
) {
    val color = PlatformColors.primary(platform)
    Box(
        modifier
            .clip(AppShapes.badge)
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = Spacing.sm, vertical = Spacing.chipVertical),
    ) {
        Text(
            text = platform,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

// ── 3. GameRowCard ────────────────────────────────────────────────────────────

/**
 * Unified list-row card used on Browse, Library, and Journal screens.
 *
 * Replaces the structural shell of:
 *   - SearchScreen.kt private GameCard
 *   - LibraryScreen.kt private LibraryGameCard
 *   - JournalScreen.kt private JournalEntryRow
 *
 * Layout: [accent bar 4dp] [PlatformBadge] [Column(weight=1)] [FpsPill]
 *         [trailingContent?]
 *
 * Journal needs a delete IconButton to the RIGHT of the FPS pill.
 * Pass it via [trailingContent]. The pill is always rendered — trailingContent
 * appends after it (e.g. Journal's delete icon), so both show simultaneously.
 *
 * @param platform          Used for left-accent color AND [PlatformBadge].
 * @param title             Game/entry title — bodyLarge SemiBold.
 * @param subtitle1         First subtitle line (report count, date, etc.) — labelSmall muted.
 * @param subtitle2         Optional second subtitle (best emulator, etc.). Null → omitted.
 * @param fps               Forwarded to [FpsPill].
 * @param stability         Forwarded to [FpsPill].
 * @param isEstimated       Forwarded to [FpsPill].
 * @param isLowConfidence   Forwarded to [FpsPill] for dimmed confidence styling.
 * @param leadingAccentColor Override the left-bar color if you want something other than
 *                          the platform color (rare; pass PlatformColors.primary(platform)
 *                          in the normal case).
 * @param isFavorite        Shows a ★ star beside the title in [AppColors.favorite].
 * @param isTried           Shows a "✓ tried" micro-chip beside the title.
 * @param trailingContent   Optional slot rendered to the RIGHT of [FpsPill] (e.g. delete
 *                          IconButton in Journal). Null → nothing extra rendered.
 * @param onClick           Row click handler.
 */
@Composable
fun GameRowCard(
    platform: String,
    title: String,
    subtitle1: String,
    subtitle2: String?,
    fps: Int?,
    stability: String?,
    isEstimated: Boolean,
    leadingAccentColor: Color,
    modifier: Modifier = Modifier,
    isLowConfidence: Boolean = false,
    isFavorite: Boolean = false,
    isTried: Boolean = false,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(AppShapes.card)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left platform accent bar
        Box(
            Modifier
                .width(4.dp)
                .height(72.dp)
                .background(leadingAccentColor),
        )

        // Platform badge
        PlatformBadge(
            platform = platform,
            modifier = Modifier.padding(start = Spacing.md),
        )

        Spacer(Modifier.width(Spacing.md))

        // Title + subtitles column
        Column(
            Modifier
                .weight(1f)
                .padding(vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            // Title row: title text + optional star + optional "tried" chip
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isFavorite) {
                    Spacer(Modifier.width(Spacing.xs))
                    Icon(
                        imageVector = Icons.Outlined.Star,
                        contentDescription = "Favorited",
                        tint = AppColors.favorite,
                        modifier = Modifier.size(14.dp),
                    )
                }
                if (isTried) {
                    Spacer(Modifier.width(Spacing.sm))
                    Box(
                        Modifier
                            .clip(AppShapes.badge)
                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f))
                            .padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
                    ) {
                        Text(
                            text = "✓ tried",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // Subtitle 1 — always shown
            Text(
                text = subtitle1,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Subtitle 2 — optional (best emulator line, etc.)
            if (subtitle2 != null) {
                Text(
                    text = subtitle2,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // FPS pill — always rendered
        FpsPill(
            fps = fps,
            stability = stability,
            isEstimated = isEstimated,
            isLowConfidence = isLowConfidence,
            modifier = Modifier.padding(end = if (trailingContent != null) Spacing.xs else Spacing.md),
        )

        // Optional trailing slot (e.g. Journal delete IconButton)
        if (trailingContent != null) {
            trailingContent()
        }
    }
}

// ── 4. SelectableChip ────────────────────────────────────────────────────────

/**
 * A styled toggle chip for filter/sort rows.
 *
 * Replaces the selected-state `Color.White` hardcode in:
 *   - SearchScreen.kt PlatformChips (~lines 259–281)
 *   - SearchScreen.kt SortAndFilterChips (~lines 302–364)
 *   - LibraryScreen.kt SortChips (~lines 124–145)
 *
 * Selected state: bg = [accentColor], text = MaterialTheme.colorScheme.onPrimary
 *   (in the dark HUD theme this is #003740, a very dark teal that reads perfectly
 *   on the cyan/colored selected surface — not hardcoded Color.White).
 * Unselected state: bg = surfaceVariant, text = onSurfaceVariant, thin outline.
 *
 * @param label       Chip label text.
 * @param selected    Whether this chip is in the selected/active state.
 * @param leadingIcon Optional icon drawn at 16 dp before the label.
 * @param accentColor Selected-state background. Defaults to MaterialTheme primary.
 * @param onClick     Selection toggle callback.
 */
@Composable
fun SelectableChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    val bgColor   = if (selected) accentColor.copy(alpha = 0.90f) else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val weight    = if (selected) FontWeight.Bold else FontWeight.Normal

    val baseModifier = modifier
        .clip(AppShapes.chip)
        .background(bgColor)
        .then(
            if (!selected) Modifier.border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = AppShapes.chip,
            ) else Modifier
        )
        .clickable(onClick = onClick)
        .padding(horizontal = Spacing.chipHorizontal, vertical = Spacing.chipVertical)

    if (leadingIcon != null) {
        Row(
            baseModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = weight,
                color = textColor,
            )
        }
    } else {
        Box(baseModifier) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = weight,
                color = textColor,
            )
        }
    }
}

// ── 5. SectionCard ───────────────────────────────────────────────────────────

/**
 * A themed content card with a header and a body column.
 *
 * Generalizes the GameDetailScreen private `Section` composable and any flat
 * section blocks on other screens into a single non-collapsing card shell.
 * Collapsing behavior (as GameDetail's Section has) should remain screen-specific
 * if needed — this is the static base visual.
 *
 * Layout:
 *   [top accent bar 3dp, accentColor]
 *   [icon circle (32dp) OR leading accent bar] [title titleMedium] [trailing?]
 *   [content column, Spacing.cardPadding]
 *
 * @param title        Section header text — titleMedium SemiBold.
 * @param icon         Optional leading icon. When present, rendered in a 32dp circle
 *                     with [accentColor].copy(0.15f) bg. When null, no icon shown.
 * @param accentColor  Color for the top bar and icon tint. Defaults to theme primary.
 * @param trailing     Optional trailing slot at the end of the header row (chevron, etc.).
 * @param content      Content composable placed in a Column below the header with
 *                     [Spacing.cardPadding] horizontal padding.
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column {
            // Top accent bar
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(accentColor),
            )

            // Header row
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = Spacing.cardPadding,
                        vertical = Spacing.md,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(Spacing.md))
                }

                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                if (trailing != null) {
                    trailing()
                }
            }

            // Content slot
            Column(
                Modifier.padding(
                    start = Spacing.cardPadding,
                    end = Spacing.cardPadding,
                    bottom = Spacing.cardPadding,
                ),
                content = content,
            )
        }
    }
}
