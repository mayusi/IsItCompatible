package io.github.mayusi.isitcompatible.ui.detail

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.mayusi.isitcompatible.ui.theme.AppColors
import io.github.mayusi.isitcompatible.ui.theme.AppShapes
import io.github.mayusi.isitcompatible.ui.theme.Spacing

/**
 * The "It didn't work" interactive troubleshooter UI.
 *
 * Renders, inside the existing detail-screen [Section] wrapper, the full guided
 * flow driven by [TroubleshootViewModel]:
 *   PICKING   -> a short list of symptom buttons.
 *   TRYING    -> ONE ranked fix at a time + "It worked" / "Still broken — next fix".
 *   SOLVED    -> success state; "Mark as working / log result" and (Windows) the
 *                existing "Import my working config" CTA.
 *   EXHAUSTED -> honest "out of known fixes" end state with the same
 *                journal/import hooks. No dead community-repo references.
 *
 * Every outcome CTA is a lambda the screen wires to the existing
 * [GameDetailViewModel] functions, so the import + journal logic is reused, not
 * duplicated.
 */
@Composable
fun TroubleshootSection(
    state: TroubleshootState,
    accent: Color,
    onPickSymptom: (TroubleshootSymptom) -> Unit,
    onWorked: () -> Unit,
    onNextFix: () -> Unit,
    onReset: () -> Unit,
    /** SOLVED + EXHAUSTED: log a "mark as working" journal entry (opens the existing form). */
    onLogResult: () -> Unit,
    /** Windows only: launch the existing "import my working config" SAF picker. */
    onImportConfig: () -> Unit,
) {
    when (state.outcome) {
        Outcome.PICKING -> SymptomPicker(accent, onPickSymptom)
        Outcome.TRYING -> FixStep(state, accent, onWorked, onNextFix, onReset)
        Outcome.SOLVED -> SolvedState(state, accent, onLogResult, onImportConfig, onReset)
        Outcome.EXHAUSTED -> ExhaustedState(state, accent, onLogResult, onImportConfig, onReset)
        Outcome.ROLLBACK_LOOKUP -> RollbackSection(state, accent, onReset)
    }
}

@Composable
private fun SymptomPicker(
    accent: Color,
    onPick: (TroubleshootSymptom) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "What's going wrong? Pick the closest match and we'll walk you through " +
                "the known fixes one at a time.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        TroubleshootSymptom.ordered.forEach { symptom ->
            OutlinedButton(
                onClick = { onPick(symptom) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            ) {
                Text(symptom.label, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FixStep(
    state: TroubleshootState,
    accent: Color,
    onWorked: () -> Unit,
    onNextFix: () -> Unit,
    onReset: () -> Unit,
) {
    val fix = state.currentFix ?: return
    Column(Modifier.fillMaxWidth()) {
        // Context row: symptom + progress + change-symptom.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.symptom?.label ?: "",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                modifier = Modifier.weight(1f),
            )
            Text(state.stepLabel, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        SourceChip(fromGuide = fix.fromGuide)
        Spacer(Modifier.height(12.dp))

        // The one fix to try.
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Lightbulb, null, tint = AppColors.favorite, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Try this:", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(fix.fix, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(fix.detail, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Then relaunch the game — did that work?",
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onWorked, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("It worked")
            }
            OutlinedButton(onClick = onNextFix, modifier = Modifier.weight(1f)) {
                Text(if (state.currentIndex < state.fixes.lastIndex) "Still broken — next" else "Still broken")
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onReset) {
            Icon(Icons.Outlined.Close, null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Different problem")
        }
    }
}

@Composable
private fun SolvedState(
    state: TroubleshootState,
    accent: Color,
    onLogResult: () -> Unit,
    onImportConfig: () -> Unit,
    onReset: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().clip(AppShapes.card).background(AppColors.success.copy(alpha = 0.18f)).padding(Spacing.cardPadding),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, null, tint = AppColors.success, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Spacing.sm))
                Column {
                    Text("Nice — it's working", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, color = AppColors.success)
                    Text("Log it so it shows as your last run, and help the next person.",
                        style = MaterialTheme.typography.bodySmall, color = AppColors.success)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onLogResult, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Description, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Mark as working / log result")
        }
        if (state.isWindowsGame) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onImportConfig, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.FileUpload, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Import your working config to mark it Verified")
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onReset) { Text("Troubleshoot something else") }
    }
}

