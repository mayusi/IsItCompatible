package io.github.mayusi.isitcompatible.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Share
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import io.github.mayusi.isitcompatible.apply.ApplyJobState
import io.github.mayusi.isitcompatible.apply.StagedFile
import io.github.mayusi.isitcompatible.compatdb.JournalShareIntent
import io.github.mayusi.isitcompatible.compatdb.room.EmulatorEntity
import io.github.mayusi.isitcompatible.compatdb.room.GameEntity
import io.github.mayusi.isitcompatible.compatdb.room.JournalEntryEntity
import io.github.mayusi.isitcompatible.compatdb.room.PresetEntity
import io.github.mayusi.isitcompatible.compatdb.room.ReportEntity
import io.github.mayusi.isitcompatible.recommend.Bucket
import io.github.mayusi.isitcompatible.recommend.Confidence
import io.github.mayusi.isitcompatible.recommend.Recommendation
import io.github.mayusi.isitcompatible.ui.common.PlatformColors
import io.github.mayusi.isitcompatible.ui.journal.JournalEntryForm
import io.github.mayusi.isitcompatible.ui.submit.SubmitLinks
import java.text.DateFormat
import java.util.Date
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailScreen(
    onBack: () -> Unit,
    vm: GameDetailViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val platformColor = s.game?.let { PlatformColors.primary(it.platform) } ?: MaterialTheme.colorScheme.primary

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.game?.title ?: "Game", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = platformColor.copy(alpha = 0.10f),
                ),
            )
        }
    ) { pad ->
        if (s.loading) {
            Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        if (s.game == null) {
            Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) { Text("Game not found.") }
            return@Scaffold
        }
        val game = s.game!!

        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()),
        ) {
            // Spec item 1: CompactGameHeader replaces PlatformHeader + CoverAndScreenshotsStrip
            CompactGameHeader(game)

            Column(Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(16.dp))

                if (s.recommendations.isEmpty()) {
                    NoReportsCard(
                        game = game,
                        fingerprint = s.fingerprint?.displayLine,
                        onSubmit = { SubmitLinks.openSubmitFor(ctx, game, s.fingerprint) },
                    )
                } else {
                    val realTop = s.recommendationsBySource.fromReal.firstOrNull()
                    val genTop = s.recommendationsBySource.fromGenerated.firstOrNull()

                    // Hoisted SAF picker reused by VerdictCard and GameNative panel
                    val importConfigLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.OpenDocument(),
                    ) { uri -> if (uri != null) vm.importGameNativeConfig(uri) }
                    val launchImport = { importConfigLauncher.launch(arrayOf("application/json", "*/*")) }

                    // Spec item 2: VerdictCard — one card replaces YourLastRunTile +
                    // HeroFpsCard REAL + HeroFpsCard GENERATED_SECONDARY
                    val lastJournal = s.journalEntries.firstOrNull()
                    VerdictCard(
                        realTop = realTop,
                        genTop = genTop,
                        lastJournal = lastJournal,
                        emulatorsById = s.emulatorsById,
                        presetsById = s.presetsById,
                        userRamMb = s.fingerprint?.totalRamMb,
                        recommendedRamGb = game.recommendedRamGb,
                        isWindows = s.isWindowsGame,
                        canOneTapLaunch = s.canOneTapLaunch,
                        onApplyReal = { realTop?.let { vm.apply(it) } },
                        onApplyGen = { genTop?.let { vm.apply(it) } },
                        onLaunchInGameNative = { vm.launchInGameNative() },
                        onDownloadConfig = { vm.applyGameNativeConfig() },
                        onLogAnother = { vm.openJournalForm() },
                        onImportConfig = launchImport,
                        onSubmitReport = { SubmitLinks.openSubmitFor(ctx, game, s.fingerprint) },
                    )

                    // The "top" pick — used downstream for Setup tabs / Preset details.
                    val top = realTop ?: genTop!!

                    // Setup steps data
                    val viableEmulators = (listOf(top) + s.recommendations.drop(1))
                        .mapNotNull { rec -> s.emulatorsById[rec.emulatorId]?.let { it to rec } }
                        .distinctBy { it.first.id }
                        .take(4)
                    val perEmulatorMap = remember(game.perEmulatorSetup) {
                        runCatching {
                            game.perEmulatorSetup
                                ?.takeIf { it.isNotBlank() }
                                ?.let { raw -> Json.parseToJsonElement(raw).jsonObject }
                                ?.mapValues { (_, v) ->
                                    (v as? JsonPrimitive)?.contentOrNullSafe().orEmpty()
                                        .split('\n').map { it.trim() }.filter { it.isNotEmpty() }
                                } ?: emptyMap()
                        }.getOrDefault(emptyMap())
                    }
                    val legacySteps = game.setupSteps
                        ?.takeIf { it.isNotBlank() }
                        ?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }
                        ?: emptyList()

                    // ── PRIMARY ZONE ──────────────────────────────────────────
                    // Spec item 3: "How to run it" — collapsed by default
                    val resolvedGuide = s.guide
                    if (resolvedGuide != null && resolvedGuide.steps.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        val guideEmuName = s.emulatorsById[resolvedGuide.emulatorId]?.name ?: resolvedGuide.emulatorId
                        Section(
                            title = "How to run it · $guideEmuName",
                            icon = Icons.Outlined.Speed,
                            accent = platformColor,
                            initiallyExpanded = false,
                        ) {
                            GuideSection(
                                guide = resolvedGuide,
                                doneSteps = s.guideDoneSteps,
                                onToggleStep = { idx, done -> vm.toggleGuideStep(idx, done) },
                                onApplyDriver = { _ ->
                                    s.recommendations.firstOrNull()?.let { vm.apply(it) }
                                },
                                accent = platformColor,
                                stepStatuses = s.guideStepStatuses,
                                onGetApp = { vm.installGuideEmulator() },
                                installStatus = s.emulatorInstallStatus,
                                deviceLabel = s.fingerprint?.let { "${it.socFamily} · ${it.gpuModel}" },
                            )
                        }
                    } else if (viableEmulators.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Section(
                            title = "How to run it",
                            icon = Icons.Outlined.Speed,
                            accent = platformColor,
                            initiallyExpanded = false,
                        ) {
                            PerEmulatorSetupTabs(
                                emulators = viableEmulators,
                                perEmulator = perEmulatorMap,
                                legacySteps = legacySteps,
                                presetsById = s.presetsById,
                                game = game,
                            )
                        }
                    }

                    // Spec item 4: "Something's wrong?" (renamed from "It didn't work? Troubleshoot")
                    val tvm: TroubleshootViewModel = hiltViewModel()
                    val ts by tvm.state.collectAsStateWithLifecycle()
                    Spacer(Modifier.height(16.dp))
                    Section(
                        title = "Something's wrong?",
                        icon = Icons.Outlined.HealthAndSafety,
                        accent = Color(0xFFEF5350),
                        initiallyExpanded = false,
                    ) {
                        TroubleshootSection(
                            state = ts,
                            accent = platformColor,
                            onPickSymptom = { tvm.pickSymptom(it) },
                            onWorked = { tvm.markWorked() },
                            onNextFix = { tvm.nextFix() },
                            onReset = { tvm.reset() },
                            onLogResult = { vm.openJournalForm() },
                            onImportConfig = launchImport,
                        )
                    }

                    // ── SECONDARY ZONE — reference material, collapsed by default ──
                    Spacer(Modifier.height(24.dp))
                    MoreDetailsHeader()

                    // Spec item 5: GameNative config wrapped in a collapsed Section.
                    // IicOfferCard is now its own separate Section (extracted out of GameNativeConfigPanel).
                    if (s.isWindowsGame) {
                        Spacer(Modifier.height(10.dp))
                        Section(
                            title = "GameNative config",
                            icon = Icons.Outlined.Build,
                            accent = platformColor,
                            initiallyExpanded = false,
                        ) {
                            GameNativeConfigPanel(
                                trust = s.configTrust,
                                accent = platformColor,
                                canOneTapLaunch = s.canOneTapLaunch,
                                iicInstalled = s.iicInstalled,
                                onLaunchInGameNative = { vm.launchInGameNative() },
                                onDownloadConfig = { vm.applyGameNativeConfig() },
                                onStageFallback = { vm.applyGameNativeConfig() },
                                onImportConfig = launchImport,
                                importState = s.importState,
                                onDismissImport = { vm.dismissImportState() },
                                onShareImported = { json -> vm.shareImportedConfig(json) },
                                isWindowsGame = s.isWindowsGame,
                            )
                        }
                        // Spec item 5: IicOfferCard in its own Section, only when IIC not installed
                        if (!s.iicInstalled) {
                            Spacer(Modifier.height(10.dp))
                            Section(
                                title = "Optional: GameNative (IIC) fork",
                                icon = Icons.Outlined.AutoFixHigh,
                                accent = Color(0xFF9C27B0),
                                initiallyExpanded = false,
                            ) {
                                IicOfferCard(
                                    iicInstallStatus = s.iicInstallStatus,
                                    onGetIic = { vm.installIicFork() },
                                )
                            }
                        }
                    }

                    // Preset details (collapsed)
                    top.presetId?.let { presetId ->
                        Spacer(Modifier.height(10.dp))
                        Section(
                            title = "Preset details",
                            icon = Icons.Outlined.Build,
                            accent = platformColor,
                            initiallyExpanded = false,
                        ) {
                            GroupedSettingsBody(
                                preset = s.presetsById[presetId],
                                driver = s.presetsById[presetId]?.driverId
                                    ?.let { s.driversById[it] },
                            )
                        }
                    }

                    // Spec item 6: "Game notes" — merges Known issues, Mods, BIOS, Version
                    val hasGameNotes = !game.knownIssues.isNullOrBlank() ||
                        !game.modsAndPatches.isNullOrBlank() ||
                        !game.biosRequirements.isNullOrBlank() ||
                        !game.bestVersionGuidance.isNullOrBlank()
                    if (hasGameNotes) {
                        Spacer(Modifier.height(10.dp))
                        Section(
                            title = "Game notes",
                            icon = Icons.Outlined.BugReport,
                            accent = Color(0xFFFFC107),
                            initiallyExpanded = false,
                        ) {
                            GameNotesBody(game = game)
                        }
                    }

                    // Other options / Alternatives (collapsed)
                    if (s.recommendations.size > 1) {
                        Spacer(Modifier.height(10.dp))
                        Section(
                            title = "Other options",
                            icon = Icons.Outlined.Devices,
                            accent = MaterialTheme.colorScheme.secondary,
                            initiallyExpanded = false,
                        ) {
                            Column {
                                s.recommendations.drop(1).forEach { rec ->
                                    AlternativeRow(
                                        rec = rec,
                                        emulator = s.emulatorsById[rec.emulatorId],
                                        preset = rec.presetId?.let(s.presetsById::get),
                                        onApply = { vm.apply(rec) },
                                    )
                                    Spacer(Modifier.height(6.dp))
                                }
                            }
                        }
                    }

                    // Spec item 8: Renamed to "Community reports (N)" with TextButton("Submit a report")
                    Spacer(Modifier.height(10.dp))
                    val allReports = s.recommendations.flatMap { it.reports }
                    Section(
                        title = "Community reports (${allReports.size})",
                        icon = Icons.Outlined.Memory,
                        accent = platformColor,
                        initiallyExpanded = false,
                    ) {
                        PerformanceOverviewBody(reports = allReports)
                        if (allReports.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            AllReportsInline(
                                reports = allReports,
                                emusById = s.emulatorsById,
                            )
                        }
                        // Spec item 8: TextButton instead of always-visible SubmitYourOwnReportCard
                        if (!game.platform.equals("WINDOWS", ignoreCase = true)) {
                            Spacer(Modifier.height(8.dp))
                            TextButton(
                                onClick = { SubmitLinks.openSubmitFor(ctx, game, s.fingerprint) },
                            ) {
                                Icon(Icons.Outlined.EditNote, contentDescription = null,
                                    modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Submit a report")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(48.dp))
            }
        }

        // One-tap GameNative launch feedback. Lightweight Toast (no Snackbar host
        // on this screen); cleared once shown so it doesn't re-fire on recompose.
        s.oneTapLaunchMessage?.let { msg ->
            LaunchedEffect(msg) {
                android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show()
                vm.dismissOneTapLaunchMessage()
            }
        }

        s.applyState?.let { applyState ->
            ApplyStateDialog(
                state = applyState,
                onDismiss = vm::dismissApplyResult,
                onLogResult = {
                    vm.dismissApplyResult()
                    vm.openJournalForm()
                },
            )
        }

        // v0.5: in-app journal entry form. Pre-fills emulator + preset from
        // the highest-confidence recommendation we have so the user only
        // needs to confirm + tweak FPS/stability.
        // IIC round-trip: when a pending session was delivered by the GameNative
        // fork, s.pendingSessionMinutes is non-null and the form opens
        // pre-filled; defaultEmulator is forced to "gamenative" in that case.
        if (s.journalFormOpen && s.game != null) {
            val hasPendingSession = s.pendingSessionMinutes != null
            val defaultEmuId = if (hasPendingSession) {
                "gamenative"
            } else {
                s.recommendationsBySource.fromReal.firstOrNull()?.emulatorId
                    ?: s.recommendationsBySource.fromGenerated.firstOrNull()?.emulatorId
            }
            val defaultPreset = if (hasPendingSession) null else (
                s.recommendationsBySource.fromReal.firstOrNull()?.presetId
                    ?: s.recommendationsBySource.fromGenerated.firstOrNull()?.presetId
            )
            val defaultFps = if (hasPendingSession) null else (
                s.recommendationsBySource.fromReal.firstOrNull()?.avgFps
                    ?: s.recommendationsBySource.fromGenerated.firstOrNull()?.avgFps
            )
            val iicNotes = if (hasPendingSession) {
                buildString {
                    append("Played via GameNative (IIC).")
                    if (s.pendingSessionShowedFps) append(" FPS HUD was on.")
                }
            } else ""
            JournalEntryForm(
                game = s.game!!,
                defaultEmulator = defaultEmuId?.let(s.emulatorsById::get),
                defaultPreset = defaultPreset?.let(s.presetsById::get),
                defaultFps = defaultFps,
                defaultSessionMinutes = s.pendingSessionMinutes,
                defaultNotes = iicNotes,
                onSave = { entry ->
                    vm.saveJournal(entry)
                    vm.closeJournalForm()
                },
                onDismiss = vm::closeJournalForm,
            )
        }
    }
}

