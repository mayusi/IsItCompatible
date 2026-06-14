package io.github.mayusi.isitcompatible.ui.autodetect

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mayusi.isitcompatible.autodetect.AllFilesAccess
import io.github.mayusi.isitcompatible.autodetect.BiosExtractor
import io.github.mayusi.isitcompatible.autodetect.BiosStatus
import io.github.mayusi.isitcompatible.autodetect.DeviceScanner
import io.github.mayusi.isitcompatible.autodetect.DetectionResult
import io.github.mayusi.isitcompatible.compatdb.CompatDbRepository
import io.github.mayusi.isitcompatible.compatdb.room.GameDao
import io.github.mayusi.isitcompatible.compatdb.room.ReportDao
import io.github.mayusi.isitcompatible.data.UserPreferences
import io.github.mayusi.isitcompatible.getit.EmulatorInstaller
import io.github.mayusi.isitcompatible.getit.InstallProgress
import io.github.mayusi.isitcompatible.hardware.DeviceFingerprint
import io.github.mayusi.isitcompatible.recommend.Bucket
import io.github.mayusi.isitcompatible.recommend.Recommender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * ViewModel for the Auto-Detect tab. Manages scanning the device for
 * installed emulators, games, and BIOS files, plus the v0.10 "Get it"
 * emulator-download flow.
 */
@HiltViewModel
class AutoDetectViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceScanner: DeviceScanner,
    private val installer: EmulatorInstaller,
    private val biosExtractor: BiosExtractor,
    private val prefs: UserPreferences,
    private val gameDao: GameDao,
    private val reportDao: ReportDao,
    private val compatDb: CompatDbRepository,
    private val recommender: Recommender,
) : ViewModel() {
    private val _state = MutableStateFlow(AutoDetectState())
    val state: StateFlow<AutoDetectState> = _state.asStateFlow()

    init {
        // Check permission and auto-scan if granted
        refreshPermission()
        // Compute hardware coverage stats once the DB is ready
        viewModelScope.launch {
            val fp = prefs.data.first().fingerprint
            _state.update { it.copy(deviceFingerprint = fp) }
            if (fp != null) {
                compatDb.ready.first { it }
                loadCoverageStats(fp)
            }
        }
    }

    private suspend fun loadCoverageStats(fp: DeviceFingerprint) {
        val stats = withContext(Dispatchers.IO) {
            val games = gameDao.all()
            var realCount = 0
            var estimatedCount = 0
            games.map { it.id }.chunked(500).forEach { chunk ->
                val reports = reportDao.byGameIds(chunk)
                val grouped = reports.groupBy { it.gameId }
                chunk.forEach { gameId ->
                    val gameReports = grouped[gameId].orEmpty()
                    if (gameReports.isEmpty()) return@forEach
                    // v0.5 source-aware split: real = non-GENERATED_HEURISTIC reports,
                    // generated = GENERATED_HEURISTIC. A game only counts as having "real
                    // data for your chip" when the real pool produces a STRONG (SAME_SOC_AND_RAM)
                    // or MODERATE (SAME_SOC_FAMILY) match — the same determination
                    // GameDetailViewModel uses via bySource.fromReal.isEmpty(). This matches
                    // exactly what the detail screen shows so the count is consistent with what
                    // the user sees per-game.
                    val bySource = recommender.rankBySource(gameReports, fp, topK = 1)
                    val topReal = bySource.fromReal.firstOrNull()
                    if (topReal != null &&
                        (topReal.bucket == Bucket.SAME_SOC_AND_RAM || topReal.bucket == Bucket.SAME_SOC_FAMILY)
                    ) {
                        realCount++
                    } else {
                        estimatedCount++
                    }
                }
            }
            DeviceCoverageStats(real = realCount, estimated = estimatedCount)
        }
        _state.update { it.copy(coverageStats = stats) }
    }

    fun refreshPermission() {
        val granted = AllFilesAccess.isGranted(context)
        _state.update { it.copy(permissionGranted = granted, emuHelperInstalled = isEmuHelperInstalled()) }
        if (granted) {
            scan()
        }
    }

    /** True if the EmuHelper companion app is installed on this device. */
    private fun isEmuHelperInstalled(): Boolean = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(EMUHELPER_PACKAGE_ID, 0)
        true
    }.getOrDefault(false)

    /**
     * Switch keys & firmware: route the user to EmuHelper. If it's already
     * installed, launch it; otherwise run the same getit download+install flow
     * used for emulators (keyed on EmuHelper's package id).
     */
    fun getEmuHelper() {
        // Already installed → just open it.
        if (isEmuHelperInstalled()) {
            val launch = context.packageManager.getLaunchIntentForPackage(EMUHELPER_PACKAGE_ID)
            if (launch != null) {
                launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(launch) }
                return
            }
            // No launch intent (rare) — fall through to (re)install path.
        }
        if (_state.value.emuHelperStatus is GetItStatus.Working) return
        viewModelScope.launch {
            installer.install(EMUHELPER_PACKAGE_ID, "EmuHelper").collect { p ->
                val status = when (p) {
                    is InstallProgress.Resolving -> GetItStatus.Working("Finding latest…")
                    is InstallProgress.Downloading -> GetItStatus.Working("Downloading ${p.percent}%")
                    is InstallProgress.ReadyToInstall -> GetItStatus.Done
                    is InstallProgress.Failed -> GetItStatus.Failed(p.message)
                }
                _state.update { it.copy(emuHelperStatus = status) }
            }
        }
    }

    fun scan() {
        viewModelScope.launch {
            _state.update { it.copy(scanning = true, lastError = null) }
            try {
                val result = deviceScanner.scan()
                val stampedResult = result.copy(scannedAtMs = System.currentTimeMillis())
                _state.update { it.copy(result = stampedResult, scanning = false) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        scanning = false,
                        lastError = e.message ?: "Unknown error during scan",
                    )
                }
            }
        }
    }

    /**
     * v0.10: "Get it" — download + open-installer for an emulator by package id.
     * Status is tracked per packageId so multiple buttons show independent state.
     */
    fun install(packageId: String, displayName: String) {
        // Don't start a second download for the same package.
        if (_state.value.installStatus[packageId] is GetItStatus.Working) return
        viewModelScope.launch {
            installer.install(packageId, displayName).collect { p ->
                val status = when (p) {
                    is InstallProgress.Resolving -> GetItStatus.Working("Finding latest…")
                    is InstallProgress.Downloading -> GetItStatus.Working("Downloading ${p.percent}%")
                    is InstallProgress.ReadyToInstall -> GetItStatus.Done
                    is InstallProgress.Failed -> GetItStatus.Failed(p.message)
                }
                _state.update {
                    it.copy(installStatus = it.installStatus + (packageId to status))
                }
            }
        }
    }

    /**
     * v0.10.1: Extract a BIOS file from a zip archive to its target directory,
     * then re-scan to refresh the UI. Status tracked per system so multiple
     * extractions can proceed independently.
     */
    fun extractBios(status: BiosStatus) {
        val key = "bios_${status.system}"
        // Don't start a second extraction for the same system
        if (_state.value.extractStatus[key] is ExtractStatus.Working) return
        if (!status.foundInZip || status.archivePath == null || status.innerEntry == null || status.targetDir == null) {
            _state.update {
                it.copy(extractStatus = it.extractStatus + (key to ExtractStatus.Failed("Invalid archive info")))
            }
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(extractStatus = it.extractStatus + (key to ExtractStatus.Working))
            }
            val result = biosExtractor.extract(status.archivePath, status.innerEntry, status.targetDir)
            if (result.isSuccess) {
                _state.update {
                    it.copy(extractStatus = it.extractStatus + (key to ExtractStatus.Done))
                }
                // Re-scan after successful extraction
                scan()
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                _state.update {
                    it.copy(extractStatus = it.extractStatus + (key to ExtractStatus.Failed(error)))
                }
            }
        }
    }

    companion object {
        /** Same package id used by GameDetailViewModel's EmuHelper flow. */
        const val EMUHELPER_PACKAGE_ID = "io.github.mayusi.emuhelper"
    }
}

