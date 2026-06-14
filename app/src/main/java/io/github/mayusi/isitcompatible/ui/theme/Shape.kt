package io.github.mayusi.isitcompatible.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * "Handheld Forge" shape system.
 *
 * Corner radii are deliberately mid-range — not the extremely round "bubbly" M3
 * defaults, but not squared-off either. The result reads as a modern firmware UI:
 * precise, purposeful, slightly angular. Cards feel solid; chips feel like HUD tags.
 *
 * Usage guide for screen-redesign agents:
 *   AppShapes.chip       → platform/source/stability chips
 *   AppShapes.badge      → small status badges, count indicators
 *   AppShapes.pill       → toggle switches, progress-bar ends
 *   AppShapes.card       → standard content cards, bottom sheets
 *   AppShapes.cardLarge  → hero verdict cards, full-width featured rows
 *   AppShapes.button     → all Buttons and FABs
 *
 * MaterialTheme.shapes is also wired (small=8, medium=16, large=20) so
 * M3 components that derive from MaterialTheme.shapes.* automatically
 * adopt the design system without manual overrides.
 */
object AppShapes {
    /** 8 dp — platform/source/stability chips, small tags */
    val chip = RoundedCornerShape(8.dp)

    /** 6 dp — compact count/status badges */
    val badge = RoundedCornerShape(6.dp)

    /** 50 % (fully rounded) — toggle-style pills, avatar containers */
    val pill = RoundedCornerShape(50)

    /** 16 dp — standard content cards, dialogs */
    val card = RoundedCornerShape(16.dp)

    /** 20 dp — hero/verdict card, featured game banner */
    val cardLarge = RoundedCornerShape(20.dp)

    /** 12 dp — all Button and FAB shapes */
    val button = RoundedCornerShape(12.dp)
}

/**
 * Material3 Shapes wired from AppShapes.
 * Referenced in MaterialTheme(shapes = AppMaterialShapes).
 * Consumed automatically by M3 components (Card, Button, ModalBottomSheet, etc.).
 */
internal val AppMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),   // tooltips, small menus
    small      = RoundedCornerShape(8.dp),   // chips, text fields
    medium     = RoundedCornerShape(16.dp),  // cards, dialogs
    large      = RoundedCornerShape(20.dp),  // bottom sheets, large surfaces
    extraLarge = RoundedCornerShape(28.dp),  // full-screen dialogs
)
