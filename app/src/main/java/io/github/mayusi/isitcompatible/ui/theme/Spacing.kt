package io.github.mayusi.isitcompatible.ui.theme

import androidx.compose.ui.unit.dp

/**
 * "Handheld Forge" spacing token system.
 *
 * Replaces the ~50 scattered hardcoded dp values (2/4/6/8/10/12/14/16/20/24/32)
 * with semantic names that screen agents reference by purpose, not by pixel count.
 * This keeps the rhythm consistent when a single token is tuned.
 *
 * Usage guide for screen-redesign agents:
 *   • screenH / screenV      — outermost content padding (matches Window insets padding)
 *   • cardPadding            — padding INSIDE a card/surface (was inconsistently 12–16)
 *   • cardGap                — vertical gap between stacked cards / list items
 *   • sectionGap             — gap between distinct UI sections on a screen
 *   • itemGap                — tight gap between siblings inside one card (icon + text)
 *   • xxs … xxl              — raw scale, use when no semantic token fits exactly
 */
object Spacing {
    /** 2 dp — icon-to-label micro spacing, divider insets */
    val xxs = 2.dp

    /** 4 dp — tight icon/badge internal padding */
    val xs  = 4.dp

    /** 8 dp — gap between siblings inside a row/column (icon + text) */
    val sm  = 8.dp

    /** 12 dp — card-to-card gap; comfortable list item separation */
    val md  = 12.dp

    /** 16 dp — standard horizontal screen padding; card internal padding */
    val lg  = 16.dp

    /** 24 dp — major section gap; large card internal padding */
    val xl  = 24.dp

    /** 32 dp — top-of-screen hero breathing room */
    val xxl = 32.dp

    // ── Semantic tokens ───────────────────────────────────────────────────────

    /** Horizontal edge padding for screen content (16 dp). */
    val screenH = 16.dp

    /** Vertical top/bottom padding for screen content (16 dp). */
    val screenV = 16.dp

    /** Gap between stacked cards or list items (12 dp — was an inconsistent 8).
     *  The extra 4 dp significantly reduces the "cramped" audit complaint. */
    val cardGap = 12.dp

    /** Internal padding inside a Card or Surface (16 dp). */
    val cardPadding = 16.dp

    /** Gap between major UI sections on a screen (20 dp). */
    val sectionGap = 20.dp

    /** Tight gap between sibling elements inside one row/card (8 dp). */
    val itemGap = 8.dp

    /** Padding inside a chip or badge (horizontal: 10 dp). */
    val chipHorizontal = 10.dp

    /** Padding inside a chip or badge (vertical: 4 dp). */
    val chipVertical = 4.dp
}
