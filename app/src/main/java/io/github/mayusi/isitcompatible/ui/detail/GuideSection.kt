package io.github.mayusi.isitcompatible.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mayusi.isitcompatible.compatdb.GuideStepDto
import io.github.mayusi.isitcompatible.compatdb.ResolvedGuide
import io.github.mayusi.isitcompatible.ui.theme.AppColors
import io.github.mayusi.isitcompatible.ui.theme.AppShapes
import io.github.mayusi.isitcompatible.ui.theme.Spacing

/**
 * v0.8: the "actually run it" guide. A typed, checkable, screen-by-screen
 * walkthrough with a "show me the settings" toggle and an expandable
 * troubleshooting section.
 *
 * The trust badge (tier) tells the user how trustworthy this guide is, and the
 * "as of" date reuses the v0.6 freshness convention.
 *
 * @param doneSteps indices the user has checked off (persisted by the ViewModel).
 * @param onToggleStep (index, done) → ViewModel persists + flips state.
 * @param onApplyDriver driverId → reuse the existing apply flow for GET_DRIVER steps.
 */
@Composable
fun GuideSection(
    guide: ResolvedGuide,
    doneSteps: Set<Int>,
    onToggleStep: (Int, Boolean) -> Unit,
    onApplyDriver: (String) -> Unit,
    accent: Color,
    /** v0.9: per-step "you already have this" status from the device scan. */
    stepStatuses: Map<Int, GuideStepStatus> = emptyMap(),
    /** v0.10: trigger in-app download+install of the guide's emulator (GET_APP step). */
    onGetApp: () -> Unit = {},
    /** v0.10: current download status for the GET_APP emulator, or null. */
    installStatus: GuideInstallStatus? = null,
    /** Device fingerprint label to display in banner, e.g. "Snapdragon 8 Elite · Adreno 830". */
    deviceLabel: String? = null,
) {
    val ctx = LocalContext.current
    var showSettingsFlat by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        // --- trust-badge header -------------------------------------------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            TierBadge(tier = guide.tier, label = guide.tierLabel)
            Spacer(Modifier.width(8.dp))
            if (guide.dataAsOf > 0) {
                val days = (System.currentTimeMillis() - guide.dataAsOf) / (1000L * 60 * 60 * 24)
                Text(
                    if (days > 60) "may be outdated" else "updated ${formatJournalDate(guide.dataAsOf)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (days > 60) AppColors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        guide.sourceUrl?.let { url ->
            Spacer(Modifier.height(2.dp))
            Text(
                "View source",
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { openUrl(ctx, url) },
            )
        }

        // --- device banner (if available) -----------------------------------------
        deviceLabel?.let {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.PhoneAndroid,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Tuned for high-end Android · Your device: $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // --- settings flatten toggle -------------------------------------------
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Tune, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Just show me the settings", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = showSettingsFlat, onCheckedChange = { showSettingsFlat = it })
        }
        Spacer(Modifier.height(8.dp))

        if (showSettingsFlat) {
            SettingsReferenceCard(guide.steps)
        } else {
            // --- the checklist --------------------------------------------------
            guide.steps.forEachIndexed { index, step ->
                GuideStepRow(
                    index = index,
                    step = step,
                    done = index in doneSteps,
                    accent = accent,
                    onToggle = { done -> onToggleStep(index, done) },
                    onApplyDriver = onApplyDriver,
                    ctx = ctx,
                    status = stepStatuses[index],
                    onGetApp = onGetApp,
                    installStatus = installStatus,
                )
                if (index < guide.steps.lastIndex) Spacer(Modifier.height(6.dp))
            }
        }

        // Troubleshooting content is now in the "Something's wrong?" Section
        // and the "Game notes" Section — removed from here (spec item 7).
    }
}

