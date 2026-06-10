package io.github.mayusi.isitcompatible.autodetect

/**
 * v0.9: the result of a device scan. Held in memory by the Auto-Detect VM and
 * read by the device-aware guide layer. (Not persisted to Room in 9.2 — the
 * scan is cheap and we re-run it on tab entry; a cache table can be added later
 * if scans get slow on huge libraries.)
 */
data class DetectionResult(
    val installedEmulators: List<DetectedEmulator>,
    val gamesBySystem: List<SystemGames>,
    val biosStatus: List<BiosStatus>,
    val missingEmulators: List<MissingEmulatorSuggestion> = emptyList(),
    val scannedAtMs: Long,
    val emulationRootExists: Boolean,
    /**
     * Switch prod.keys + firmware detection status. Null means "not scanned yet"
     * (e.g. permission not granted / pre-scan) — the UI must NOT claim found/not-found
     * in that case. Default keeps existing constructor call sites compiling.
     */
    val switchKeys: SwitchKeysStatus? = null,
) {
    val installedPackageIds: Set<String> get() = installedEmulators.map { it.packageId }.toSet()
    val installedEmulatorIds: Set<String> get() = installedEmulators.map { it.emulatorId }.toSet()

    fun hasBiosFor(system: String): Boolean =
        biosStatus.firstOrNull { it.system.equals(system, true) }?.present == true

    fun biosFor(system: String): BiosStatus? =
        biosStatus.firstOrNull { it.system.equals(system, true) }

    companion object {
        val EMPTY = DetectionResult(
            installedEmulators = emptyList(),
            gamesBySystem = emptyList(),
            biosStatus = emptyList(),
            missingEmulators = emptyList(),
            scannedAtMs = 0L,
            emulationRootExists = false,
        )
    }
}

data class DetectedEmulator(
    val emulatorId: String,
    val name: String,
    val packageId: String,
    val installedVersion: String?,
    val platformTargets: String,
)

data class SystemGames(
    val system: String,
    val platform: String,
    val count: Int,
    val sampleNames: List<String>,
)

data class BiosStatus(
    val system: String,
    val display: String,
    val usedBy: String,
    val required: Boolean,
    val present: Boolean,
    /** the actual filename found, e.g. "scph-70012.bin", or null if missing. */
    val foundFile: String?,
    /** true if the BIOS was found inside a zip archive instead of as a plain file. */
    val foundInZip: Boolean = false,
    /** absolute path to the archive if foundInZip=true; null otherwise. */
    val archivePath: String? = null,
    /** path inside the archive (e.g., "folder/scph70012.bin") if foundInZip=true. */
    val innerEntry: String? = null,
    /** region of the BIOS variant, e.g. "USA", "Europe", "Japan" — from knowledge table. */
    val region: String? = null,
    /** user-facing notes about this BIOS, e.g., "USA PS2 BIOS — works for US/NTSC games." */
    val notes: String? = null,
    /** target directory for extraction, e.g., "Emulation/bios/ps2" — from knowledge table. */
    val targetDir: String? = null,
    /**
     * v0.12: uncompressed size in bytes of the chosen BIOS file (File.length() for a
     * plain file, ZipEntry.size for a zip entry). Null if unknown/not applicable.
     */
    val sizeBytes: Long? = null,
    /**
     * v0.12: true if the chosen file's uncompressed size matches the canonical size
     * for this system (PS2 boot BIOS = 4,194,304 bytes). Null when the system has no
     * size rule. False means "found but size looks wrong — may be a bad dump".
     */
    val sizeOk: Boolean? = null,
    /**
     * v0.12: true if the chosen file's checksum was computed and matched a known-good
     * dump (currently only PS2 boot BIOS, MD5 of one chosen candidate). False = computed
     * but no match; null = not computed.
     */
    val verified: Boolean? = null,
    /** v0.12: MD5 of the chosen file if it was cheaply computed; null otherwise. */
    val md5: String? = null,
    /** v0.12: how many valid boot-BIOS candidates were found for this system. */
    val candidateCount: Int = 0,
    /**
     * v0.12: count of files that matched the name but were rejected as the emulator
     * boot BIOS (e.g. PS2 "ps2-dvd-*" DVD player ROMs). Informational for the UI.
     */
    val rejectedDvdRoms: Int = 0,
)

/**
 * Switch keys & firmware detection. These come from a Nintendo Switch the user
 * owns — there is no download for them — so we only DETECT what's already present
 * and report it honestly. Firmware version is intentionally not detected (hard to
 * derive from NCAs); we only report presence + an .nca count.
 */
data class SwitchKeysStatus(
    /** A prod.keys file was found anywhere we looked. */
    val prodKeysFound: Boolean,
    /** Absolute path to the prod.keys we found, or null. */
    val prodKeysPath: String?,
    /** How many "KEYNAME = HEXVALUE" lines we counted (0 if unreadable/not found). */
    val keyCount: Int,
    /** Heuristic: looks complete (has master keys / enough entries) vs incomplete. */
    val keysLookComplete: Boolean,
    /** Short human note, e.g. "looks complete", "incomplete", or the read-failure hint. */
    val keysNote: String?,
    /** A firmware set (a cluster of .nca files) was found. */
    val firmwareFound: Boolean,
    /** Absolute path to the folder containing the firmware .nca files, or null. */
    val firmwarePath: String?,
    /** Number of .nca files in the firmware folder (0 if not found). */
    val firmwareNcaCount: Int,
)

/** v0.10: a system the user has games for but no installed emulator. */
data class MissingEmulatorSuggestion(
    val system: String,
    val platform: String,
    val gameCount: Int,
    val emulatorId: String,
    val emulatorName: String,
    val packageId: String,
)