@Composable
private fun ExhaustedState(
    state: TroubleshootState,
    accent: Color,
    onLogResult: () -> Unit,
    onImportConfig: () -> Unit,
    onReset: () -> Unit,
) {
    val hadFixes = state.fixes.isNotEmpty()
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().clip(AppShapes.card)
                .background(AppColors.warning.copy(alpha = 0.18f)).padding(Spacing.cardPadding),
        ) {
            Column {
                Text(
                    if (hadFixes) "We're out of known fixes for this one" else "No specific fixes for this one yet",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    color = AppColors.warning,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    if (hadFixes)
                        "That's everything we know to try for this symptom on the recommended " +
                            "emulator. If you DO get it working, importing your config or logging " +
                            "the result helps the next person hit the same wall."
                    else
                        "We don't have ranked fixes for this symptom on the recommended emulator " +
                            "yet. If you work it out, logging the result (or importing your config) " +
                            "saves the next person the trouble.",
                    style = MaterialTheme.typography.bodySmall, color = AppColors.warning,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (state.isWindowsGame) {
            Button(onClick = onImportConfig, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.FileUpload, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Got it working? Import your config")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onLogResult, modifier = Modifier.fillMaxWidth()) {
                Text("Log the result instead")
            }
        } else {
            Button(onClick = onLogResult, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Description, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Got it working? Log the result")
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onReset) { Text("Try a different symptom") }
    }
}

/**
 * Feature A: rollback guidance section for "It worked before, now it doesn't".
 *
 * Only surfaces a driver-regression conclusion when journal evidence backs it
 * (a PERFECT/PLAYABLE entry with driverIdAtTimeOfRun recorded). Otherwise
 * shows an honest "can't tell" or "same driver" message.
 */
@Composable
private fun RollbackSection(
    state: TroubleshootState,
    accent: Color,
    onReset: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Column(Modifier.fillMaxWidth()) {
        // Symptom label + back button
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.symptom?.label ?: "",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))

        when (val rb = state.rollback) {
            null, RollbackState.Loading -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Checking your journal history…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            is RollbackState.Regression -> {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(AppShapes.card)
                        .background(AppColors.warning.copy(alpha = 0.18f))
                        .padding(Spacing.cardPadding),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.History,
                                null,
                                tint = AppColors.warning,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            Text(
                                "Possible driver regression detected",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.warning,
                            )
                        }
                        Spacer(Modifier.height(Spacing.sm))
                        Text(
                            "Your last working run on ${rb.lastWorkingDate} used driver " +
                                "${rb.workingDriverName}. A newer driver (${rb.currentDriverName}) " +
                                "may have caused this regression.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.warning,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Consider rolling back to ${rb.workingDriverName} to confirm.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(10.dp))
                if (!rb.workingDriverUrl.isNullOrBlank()) {
                    Button(
                        onClick = { uriHandler.openUri(rb.workingDriverUrl) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.History, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Download ${rb.workingDriverName}")
                    }
                } else {
                    OutlinedButton(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("No download URL for ${rb.workingDriverName}")
                    }
                }
            }

            is RollbackState.SameDriver -> {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(AppShapes.card)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(Spacing.cardPadding),
                ) {
                    Column {
                        Text(
                            "Driver hasn't changed",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            "Your last working run (${rb.lastWorkingDate}) also used " +
                                "${rb.driverName} — the same driver you're on now. " +
                                "The issue is likely elsewhere (emulator update, game files, settings).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            is RollbackState.NoEvidence -> {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(AppShapes.card)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(Spacing.cardPadding),
                ) {
                    Text(
                        rb.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onReset) {
            Icon(Icons.Outlined.Close, null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Different problem")
        }
    }
}

/** Small tag showing whether a fix is game-specific or an emulator-wide fallback. */
@Composable
private fun SourceChip(fromGuide: Boolean) {
    val (accent, label) = if (fromGuide)
        AppColors.sourceEmuReady to "Known issue for this game"
    else
        AppColors.neutral to "General fix for this emulator"
    Box(
        Modifier
            .clip(AppShapes.pill)
            .background(accent.copy(alpha = 0.18f))
            .padding(horizontal = Spacing.xs, vertical = Spacing.chipVertical),
    ) {
        Text(label, color = accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}