@Composable
private fun GuideStepRow(
    index: Int,
    step: GuideStepDto,
    done: Boolean,
    accent: Color,
    onToggle: (Boolean) -> Unit,
    onApplyDriver: (String) -> Unit,
    ctx: Context,
    status: GuideStepStatus? = null,
    onGetApp: () -> Unit = {},
    installStatus: GuideInstallStatus? = null,
) {
    val alreadyHave = status as? GuideStepStatus.AlreadyHave
    val isTip = step.kind == "TIP"
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        if (!isTip) {
            Icon(
                imageVector = if (done) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (done) "Done" else "Not done",
                tint = if (done) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onToggle(!done) },
            )
        } else {
            Icon(Icons.Outlined.Bolt, null, tint = AppColors.favorite, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                step.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (done) TextDecoration.LineThrough else null,
            )
            // v0.9: if the device scan says the user already satisfies this step,
            // show a green "you have it" chip instead of the download/BIOS prompt.
            if (alreadyHave != null && (step.kind == "GET_APP" || step.kind == "BIOS")) {
                Spacer(Modifier.height(4.dp))
                HaveChip(alreadyHave.label)
            } else when (step.kind) {
                "GET_APP" -> {
                    Spacer(Modifier.height(4.dp))
                    when (installStatus) {
                        is GuideInstallStatus.Working -> Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(14.dp), strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(installStatus.label, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        is GuideInstallStatus.Done -> Text(
                            "✓ opening installer",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold, color = AppColors.success,
                        )
                        is GuideInstallStatus.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                            ActionChip("Retry", Icons.Outlined.Download, accent) { onGetApp() }
                            step.url?.let { url ->
                                Spacer(Modifier.width(8.dp))
                                Text("open page", style = MaterialTheme.typography.labelSmall, color = accent,
                                    textDecoration = TextDecoration.Underline,
                                    modifier = Modifier.clickable { openUrl(ctx, url) })
                            }
                        }
                        null -> Row(verticalAlignment = Alignment.CenterVertically) {
                            // In-app "Get it": downloads the APK + opens the installer.
                            ActionChip("Get it", Icons.Outlined.Download, accent) { onGetApp() }
                            step.url?.let { url ->
                                Spacer(Modifier.width(8.dp))
                                Text("open page", style = MaterialTheme.typography.labelSmall, color = accent,
                                    textDecoration = TextDecoration.Underline,
                                    modifier = Modifier.clickable { openUrl(ctx, url) })
                            }
                        }
                    }
                }
                "GET_DRIVER" -> step.driverId?.let { id ->
                    Spacer(Modifier.height(4.dp))
                    ActionChip("Apply driver", Icons.Outlined.Bolt, accent) { onApplyDriver(id) }
                }
                "FILES" -> step.path?.let { path ->
                    Spacer(Modifier.height(4.dp))
                    PathChip(path, accent, ctx)
                }
                "CONTAINER" -> if (step.settings.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    SettingsTable(step.settings)
                }
                "BIOS" -> {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Lock, null, tint = accent, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "This file comes from hardware you own — there's no download here. Place/import it once you have it.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    step.path?.let {
                        Spacer(Modifier.height(4.dp))
                        PathChip(it, accent, ctx)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(AppShapes.chip)
            .background(accent.copy(alpha = 0.18f))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.chipHorizontal, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(Spacing.xs))
        Text(label, style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.SemiBold)
    }
}

/** v0.9: green "✓ you already have this" chip from the device scan. */
@Composable
private fun HaveChip(label: String) {
    Row(
        Modifier
            .clip(AppShapes.chip)
            .background(AppColors.success.copy(alpha = 0.18f))
            .padding(horizontal = Spacing.chipHorizontal, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.CheckCircle, null, tint = AppColors.success, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(Spacing.xs))
        Text(label, style = MaterialTheme.typography.labelMedium, color = AppColors.success, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PathChip(path: String, accent: Color, ctx: Context) {
    Row(
        Modifier
            .clip(AppShapes.chip)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable {
                copyToClipboard(ctx, "path", path)
                Toast.makeText(ctx, "Path copied", Toast.LENGTH_SHORT).show()
            }
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            path,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Outlined.ContentCopy, "Copy path", tint = accent, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun SettingsTable(settings: Map<String, String>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(AppShapes.chip)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(Spacing.chipHorizontal),
    ) {
        settings.entries.forEachIndexed { i, (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xxs)) {
                Text(
                    k,
                    modifier = Modifier.width(130.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(v, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** Flattened view: every CONTAINER/FILES step's data, no step sequencing. */
@Composable
private fun SettingsReferenceCard(steps: List<GuideStepDto>) {
    val containers = steps.filter { it.kind == "CONTAINER" && it.settings.isNotEmpty() }
    val files = steps.filter { it.kind == "FILES" && it.path != null }
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth()) {
        containers.forEach { SettingsTable(it.settings); Spacer(Modifier.height(8.dp)) }
        if (files.isNotEmpty()) {
            Text("Paths", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            files.forEach { it.path?.let { p -> PathChip(p, MaterialTheme.colorScheme.primary, ctx); Spacer(Modifier.height(4.dp)) } }
        }
        if (containers.isEmpty() && files.isEmpty()) {
            Text("No container settings for this guide.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TierBadge(tier: Int, label: String) {
    val accent = when (tier) {
        1    -> AppColors.success        // green — verified
        2    -> AppColors.sourceEmuReady // blue — authored
        3    -> AppColors.warning        // amber — EmuReady
        else -> AppColors.neutral        // grey — base
    }
    Box(
        Modifier
            .clip(AppShapes.pill)
            .background(accent.copy(alpha = 0.18f))
            .padding(horizontal = Spacing.chipHorizontal, vertical = Spacing.chipVertical),
    ) {
        Text(label, color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun openUrl(ctx: Context, url: String) {
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun copyToClipboard(ctx: Context, label: String, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}
