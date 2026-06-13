package io.github.mayusi.isitcompatible.ui.detail

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mayusi.isitcompatible.apply.ApplyJobState
import io.github.mayusi.isitcompatible.apply.PresetStager
import io.github.mayusi.isitcompatible.compatdb.CompatDbRepository
import io.github.mayusi.isitcompatible.compatdb.GuideResolver
import io.github.mayusi.isitcompatible.compatdb.GuideStepDto
import io.github.mayusi.isitcompatible.compatdb.JournalShareIntent
import io.github.mayusi.isitcompatible.compatdb.ResolvedGuide
import io.github.mayusi.isitcompatible.compatdb.compatJson
import io.github.mayusi.isitcompatible.compatdb.room.DriverDao
import io.github.mayusi.isitcompatible.compatdb.room.EmulatorDao
import io.github.mayusi.isitcompatible.compatdb.room.EmulatorEntity
import io.github.mayusi.isitcompatible.compatdb.room.GameDao
import io.github.mayusi.isitcompatible.compatdb.room.GameEntity
import io.github.mayusi.isitcompatible.compatdb.room.GuideDao
import io.github.mayusi.isitcompatible.compatdb.room.GuideProgressEntity
import io.github.mayusi.isitcompatible.compatdb.room.JournalDao
import io.github.mayusi.isitcompatible.compatdb.room.JournalEntryEntity
import io.github.mayusi.isitcompatible.compatdb.room.LocalVerifiedGuideDao
import io.github.mayusi.isitcompatible.compatdb.room.LocalVerifiedGuideEntity
import io.github.mayusi.isitcompatible.compatdb.room.PresetDao
import io.github.mayusi.isitcompatible.compatdb.room.PresetEntity
import io.github.mayusi.isitcompatible.compatdb.room.ReportDao
import io.github.mayusi.isitcompatible.data.UserPreferences
import io.github.mayusi.isitcompatible.hardware.DeviceFingerprint
import io.github.mayusi.isitcompatible.recommend.Recommendation
import io.github.mayusi.isitcompatible.recommend.RecommendationsBySource
import io.github.mayusi.isitcompatible.recommend.Recommender
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class GameDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao,
    private val emulatorDao: EmulatorDao,
    private val presetDao: PresetDao,
    private val reportDao: ReportDao,
    private val driverDao: DriverDao,
    private val journalDao: JournalDao,
    private val guideDao: GuideDao,
    private val localVerifiedGuideDao: LocalVerifiedGuideDao,
    private val guideResolver: GuideResolver,
    private val deviceScanner: io.github.mayusi.isitcompatible.autodetect.DeviceScanner,
    private val emulatorInstaller: io.github.mayusi.isitcompatible.getit.EmulatorInstaller,
    private val prefs: UserPreferences,
    private val stager: PresetStager,
    private val compatDb: CompatDbRepository,
    handle: SavedStateHandle,
) : ViewModel() {

    private val gameId: String = handle["gameId"] ?: error("gameId required")
    private val recommender = Recommender()

    private companion object {
        /** Android package id of the EmuHelper companion app; must match the manifest entry id. */
        const val EMUHELPER_PACKAGE_ID = "io.github.mayusi.emuhelper"

        /** Emulator id used for GameNative (Windows games). Matches GameNativeTemplate. */
        const val GAMENATIVE_EMULATOR_ID = "gamenative"

        /** Package id for the IIC fork; matches the manifest entry added in emulators.json. */
        const val GAMENATIVE_IIC_PACKAGE_ID = "app.gamenative.iic"
        const val GAMENATIVE_IIC_DISPLAY_NAME = "GameNative (IIC)"

        /**
         * Chunk 4: top-level keys a real GameNative config must carry for us to
         * trust it as a verified import. Deliberately lenient — a sane core set
         * from the reference schema (emulator, wineVersion, graphicsDriver,
         * dxwrapper) plus the extraData object (checked separately). We reject
         * obviously-wrong files but don't demand every field GameNative writes.
         */
        val REQUIRED_CONFIG_KEYS = listOf(
            "emulator", "wineVersion", "graphicsDriver", "dxwrapper",
        )
    }

    private val _state = MutableStateFlow(GameDetailState())
    val state: StateFlow<GameDetailState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            compatDb.ready.first { it }
            val fp = prefs.data.first().fingerprint
            val game = gameDao.byId(gameId)
            val rawReports = reportDao.byGame(gameId)
            // Policy: Windows games are GameNative-only. Filter out any reports
            // for other runners (winlator/cmod/mobox/gamehub) so the recommender
            // never surfaces them — even if the seed still contains older entries.
            val isWindowsForFilter = game?.platform.equals("WINDOWS", ignoreCase = true)
            val reports = if (isWindowsForFilter) {
                rawReports.filter { it.emulatorId.equals(GAMENATIVE_EMULATOR_ID, ignoreCase = true) }
            } else rawReports
            // v0.5: split recommendations by source so the UI can render real
            // user reports separately from heuristic estimates.
            val bySource: RecommendationsBySource = if (game != null && fp != null) {
                recommender.rankBySource(reports, fp, topK = 3)
            } else {
                RecommendationsBySource(emptyList(), emptyList())
            }

            val emusById = emulatorDao.all().associateBy { it.id }
            // Hydrate every preset id referenced by either pool so the UI
            // can render details for both real + generated tiles.
            val presetIds = bySource.all().mapNotNull { it.presetId }.distinct()
            val presetsById = presetIds.mapNotNull { presetDao.byId(it) }.associateBy { it.id }

            // v0.6: hydrate the drivers referenced by these presets so the UI
            // can surface "newer Turnip available" when DriverSyncWorker has
            // stamped upstreamLatestTag on a driver row.
            val driverIds = presetsById.values.mapNotNull { it.driverId }.distinct()
            val driversById = driverIds.mapNotNull { driverDao.byId(it) }.associateBy { it.id }

            // v0.5: load the user's own past entries for this game so the
            // detail screen can show "Your last run" prominently above the
            // community recommendation.
            val journalEntries = journalDao.forGame(gameId)

            // v0.8: resolve the best-available setup guide for the recommended
            // emulator and load any saved checklist progress. The recommender
            // already chose the emulator; the resolver layers the right guide
            // (Tier 1 verified > 2 authored > 3 EmuReady > 4 base) on top.
            val topEmulatorId = bySource.all().firstOrNull()?.emulatorId
            val guide = topEmulatorId?.let { guideResolver.resolve(gameId, it) }
            val guideProgress: Set<Int> = guide
                ?.let { guideDao.progressFor(it.progressKey(gameId)).filter { p -> p.done }.map { p -> p.stepIndex }.toSet() }
                ?: emptySet()

            // v0.9: device-aware guide. ONLY scan if the user already granted
            // all-files-access via the Auto-Detect tab — we never trigger that
            // permission from a game screen. When we have a detection result we
            // compute per-step "you already have this" status (installed
            // emulator, dumped BIOS) so the guide adapts to the user's device.
            val detection = if (io.github.mayusi.isitcompatible.autodetect.AllFilesAccess.isGranted(context)) {
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { deviceScanner.scan() }.getOrNull()
                }
            } else null
            val stepStatuses: Map<Int, GuideStepStatus> =
                if (guide != null && detection != null)
                    computeStepStatuses(guide, detection, game)
                else emptyMap()

            // Chunk 3: honesty gate for the GameNative downloadable config.
            // A config may only be offered as "Verified" when the resolved guide
            // for (this game, GameNative) is tier 1 AND actually carries a real,
            // non-blank importable gameNativeConfigJson. We resolve the GameNative
            // guide explicitly here rather than reusing `guide` above, because the
            // top recommendation's emulator may differ — the gate must key off
            // GameNative's own guide. Crucially this keys ONLY off the tier-1 guide
            // and its config payload, NEVER off report source, so mislabeled
            // OUR_GITHUB "estimate" reports cannot unlock a confident config.
            val isWindowsGame = game?.platform.equals("WINDOWS", ignoreCase = true)
            val gameNativeGuide = if (isWindowsGame && game != null) {
                runCatching { guideResolver.resolve(game.id, GAMENATIVE_EMULATOR_ID) }.getOrNull()
            } else null
            val verifiedGameNativeConfig =
                gameNativeGuide != null &&
                    gameNativeGuide.tier == 1 &&
                    !gameNativeGuide.gameNativeConfigJson.isNullOrBlank()

            // QW-1: DELIVER real configs honestly, labeled by tier — do NOT gate
            // delivery on tier 1. Any guide that actually carries a non-blank
            // gameNativeConfigJson can be handed to the user; the wording just
            // tells the truth about how trustworthy it is:
            //   tier 1     -> VERIFIED   (green)  "Verified config"
            //   tier 2/3   -> AUTHORED   (amber)  "Authored — try it, not yet device-verified"
            //   no real config on any guide (synthesized fallback only)
            //              -> FALLBACK   (grey)   "Experimental untested defaults"
            // This keys ONLY off the guide's tier + its config payload, never off
            // report source.
            val hasRealConfig = !gameNativeGuide?.gameNativeConfigJson.isNullOrBlank()

            // One-tap GameNative handoff availability: show the "Apply config &
            // launch in GameNative" button only when we know the game's store app
            // id, we have a real config to hand over, AND GameNative (IIC or
            // official) is installed so the LAUNCH_GAME intent will actually resolve.
            // Otherwise the UI falls back to the existing download/export path.
            val gameNativeInstalled = io.github.mayusi.isitcompatible.getit.GameNativeLaunch.isInstalled(context)
            val iicInstalled = io.github.mayusi.isitcompatible.getit.GameNativeLaunch.isIicInstalled(context)
            val canOneTapLaunch =
                isWindowsGame &&
                    game?.steamAppId != null && game.steamAppId > 0 &&
                    hasRealConfig &&
                    gameNativeInstalled

            val configTrust = when {
                !isWindowsGame -> ConfigTrust.NONE
                hasRealConfig && gameNativeGuide?.tier == 1 -> ConfigTrust.VERIFIED
                hasRealConfig -> ConfigTrust.AUTHORED
                else -> ConfigTrust.FALLBACK
            }

            // QW-6: demote heuristic guesses. When this game's recommendation/FPS
            // comes ONLY from GENERATED_HEURISTIC reports (no real reports at all),
            // the hero/FPS tile must NOT read as a confident "tested" result. The
            // recommender already split the pools; we just surface a flag the UI
            // uses to swap to an honest "no tested data yet — estimated only" state.
            val generatedOnly = bySource.fromReal.isEmpty() && bySource.fromGenerated.isNotEmpty()

            _state.update {
                it.copy(
                    loading = false,
                    game = game,
                    fingerprint = fp,
                    recommendations = bySource.all(),       // legacy field — kept for any callers
                    recommendationsBySource = bySource,     // v0.5 source-aware
                    emulatorsById = emusById,
                    presetsById = presetsById,
                    driversById = driversById,
                    reportCount = reports.size,
                    journalEntries = journalEntries,
                    guide = guide,
                    guideDoneSteps = guideProgress,
                    guideStepStatuses = stepStatuses,
                    isWindowsGame = isWindowsGame,
                    verifiedGameNativeConfig = verifiedGameNativeConfig,
                    configTrust = configTrust,
                    gameNativeGuide = gameNativeGuide,
                    recommendationGeneratedOnly = generatedOnly,
                    canOneTapLaunch = canOneTapLaunch,
                    iicInstalled = iicInstalled,
                )
            }
            // IIC round-trip: after state is ready, check whether the GameNative
            // fork left a pending session for this game and pre-fill the form.
            checkAndConsumePendingSession()
        }
    }

    /**
     * v0.9: for each guide step, decide whether the user already satisfies it,
     * based on the device scan. Drives the "✓ Installed" / "✓ you have
     * scph70012.bin" affordances in GuideSection.
     */
    private fun computeStepStatuses(
        guide: ResolvedGuide,
        detection: io.github.mayusi.isitcompatible.autodetect.DetectionResult,
        game: GameEntity?,
    ): Map<Int, GuideStepStatus> {
        val out = HashMap<Int, GuideStepStatus>()
        guide.steps.forEachIndexed { i, step ->
            when (step.kind) {
                "GET_APP" -> {
                    // The guide is for one emulator (guide.emulatorId). Installed?
                    val installed = guide.emulatorId in detection.installedEmulatorIds
                    if (installed) {
                        val ver = detection.installedEmulators
                            .firstOrNull { it.emulatorId == guide.emulatorId }?.installedVersion
                        out[i] = GuideStepStatus.AlreadyHave(
                            if (ver != null) "Installed (v$ver)" else "Installed",
                        )
                    }
                }
                "BIOS" -> {
                    // Map the game's platform to a bios system, check detection.
                    val sys = game?.platform?.let { platformToBiosSystem(it) }
                    val bios = sys?.let { detection.biosFor(it) }
                    if (bios?.present == true) {
                        out[i] = GuideStepStatus.AlreadyHave(
                            bios.foundFile?.let { "You have $it" } ?: "BIOS present",
                        )
                    }
                }
            }
        }
        return out
    }

    /** Catalog platform tag → FolderSpec bios system key. */
    private fun platformToBiosSystem(platform: String): String? = when (platform.uppercase()) {
        "PS1" -> "ps1"; "PS2" -> "ps2"; "DC" -> "dc"; "NDS" -> "ds"
        "N3DS" -> "3ds"; "GBA" -> "gba"; "SATURN" -> "saturn"
        "SWITCH" -> "switch"; "PSP" -> "psp"; "PSVITA" -> "psvita"; "WIIU" -> "wiiu"
        else -> null
    }

    /**
     * v0.8: toggle a guide checklist step. Persists to guide_progress (local-only)
     * and updates state in place so the checkbox flips instantly.
     */
    fun toggleGuideStep(stepIndex: Int, done: Boolean) {
        val guide = _state.value.guide ?: return
        val key = guide.progressKey(gameId)
        viewModelScope.launch {
            guideDao.setProgress(GuideProgressEntity(guideKey = key, stepIndex = stepIndex, done = done))
            _state.update {
                val next = it.guideDoneSteps.toMutableSet().apply { if (done) add(stepIndex) else remove(stepIndex) }
                it.copy(guideDoneSteps = next)
            }
        }
    }

    /**
     * v0.10: download + install the recommended emulator for this guide,
     * straight from the guide's GET_APP step. Looks up the emulator's package
     * id from the hydrated map, then runs the same getit flow as Auto-Detect.
     */
    fun installGuideEmulator() {
        val guide = _state.value.guide ?: return
        val emu = _state.value.emulatorsById[guide.emulatorId] ?: return
        val pkg = emu.packageId ?: return
        if (_state.value.emulatorInstallStatus is GuideInstallStatus.Working) return
        viewModelScope.launch {
            emulatorInstaller.install(pkg, emu.name).collect { p ->
                val status = when (p) {
                    is io.github.mayusi.isitcompatible.getit.InstallProgress.Resolving ->
                        GuideInstallStatus.Working("Finding latest…")
                    is io.github.mayusi.isitcompatible.getit.InstallProgress.Downloading ->
                        GuideInstallStatus.Working("Downloading ${p.percent}%")
                    is io.github.mayusi.isitcompatible.getit.InstallProgress.ReadyToInstall ->
                        GuideInstallStatus.Done
                    is io.github.mayusi.isitcompatible.getit.InstallProgress.Failed ->
                        GuideInstallStatus.Failed(p.message)
                }
                _state.update { it.copy(emulatorInstallStatus = status) }
            }
        }
    }

    /**
     * v0.10: download + install the EmuHelper companion app, used by BIOS guide
     * steps. Reuses the exact same getit flow as [installGuideEmulator], keyed on
     * EmuHelper's package id so [EmulatorManifestRepository.findByPackageId] can
     * locate its manifest entry.
     */
    fun installEmuHelper() {
        if (_state.value.emuHelperInstallStatus is GuideInstallStatus.Working) return
        viewModelScope.launch {
            emulatorInstaller.install(EMUHELPER_PACKAGE_ID, "EmuHelper").collect { p ->
                val status = when (p) {
                    is io.github.mayusi.isitcompatible.getit.InstallProgress.Resolving ->
                        GuideInstallStatus.Working("Finding latest…")
                    is io.github.mayusi.isitcompatible.getit.InstallProgress.Downloading ->
                        GuideInstallStatus.Working("Downloading ${p.percent}%")
                    is io.github.mayusi.isitcompatible.getit.InstallProgress.ReadyToInstall ->
                        GuideInstallStatus.Done
                    is io.github.mayusi.isitcompatible.getit.InstallProgress.Failed ->
                        GuideInstallStatus.Failed(p.message)
                }
                _state.update { it.copy(emuHelperInstallStatus = status) }
            }
        }
    }

    /**
     * Download + install the GameNative (IIC) fork. Reuses the same getit flow
     * as installGuideEmulator / installEmuHelper, keyed on the IIC package id so
     * EmulatorManifestRepository.findByPackageId locates the manifest entry we
     * added in emulators.json. Progress is tracked in iicInstallStatus so the UI
     * can show Working/Done/Failed independently of the main emulator install path.
     */
    fun installIicFork() {
        if (_state.value.iicInstallStatus is GuideInstallStatus.Working) return
        viewModelScope.launch {
            emulatorInstaller.install(GAMENATIVE_IIC_PACKAGE_ID, GAMENATIVE_IIC_DISPLAY_NAME).collect { p ->
                val status = when (p) {
                    is io.github.mayusi.isitcompatible.getit.InstallProgress.Resolving ->
                        GuideInstallStatus.Working("Finding latest…")
                    is io.github.mayusi.isitcompatible.getit.InstallProgress.Downloading ->
                        GuideInstallStatus.Working("Downloading ${p.percent}%")
                    is io.github.mayusi.isitcompatible.getit.InstallProgress.ReadyToInstall ->
                        GuideInstallStatus.Done
                    is io.github.mayusi.isitcompatible.getit.InstallProgress.Failed ->
                        GuideInstallStatus.Failed(p.message)
                }
                _state.update { it.copy(iicInstallStatus = status) }
            }
        }
    }

    /** Persist a new journal entry then refresh only the journal entries in state. */
    fun saveJournal(entry: JournalEntryEntity) {
        viewModelScope.launch {
            journalDao.upsert(entry)
            // v0.6: if the user opted in, open a pre-filled GitHub issue so they
            // can review and submit. We never POST silently — the user always
            // sees what's being shared and clicks Submit themselves.
            if (entry.shareWithCommunity) {
                val snap = _state.value
                JournalShareIntent.fire(
                    context = context,
                    entry = entry,
                    gameTitle = snap.game?.title ?: "(unknown game)",
                    emulatorName = entry.emulatorId?.let { snap.emulatorsById[it]?.name },
                    presetName = entry.presetId?.let { snap.presetsById[it]?.name },
                    fp = snap.fingerprint,
                )
            }
            // Targeted refresh: reload only the journal entries for this game so
            // we don't trigger a full load() (which flashes loading=true, re-runs
            // the blocking deviceScanner.scan(), and re-fetches the recommender).
            val fresh = journalDao.forGame(gameId)
            _state.update { it.copy(journalEntries = fresh, journalFormOpen = false) }
        }
    }

    /** UI hint that the user wants the form open. */
    fun openJournalForm() { _state.update { it.copy(journalFormOpen = true) } }
    fun closeJournalForm() { _state.update { it.copy(journalFormOpen = false) } }

    /**
     * IIC round-trip: check whether the GameNative fork left a pending session
     * for *this* game. If so, pre-fill the journal form with the session data
     * and clear the pending-session pref so it only fires once.
     *
     * Called from [load] after state has been initialised so the game is known.
     * Safe to call when no pending session exists — it's a no-op in that case.
     */
    fun checkAndConsumePendingSession() {
        viewModelScope.launch {
            val snap = prefs.data.first()
            val pendingGameId = snap.pendingSessionGameId ?: return@launch
            if (pendingGameId != gameId) return@launch

            // This pending session is for our game — consume it.
            val sessionMinutes = snap.pendingSessionMinutes
            val showedFps = snap.pendingSessionShowedFps
            // Clear the pref first so a crash / re-navigation doesn't re-trigger.
            prefs.setPendingSession(gameId = null, sessionMinutes = null, showedFps = false)
            // Surface the pre-filled form. We pre-populate sessionMinutes; the
            // JournalEntryForm already handles defaultEmulator/defaultPreset from
            // the normal recommendation path — we only override sessionMinutes here.
            _state.update {
                it.copy(
                    journalFormOpen = true,
                    pendingSessionMinutes = sessionMinutes,
                    pendingSessionShowedFps = showedFps,
                )
            }
        }
    }

    fun apply(rec: Recommendation) {
        val game = _state.value.game ?: return
        val preset = rec.presetId?.let { _state.value.presetsById[it] } ?: return
        val emulator = _state.value.emulatorsById[rec.emulatorId] ?: return
        viewModelScope.launch {
            val staging = prefs.data.first().stagingFolderUri
            if (staging == null) {
                _state.update { it.copy(applyState = ApplyJobState.Error("Pick a staging folder in Settings first.")) }
                return@launch
            }
            _state.update { it.copy(applyState = ApplyJobState.Working("Preparing...")) }
            val driver = preset.driverId?.let { driverDao.byId(it) }
            // Chunk 2: for GameNative, hand the renderer the resolved guide's
            // verified config (real importable schema) so it can emit it verbatim.
            val gameNativeConfigJson = guideResolver.resolve(game.id, emulator.id)
                ?.gameNativeConfigJson
            val res = stager.stage(
                stagingUri = android.net.Uri.parse(staging),
                game = game,
                emulator = emulator,
                preset = preset,
                driver = driver,
                gameNativeConfigJson = gameNativeConfigJson,
            ) { msg -> _state.update { it.copy(applyState = ApplyJobState.Working(msg)) } }
            _state.update { it.copy(applyState = res) }
        }
    }

    /**
     * Chunk 3: stage the GameNative config for this game. Reuses the existing
     * [apply] flow (which already forwards the resolved guide's real
     * gameNativeConfigJson into the stager / GameNativeTemplate). We pick the
     * GameNative recommendation so the staged file is the importable
     * `<Game>_config.json`. This method is wired to BOTH the verified
     * "Download GameNative config" action and the clearly-labeled unverified
     * fallback — the honesty distinction lives entirely in the UI wording driven
     * by [GameDetailState.verifiedGameNativeConfig]; the staging mechanics are the
     * same. The gate for *whether to present this as verified* never touches this
     * method, so it can't be unlocked by report source.
     */
    fun applyGameNativeConfig() {
        val rec = _state.value.recommendationsBySource.all()
            .firstOrNull { it.emulatorId == GAMENATIVE_EMULATOR_ID }
            ?: _state.value.recommendations.firstOrNull { it.emulatorId == GAMENATIVE_EMULATOR_ID }
            ?: return
        apply(rec)
    }

    fun dismissApplyResult() {
        _state.update { it.copy(applyState = null) }
    }

    /**
     * One-tap handoff: APPLY the guide's GameNative config AND launch the game in
     * GameNative via its first-party LAUNCH_GAME intent — no manual file import.
     *
     * Uses the game's [GameEntity.steamAppId] plus the resolved GameNative guide's
     * real importable config (mapped to the intent's accepted subset inside
     * [GameNativeLaunch]). On any failure (GameNative not installed, missing app
     * id) we gracefully fall back to the existing download/export-config flow so
     * the user still gets the config.
     *
     * NOTE: this only HANDS the config to GameNative and launches — it doesn't and
     * can't guarantee the game runs. The existing verified/authored labeling on
     * the panel still describes how trustworthy the config itself is.
     */
    fun launchInGameNative() {
        val game = _state.value.game ?: return
        val appId = game.steamAppId
        if (appId == null || appId <= 0) {
            // No app id — can't use the one-tap path; fall back to download.
            applyGameNativeConfig()
            return
        }
        viewModelScope.launch {
            // Prefer the already-resolved GameNative guide's config; re-resolve as
            // a backstop so we always hand over the freshest real config.
            val configJson = _state.value.gameNativeGuide?.gameNativeConfigJson
                ?: runCatching { guideResolver.resolve(game.id, GAMENATIVE_EMULATOR_ID) }
                    .getOrNull()?.gameNativeConfigJson

            val iicWillBeUsed = io.github.mayusi.isitcompatible.getit.GameNativeLaunch.isIicInstalled(context)
            val launched = io.github.mayusi.isitcompatible.getit.GameNativeLaunch.launchInGameNative(
                context = context,
                appId = appId,
                gameSource = "STEAM",
                configJson = configJson,
            )
            if (launched) {
                val msg = if (iicWillBeUsed)
                    "Launching via GameNative (IIC) — applying config with auto-fixes…"
                else
                    "Applying config and launching in GameNative…"
                _state.update { it.copy(oneTapLaunchMessage = msg) }
            } else {
                // GameNative isn't installed / intent didn't resolve. Fall back to
                // the existing export-config flow so the user still gets the config.
                _state.update {
                    it.copy(oneTapLaunchMessage = "GameNative isn't installed — staged the config to import manually instead.")
                }
                applyGameNativeConfig()
            }
        }
    }

    fun dismissOneTapLaunchMessage() {
        _state.update { it.copy(oneTapLaunchMessage = null) }
    }

    // ========================================================================
    // Chunk 4: import the user's OWN working GameNative config → Tier-1 verified
    // local guide. THE self-correcting loop: verified data comes from a real
    // device run the user imports, never from invented values.
    // ========================================================================

    /** UI state for the import flow, surfaced as a dialog/snackbar on the screen. */
    fun dismissImportState() { _state.update { it.copy(importState = null) } }

    /**
     * Import a GameNative `<Game>_config.json` the user exported off their own
     * device. Validates it's a real config, sanitizes device/user-specific bits,
     * persists it as a DURABLE tier-1 verified guide (survives sync wipes), and
     * records a journal entry. After success the screen refreshes and the
     * verified panel flips on.
     */
    fun importGameNativeConfig(uri: Uri) {
        val game = _state.value.game ?: return
        _state.update { it.copy(importState = ImportConfigState.Working) }
        viewModelScope.launch {
            // 1. Read the file off the SAF uri.
            val rawText = withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                }.getOrNull()
            }
            if (rawText.isNullOrBlank()) {
                _state.update { it.copy(importState = ImportConfigState.Error("Couldn't read that file. Pick the GameNative config JSON you exported.")) }
                return@launch
            }

            // 2. Parse + validate it's actually a GameNative config.
            val parsed = runCatching { compatJson.parseToJsonElement(rawText).jsonObject }.getOrNull()
            if (parsed == null) {
                _state.update { it.copy(importState = ImportConfigState.Error("That file isn't valid JSON. Export the config from GameNative and try again.")) }
                return@launch
            }
            val missing = REQUIRED_CONFIG_KEYS.filter { it !in parsed.keys }
            val hasExtraData = parsed["extraData"] is JsonObject
            if (missing.isNotEmpty() || !hasExtraData) {
                val why = buildString {
                    append("This doesn't look like a GameNative config. ")
                    if (missing.isNotEmpty()) append("Missing: ${missing.joinToString(", ")}. ")
                    if (!hasExtraData) append("Missing the extraData section. ")
                    append("Export it from GameNative (game → 3 dots → Export Config).")
                }
                _state.update { it.copy(importState = ImportConfigState.Error(why)) }
                return@launch
            }

            // 3. Sanitize — strip device/user-specific + volatile bits so the
            //    config is reusable + shareable. Keep everything reproducible.
            val sanitized = sanitizeGameNativeConfig(parsed)
            val sanitizedJson = compatJson.encodeToString(JsonObject.serializer(), sanitized)

            // 4. Build the verified source label from the device fingerprint.
            val fp = _state.value.fingerprint
            val deviceLine = fp?.let { "${it.socFamily} · ${it.gpuModel}" }
            val sourceLabel = if (deviceLine != null) "Verified on your device · $deviceLine" else "Verified on your device"
            val now = System.currentTimeMillis()

            // 5. Minimal typed steps for the guide: get the app + import the config.
            val steps = listOf(
                GuideStepDto(
                    kind = "GET_APP",
                    text = "Install GameNative",
                ),
                GuideStepDto(
                    kind = "ACTION",
                    text = "Import this verified config (open the game → 3 dots → Import Config).",
                ),
            )
            val stepsJson = compatJson.encodeToString(ListSerializer(GuideStepDto.serializer()), steps)

            // 6. Persist as a DURABLE local verified guide. This table is never
            //    wiped by sync; replaceAll re-applies it into `guides` as tier 1
            //    after every seed reload, so it survives restarts AND wins over
            //    the seed via the resolver's lowest-tier-wins ordering.
            val localId = "local:${game.id}:$GAMENATIVE_EMULATOR_ID:t1"
            val local = LocalVerifiedGuideEntity(
                id = localId,
                gameId = game.id,
                emulatorId = GAMENATIVE_EMULATOR_ID,
                sourceLabel = sourceLabel,
                dataAsOf = now,
                stepsJson = stepsJson,
                gameNativeConfigJson = sanitizedJson,
                createdAt = now,
            )
            runCatching {
                localVerifiedGuideDao.upsert(local)
                // Apply immediately into `guides` so the resolver sees it without
                // waiting for the next sync's replaceAll re-apply.
                guideDao.upsertAll(listOf(local.toGuideEntity()))
            }.onFailure { err ->
                _state.update { it.copy(importState = ImportConfigState.Error("Couldn't save the imported config: ${err.message}")) }
                return@launch
            }

            // 7. Record a journal entry tying this verified result to the game.
            //    PLAYABLE by default — the user imported a config that worked.
            runCatching {
                journalDao.upsert(
                    JournalEntryEntity(
                        id = UUID.randomUUID().toString(),
                        gameId = game.id,
                        emulatorId = GAMENATIVE_EMULATOR_ID,
                        presetId = null,
                        avgFps = null,
                        stability = "PLAYABLE",
                        notes = "Imported a verified working GameNative config.",
                        createdAt = now,
                        sessionMinutes = null,
                        peakTempC = null,
                        driverIdAtTimeOfRun = null,
                        shareWithCommunity = false,
                    ),
                )
            }

            // 8. Refresh so verifiedGameNativeConfig flips true + the panel updates.
            load()
            _state.update {
                it.copy(importState = ImportConfigState.Success(sanitizedConfigJson = sanitizedJson))
            }
        }
    }

    /**
     * Opt-in community share of a just-imported verified config. Reuses the
     * existing JournalShareIntent pre-filled GitHub Issue path and appends the
     * sanitized config JSON to the issue body. Never POSTs silently — opens the
     * browser to a review-before-submit page.
     */
    fun shareImportedConfig(sanitizedConfigJson: String) {
        val game = _state.value.game ?: return
        val fp = _state.value.fingerprint
        val entry = JournalEntryEntity(
            id = UUID.randomUUID().toString(),
            gameId = game.id,
            emulatorId = GAMENATIVE_EMULATOR_ID,
            presetId = null,
            avgFps = null,
            stability = "PLAYABLE",
            notes = "Verified working GameNative config (imported off-device). " +
                "Config attached below for inclusion as a Tier-1 verified guide.\n\n" +
                "```json\n$sanitizedConfigJson\n```",
            createdAt = System.currentTimeMillis(),
            sessionMinutes = null,
            peakTempC = null,
            driverIdAtTimeOfRun = null,
            shareWithCommunity = true,
        )
        JournalShareIntent.fire(
            context = context,
            entry = entry,
            gameTitle = game.title,
            emulatorName = _state.value.emulatorsById[GAMENATIVE_EMULATOR_ID]?.name ?: "GameNative",
            presetName = null,
            fp = fp,
        )
    }

    /**
     * Sanitize a parsed GameNative config: blank device/user-specific identity
     * (name), strip the absolute game-install path from `drives`, drop volatile
     * telemetry (sessionMetadata), and keep everything that's reusable. The
     * `id`/`name`/`installPath`/`executablePath` are container-instance specifics
     * that don't transfer; we normalize them so the config imports cleanly for
     * the next person without leaking the importer's paths.
     */
    private fun sanitizeGameNativeConfig(src: JsonObject): JsonObject = buildJsonObject {
        for ((key, value) in src) {
            when (key) {
                // Volatile telemetry — never reusable, never share.
                "sessionMetadata" -> { /* drop */ }
                // Identity / instance-specifics — blank so it's reusable + not leaky.
                "name" -> put("name", JsonPrimitive(""))
                "id" -> put("id", JsonPrimitive(""))
                "installPath" -> put("installPath", JsonPrimitive(""))
                "executablePath" -> put("executablePath", JsonPrimitive(""))
                // `drives` embeds the absolute on-device game-install path; blank it.
                // The importing user re-points their own install on import.
                "drives" -> put("drives", JsonPrimitive(""))
                // Keep everything else verbatim — emulator, wineVersion,
                // containerVariant, graphicsDriver(+config+version), dxwrapper(+config),
                // box/fex presets + versions, wincomponents, envVars, cpu lists,
                // extraData (version/applied fields), audio, screen size, etc.
                else -> put(key, value)
            }
        }
    }
}

