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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mayusi.isitcompatible.autodetect.AllFilesAccess
import io.github.mayusi.isitcompatible.autodetect.SwitchKeysStatus
import io.github.mayusi.isitcompatible.hardware.DeviceFingerprint
import io.github.mayusi.isitcompatible.hardware.SocCatalog
import io.github.mayusi.isitcompatible.hardware.SocTier
import java.io.File

@Composable
fun AutoDetectScreen(
    contentPadding: PaddingValues,
    vm: AutoDetectViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header
        Text("My Device", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "What you already have on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Hardware summary card — shown when fingerprint is available
        s.deviceFingerprint?.let { fp ->
            HardwareSummaryCard(fp = fp, stats = s.coverageStats)
        }

        // Permission gate
        if (!s.permissionGranted) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Grant all-files access",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "We scan your Emulation folder for games and BIOS you've dumped. Only this tab uses it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                AllFilesAccess.settingsIntent(context)?.let {
                                    context.startActivity(it)
                                }
                            },
                            modifier = Modifier.weight(1f),
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
            }
            return@Column
        }

        // Loading state
        if (s.scanning) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Scanning…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            return@Column
        }

        s.result?.let { result ->
            // Emulators section
            Section(
                title = "Emulators installed (${result.installedEmulators.size})",
                isEmpty = result.installedEmulators.isEmpty(),
                emptyText = "No known emulators detected.",
            ) {
                result.installedEmulators.forEach { emulator ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
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
                        // v0.10: "Update" Get-it action (re-download latest via the manifest).
                        GetItButton(
                            status = s.installStatus[emulator.packageId],
                            label = "Update",
                            onClick = { vm.install(emulator.packageId, emulator.name) },
                        )
                    }
                }
            }

            // Games section
            Section(
                title = "Games found (${result.gamesBySystem.size} systems)",
                isEmpty = result.gamesBySystem.isEmpty(),
                emptyText = "No games found under Emulation/roms/. Check Settings to pick your ROM folder.",
            ) {
                result.gamesBySystem.forEach { systemGames ->
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text(
                            "${systemGames.platform} · ${systemGames.count} games",
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
                }
            }

            // v0.10: Missing-emulator suggestions — you have games for a system
            // but no emulator that runs it. Offer to download the right one.
            if (result.missingEmulators.isNotEmpty()) {
                Section(
                    title = "Get an emulator for your games",
                    isEmpty = false,
                ) {
                    result.missingEmulators.forEach { sug ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
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

            // BIOS section
            Section(
                title = "BIOS status",
                isEmpty = false,
            ) {
                result.biosStatus.forEach { bios ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
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
                            // Status indicator
                            when {
                                bios.foundInZip -> {
                                    // Found in zip — show amber indicator
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            "📦 in archive",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFFB45309),
                                        )
                                    }
                                }
                                bios.present -> {
                                    // Plain file present — show green checkmark
                                    Text(
                                        "✓ ${bios.foundFile ?: "found"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF059669),
                                    )
                                }
                                else -> {
                                    // Missing — show red X
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            "✗ not found",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (bios.required) Color(0xFFDC2626) else Color(0xFF6B7280),
                                        )
                                        if (!bios.required) {
                                            Text(
                                                "optional",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF6B7280),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Show region + notes for found BIOS
                        if (bios.present && bios.region != null) {
                            Text(
                                "${bios.region} — ${bios.notes ?: ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else if (!bios.present && bios.notes != null) {
                            // Show hint for missing BIOS
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
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                when (extractStatus) {
                                    is ExtractStatus.Working -> {
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                            color = Color(0xFF059669),
                                            modifier = Modifier.padding(8.dp),
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
                                        ) {
                                            Icon(Icons.Filled.GetApp, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.size(4.dp))
                                            Text("Extract from ${File(bios.archivePath ?: "").name}")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Switch keys & firmware — DETECT what's present and report honestly.
            // Keys/firmware come from a Switch the user owns; there's no download
            // for them in this app, and no app can provide or fetch them.
            SwitchKeysSection(
                switchKeys = result.switchKeys,
            )

            // Emulation root warning
            if (!result.emulationRootExists) {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        "No Emulation/ folder found at /storage/emulated/0/Emulation. " +
                            "Create it (or use EmuTran) and rescan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF92400E),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        // Error state
        s.lastError?.let {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    "Error: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFDC2626),
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        // Rescan button
        Button(onClick = vm::scan, modifier = Modifier.fillMaxWidth()) {
            Text("Rescan")
        }

        Spacer(Modifier.height(16.dp))
    }
}

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.size(6.dp))
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
                color = Color(0xFF059669),
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
    val green = Color(0xFF059669)
    val amber = Color(0xFFB45309)
    val red = Color(0xFFDC2626)
    val grey = Color(0xFF6B7280)

    Section(title = "Switch keys & firmware", isEmpty = false) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

            // STATUS first — only when we've actually scanned.
            if (switchKeys != null) {
                // prod.keys line.
                val keysText: String
                val keysColor: Color
                when {
                    switchKeys.prodKeysFound && switchKeys.keyCount == 0 -> {
                        // Found on disk but unreadable (scoped storage) — keysNote carries the hint.
                        keysText = "prod.keys ${switchKeys.keysNote ?: "found but couldn't read"}"
                        keysColor = amber
                    }
                    switchKeys.prodKeysFound && switchKeys.keysLookComplete -> {
                        keysText = "✓ prod.keys found (${switchKeys.keyCount} keys, looks complete)"
                        keysColor = green
                    }
                    switchKeys.prodKeysFound -> {
                        keysText = "✓ found but looks incomplete (${switchKeys.keyCount} keys)"
                        keysColor = amber
                    }
                    else -> {
                        keysText = "✗ prod.keys not found"
                        keysColor = red
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
                        color = green,
                    )
                    if (switchKeys.firmwarePath != null) {
                        CopyPathChip(label = "Firmware", path = switchKeys.firmwarePath, context = context)
                    }
                } else {
                    Text(
                        "✗ Firmware not found",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = grey,
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

            // Informational target paths (Eden) — tap to copy. Always shown so the
            // user knows where the files belong even before/without a scan.
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

/** A tap-to-copy path chip. Mirrors the PathChip used in the setup guide. */
@Composable
private fun CopyPathChip(label: String, path: String, context: Context) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                cm?.setPrimaryClip(android.content.ClipData.newPlainText(label, path))
                android.widget.Toast.makeText(context, "Path copied", android.widget.Toast.LENGTH_SHORT).show()
            }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
        Spacer(Modifier.size(2.dp))
        Icon(
            Icons.Outlined.ContentCopy,
            contentDescription = "Copy path",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun HardwareSummaryCard(
    fp: DeviceFingerprint,
    stats: DeviceCoverageStats?,
) {
    val tier = SocCatalog.tier(fp.socFamily)
    val tierColor = when (tier) {
        SocTier.FLAGSHIP -> Color(0xFF6200EE)
        SocTier.HIGH_END -> Color(0xFF1976D2)
        SocTier.MID_RANGE -> Color(0xFF00897B)
        SocTier.BUDGET -> Color(0xFF757575)
        SocTier.UNKNOWN -> Color(0xFF9E9E9E)
    }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        ),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Your hardware",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                // For unknown/unlisted chips, omit the tier badge and show a softer label
                // instead so the device doesn't feel unsupported.
                if (tier != SocTier.UNKNOWN) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(tierColor.copy(alpha = 0.18f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
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
            Text(
                fp.displayLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // For unknown chips, show a reassurance line explaining detection still works.
            if (tier == SocTier.UNKNOWN) {
                Text(
                    "SoC not in our catalog yet — we'll match you to compatible reports using your GPU (${fp.gpuModel}) and ${fp.totalRamMb / 1024} GB RAM.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (stats != null) {
                val total = stats.real + stats.estimated
                val fraction = if (total > 0) stats.real.toFloat() / total else 0f
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val coverageText = if (stats.real == 0) {
                        "No chip-specific data yet — ${stats.estimated} games have estimated compatibility for similar hardware"
                    } else {
                        "${stats.real} games have data for your chip"
                    }
                    Text(
                        coverageText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (stats.real > 0) {
                        Text(
                            "${stats.estimated} estimated",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFF4CAF50),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
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

@Composable
private fun Section(
    title: String,
    isEmpty: Boolean = false,
    emptyText: String? = null,
    content: @Composable () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Card(Modifier.fillMaxWidth()) {
            if (isEmpty) {
                Text(
                    emptyText ?: "No data",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                Column(Modifier.padding(16.dp)) {
                    content()
                }
            }
        }
    }
}
