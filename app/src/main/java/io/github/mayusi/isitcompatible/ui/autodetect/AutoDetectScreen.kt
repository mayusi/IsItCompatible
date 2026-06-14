package io.github.mayusi.isitcompatible.ui.autodetect

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.horizontalScroll
import io.github.mayusi.isitcompatible.autodetect.AllFilesAccess
import io.github.mayusi.isitcompatible.autodetect.SwitchKeysStatus
import io.github.mayusi.isitcompatible.hardware.DeviceFingerprint
import io.github.mayusi.isitcompatible.hardware.SocCatalog
import io.github.mayusi.isitcompatible.hardware.SocTier
import io.github.mayusi.isitcompatible.recommend.Confidence
import io.github.mayusi.isitcompatible.ui.common.PlatformColors
import io.github.mayusi.isitcompatible.ui.common.SectionCard
import io.github.mayusi.isitcompatible.ui.common.PlatformBadge
import io.github.mayusi.isitcompatible.ui.theme.AppColors
import io.github.mayusi.isitcompatible.ui.theme.AppShapes
import io.github.mayusi.isitcompatible.ui.theme.Spacing
import io.github.mayusi.isitcompatible.ui.search.GameSummary
import java.io.File

@Composable
fun AutoDetectScreen(
    contentPadding: PaddingValues,
    onOpenGame: (gameId: String) -> Unit = {},
    vm: AutoDetectViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = Spacing.screenH, vertical = Spacing.screenV)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.sectionGap),
    ) {
        // ── Header ───────────────────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                "My Device",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "What you already have on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ── Hardware summary hero card — shown when fingerprint is available ──
        s.deviceFingerprint?.let { fp ->
            HardwareSummaryCard(fp = fp, stats = s.coverageStats)
        }

        // ── "Best for your chip" discovery feed ──────────────────────────────
        s.deviceFingerprint?.let { fp ->
            BestForChipSection(
                socLabel = fp.socFamily,
                games = s.bestForChipGames,
                onOpenGame = onOpenGame,
            )
        }

        // ── Permission gate ───────────────────────────────────────────────────
        if (!s.permissionGranted) {
            SectionCard(
                title = "Grant all-files access",
                icon = Icons.Filled.Storage,
                accentColor = AppColors.warning,
            ) {
                Text(
                    "We scan your Emulation folder for games and BIOS you've dumped. Only this tab uses it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.md))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Button(
                        onClick = {
                            AllFilesAccess.settingsIntent(context)?.let {
                                context.startActivity(it)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = AppShapes.button,
                    ) {
                        Text("Grant access")
                    }
                    TextButton(
                        onClick = vm::refreshPermission,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("I've granted it — rescan")
                    }
                }
            }
            return@Column
        }

        // ── Loading state ─────────────────────────────────────────────────────
        if (s.scanning) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text("Scanning…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            return@Column
        }

        s.result?.let { result ->

            // ── Emulators installed ───────────────────────────────────────────
            SectionCard(
                title = "Emulators installed (${result.installedEmulators.size})",
                icon = Icons.Filled.Devices,
                accentColor = MaterialTheme.colorScheme.primary,
            ) {
                if (result.installedEmulators.isEmpty()) {
                    Text(
                        "No known emulators detected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        result.installedEmulators.forEach { emulator ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Spacing.xs),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        emulator.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "${emulator.platformTargets} · ${emulator.installedVersion ?: "installed"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                GetItButton(
                                    status = s.installStatus[emulator.packageId],
                                    label = "Update",
                                    onClick = { vm.install(emulator.packageId, emulator.name) },
                                )
                            }
                        }
                    }
                }
            }

            // ── Games found ───────────────────────────────────────────────────
            SectionCard(
                title = "Games found (${result.gamesBySystem.size} systems)",
                icon = Icons.Filled.SportsEsports,
                accentColor = AppColors.sourceCommunity,
            ) {
                if (result.gamesBySystem.isEmpty()) {
                    Text(
                        "No games found under Emulation/roms/. Check Settings to pick your ROM folder.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        result.gamesBySystem.forEach { systemGames ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Spacing.xs),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        systemGames.platform,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        systemGames.sampleNames.joinToString(", "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                    )
                                }
                                // Game count badge
                                Box(
                                    Modifier
                                        .clip(AppShapes.badge)
                                        .background(AppColors.sourceCommunity.copy(alpha = 0.15f))
                                        .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
                                ) {
                                    Text(
                                        "${systemGames.count}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = AppColors.sourceCommunity,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Get an emulator for your games ────────────────────────────────
            if (result.missingEmulators.isNotEmpty()) {
                SectionCard(
                    title = "Get an emulator for your games",
                    icon = Icons.Outlined.Download,
                    accentColor = AppColors.sourceEmuReady,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        result.missingEmulators.forEach { sug ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Spacing.xs),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "You have ${sug.gameCount} ${sug.platform} game${if (sug.gameCount == 1) "" else "s"}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "No emulator yet — try ${sug.emulatorName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                GetItButton(
                                    status = s.installStatus[sug.packageId],
                                    label = "Get it",
                                    onClick = { vm.install(sug.packageId, sug.emulatorName) },
                                )
                            }
                        }
                    }
                }
            }

            // ── BIOS status ───────────────────────────────────────────────────
            SectionCard(
                title = "BIOS status",
                icon = Icons.Filled.Memory,
                accentColor = AppColors.warning,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    result.biosStatus.forEach { bios ->
                        Column(
                            Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        bios.display,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "Used by: ${bios.usedBy}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.width(Spacing.sm))
                                // Status pill
                                when {
                                    bios.foundInZip -> BiosStatusPill(
                                        label = "in archive",
                                        accentColor = AppColors.warning,
                                    )
                                    bios.present -> BiosStatusPill(
                                        label = "✓ found",
                                        accentColor = AppColors.success,
                                    )
                                    bios.required -> BiosStatusPill(
                                        label = "✗ missing",
                                        accentColor = AppColors.danger,
                                    )
                                    else -> BiosStatusPill(
                                        label = "optional",
                                        accentColor = AppColors.neutral,
                                    )
                                }
                            }

                            // Region + notes for found BIOS
                            if (bios.present && bios.region != null) {
                                Text(
                                    "${bios.region} — ${bios.notes ?: ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else if (!bios.present && bios.notes != null) {
                                Text(
                                    bios.notes,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            // Extract button for zipped BIOS
                            if (bios.foundInZip) {
                                val extractKey = "bios_${bios.system}"
                                val extractStatus = s.extractStatus[extractKey]
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                ) {
                                    when (extractStatus) {
                                        is ExtractStatus.Working -> {
                                            Row(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(Spacing.sm),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                )
                                                Text(
                                                    "Extracting…",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                        is ExtractStatus.Done -> {
                                            Text(
                                                "✓ Extracted",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = AppColors.success,
                                                modifier = Modifier.padding(Spacing.sm),
                                            )
                                        }
                                        is ExtractStatus.Failed -> {
                                            TextButton(
                                                onClick = { vm.extractBios(bios) },
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Text("Retry extraction")
                                            }
                                        }
                                        null -> {
                                            Button(
                                                onClick = { vm.extractBios(bios) },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = AppShapes.button,
                                            ) {
                                                Icon(Icons.Filled.GetApp, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.size(Spacing.xs))
                                                Text("Extract from ${File(bios.archivePath ?: "").name}")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Switch keys & firmware ────────────────────────────────────────
            SwitchKeysSection(
                switchKeys = result.switchKeys,
            )

            // ── Emulation root warning ────────────────────────────────────────
            if (!result.emulationRootExists) {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = AppShapes.card,
                    colors = CardDefaults.cardColors(
                        containerColor = AppColors.warning.copy(alpha = 0.10f),
                    ),
                ) {
                    Text(
                        "No Emulation/ folder found at /storage/emulated/0/Emulation. " +
                            "Create it (or use EmuTran) and rescan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.warning,
                        modifier = Modifier.padding(Spacing.cardPadding),
                    )
                }
            }
        }

        // ── Error state ───────────────────────────────────────────────────────
        s.lastError?.let {
            Card(
                Modifier.fillMaxWidth(),
                shape = AppShapes.card,
                colors = CardDefaults.cardColors(
                    containerColor = AppColors.danger.copy(alpha = 0.10f),
                ),
            ) {
                Text(
                    "Error: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.danger,
                    modifier = Modifier.padding(Spacing.cardPadding),
                )
            }
        }

        // ── Rescan button ─────────────────────────────────────────────────────
        Button(
            onClick = vm::scan,
            modifier = Modifier.fillMaxWidth(),
            shape = AppShapes.button,
        ) {
            Text("Rescan")
        }

        Spacer(Modifier.height(Spacing.lg))
    }
}

// ── BiosStatusPill ────────────────────────────────────────────────────────────

/** Compact tinted status pill for BIOS found / missing / in-archive / optional. */
@Composable
private fun BiosStatusPill(label: String, accentColor: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .clip(AppShapes.badge)
            .background(accentColor.copy(alpha = 0.15f))
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = accentColor,
        )
    }
}

// ── GetItButton ───────────────────────────────────────────────────────────────

/**
 * v0.10: "Get it / Update" button with inline status. Shows a spinner+label
 * while downloading, a green check when the installer was handed off, or a
 * short error. Reuses the AutoDetect install flow.
 */
@Composable
private fun GetItButton(
    status: GetItStatus?,
    label: String,
    onClick: () -> Unit,
) {
    when (status) {
        is GetItStatus.Working -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    status.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        is GetItStatus.Done -> {
            Text(
                "✓ opening installer",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.success,
            )
        }
        is GetItStatus.Failed -> {
            TextButton(onClick = onClick) { Text("Retry") }
        }
        null -> {
            TextButton(onClick = onClick) { Text(label) }
        }
    }
}

// ── SwitchKeysSection ─────────────────────────────────────────────────────────

/**
 * Switch keys & firmware. Nintendo Switch emulators (Eden / Citron / Sudachi)
 * need prod.keys + matching firmware before they can run anything. These come from
 * a Switch the user owns — there is NO download for them, and no app can provide or
 * fetch them — so this section only DETECTS what's already on the device, reports it
 * honestly, and shows where the files belong.
 *
 * [switchKeys] is null before a scan completes — in that case we show the explainer
 * and paths WITHOUT claiming found/not-found.
 */
@Composable
private fun SwitchKeysSection(
    switchKeys: SwitchKeysStatus?,
) {
    val context = LocalContext.current

    SectionCard(
        title = "Switch keys & firmware",
        icon = Icons.Filled.Key,
        accentColor = AppColors.sourceBundled,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {

            // STATUS first — only when we've actually scanned.
            if (switchKeys != null) {
                // prod.keys line.
                val keysText: String
                val keysColor: androidx.compose.ui.graphics.Color
                when {
                    switchKeys.prodKeysFound && switchKeys.keyCount == 0 -> {
                        keysText = "prod.keys ${switchKeys.keysNote ?: "found but couldn't read"}"
                        keysColor = AppColors.warning
                    }
                    switchKeys.prodKeysFound && switchKeys.keysLookComplete -> {
                        keysText = "✓ prod.keys found (${switchKeys.keyCount} keys, looks complete)"
                        keysColor = AppColors.success
                    }
                    switchKeys.prodKeysFound -> {
                        keysText = "✓ found but looks incomplete (${switchKeys.keyCount} keys)"
                        keysColor = AppColors.warning
                    }
                    else -> {
                        keysText = "✗ prod.keys not found"
                        keysColor = AppColors.danger
                    }
                }
                Text(
                    keysText,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = keysColor,
                )
                if (switchKeys.prodKeysFound && switchKeys.prodKeysPath != null) {
                    CopyPathChip(label = "prod.keys", path = switchKeys.prodKeysPath, context = context)
                }

                // firmware line.
                if (switchKeys.firmwareFound) {
                    Text(
                        "✓ Firmware present (${switchKeys.firmwareNcaCount} files)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.success,
                    )
                    if (switchKeys.firmwarePath != null) {
                        CopyPathChip(label = "Firmware", path = switchKeys.firmwarePath, context = context)
                    }
                } else {
                    Text(
                        "✗ Firmware not found",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.neutral,
                    )
                }
            }

            // HONEST explainer — neutral wording, promises nothing and names no provider.
            Text(
                "These come from a Nintendo Switch you own. There's no download for them " +
                    "in this app. Once you have them, place them in the folders shown below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Informational target paths (Eden) — tap to copy.
            Text(
                "Where they go (Eden):",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CopyPathChip(
                label = "Keys",
                path = "Android/data/dev.eden.eden_emulator/files/keys/",
                context = context,
            )
            CopyPathChip(
                label = "Firmware",
                path = "Android/data/dev.eden.eden_emulator/files/nand/system/Contents/registered/",
                context = context,
            )
        }
    }
}

// ── CopyPathChip ──────────────────────────────────────────────────────────────

/** A tap-to-copy path chip. Mirrors the PathChip used in the setup guide. */
@Composable
private fun CopyPathChip(label: String, path: String, context: Context) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(AppShapes.chip)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                cm?.setPrimaryClip(android.content.ClipData.newPlainText(label, path))
                android.widget.Toast.makeText(context, "Path copied", android.widget.Toast.LENGTH_SHORT).show()
            }
            .padding(horizontal = Spacing.chipHorizontal, vertical = Spacing.xs + Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            "$label:",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            path,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.size(Spacing.xxs))
        Icon(
            Icons.Outlined.ContentCopy,
            contentDescription = "Copy path",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp),
        )
    }
}

// ── HardwareSummaryCard ───────────────────────────────────────────────────────

@Composable
private fun HardwareSummaryCard(
    fp: DeviceFingerprint,
    stats: DeviceCoverageStats?,
) {
    val tier = SocCatalog.tier(fp.socFamily)
    val tierColor = when (tier) {
        SocTier.FLAGSHIP -> AppColors.tierFlagship
        SocTier.HIGH_END -> AppColors.tierHighEnd
        SocTier.MID_RANGE -> AppColors.tierMidRange
        SocTier.BUDGET -> AppColors.tierEntry
        SocTier.UNKNOWN -> AppColors.neutral
    }

    Card(
        Modifier.fillMaxWidth(),
        shape = AppShapes.cardLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
        ),
    ) {
        Column {
            // Top accent bar in tier color
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(tierColor),
            )

            Column(
                Modifier.padding(Spacing.cardPadding),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                // Header row: "Your hardware" label + tier badge
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Your hardware",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (tier != SocTier.UNKNOWN) {
                        Box(
                            Modifier
                                .clip(AppShapes.badge)
                                .background(tierColor.copy(alpha = 0.18f))
                                .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
                        ) {
                            Text(
                                tier.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = tierColor,
                            )
                        }
                    }
                }

                // Chip name — hero headline
                Text(
                    fp.socFamily,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                // Device display line (model/GPU/RAM)
                Text(
                    fp.displayLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Unknown chip reassurance
                if (tier == SocTier.UNKNOWN) {
                    Text(
                        "SoC not in our catalog yet — we'll match you to compatible reports using your GPU (${fp.gpuModel}) and ${fp.totalRamMb / 1024} GB RAM.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Coverage bar
                if (stats != null) {
                    val total = stats.real + stats.estimated
                    val fraction = if (total > 0) stats.real.toFloat() / total else 0f
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            val coverageText = if (stats.real == 0) {
                                "No chip-specific data yet"
                            } else {
                                "${stats.real} games have chip data"
                            }
                            Text(
                                coverageText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (stats.real == 0 && stats.estimated > 0) {
                                Text(
                                    "${stats.estimated} estimated",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppColors.neutral,
                                )
                            } else if (stats.real > 0) {
                                Text(
                                    "${stats.estimated} estimated",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppColors.neutral,
                                )
                            }
                        }
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(AppShapes.pill),
                            color = AppColors.success,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                        Text(
                            "Computing catalog coverage…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ── BestForChipSection ────────────────────────────────────────────────────────

/**
 * Feature A: "Best for your [chip]" horizontal discovery feed.
 *
 * Only shows games with STRONG/MODERATE confidence AND PERFECT/PLAYABLE stability —
 * i.e. real same-SoC or same-family data at good performance. No estimates,
 * no widened fallbacks. When the chip has no such data yet (untested/unknown
 * device), shows an honest "Not enough chip-specific data yet" card instead.
 *
 * [games] is null while loading, empty list when load finished but nothing qualifies.
 */
@Composable
private fun BestForChipSection(
    socLabel: String,
    games: List<GameSummary>?,
    onOpenGame: (gameId: String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            "Best for your $socLabel",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        when {
            games == null -> {
                // Still computing
                Card(
                    Modifier.fillMaxWidth(),
                    shape = AppShapes.card,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Row(
                        Modifier.padding(Spacing.cardPadding),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp)
                        Text(
                            "Finding games that run great on your chip…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            games.isEmpty() -> {
                // Honest empty state
                Card(
                    Modifier.fillMaxWidth(),
                    shape = AppShapes.card,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Column(
                        Modifier.padding(Spacing.cardPadding),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text(
                            "Not enough chip-specific data yet",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "No confirmed same-SoC reports at good performance exist for your chip. " +
                                "Browse all games to find options — reports for your hardware build " +
                                "up over time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            else -> {
                // Horizontal scroll of chip-confirmed game cards
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    games.forEach { summary ->
                        ChipGameCard(summary = summary, onClick = { onOpenGame(summary.game.id) })
                    }
                }
            }
        }
    }
}

// ── ChipGameCard ──────────────────────────────────────────────────────────────

/** A compact vertical card for the chip discovery feed. */
@Composable
private fun ChipGameCard(summary: GameSummary, onClick: () -> Unit) {
    val color = PlatformColors.primary(summary.game.platform)
    val stabColor = PlatformColors.stability(summary.bestStability)
    val isStrong = summary.bestConfidence == Confidence.STRONG

    Card(
        modifier = Modifier
            .width(148.dp)
            .clickable(onClick = onClick),
        shape = AppShapes.card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column {
            // Platform color bar at top
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(color),
            )
            Column(
                Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                // Platform badge
                PlatformBadge(platform = summary.game.platform)

                // Game title
                Text(
                    summary.game.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    modifier = Modifier.height(36.dp),
                )

                // FPS pill
                if (summary.bestFps != null) {
                    Box(
                        Modifier
                            .clip(AppShapes.badge)
                            .background(stabColor.copy(alpha = 0.18f))
                            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
                    ) {
                        Text(
                            "${summary.bestFps} fps",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = stabColor,
                        )
                    }
                }

                // Confidence badge — STRONG = same SoC+RAM, MODERATE = same SoC family
                Text(
                    if (isStrong) "Same SoC" else "Same family",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.neutral,
                )
            }
        }
    }
}