data class GameDetailState(
    val loading: Boolean = true,
    val game: GameEntity? = null,
    val fingerprint: DeviceFingerprint? = null,
    /** Flat concatenation of real + generated, real first. Kept for legacy UI bits. */
    val recommendations: List<Recommendation> = emptyList(),
    /** v0.5: source-aware ranking so the UI can render real and generated tiles separately. */
    val recommendationsBySource: RecommendationsBySource = RecommendationsBySource(emptyList(), emptyList()),
    val emulatorsById: Map<String, EmulatorEntity> = emptyMap(),
    val presetsById: Map<String, PresetEntity> = emptyMap(),
    /** v0.6: drivers referenced by visible presets — used for "newer driver available" hints. */
    val driversById: Map<String, io.github.mayusi.isitcompatible.compatdb.room.DriverEntity> = emptyMap(),
    val reportCount: Int = 0,
    val applyState: ApplyJobState? = null,
    /** v0.5: user's own logged runs for this game, newest first. */
    val journalEntries: List<JournalEntryEntity> = emptyList(),
    /** True when the journal entry form should be visible as a bottom sheet. */
    val journalFormOpen: Boolean = false,
    /** v0.8: resolved setup guide for the recommended emulator, or null if none. */
    val guide: ResolvedGuide? = null,
    /** v0.8: indices of guide steps the user has checked off. */
    val guideDoneSteps: Set<Int> = emptySet(),
    /** v0.9: per-step "you already have this" status from the device scan. */
    val guideStepStatuses: Map<Int, GuideStepStatus> = emptyMap(),
    /** v0.10: status of downloading the guide's recommended emulator, or null. */
    val emulatorInstallStatus: GuideInstallStatus? = null,
    /** v0.10: status of downloading the EmuHelper companion app (BIOS steps), or null. */
    val emuHelperInstallStatus: GuideInstallStatus? = null,
    /** Chunk 3: true when this game is a Windows/GameNative title. */
    val isWindowsGame: Boolean = false,
    /**
     * Chunk 3 honesty gate: true only when the resolved GameNative guide for THIS
     * game is tier 1 (Verified) AND carries a real, non-blank importable config.
     * This is the ONLY signal that may surface a confident "Download GameNative
     * config" affordance. It deliberately ignores report source, so mislabeled
     * OUR_GITHUB "estimate" reports can never unlock a config.
     */
    val verifiedGameNativeConfig: Boolean = false,
    /**
     * QW-1: tier-accurate trust level for the downloadable GameNative config.
     * Drives the config panel's label/color and which action is primary:
     *  - VERIFIED  (tier 1, real config)   → green "Verified config", primary download.
     *  - AUTHORED  (tier 2/3, real config) → amber "Authored — try it, not yet device-verified".
     *  - FALLBACK  (no real config)        → grey "Experimental untested defaults", secondary only.
     *  - NONE      (non-Windows game)      → panel not shown.
     * Computed purely from the GameNative guide's tier + config payload — never report source.
     */
    val configTrust: ConfigTrust = ConfigTrust.NONE,
    /**
     * QW-6: true when the only recommendation/FPS data for this game is
     * GENERATED_HEURISTIC (no real reports). The UI must then present an honest
     * "no tested data yet — estimated only" state instead of a confident tile.
     */
    val recommendationGeneratedOnly: Boolean = false,
    /**
     * Chunk 3: the resolved GameNative guide for this game (tier + config payload),
     * or null for non-Windows games / when GameNative has no guide. Chunk 4 (import)
     * can build on this to know what schema/version a shared config should match.
     */
    val gameNativeGuide: ResolvedGuide? = null,
    /**
     * Chunk 4: state of the "import my working config" flow, or null when idle.
     * Drives the import progress/error/success UI on the GameNative panel.
     */
    val importState: ImportConfigState? = null,
    /**
     * One-tap GameNative handoff: true when this game can be applied+launched in
     * GameNative directly via the LAUNCH_GAME intent — i.e. it's a Windows game
     * with a known steamAppId, a real GameNative config, AND GameNative installed.
     * Drives whether the primary "Apply config & launch in GameNative" button shows.
     */
    val canOneTapLaunch: Boolean = false,
    /** Transient snackbar message after a one-tap launch attempt, or null. */
    val oneTapLaunchMessage: String? = null,
    /**
     * True when the GameNative IIC fork (app.gamenative.iic) is installed. Used to
     * drive the "Get GameNative (IIC)" offer card: shown when false, replaced with
     * a "Launching via IIC" hint when true.
     */
    val iicInstalled: Boolean = false,
    /** Download/install status for the IIC fork "Get it" card, or null when idle. */
    val iicInstallStatus: GuideInstallStatus? = null,
    /**
     * IIC round-trip: session minutes delivered by the fork broadcast, pre-filling
     * the journal form when the user opens this game's detail. Null if no pending
     * session was delivered (normal case).
     */
    val pendingSessionMinutes: Int? = null,
    /**
     * IIC round-trip: whether the FPS HUD was active during the fork session.
     * Pre-populated into the journal form notes when a pending session exists.
     */
    val pendingSessionShowedFps: Boolean = false,
)

