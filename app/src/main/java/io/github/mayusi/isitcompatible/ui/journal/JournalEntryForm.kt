package io.github.mayusi.isitcompatible.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.mayusi.isitcompatible.compatdb.room.EmulatorEntity
import io.github.mayusi.isitcompatible.compatdb.room.GameEntity
import io.github.mayusi.isitcompatible.compatdb.room.JournalEntryEntity
import io.github.mayusi.isitcompatible.compatdb.room.PresetEntity
import io.github.mayusi.isitcompatible.ui.common.PlatformColors
import java.util.UUID

/**
 * In-app journal entry form. Rendered as a ModalBottomSheet from Game Detail
 * (and from the Apply confirmation sheet's "Log my result" button).
 *
 * Aggressive defaults so logging an entry takes 3-4 taps:
 *  - FPS slider pre-populated from the recommended preset's expected fps (when
 *    available) — user only adjusts if reality differed.
 *  - Stability defaults to PLAYABLE (the most common honest answer).
 *  - Emulator + preset auto-filled from the recommendation that prompted the
 *    log (passed in via [defaultEmulator] / [defaultPreset]).
 *  - Notes / session length / temp are optional.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalEntryForm(
    game: GameEntity,
    defaultEmulator: EmulatorEntity?,
    defaultPreset: PresetEntity?,
    defaultFps: Int?,
    defaultStability: String = "PLAYABLE",
    /** IIC round-trip: pre-fill session minutes from the GameNative fork broadcast. */
    defaultSessionMinutes: Int? = null,
    /** IIC round-trip: pre-fill a note if the FPS HUD was on during the session. */
    defaultNotes: String = "",
    onSave: (JournalEntryEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var fps by remember { mutableStateOf(defaultFps ?: 30) }
    var stability by remember { mutableStateOf(defaultStability) }
    var notes by remember { mutableStateOf(defaultNotes) }
    var sessionMinutes by remember {
        mutableStateOf(defaultSessionMinutes?.toString() ?: "")
    }
    var peakTempC by remember { mutableStateOf("") }
    var fpsKnown by remember { mutableStateOf(defaultFps != null) }
    // v0.6: default OFF. Journal stays local unless the user explicitly opts in.
    var shareWithCommunity by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
        ) {
            Text("Log a result",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(game.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary)
            defaultEmulator?.let {
                Text("Tested with ${it.name}${defaultPreset?.let { p -> " · ${p.name}" } ?: ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(20.dp))

            // -- FPS slider ----------------------------------------------------
            Text("Average FPS",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold)
            if (fpsKnown) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$fps",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = PlatformColors.stability(stability))
                    Text(" fps",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Slider(
                    value = fps.toFloat(),
                    onValueChange = { fps = it.toInt() },
                    valueRange = 0f..120f,
                    steps = 119,
                )
                TextButton(onClick = { fpsKnown = false }) { Text("I didn't measure FPS") }
            } else {
                Text("Not measured",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { fpsKnown = true }) { Text("Add an FPS estimate") }
            }

            // -- Stability radio -----------------------------------------------
            Spacer(Modifier.height(16.dp))
            Text("Stability",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            StabilityChips(selected = stability, onSelect = { stability = it })

            // -- Notes ---------------------------------------------------------
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                placeholder = { Text("e.g. 'crashes in Solitude after 2h, otherwise locked 60'") },
                modifier = Modifier.fillMaxWidth().height(120.dp),
            )

            // -- Optional fields -----------------------------------------------
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = sessionMinutes,
                    onValueChange = { sessionMinutes = it.filter { c -> c.isDigit() } },
                    label = { Text("Session (min)") },
                    placeholder = { Text("e.g. 45") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = peakTempC,
                    onValueChange = { peakTempC = it.filter { c -> c.isDigit() } },
                    label = { Text("Peak °C") },
                    placeholder = { Text("e.g. 68") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }

            // -- Share toggle --------------------------------------------------
            Spacer(Modifier.height(20.dp))
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                ),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Share with the community",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Opens a pre-filled GitHub issue you can review and submit. Includes only your device specs + the fields above.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = shareWithCommunity,
                            onCheckedChange = { shareWithCommunity = it },
                        )
                    }
                }
            }

            // -- Save ----------------------------------------------------------
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) { Text("Cancel") }
                Button(
                    onClick = {
                        val entry = JournalEntryEntity(
                            id = UUID.randomUUID().toString(),
                            gameId = game.id,
                            emulatorId = defaultEmulator?.id,
                            presetId = defaultPreset?.id,
                            avgFps = if (fpsKnown) fps else null,
                            stability = stability,
                            notes = notes.takeIf { it.isNotBlank() },
                            createdAt = System.currentTimeMillis(),
                            sessionMinutes = sessionMinutes.toIntOrNull(),
                            peakTempC = peakTempC.toIntOrNull(),
                            driverIdAtTimeOfRun = defaultPreset?.driverId,
                            shareWithCommunity = shareWithCommunity,
                        )
                        onSave(entry)
                    },
                    modifier = Modifier.weight(2f),
                ) { Text(if (shareWithCommunity) "Save & share" else "Save entry") }
            }
        }
    }
}

@Composable
private fun StabilityChips(selected: String, onSelect: (String) -> Unit) {
    val options = listOf("PERFECT", "PLAYABLE", "GLITCHY", "CRASHES")
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { opt ->
            val isSel = opt == selected
            val color = PlatformColors.stability(opt)
            Box(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isSel) color.copy(alpha = 0.85f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                    )
                    .clickable { onSelect(opt) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    PlatformColors.stabilityLabel(opt),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSel) Color.White
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