/* =============================================================================
   COMPACT GAME HEADER (spec item 1)
   Replaces PlatformHeader + CoverAndScreenshotsStrip.
   - Removes: duplicate title Text, description block, alsoOn block.
   - Keeps: platform badge, year, region, genre chips (max 3).
   - Adds: small cover thumbnail inline (96dp tall, 72dp wide) when coverUrl != null.
   ============================================================================= */

@Composable
private fun CompactGameHeader(game: GameEntity) {
    val platformColor = PlatformColors.primary(game.platform)
    val cover = game.coverUrl?.takeIf { it.isNotBlank() }
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        platformColor.copy(alpha = 0.25f),
                        platformColor.copy(alpha = 0.05f),
                    ),
                )
            )
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Small cover thumbnail when available
            if (cover != null) {
                AsyncImage(
                    model = cover,
                    contentDescription = "Cover art for ${game.title}",
                    modifier = Modifier
                        .height(96.dp)
                        .width(72.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(platformColor.copy(alpha = 0.10f)),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlatformBadge(platform = game.platform)
                    Spacer(Modifier.width(8.dp))
                    game.releaseYear?.let {
                        Text("$it",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                    }
                    game.region?.let {
                        Text(it,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                val genres = game.genres?.split('|').orEmpty().filter { it.isNotBlank() }
                if (genres.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        genres.take(3).forEach { g -> Chip(g) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlatformBadge(platform: String) {
    val color = PlatformColors.primary(platform)
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.85f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(platform.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Chip(label: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

/* =============================================================================
   SECTION WRAPPER — every content block uses this to feel consistent
   ============================================================================= */

@Composable
private fun Section(
    title: String,
    icon: ImageVector,
    accent: Color,
    initiallyExpanded: Boolean = true,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            // Accent bar
            Box(Modifier.fillMaxWidth().height(3.dp).background(accent))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    content()
                }
            }
        }
    }
}

/* =============================================================================
   MORE DETAILS HEADER — visual break before the secondary / reference zone
   ============================================================================= */

@Composable
private fun MoreDetailsHeader() {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.width(12.dp))
        Text(
            "More details",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(12.dp))
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

internal fun formatJournalDate(epochMs: Long): String {
    val fmt = DateFormat.getDateInstance(DateFormat.MEDIUM)
    return fmt.format(Date(epochMs))
}

/* =============================================================================
   VERDICT CARD (spec item 2)
   Collapses YourLastRunTile + HeroFpsCard REAL + HeroFpsCard GENERATED_SECONDARY
   into one card.
   - ONE FPS number from realTop if present, else genTop (dimmed "Estimated" framing).
   - ONE emulator name, ONE preset name, ONE confidence/match context line.
   - HardwareCallout inline only when a RAM warning exists.
   - ONE primary Button wired to same VM calls as old cards.
   - ONE optional secondary OutlinedButton.
   - If lastJournal exists: compact single row below the card + "Log another" TextButton.
   - GENERATED_SECONDARY second hero card is removed.
   ============================================================================= */

@Composable
private fun VerdictCard(
    realTop: Recommendation?,
    genTop: Recommendation?,
    lastJournal: JournalEntryEntity?,
    emulatorsById: Map<String, EmulatorEntity>,
    presetsById: Map<String, PresetEntity>,
    userRamMb: Int?,
    recommendedRamGb: Int?,
    isWindows: Boolean,
    canOneTapLaunch: Boolean,
    onApplyReal: () -> Unit,
    onApplyGen: () -> Unit,
    onLaunchInGameNative: () -> Unit,
    onDownloadConfig: () -> Unit,
    onLogAnother: () -> Unit,
    onImportConfig: () -> Unit,
    onSubmitReport: () -> Unit,
) {
    // No data at all — GeneratedOnlyHonestTile path
    if (realTop == null && genTop == null) return

    val isGenOnly = realTop == null && genTop != null
    val rec = realTop ?: genTop!!
    val emulator = emulatorsById[rec.emulatorId]
    val preset = rec.presetId?.let(presetsById::get)
    val stab = PlatformColors.stability(rec.stability)

    val containerColor = if (realTop != null) MaterialTheme.colorScheme.primaryContainer
                         else MaterialTheme.colorScheme.surfaceVariant
    val onContainer = if (realTop != null) MaterialTheme.colorScheme.onPrimaryContainer
                      else MaterialTheme.colorScheme.onSurfaceVariant

    Column {
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor),
        ) {
            Column(Modifier.padding(20.dp)) {
                // Header label
                val headerLabel = if (realTop != null)
                    "RECOMMENDED · BASED ON REAL REPORTS"
                else
                    "ESTIMATED · NO REAL REPORTS YET"
                Text(
                    headerLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = onContainer.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))

                // FPS number
                Row(verticalAlignment = Alignment.Bottom) {
                    val fpsText = if (isGenOnly) rec.avgFps?.let { "~$it" } ?: "—"
                                  else rec.avgFps?.toString() ?: "—"
                    Text(
                        text = fpsText,
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp),
                        fontWeight = FontWeight.Bold,
                        color = if (isGenOnly) stab.copy(alpha = 0.7f) else stab,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.padding(bottom = 14.dp)) {
                        Text(
                            if (isGenOnly) "fps est." else "FPS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = onContainer,
                        )
                        StabilityPill(rec.stability)
                    }
                }
                Spacer(Modifier.height(12.dp))

                // Emulator + preset
                Text(
                    emulator?.name ?: rec.emulatorId,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = onContainer,
                )
                preset?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(it.name, style = MaterialTheme.typography.bodyMedium, color = onContainer)
                }
                Spacer(Modifier.height(12.dp))

                // Confidence context line (single labelSmall)
                if (realTop != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ConfidenceBadge(rec.bucket.confidence)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "matched on ${rec.bucket.label.lowercase()} · ${rec.reportCount} report${if (rec.reportCount == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = onContainer.copy(alpha = 0.7f),
                        )
                    }
                } else {
                    Text(
                        "Estimated for ${emulator?.name ?: rec.emulatorId} — not a real test result.",
                        style = MaterialTheme.typography.labelSmall,
                        color = onContainer.copy(alpha = 0.7f),
                    )
                }

                // HardwareCallout: only when RAM warning exists
                preset?.let { p ->
                    val (vramMb, presetRecRam) = readRamRequirements(p)
                    val effectiveRecRamGb = recommendedRamGb ?: presetRecRam
                    val userRamGb = userRamMb?.let { it / 1024 }
                    val warns = effectiveRecRamGb != null && userRamGb != null && userRamGb < effectiveRecRamGb
                    if (warns && (vramMb != null || effectiveRecRamGb != null)) {
                        Spacer(Modifier.height(14.dp))
                        HardwareCallout(vramMb = vramMb, recRamGb = effectiveRecRamGb, userRamMb = userRamMb)
                    }
                }

                Spacer(Modifier.height(18.dp))

                // ONE primary Button
                when {
                    isWindows && canOneTapLaunch -> {
                        Button(onClick = onLaunchInGameNative, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Apply config & launch")
                        }
                    }
                    isWindows && !canOneTapLaunch -> {
                        Button(onClick = onDownloadConfig, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Description, contentDescription = null,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Download config")
                        }
                    }
                    isGenOnly -> {
                        Button(onClick = onSubmitReport, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.EditNote, contentDescription = null,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Help out — submit a real result")
                        }
                    }
                    else -> {
                        Button(onClick = onApplyReal, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.AutoFixHigh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Apply this preset")
                        }
                    }
                }

                // ONE optional secondary OutlinedButton
                if (isWindows && canOneTapLaunch) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onDownloadConfig, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Description, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Download config")
                    }
                } else if (isWindows && !isGenOnly) {
                    // Import CTA as secondary for Windows non-one-tap
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onImportConfig, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.FileUpload, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import my working config")
                    }
                }
            }
        }

        // Compact journal row: "Your run: Xfps · emulator · date" + "Log another" TextButton
        if (lastJournal != null) {
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val emuName = lastJournal.emulatorId?.let(emulatorsById::get)?.name ?: ""
                val fpsStr = lastJournal.avgFps?.let { "${it}fps" } ?: "no fps"
                val dateStr = formatJournalDate(lastJournal.createdAt)
                Text(
                    "Your run: $fpsStr${if (emuName.isNotBlank()) " · $emuName" else ""} · $dateStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onLogAnother) {
                    Text("Log another", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun StabilityPill(stability: String) {
    val color = PlatformColors.stability(stability)
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.20f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(PlatformColors.stabilityLabel(stability),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold)
    }
}

private fun friendlyConfidenceLabel(b: Bucket): String = when (b) {
    Bucket.SAME_SOC_AND_RAM -> "Same chip and RAM class as you"
    Bucket.SAME_SOC_FAMILY -> "Same chip as you"
    Bucket.SAME_GPU_VENDOR -> "Similar GPU, different chip"
    Bucket.ANY_DEVICE -> "Different hardware — best guess"
}

@Composable
private fun ConfidenceBadge(c: Confidence) {
    val (label, color) = when (c) {
        Confidence.STRONG -> "STRONG" to Color(0xFF4CAF50)
        Confidence.MODERATE -> "MODERATE" to Color(0xFFAED581)
        Confidence.WEAK -> "WEAK" to Color(0xFFFFC107)
        Confidence.VERY_WEAK -> "VERY WEAK" to Color(0xFFEF5350)
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HardwareCallout(vramMb: Int?, recRamGb: Int?, userRamMb: Int?) {
    val userRamGb = userRamMb?.let { it / 1024 }
    val warns = recRamGb != null && userRamGb != null && userRamGb < recRamGb
    val bg = if (warns) Color(0xFFFFC107).copy(alpha = 0.18f)
             else MaterialTheme.colorScheme.surface.copy(alpha = 0.70f)
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bg),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Memory, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Hardware requirements",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            if (vramMb != null) {
                StatLine("VRAM to allocate", "${vramMb / 1024} GB")
            }
            if (recRamGb != null) {
                val rhs = "${recRamGb}+ GB" + (userRamGb?.let { " (you have $it)" } ?: "")
                StatLine("Recommended RAM", rhs)
            }
            if (warns) {
                Spacer(Modifier.height(6.dp))
                Text("Your device may be short on RAM — try a lighter preset or expect crashes.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFB07000))
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label,
            modifier = Modifier.width(150.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

/* =============================================================================
   GROUPED SETTINGS BODY (used inside Section)
   ============================================================================= */

private data class SettingsGroup(val title: String, val keys: List<String>)

private val SETTINGS_GROUPS = listOf(
    SettingsGroup("Container",  listOf("containerName","wineVersion","windowsVersion","protonVersion","container","containerProfile")),
    SettingsGroup("Graphics",   listOf("dxvkVersion","vkd3dVersion","vulkanDriver","graphicsDriver","renderer","backend","resolution","internalRes","videoMemorySizeMB","dxvk","vkd3d","anisotropic","gpuAccuracy","useDiskShaderCache","asyncShaders","asyncShader","asyncShaderCompile","blendingAccuracy","vsync","msaa","ptgu","trueColor","wineD3D","dxvkHud","plugin")),
    SettingsGroup("CPU / Box64",listOf("box64Dynarec","box64Preset","cpuList","cpuListWoW64","cpuAccuracy","cpuPreset","preset","spuMode","ppuMode","fastMemAccess","fastGpu","asyncMtvu")),
    SettingsGroup("Anti-cheat / Compat", listOf("easyAntiCheat","noDxvk")),
    SettingsGroup("Args & Env", listOf("args","envVars","frameskip","rewind","shader","core")),
    SettingsGroup("Notes",      listOf("notes")),
)

@Composable
private fun GroupedSettingsBody(
    preset: PresetEntity?,
    driver: io.github.mayusi.isitcompatible.compatdb.room.DriverEntity? = null,
) {
    if (preset == null) {
        Text("No preset details.", style = MaterialTheme.typography.bodySmall)
        return
    }
    val parsed = remember(preset.settingsJson) {
        runCatching { Json.parseToJsonElement(preset.settingsJson).jsonObject }.getOrNull()
    } ?: return
    val flatRows: Map<String, String> = parsed.entries
        .mapNotNull { (k, v) ->
            val s = (v as? JsonPrimitive)?.contentOrNullSafe() ?: v.toString()
            if (s.isBlank() || s == "null") null else k to s
        }.toMap()

    val seenKeys = mutableSetOf<String>()
    SETTINGS_GROUPS.forEach { group ->
        val groupRows = group.keys.mapNotNull { k -> flatRows[k]?.let { k to it } }
        if (groupRows.isNotEmpty()) {
            SettingsGroupBlock(group.title, groupRows)
            seenKeys.addAll(groupRows.map { it.first })
            Spacer(Modifier.height(10.dp))
        }
    }
    val unmatched = flatRows.entries.filter { it.key !in seenKeys }.map { it.key to it.value }
    if (unmatched.isNotEmpty()) {
        SettingsGroupBlock("Other", unmatched)
    }
    // v0.6: honest "as of" footer. When `dataAsOf` is 0 the preset has never
    // been verified upstream — we still show "may be outdated" rather than
    // pretending the bundled-snapshot date is the source of truth.
    Spacer(Modifier.height(12.dp))
    DataAsOfFooter(epochMs = preset.dataAsOf)

    // v0.6: if the driver this preset depends on has a newer upstream tag,
    // surface a friendly hint. Worker DriverSyncWorker fills upstreamLatestTag
    // on a background tick; until then it's null and we render nothing.
    if (driver != null && !driver.upstreamLatestTag.isNullOrBlank()) {
        Spacer(Modifier.height(6.dp))
        NewerDriverHint(currentName = driver.name, upstreamTag = driver.upstreamLatestTag!!)
    }
}

/**
 * v0.6: Small pill saying "newer Turnip available — <tag>". Tap behaviour is
 * deferred — for now the hint is informational and the user heads to the
 * AdrenoToolsDrivers releases page themselves via the existing driver link in
 * the Apply sheet.
 */
@Composable
private fun NewerDriverHint(currentName: String, upstreamTag: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF1976D2).copy(alpha = 0.18f))
                .padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            Text(
                "newer driver available",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = Color(0xFF0D47A1),
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            upstreamTag,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DataAsOfFooter(epochMs: Long) {
    val now = System.currentTimeMillis()
    val daysOld = if (epochMs > 0) (now - epochMs) / (1000L * 60 * 60 * 24) else Long.MAX_VALUE
    val stale = daysOld > 60
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (epochMs > 0) "Preset info from ${formatJournalDate(epochMs)}"
                   else "Preset info — last verification date unknown",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (stale) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFFFC107).copy(alpha = 0.20f))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                Text(
                    if (epochMs > 0) "may be outdated" else "unverified",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = Color(0xFFB07000),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun SettingsGroupBlock(title: String, rows: List<Pair<String, String>>) {
    Text(title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp)
    Spacer(Modifier.height(4.dp))
    rows.forEach { (k, v) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Text(k,
                modifier = Modifier.width(140.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(v,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium)
        }
    }
}

private fun JsonPrimitive.contentOrNullSafe(): String? = runCatching { content }.getOrNull()

private fun readRamRequirements(preset: PresetEntity): Pair<Int?, Int?> {
    val obj = runCatching { Json.parseToJsonElement(preset.settingsJson).jsonObject }.getOrNull()
        ?: return null to null
    val vram = (obj["videoMemorySizeMB"] as? JsonPrimitive)?.contentOrNullSafe()?.toIntOrNull()
    val recRam = (obj["recommendedRamGb"] as? JsonPrimitive)?.contentOrNullSafe()?.toIntOrNull()
        ?: vram?.let { (it / 1024) + 4 }
    return vram to recRam
}

/* =============================================================================
   SETUP STEPS TIMELINE
   ============================================================================= */

@Composable
private fun SetupStepsTimeline(steps: List<String>) {
    Column {
        steps.forEachIndexed { i, step ->
            Row {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${i + 1}",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold)
                    }
                    if (i < steps.size - 1) {
                        Spacer(Modifier.height(2.dp))
                        Box(
                            Modifier
                                .width(2.dp)
                                .height(28.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(step,
                    modifier = Modifier.padding(top = 4.dp, bottom = if (i < steps.size - 1) 12.dp else 0.dp),
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/* =============================================================================
   KNOWN ISSUES LIST
   ============================================================================= */

@Composable
private fun KnownIssuesList(issues: List<String>) {
    Column {
        issues.forEach { issue ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFC107))
                        .padding(top = 8.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(issue, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/* =============================================================================
   GAME NOTES BODY (spec item 6)
   Merges Known issues, Mods & community patches, BIOS/firmware requirements,
   and Which version to emulate into one composable rendered inside a single
   collapsed Section. Each non-blank field is a labeled subgroup separated by
   HorizontalDivider.
   ============================================================================= */

@Composable
private fun GameNotesBody(game: GameEntity) {
    val issuesList = game.knownIssues?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
    val modsList = game.modsAndPatches?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
    val biosList = game.biosRequirements?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
    val versionText = game.bestVersionGuidance?.takeIf { it.isNotBlank() }

    var firstRendered = false

    if (issuesList.isNotEmpty()) {
        if (firstRendered) { HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant) }
        Text("KNOWN ISSUES",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFB07000),
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp)
        Spacer(Modifier.height(6.dp))
        KnownIssuesList(issues = issuesList)
        firstRendered = true
    }
    if (modsList.isNotEmpty()) {
        if (firstRendered) { HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant) }
        Text("MODS & COMMUNITY PATCHES",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF9C27B0),
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp)
        Spacer(Modifier.height(6.dp))
        BulletedList(items = modsList, bulletColor = Color(0xFF9C27B0))
        firstRendered = true
    }
    if (biosList.isNotEmpty()) {
        if (firstRendered) { HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant) }
        Text("BIOS / FIRMWARE REQUIREMENTS",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFEF5350),
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp)
        Spacer(Modifier.height(6.dp))
        BulletedList(items = biosList, bulletColor = Color(0xFFEF5350))
        Spacer(Modifier.height(4.dp))
        Text(
            "BIOS/firmware come from hardware you own — there's no download here. Place/import them once you have them.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        firstRendered = true
    }
    if (versionText != null) {
        if (firstRendered) { HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant) }
        Text("WHICH VERSION TO EMULATE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp)
        Spacer(Modifier.height(6.dp))
        Text(versionText, style = MaterialTheme.typography.bodyMedium)
    }
}

/* =============================================================================
   GENERIC BULLETED LIST (used by Mods & patches, BIOS requirements)
   ============================================================================= */

@Composable
private fun BulletedList(items: List<String>, bulletColor: Color) {
    Column {
        items.forEach { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(bulletColor)
                        .padding(top = 8.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(item, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/* =============================================================================
   PER-EMULATOR SETUP TABS (v0.4)
   Renders one tab per viable emulator. Tab body uses perEmulatorSetup[id]
   if present, falls back to game.setupSteps (legacy single-emulator field),
   then falls back to genericSetupSteps derived from emulator + preset.
   ============================================================================= */

@Composable
private fun PerEmulatorSetupTabs(
    emulators: List<Pair<EmulatorEntity, Recommendation>>,
    perEmulator: Map<String, List<String>>,
    legacySteps: List<String>,
    presetsById: Map<String, PresetEntity>,
    game: GameEntity,
) {
    if (emulators.isEmpty()) return
    var selectedIndex by remember(emulators.first().first.id) { mutableStateOf(0) }

    // Tab strip — horizontally scrollable so 4 emulator names always fit
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        emulators.forEachIndexed { index, (emu, _) ->
            val isSelected = index == selectedIndex
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { selectedIndex = index }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.SportsEsports,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(emu.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))

    val (selectedEmu, selectedRec) = emulators[selectedIndex]
    val preset = selectedRec.presetId?.let(presetsById::get)

    // Pick the best source of steps for this specific emulator
    val handwrittenForThis = perEmulator[selectedEmu.id].orEmpty()
    val steps: List<String>
    val sourceLabel: String?
    when {
        handwrittenForThis.isNotEmpty() -> {
            steps = handwrittenForThis
            sourceLabel = null
        }
        // legacySteps applies only to the recommended emulator (the first tab)
        // since old data wasn't keyed per-emulator
        selectedIndex == 0 && legacySteps.isNotEmpty() -> {
            steps = legacySteps
            sourceLabel = null
        }
        else -> {
            steps = genericSetupSteps(selectedEmu, preset, game)
            sourceLabel = "Generic guidance for ${selectedEmu.name} — game-specific steps haven't been written yet. Help out by submitting a report!"
        }
    }

    if (steps.isEmpty()) {
        Text("No setup steps available for ${selectedEmu.name} yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        SetupStepsTimeline(steps)
        sourceLabel?.let {
            Spacer(Modifier.height(8.dp))
            Text(it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/* =============================================================================
   ALTERNATIVE ROW
   ============================================================================= */

@Composable
private fun AlternativeRow(
    rec: Recommendation,
    emulator: EmulatorEntity?,
    preset: PresetEntity?,
    onApply: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(emulator?.name ?: rec.emulatorId,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                preset?.let {
                    Text(it.name, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    rec.avgFps?.let {
                        Text("~${it} fps",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = PlatformColors.stability(rec.stability))
                        Spacer(Modifier.width(8.dp))
                    }
                    StabilityPill(rec.stability)
                    Spacer(Modifier.width(8.dp))
                    Text("${rec.reportCount} report${if (rec.reportCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = onApply) { Text("Apply") }
        }
    }
}

/* =============================================================================
   PERFORMANCE OVERVIEW
   ============================================================================= */

@Composable
private fun PerformanceOverviewBody(reports: List<ReportEntity>) {
    if (reports.isEmpty()) {
        Text("No reports yet.", style = MaterialTheme.typography.bodySmall); return
    }
    val fpsList = reports.mapNotNull { it.avgFps }
    val best = fpsList.maxOrNull()
    val worst = fpsList.minOrNull()
    val avg = if (fpsList.isNotEmpty()) fpsList.average().toInt() else null

    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
        Stat("Best", best?.let { "$it fps" } ?: "—", Color(0xFF4CAF50))
        Stat("Avg",  avg?.let { "$it fps" } ?: "—", MaterialTheme.colorScheme.onSurface)
        Stat("Worst", worst?.let { "$it fps" } ?: "—", Color(0xFFEF5350))
    }
    Spacer(Modifier.height(14.dp))
    Text("Stability breakdown",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.8.sp)
    Spacer(Modifier.height(6.dp))
    val total = reports.size.toFloat()
    val tallies = listOf(
        Triple("Perfect", reports.count { it.stability.equals("PERFECT", true) }, Color(0xFF4CAF50)),
        Triple("Playable", reports.count { it.stability.equals("PLAYABLE", true) }, Color(0xFFAED581)),
        Triple("Glitchy", reports.count { it.stability.equals("GLITCHY", true) }, Color(0xFFFFC107)),
        Triple("Crashes", reports.count { it.stability.equals("CRASHES", true) }, Color(0xFFEF5350)),
    ).filter { it.second > 0 }
    tallies.forEach { (label, count, color) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(label,
                modifier = Modifier.width(80.dp),
                style = MaterialTheme.typography.bodySmall)
            // Visual bar
            Box(
                Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(count / total)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(color),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text("$count", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color) {
    Column {
        Text(label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color)
    }
}

/* =============================================================================
   ALL REPORTS INLINE (used inside the Raw data & all reports Section)
   A flat report list without its own card/accordion wrapper — the parent
   Section already provides the collapsible container.
   ============================================================================= */

@Composable
private fun AllReportsInline(
    reports: List<ReportEntity>,
    emusById: Map<String, EmulatorEntity>,
) {
    Text(
        "All ${reports.size} report${if (reports.size == 1) "" else "s"}",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(8.dp))
    reports.sortedByDescending { it.avgFps ?: 0 }.forEach { r ->
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emusById[r.emulatorId]?.name ?: r.emulatorId,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f))
                r.avgFps?.let {
                    Text("$it fps",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PlatformColors.stability(r.stability),
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                }
                StabilityPill(r.stability)
            }
            Spacer(Modifier.height(2.dp))
            Text(reportContextSentence(r),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            r.notes?.takeIf { it.isNotBlank() }?.let {
                Text(it,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp))
            }
            HorizontalDivider(
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

/* =============================================================================
   GAMENATIVE CONFIG PANEL (QW-1 honesty + QW-4 frictionless import)
   For Windows/GameNative games. Tier-accurate, honest delivery:
   - VERIFIED (tier 1, real config) : green "Verified config". Prominent
                                      "Download GameNative config" as the primary.
   - AUTHORED (tier 2/3, real config): amber "Authored config — try it, not yet
                                      device-verified". STILL delivers the real
                                      config as the primary download (QW-1: don't
                                      require tier 1), just honestly labeled.
   - FALLBACK (no real config)      : no real config exists. We never call the
                                      synthesized defaults "verified". The primary
                                      ask is "Import my working config"; the
                                      synthesized config is a clearly-labeled
                                      SECONDARY "Experimental untested defaults".
   QW-4: the "Import my working config" CTA is present + reachable in EVERY state.
   The `trust` value is computed in the ViewModel purely from the GameNative
   guide's tier + config payload — never from report source.
   ============================================================================= */

// Spec item 5: iicInstalled/iicInstallStatus/onGetIic params removed.
// IicOfferCard is now its own separate Section above (shown when !s.iicInstalled).
@Composable
private fun GameNativeConfigPanel(
    trust: ConfigTrust,
    accent: Color,
    canOneTapLaunch: Boolean,
    iicInstalled: Boolean,
    onLaunchInGameNative: () -> Unit,
    onDownloadConfig: () -> Unit,
    onStageFallback: () -> Unit,
    onImportConfig: () -> Unit,
    importState: ImportConfigState?,
    onDismissImport: () -> Unit,
    onShareImported: (String) -> Unit,
    isWindowsGame: Boolean = true,
) {
    when (trust) {
        ConfigTrust.NONE -> {
            if (!isWindowsGame) return
            Column {
                ConfigStatusBadge(
                    text = "NO CONFIG YET",
                    bg = Color(0xFFE8EAF6),
                    fg = Color(0xFF3949AB),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "To run this Windows game you need GameNative",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "No specific config exists for this game yet. Install GameNative " +
                        "(official or IIC fork), then use the general setup guide below. " +
                        "Once you get it working, import your config here to help others.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedButton(onClick = onImportConfig, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.FileUpload, contentDescription = null,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Got it working? Import your config")
                }
                ImportStatus(importState, onDismissImport, onShareImported)
            }
            return
        }
        ConfigTrust.VERIFIED, ConfigTrust.AUTHORED -> {
            val authored = trust == ConfigTrust.AUTHORED
            val onContainer = if (authored)
                MaterialTheme.colorScheme.onSurfaceVariant
            else
                MaterialTheme.colorScheme.onPrimaryContainer
            Column {
                if (authored) {
                    ConfigStatusBadge(
                        text = "COMMUNITY CONFIG",
                        bg = Color(0xFFE8EAF6),
                        fg = Color(0xFF3949AB),
                    )
                } else {
                    ConfigStatusBadge(
                        text = "VERIFIED CONFIG",
                        bg = Color(0xFFD1FAE5),
                        fg = Color(0xFF065F46),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (authored) "Community config for this game"
                    else "Verified GameNative config available",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = onContainer,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (authored)
                        "A config written specifically for this game. Try it and report whether it worked — then import your own working config to mark it Verified."
                    else
                        "A working config for this game has been verified. Download it, then import it into GameNative (open the game → 3 dots → Import Config).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onContainer,
                )
                Spacer(Modifier.height(16.dp))
                if (canOneTapLaunch) {
                    Button(onClick = onLaunchInGameNative, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Apply config & launch in GameNative")
                    }
                    if (iicInstalled) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFD1FAE5))
                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    "Launching via GameNative (IIC)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = Color(0xFF065F46),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                val downloadLabel =
                    if (authored) "Download authored config" else "Download GameNative config"
                if (canOneTapLaunch) {
                    OutlinedButton(onClick = onDownloadConfig, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Description, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(downloadLabel)
                    }
                } else {
                    Button(onClick = onDownloadConfig, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Description, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(downloadLabel)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onImportConfig, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.FileUpload, contentDescription = null,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (authored)
                            "Got it working? Import your config to mark this Verified"
                        else
                            "Import an updated config",
                    )
                }
                ImportStatus(importState, onDismissImport, onShareImported)
            }
        }
        ConfigTrust.FALLBACK -> {
            Column {
                ConfigStatusBadge(
                    text = "NO VERIFIED SETUP YET",
                    bg = Color(0xFFFEF3C7),
                    fg = Color(0xFF92400E),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "No config yet for this game on GameNative",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "We don't have a confirmed working config to hand you. Use the " +
                        "general GameNative guide below to get it running, then import " +
                        "your own working config to help the next person.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                Button(onClick = onImportConfig, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.FileUpload, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Got it working? Import your config to mark this Verified")
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Export your config from GameNative (game → 3 dots → Export Config) " +
                        "and import it here. It becomes the verified config for this " +
                        "game — downloadable, and optionally shareable.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                )
                ImportStatus(importState, onDismissImport, onShareImported)
                Spacer(Modifier.height(14.dp))
                OutlinedButton(onClick = onStageFallback, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.AutoFixHigh, contentDescription = null,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Experimental untested defaults")
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Experimental untested defaults — synthesized, never run on a " +
                        "device. Expect to tweak it. Not a known-good config.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                )
            }
        }
    }
}

/**
 * Chunk 4: inline progress / error / success feedback for the import flow.
 * On success it confirms the verified config is saved and offers the opt-in
 * community share (reuses the GitHub-Issue path, with the config in the body).
 */
@Composable
private fun ImportStatus(
    state: ImportConfigState?,
    onDismiss: () -> Unit,
    onShare: (String) -> Unit,
) {
    when (state) {
        null -> {}
        is ImportConfigState.Working -> {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Validating and saving your config…",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        is ImportConfigState.Error -> {
            Spacer(Modifier.height(10.dp))
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEF5350).copy(alpha = 0.14f)),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Import failed",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB71C1C))
                    Spacer(Modifier.height(4.dp))
                    Text(state.message, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = onDismiss) { Text("Dismiss") }
                }
            }
        }
        is ImportConfigState.Success -> {
            Spacer(Modifier.height(10.dp))
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFD1FAE5)),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Config verified and saved",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF065F46))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Your working config is now the verified config for this game — " +
                            "it'll show as Verified and is downloadable any time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF065F46),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row {
                        // QW-4/QW-5: only show "Share with community" when the
                        // community DB repo actually exists. While disabled, the
                        // button is hidden entirely so no user action 404s. The
                        // share code path stays intact behind JournalShareIntent's
                        // SHARE_ENABLED flag for when the repo is created.
                        if (JournalShareIntent.isEnabled()) {
                            OutlinedButton(onClick = { onShare(state.sanitizedConfigJson) }) {
                                Icon(Icons.Outlined.Share, contentDescription = null,
                                    modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Share with community")
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        TextButton(onClick = onDismiss) { Text("Done") }
                    }
                }
            }
        }
    }
}

/**
 * Optional, honest offer to install the GameNative (IIC) fork. Shown only when
 * the fork is NOT installed. Non-pushy: presented as an informational card with a
 * secondary-style button, below all primary config actions.
 *
 * "IIC" = Is It Compatible fork; has per-game auto-fixes baked in (e.g. DMC HD
 * Collection crashing intro videos are silently fixed on launch).
 */
@Composable
private fun IicOfferCard(
    iicInstallStatus: GuideInstallStatus?,
    onGetIic: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "Get GameNative (IIC) — auto-fixes tricky games",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "A community fork that installs alongside the official app. " +
                    "Has per-game fixes baked in — e.g. DMC HD Collection's " +
                    "crashing intro videos are auto-fixed on launch. Optional; " +
                    "the official GameNative above works fine too.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(8.dp))
            when (iicInstallStatus) {
                is GuideInstallStatus.Working -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(iicInstallStatus.label, style = MaterialTheme.typography.labelSmall)
                    }
                }
                is GuideInstallStatus.Done -> {
                    Text(
                        "Installer opened — tap Install in the system prompt.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF065F46),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                is GuideInstallStatus.Failed -> {
                    Text(
                        "Download failed: ${iicInstallStatus.message}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFB71C1C),
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(onClick = onGetIic, modifier = Modifier.fillMaxWidth()) {
                        Text("Retry")
                    }
                }
                null -> {
                    OutlinedButton(onClick = onGetIic, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.AutoFixHigh, contentDescription = null,
                            modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Get GameNative (IIC)", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigStatusBadge(text: String, bg: Color, fg: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/* =============================================================================
   NO REPORTS YET / SUBMIT / APPLY DIALOG
   ============================================================================= */

@Composable
private fun NoReportsCard(game: GameEntity, fingerprint: String?, onSubmit: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("No reports for this game yet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "We don't have compatibility data for ${game.title} on ${game.platform}. " +
                    "You can be the first to share what works.",
                style = MaterialTheme.typography.bodyMedium,
            )
            fingerprint?.let {
                Spacer(Modifier.height(10.dp))
                Text("Your device: $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            game.description?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(16.dp))
            // QW-5: for Windows games "Submit the first report" went to the
            // non-existent GitHub repo (404). Only offer the submit button for
            // console games, where it opens EmuReady (a real destination).
            if (!game.platform.equals("WINDOWS", ignoreCase = true)) {
                Button(onClick = onSubmit) { Text("Submit the first report") }
            } else {
                Text(
                    "Got it working in GameNative? Open this game from the library and use " +
                        "“Import my working config” to save what worked — it stays on your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApplyStateDialog(
    state: ApplyJobState,
    onDismiss: () -> Unit,
    onLogResult: () -> Unit,
) {
    when (state) {
        is ApplyJobState.Working -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Preparing…") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(state.message)
                }
            },
            confirmButton = {},
        )
        is ApplyJobState.Done -> ApplyResultSheet(
            state = state,
            onDismiss = onDismiss,
            onLogResult = onLogResult,
        )
        is ApplyJobState.Error -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Couldn't apply") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
    }
}

/* =============================================================================
   APPLY RESULT SHEET (v0.5)
   The end of the apply flow used to be a small dialog with raw text. v0.5
   replaces it with a real ModalBottomSheet that surfaces every file we wrote,
   exposes per-file copy-path / share / open-folder affordances, and offers
   "Log my result" (wires to journal in Chunk 5.3 — for now it just dismisses).
   ============================================================================= */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApplyResultSheet(
    state: ApplyJobState.Done,
    onDismiss: () -> Unit,
    onLogResult: () -> Unit,
) {
    val ctx = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
            Text("Ready to apply",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "We wrote ${state.stagedFiles.size} file${if (state.stagedFiles.size == 1) "" else "s"} into your staging folder. " +
                    "Open the emulator and follow the steps below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // -- What we wrote ----------------------------------------------------
            Spacer(Modifier.height(20.dp))
            Text("What we wrote",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            state.stagedFiles.forEach { file ->
                StagedFileRow(file = file, onCopy = { copyToClipboard(ctx, file.displayPath) })
                Spacer(Modifier.height(6.dp))
            }

            // -- Next steps -------------------------------------------------------
            Spacer(Modifier.height(20.dp))
            Text("Next steps",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { openStagingTree(ctx, state.stagingTreeUri) },
                ) {
                    Icon(Icons.Outlined.FolderOpen, contentDescription = null,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open folder")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { shareInstructions(ctx, state.instructions) },
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share steps")
                }
            }

            // -- Log my result (journal — wired to a real form in Chunk 5.3) -----
            Spacer(Modifier.height(16.dp))
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Tried it?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Log what actually happened so the recommender learns from your hardware. " +
                            "(Coming in the next release — for now this is a stub.)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onLogResult) {
                        Icon(Icons.Outlined.EditNote, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Log my result")
                    }
                }
            }

            // -- Full INSTRUCTIONS.md preview ------------------------------------
            Spacer(Modifier.height(20.dp))
            Text("Instructions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Text(state.instructions,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp))
            }

            Spacer(Modifier.height(20.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Close")
            }
        }
    }
}

@Composable
private fun StagedFileRow(file: StagedFile, onCopy: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(file.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold)
                Text(file.displayPath,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy path",
                    modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun copyToClipboard(ctx: android.content.Context, text: String) {
    val cm = ContextCompat.getSystemService(ctx, ClipboardManager::class.java) ?: return
    cm.setPrimaryClip(ClipData.newPlainText("path", text))
    Toast.makeText(ctx, "Path copied", Toast.LENGTH_SHORT).show()
}

private fun openStagingTree(ctx: android.content.Context, stagingTreeUri: String) {
    // Best-effort: fire ACTION_VIEW on the SAF tree URI. Most file managers
    // accept this and navigate the user to the folder; if none do, we toast.
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = android.net.Uri.parse(stagingTreeUri)
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_ACTIVITY_NEW_TASK
    }
    if (ctx.packageManager.queryIntentActivities(intent, 0).isNotEmpty()) {
        ctx.startActivity(intent)
    } else {
        Toast.makeText(ctx,
            "No file manager handled the request. Path is copied to clipboard.",
            Toast.LENGTH_LONG).show()
        copyToClipboard(ctx, stagingTreeUri)
    }
}

private fun shareInstructions(ctx: android.content.Context, instructions: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Is It Compatible? — apply instructions")
        putExtra(Intent.EXTRA_TEXT, instructions)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    ctx.startActivity(Intent.createChooser(intent, "Share instructions").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}

/* =============================================================================
   HELPERS
   ============================================================================= */

/**
 * Builds emulator-aware generic setup steps for games that don't have hand-written
 * steps yet. Reads the recommended preset's settings to surface the specific
 * Wine version, driver, etc.
 */
private fun genericSetupSteps(
    emulator: EmulatorEntity?,
    preset: PresetEntity?,
    game: GameEntity,
): List<String> {
    if (emulator == null) return emptyList()
    val settings = preset?.let {
        runCatching { Json.parseToJsonElement(it.settingsJson).jsonObject }.getOrNull()
    }
    val wineVer = settings?.get("wineVersion")?.let { (it as? JsonPrimitive)?.contentOrNullSafe() }
    val protonVer = settings?.get("protonVersion")?.let { (it as? JsonPrimitive)?.contentOrNullSafe() }
    val dxvkVer = settings?.get("dxvkVersion")?.let { (it as? JsonPrimitive)?.contentOrNullSafe() }
    val driverName = preset?.driverId?.let { "Turnip driver matching the preset" }
    val containerName = settings?.get("containerName")?.let { (it as? JsonPrimitive)?.contentOrNullSafe() }

    return buildList {
        add("Install ${emulator.name}.")
        driverName?.let { add("Install $it via the emulator's GPU driver settings.") }
        when {
            protonVer != null -> add("Set Proton version to $protonVer.")
            wineVer != null && dxvkVer != null -> add("Create a container using Wine $wineVer + DXVK $dxvkVer.")
            wineVer != null -> add("Create a container using Wine $wineVer.")
        }
        containerName?.let { add("Name the container '$it' (matches the recommended preset).") }
        when {
            game.platform.equals("WINDOWS", true) -> {
                add("Place the game's install folder under the container's C: drive.")
                add("Launch the main game executable from inside ${emulator.name}.")
            }
            game.platform.uppercase() in listOf("PS1","PS2","PS3","PSP","PSVITA") ->
                add("Load your legally-dumped ${game.platform} ISO/disc image.")
            game.platform.uppercase() in listOf("SWITCH","WIIU","N3DS","NDS","WII","GC","N64") ->
                add("Load your legally-dumped ROM/cartridge image.")
            else ->
                add("Load the game in ${emulator.name}.")
        }
        add("Build shader cache by playing the title briefly — second run will be smoother.")
    }
}

private fun reportContextSentence(r: ReportEntity): String {
    val ramGb = if (r.ramMb > 0) "${r.ramMb / 1024} GB RAM" else "unknown RAM"
    val device = "${r.socFamily} (${r.gpuModel}, $ramGb)"
    val source = when (r.source.uppercase()) {
        "OUR_GITHUB" -> "submitted via this app's community DB"
        "EMUREADY_LIVE" -> "from EmuReady (live)"
        "EMUREADY_SNAPSHOT" -> "from EmuReady (snapshot)"
        "BUNDLED" -> "bundled with this release"
        "GENERATED_HEURISTIC" -> "estimated by rules engine — help by submitting a real test"
        else -> r.source.lowercase().replace('_', ' ')
    }
    return "Tested on $device · $source"
}