/**
 * QW-1: how much we trust the downloadable GameNative config for a game.
 * Maps directly to honest, tier-accurate UI wording in the config panel.
 */
enum class ConfigTrust {
    /** Tier-1 guide with a real importable config — a known-good, device-verified config. */
    VERIFIED,
    /** Tier-2/3 guide with a real importable config — authored, not yet device-verified. */
    AUTHORED,
    /** No guide carries a real config — only synthesized experimental defaults are available. */
    FALLBACK,
    /** Not a Windows/GameNative game — no config panel. */
    NONE,
}

/** Chunk 4: progress of importing the user's own GameNative config. */
sealed interface ImportConfigState {
    data object Working : ImportConfigState
    data class Error(val message: String) : ImportConfigState
    /** [sanitizedConfigJson] is the cleaned config, handed to the optional share path. */
    data class Success(val sanitizedConfigJson: String) : ImportConfigState
}

/** v0.10: emulator-download status for the guide's GET_APP step. */
sealed interface GuideInstallStatus {
    data class Working(val label: String) : GuideInstallStatus
    data object Done : GuideInstallStatus
    data class Failed(val message: String) : GuideInstallStatus
}

/** v0.9: device-aware status for a guide step. */
sealed interface GuideStepStatus {
    /** User already satisfies this step (emulator installed / BIOS dumped). [label] e.g. "Installed (v1.2)". */
    data class AlreadyHave(val label: String) : GuideStepStatus
}