/** Per-emulator "Get it" button state. */
sealed interface GetItStatus {
    data class Working(val label: String) : GetItStatus
    data object Done : GetItStatus
    data class Failed(val message: String) : GetItStatus
}

/** Per-system BIOS extraction state. */
sealed interface ExtractStatus {
    data object Working : ExtractStatus
    data object Done : ExtractStatus
    data class Failed(val message: String) : ExtractStatus
}

/** Catalog coverage counts for the device hardware summary card. */
data class DeviceCoverageStats(
    /** Games with STRONG or MODERATE confidence reports for this device. */
    val real: Int,
    /** Games where only WEAK/VERY_WEAK (estimated) reports are available. */
    val estimated: Int,
)

data class AutoDetectState(
    val permissionGranted: Boolean = false,
    val scanning: Boolean = false,
    val result: DetectionResult? = null,
    val lastError: String? = null,
    /** v0.10: per-packageId download/install status for "Get it" buttons. */
    val installStatus: Map<String, GetItStatus> = emptyMap(),
    /** v0.10.1: per-system BIOS extraction status. */
    val extractStatus: Map<String, ExtractStatus> = emptyMap(),
    /** Switch keys & firmware: whether EmuHelper is installed (drives Open vs Get). */
    val emuHelperInstalled: Boolean = false,
    /** Switch keys & firmware: download/install status for the EmuHelper Get button. */
    val emuHelperStatus: GetItStatus? = null,
    /** Device fingerprint from prefs (for hardware summary card). */
    val deviceFingerprint: DeviceFingerprint? = null,
    /** Catalog coverage stats — null while computing. */
    val coverageStats: DeviceCoverageStats? = null,
)
