package io.github.mayusi.isitcompatible.autodetect

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mayusi.isitcompatible.compatdb.room.EmulatorDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v0.9: scans the device for what the user already has, so guides can adapt
 * ("you have the PS2 BIOS ✓"). Three things:
 *   1. installed emulators  — PackageManager ∩ our catalog's packageId column
 *   2. games per system     — file count under Emulation/roms/<system>/
 *   3. BIOS status per system — filename match under Emulation/bios/<system>/
 *
 * All file IO on Dispatchers.IO. Requires all-files-access (see [AllFilesAccess]);
 * the caller gates on that before calling [scan].
 */
@Singleton
class DeviceScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val emulatorDao: EmulatorDao,
    private val biosKnowledge: BiosKnowledge,
) {

    suspend fun scan(): DetectionResult = withContext(Dispatchers.IO) {
        val catalog = runCatching { emulatorDao.all() }.getOrDefault(emptyList())
        val installed = detectInstalledEmulators(catalog)
        val emuRoot = AllFilesAccess.defaultEmulationRoot()

        // v0.9.1: scan recursively across the WHOLE shared store, not just the
        // Emulation/ tree — users keep games in arbitrary folders. Match by file
        // extension (+ folder-name hint for ambiguous disc images).
        val games = detectGamesRecursive()
        // BIOS still checked in the conventional bios/ dir AND anywhere named like one.
        // Switch prod.keys + firmware (.nca cluster) are folded into the SAME single
        // storage walk so we never pay for a second full-tree scan.
        val switchSink = SwitchSink()
        val bios = detectBios(emuRoot, switchSink)
        val switchKeys = buildSwitchKeysStatus(switchSink)
        // v0.10: for each system with games but no installed emulator, suggest
        // the best one to download.
        val suggestions = computeSuggestions(catalog, installed, games)

        DetectionResult(
            installedEmulators = installed,
            gamesBySystem = games,
            biosStatus = bios,
            missingEmulators = suggestions,
            scannedAtMs = 0L, // stamped by the caller
            emulationRootExists = emuRoot.exists(),
            switchKeys = switchKeys,
        )
    }

    /**
     * Mutable collector for Switch artifacts found during the single BIOS storage walk:
     * every plain `prod.keys` path, and an .nca count per directory (so a folder with a
     * cluster of NCAs reads as a firmware set). Folded into the existing walk to avoid a
     * second full-storage pass.
     */
    private class SwitchSink {
        val prodKeysPaths = mutableListOf<String>()
        val ncaCountByDir = HashMap<String, Int>()
    }

    /**
     * Turn the artifacts gathered during the walk (plus one cheap targeted check of the
     * Eden keys dir, which may be unreadable on Android 11+ scoped storage) into a
     * [SwitchKeysStatus]. prod.keys is validated cheaply by counting "KEYNAME = HEX"
     * lines and looking for master keys; we never fail hard if a file is unreadable.
     */
    private fun buildSwitchKeysStatus(sink: SwitchSink): SwitchKeysStatus {
        // prod.keys: prefer anything found during the walk; otherwise try the Eden dir
        // directly (commonly under Android/data and often unreadable without the app).
        val candidatePaths = LinkedHashSet<String>().apply {
            addAll(sink.prodKeysPaths)
            val edenKeys = File(
                android.os.Environment.getExternalStorageDirectory(),
                "$EDEN_FILES/keys/prod.keys",
            )
            if (edenKeys.exists()) add(edenKeys.absolutePath)
            else add(edenKeys.absolutePath) // still attempt a read below; degrade gracefully
        }

        var prodKeysFound = false
        var prodKeysPath: String? = null
        var keyCount = 0
        var keysLookComplete = false
        var keysNote: String? = null

        for (path in candidatePaths) {
            val f = File(path)
            if (!f.exists()) continue
            // We have at least a file on disk at this point.
            val validation = validateProdKeys(f)
            prodKeysFound = true
            prodKeysPath = path
            keyCount = validation.keyCount
            keysLookComplete = validation.looksComplete
            keysNote = validation.note
            // Prefer a readable, complete-looking file; stop on the first good one.
            if (validation.readable && validation.looksComplete) break
        }

        // firmware: any directory containing a cluster of .nca files counts as present.
        // The storage walk skips Android/, so also probe the Eden registered dir directly
        // (may be unreadable on scoped storage — listFiles() then returns null/empty).
        val edenRegistered = File(
            android.os.Environment.getExternalStorageDirectory(),
            "$EDEN_FILES/nand/system/Contents/registered",
        )
        val edenNcaCount = runCatching {
            edenRegistered.listFiles()?.count { it.isFile && it.extension.equals("nca", true) } ?: 0
        }.getOrDefault(0)
        if (edenNcaCount >= MIN_FIRMWARE_NCA) {
            sink.ncaCountByDir[edenRegistered.absolutePath] = edenNcaCount
        }

        val firmwareDir = sink.ncaCountByDir
            .filter { it.value >= MIN_FIRMWARE_NCA }
            .maxByOrNull { it.value }
        val firmwareFound = firmwareDir != null

        Log.i(
            TAG,
            "Switch keys: prodKeysFound=$prodKeysFound path=$prodKeysPath keyCount=$keyCount " +
                "complete=$keysLookComplete | firmwareFound=$firmwareFound " +
                "ncaCount=${firmwareDir?.value ?: 0} dir=${firmwareDir?.key}",
        )

        return SwitchKeysStatus(
            prodKeysFound = prodKeysFound,
            prodKeysPath = prodKeysPath,
            keyCount = keyCount,
            keysLookComplete = keysLookComplete,
            keysNote = keysNote,
            firmwareFound = firmwareFound,
            firmwarePath = firmwareDir?.key,
            firmwareNcaCount = firmwareDir?.value ?: 0,
        )
    }

    private data class ProdKeysValidation(
        val readable: Boolean,
        val keyCount: Int,
        val looksComplete: Boolean,
        val note: String,
    )

    /**
     * Cheap prod.keys validation. The file is small (<100 KB UTF-8 text); read it,
     * count "KEYNAME = HEXVALUE" lines, and look for master-key names. Never throws —
     * an unreadable file (scoped storage on Android 11+) returns readable=false with a
     * graceful hint instead of failing the scan.
     */
    private fun validateProdKeys(file: File): ProdKeysValidation {
        return runCatching {
            if (file.length() > MAX_KEYS_READ_BYTES) {
                return@runCatching ProdKeysValidation(
                    readable = false, keyCount = 0, looksComplete = false,
                    note = "found but file is unexpectedly large — not read",
                )
            }
            val text = file.readText(Charsets.UTF_8)
            var keyCount = 0
            val names = HashSet<String>()
            text.lineSequence().forEach { line ->
                val idx = line.indexOf("= ")
                if (idx > 0) {
                    keyCount++
                    names += line.substring(0, idx).trim().lowercase()
                }
            }
            val hasMasterKeys = MASTER_KEY_HINTS.any { hint -> names.any { it.startsWith(hint) } }
            val looksComplete = hasMasterKeys || keyCount >= MIN_COMPLETE_KEY_COUNT
            val note = if (looksComplete) "looks complete" else "looks incomplete"
            ProdKeysValidation(readable = true, keyCount = keyCount, looksComplete = looksComplete, note = note)
        }.getOrElse {
            Log.w(TAG, "Could not read prod.keys at ${file.absolutePath}", it)
            ProdKeysValidation(
                readable = false, keyCount = 0, looksComplete = false,
                note = "found but couldn't read (Android storage restriction)",
            )
        }
    }

    /**
     * v0.10: systems where the user has games but NO emulator that targets that
     * platform. Returns one suggested catalog emulator per such system, so the
     * UI can offer "you have PS2 games — Get NetherSX2".
     */
    private fun computeSuggestions(
        catalog: List<io.github.mayusi.isitcompatible.compatdb.room.EmulatorEntity>,
        installed: List<DetectedEmulator>,
        games: List<SystemGames>,
    ): List<MissingEmulatorSuggestion> {
        if (catalog.isEmpty() || games.isEmpty()) return emptyList()
        // platforms the user can already play (any installed emulator targeting them)
        val coveredPlatforms = installed
            .flatMap { it.platformTargets.split("|") }
            .map { it.trim().uppercase() }
            .toSet()

        val out = mutableListOf<MissingEmulatorSuggestion>()
        val suggestedSystems = mutableSetOf<String>()
        for (sg in games) {
            val platform = sg.platform.uppercase()
            if (platform == "DISC IMAGE" || platform == "WINDOWS") continue // disc = unknown; Windows handled via guides
            if (platform in coveredPlatforms) continue
            if (sg.system in suggestedSystems) continue
            // pick the preferred emulator for this platform from the catalog
            val pick = pickEmulatorFor(platform, catalog) ?: continue
            suggestedSystems += sg.system
            out += MissingEmulatorSuggestion(
                system = sg.system,
                platform = sg.platform,
                gameCount = sg.count,
                emulatorId = pick.id,
                emulatorName = pick.name,
                packageId = pick.packageId ?: "",
            )
        }
        Log.i(TAG, "Missing-emulator suggestions: ${out.size}")
        return out.filter { it.packageId.isNotBlank() }
    }

    /** Preferred emulator id per platform; falls back to first catalog match. */
    private fun pickEmulatorFor(
        platform: String,
        catalog: List<io.github.mayusi.isitcompatible.compatdb.room.EmulatorEntity>,
    ): io.github.mayusi.isitcompatible.compatdb.room.EmulatorEntity? {
        val preferred = PREFERRED_EMU_PER_PLATFORM[platform]
        if (preferred != null) {
            catalog.firstOrNull { it.id == preferred }?.let { return it }
        }
        return catalog.firstOrNull { e ->
            e.platformTargets.split("|").any { it.trim().equals(platform, true) }
        }
    }

    /** PackageManager ∩ catalog packageIds → which of our known emulators are installed. */
    private fun detectInstalledEmulators(
        catalog: List<io.github.mayusi.isitcompatible.compatdb.room.EmulatorEntity>,
    ): List<DetectedEmulator> {
        val pm = context.packageManager
        val installedPkgs: Set<String> = runCatching {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(0).mapNotNull { it.packageName }.toSet()
        }.getOrDefault(emptySet())

        val out = mutableListOf<DetectedEmulator>()
        val seenPkgs = mutableSetOf<String>()
        for (emu in catalog) {
            val pkg = emu.packageId ?: continue
            if (pkg in installedPkgs && pkg !in seenPkgs) {
                seenPkgs += pkg
                val versionName = runCatching {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0).versionName
                }.getOrNull()
                out += DetectedEmulator(
                    emulatorId = emu.id,
                    name = emu.name,
                    packageId = pkg,
                    installedVersion = versionName,
                    platformTargets = emu.platformTargets,
                )
            }
        }
        Log.i(TAG, "Detected ${out.size} installed emulators of ${catalog.size} known")
        return out
    }

    /**
     * v0.9.1: RECURSIVE game scan across common storage roots. Identifies a
     * game by its file extension (strong signal) or, for ambiguous disc images
     * (.iso/.chd/.cue/.bin), by the name of the folder it's in. No longer limited
     * to the Emulation/roms/<system>/ tree — finds games wherever they live.
     *
     * Bounded so it never hangs: max depth, a global file-visit cap, and it
     * skips the obvious junk dirs (Android/, .thumbnails, etc.).
     */
    private fun detectGamesRecursive(): List<SystemGames> {
        val storageRoot = android.os.Environment.getExternalStorageDirectory()
        // Group matches by system → (count, sample names).
        val counts = HashMap<String, Int>()
        val samples = HashMap<String, MutableList<String>>()
        var visited = 0

        fun classify(file: File): String? {
            val ext = file.extension.lowercase()
            if (ext.isEmpty()) return null
            // Strong extension signal.
            FolderSpec.extensionToSystem[ext]?.let { return it }
            // Ambiguous disc image → use the folder-name hint.
            if (ext in FolderSpec.ambiguousDiscExt) {
                val parent = file.parentFile?.name?.lowercase().orEmpty()
                for ((hint, sys) in FolderSpec.folderHintToSystem) {
                    if (hint in parent) return sys
                }
                // disc image with no folder hint — record as unknown disc
                return "disc"
            }
            return null
        }

        fun walk(dir: File, depth: Int) {
            if (depth > MAX_DEPTH || visited > MAX_VISITS) return
            val entries = dir.listFiles() ?: return
            for (e in entries) {
                if (visited > MAX_VISITS) return
                if (e.name.startsWith(".")) continue
                if (e.isDirectory) {
                    if (e.name.lowercase() in SKIP_DIRS) continue
                    walk(e, depth + 1)
                } else {
                    visited++
                    if (e.length() < 50_000) continue
                    val sys = classify(e) ?: continue
                    counts[sys] = (counts[sys] ?: 0) + 1
                    samples.getOrPut(sys) { mutableListOf() }.let { if (it.size < 5) it.add(e.name) }
                }
            }
        }

        runCatching { walk(storageRoot, 0) }

        val out = counts.entries
            .filter { it.key != "disc" || it.value > 0 } // keep unknown discs too — user still has games
            .map { (sys, count) ->
                SystemGames(
                    system = sys,
                    platform = if (sys == "disc") "Disc image"
                        else FolderSpec.romSystemToPlatform[sys] ?: sys.uppercase(),
                    count = count,
                    sampleNames = samples[sys].orEmpty(),
                )
            }
            .sortedByDescending { it.count }
        Log.i(TAG, "Recursive scan: visited $visited files, found games in ${out.size} systems")
        return out
    }

    /** A single BIOS hit found during the storage walk (plain file or zip entry). */
    private data class BiosHit(
        val match: BiosMatch,
        val foundFile: String,
        val foundInZip: Boolean,
        val archivePath: String?,
        val innerEntry: String?,
        /**
         * UNCOMPRESSED size in bytes: File.length() for a plain file, ZipEntry.size
         * for a zip entry. -1 if the zip didn't record an uncompressed size.
         */
        val sizeBytes: Long,
        /** Absolute path to a plain BIOS file on disk (null for zip-entry hits). */
        val plainAbsPath: String? = null,
    ) {
        val isBootBios: Boolean get() = match.kind == BiosKind.BOOT_BIOS
        val isDvdRom: Boolean get() = match.kind == BiosKind.DVD_ROM
    }

    /**
     * v0.11: SINGLE-PASS BIOS detection.
     *
     * The old design walked the entire /storage/emulated/0 tree (and re-opened
     * every .zip) once PER system — ~13 full walks and ~1500 zip opens for a user
     * with ~116 BIOS zips, which blew the visit cap and surfaced nothing.
     *
     * This walks storage exactly ONCE and opens each .zip exactly ONCE, collecting
     * every BIOS match across ALL systems into a single map keyed by system string.
     * The conventional bios/<system> and bios/ plain-file checks are folded into the
     * same walk (they live under the storage root). The per-system BiosStatus list is
     * then assembled from that map. Keeps the <200 MB per-zip guard and the visit cap.
     */
    private fun detectBios(root: File, switchSink: SwitchSink): List<BiosStatus> {
        val biosKnowledgeSystems = biosKnowledge.allSystems()
        val storageRoot = android.os.Environment.getExternalStorageDirectory()

        // system string → all BIOS hits found for it (first plain hit wins over zip).
        val hitsBySystem = HashMap<String, MutableList<BiosHit>>()
        var zipsOpened = 0
        var plainFilesMatched = 0
        var visited = 0

        fun record(hit: BiosHit) {
            hitsBySystem.getOrPut(hit.match.system) { mutableListOf() }.add(hit)
        }

        fun walk(dir: File, depth: Int) {
            if (depth > MAX_DEPTH || visited > MAX_VISITS) return
            val entries = dir.listFiles() ?: return
            for (e in entries) {
                if (visited > MAX_VISITS) return
                if (e.name.startsWith(".")) continue
                if (e.isDirectory) {
                    if (e.name.lowercase() in SKIP_DIRS) continue
                    walk(e, depth + 1)
                } else {
                    visited++
                    val ext = e.extension.lowercase()
                    if (ext == "zip") {
                        // Open each zip exactly once; peek all entries for any system.
                        // ZipFile reads only the central directory (entry metadata,
                        // incl. each entry's UNCOMPRESSED size) — it does NOT load the
                        // archive into RAM — so peeking even a ~277 MB combined-BIOS zip
                        // is cheap. Cap raised from 200 MB → 450 MB so the No-Intro
                        // "Sony - PlayStation 2 - BIOS Images" archive is no longer skipped.
                        if (e.length() > MAX_ZIP_PEEK_BYTES) {
                            Log.w(TAG, "Skipping oversized zip (${e.length()} B > $MAX_ZIP_PEEK_BYTES): ${e.absolutePath}")
                            continue
                        }
                        runCatching {
                            ZipFile(e.absolutePath).use { zipFile ->
                                val zentries = zipFile.entries()
                                while (zentries.hasMoreElements()) {
                                    val entry = zentries.nextElement()
                                    if (entry.isDirectory) continue
                                    biosKnowledge.match(entry.name)?.let { match ->
                                        record(
                                            BiosHit(
                                                match = match,
                                                foundFile = File(entry.name).name,
                                                foundInZip = true,
                                                archivePath = e.absolutePath,
                                                innerEntry = entry.name,
                                                // ZipEntry.size = UNCOMPRESSED size (not the
                                                // compressed bytes on disk). -1 if unknown.
                                                sizeBytes = entry.size,
                                            )
                                        )
                                    }
                                }
                            }
                            zipsOpened++
                        }.onFailure {
                            Log.w(TAG, "Failed to peek into zip ${e.absolutePath}", it)
                        }
                    } else {
                        // Switch artifacts (folded into this same pass): collect prod.keys
                        // anywhere, and tally .nca files per directory for firmware sets.
                        if (e.name.equals("prod.keys", ignoreCase = true)) {
                            switchSink.prodKeysPaths.add(e.absolutePath)
                        } else if (ext == "nca") {
                            val dirPath = e.parentFile?.absolutePath ?: dir.absolutePath
                            switchSink.ncaCountByDir[dirPath] =
                                (switchSink.ncaCountByDir[dirPath] ?: 0) + 1
                        }
                        // Plain file: try to match it directly (covers bios/<system>,
                        // bios/ root, and any loose BIOS anywhere on storage).
                        biosKnowledge.match(e.name)?.let { match ->
                            plainFilesMatched++
                            record(
                                BiosHit(
                                    match = match,
                                    foundFile = e.name,
                                    foundInZip = false,
                                    archivePath = null,
                                    innerEntry = null,
                                    sizeBytes = e.length(),
                                    plainAbsPath = e.absolutePath,
                                )
                            )
                        }
                    }
                }
            }
        }

        runCatching { walk(storageRoot, 0) }

        Log.i(
            TAG,
            "BIOS single-pass: opened $zipsOpened zips, $plainFilesMatched plain files, " +
                "visited $visited files, found bios for systems: ${hitsBySystem.keys.toList()}"
        )

        // Build per-system status from the single-pass map, CLASSIFYING and PREFERRING
        // the best candidate rather than taking the first name match.
        return biosKnowledgeSystems.map { knownSys ->
            val hits = hitsBySystem[knownSys.system].orEmpty()
            buildStatusForSystem(knownSys, hits)
        }
    }

    /**
     * v0.12: turn the raw hits for one system into a BiosStatus.
     *
     * Classify: only [BiosKind.BOOT_BIOS] hits can satisfy the requirement. PS2
     * "ps2-dvd-*" DVD-player ROMs are counted separately and NEVER make present=true.
     *
     * Prefer: among boot-BIOS hits, choose by (1) correct uncompressed size, then
     * (2) region rank USA>Europe>Japan>other, then (3) a ready-to-use plain file over
     * a zip entry. The chosen file's MD5 is computed ONCE (cheap: one 4 MB read) to
     * upgrade its status to "verified good" when it matches a known dump.
     */
    private fun buildStatusForSystem(
        knownSys: BiosSystem,
        hits: List<BiosHit>,
    ): BiosStatus {
        val expectedSize = biosKnowledge.expectedBootBiosSize(knownSys.system)
        val bootHits = hits.filter { it.isBootBios }
        val dvdRomCount = hits.count { it.isDvdRom }

        if (bootHits.isEmpty()) {
            // Nothing usable. If we only saw DVD-player ROMs, say so explicitly.
            val notes = when {
                dvdRomCount > 0 ->
                    "Found $dvdRomCount PS2 DVD-player ROM(s) but NO boot BIOS — " +
                        "the DVD-player ROM is not the emulator BIOS. You still need a boot BIOS."
                else -> knownSys.files.firstOrNull()?.notes ?: "BIOS not found"
            }
            if (dvdRomCount > 0) {
                Log.i(TAG, "${knownSys.system}: rejected $dvdRomCount dvd-player ROM(s), no boot BIOS present")
            }
            return BiosStatus(
                system = knownSys.system,
                display = knownSys.display,
                usedBy = knownSys.usedBy,
                required = knownSys.required,
                present = false,
                foundFile = null,
                foundInZip = false,
                archivePath = null,
                innerEntry = null,
                region = null,
                notes = notes,
                targetDir = knownSys.targetDir,
                sizeBytes = null,
                sizeOk = null,
                verified = null,
                md5 = null,
                candidateCount = 0,
                rejectedDvdRoms = dvdRomCount,
            )
        }

        // Size correctness per hit (null when the system has no size rule).
        fun sizeOk(hit: BiosHit): Boolean? =
            expectedSize?.let { hit.sizeBytes == it }

        // PREFER: correct size first, then region rank, then plain file over zip.
        val chosen = bootHits.sortedWith(
            compareByDescending<BiosHit> { sizeOk(it) == true }
                .thenBy { biosKnowledge.regionRank(it.match.region) }
                .thenByDescending { !it.foundInZip }
        ).first()

        val chosenSizeOk = sizeOk(chosen)

        // Cheap verification: MD5 of the ONE chosen candidate (never all of them).
        val md5 = if (knownSys.system.equals("ps2", true) && chosenSizeOk == true) {
            computeMd5(chosen)
        } else null
        val knownGoodLabel = biosKnowledge.knownGoodPs2Label(md5)
        val verified: Boolean? = when {
            md5 == null -> null
            knownGoodLabel != null -> true
            else -> false
        }

        val notes = buildString {
            when {
                verified == true -> append("Verified good ${knownGoodLabel}. ")
                chosenSizeOk == true -> append("Found ${chosen.match.region} ${knownSys.display} boot BIOS (size OK, checksum not verified). ")
                chosenSizeOk == false -> append("Found ${knownSys.display} boot BIOS but size looks wrong (${chosen.sizeBytes} bytes, expected $expectedSize) — may be a bad dump. ")
                else -> append(chosen.match.notes).append(' ')
            }
            if (bootHits.size > 1) append("${bootHits.size} candidates found. ")
            if (dvdRomCount > 0) append("Ignored $dvdRomCount DVD-player ROM(s).")
        }.trim()

        Log.i(
            TAG,
            "${knownSys.system} boot BIOS chosen: ${chosen.foundFile} size=${chosen.sizeBytes} " +
                "region=${chosen.match.region} sizeOk=$chosenSizeOk verified=$verified " +
                "candidates=${bootHits.size}; rejected $dvdRomCount dvd-player ROMs" +
                (chosen.archivePath?.let { " (in zip $it!/${chosen.innerEntry})" } ?: "")
        )

        return BiosStatus(
            system = knownSys.system,
            display = knownSys.display,
            usedBy = knownSys.usedBy,
            required = knownSys.required,
            present = true,
            foundFile = chosen.foundFile,
            foundInZip = chosen.foundInZip,
            archivePath = chosen.archivePath,
            innerEntry = chosen.innerEntry,
            region = chosen.match.region,
            notes = notes,
            targetDir = chosen.match.targetDir,
            sizeBytes = chosen.sizeBytes.takeIf { it >= 0 },
            sizeOk = chosenSizeOk,
            verified = verified,
            md5 = md5,
            candidateCount = bootHits.size,
            rejectedDvdRoms = dvdRomCount,
        )
    }

    /**
     * Compute MD5 of a single chosen BIOS candidate. Reads the plain file, or streams
     * the one inner zip entry — a 4 MB read, done once per system at most. Returns
     * lowercase hex, or null on any IO error (verification then reported as "unknown").
     */
    private fun computeMd5(hit: BiosHit): String? = runCatching {
        val md = MessageDigest.getInstance("MD5")
        val buf = ByteArray(64 * 1024)
        val input = if (hit.foundInZip && hit.archivePath != null && hit.innerEntry != null) {
            val zf = ZipFile(hit.archivePath)
            val entry = zf.getEntry(hit.innerEntry) ?: run { zf.close(); return null }
            // Wrap so closing the stream also closes the ZipFile.
            object : java.io.FilterInputStream(zf.getInputStream(entry)) {
                override fun close() { try { super.close() } finally { zf.close() } }
            }
        } else {
            val path = hit.plainAbsPath ?: return null
            File(path).let { f -> if (f.exists()) f.inputStream() else return null }
        }
        input.use { stream ->
            while (true) {
                val n = stream.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    private companion object {
        const val TAG = "DeviceScanner"
        val IGNORE_EXT = setOf("txt", "xml", "json", "sqlite", "db", "ini", "cfg", "dat")
        /** v0.10: which emulator to suggest first per platform (matches recommender favorites). */
        val PREFERRED_EMU_PER_PLATFORM = mapOf(
            "PS2" to "nethersx2-patch", "PS1" to "duckstation", "PSP" to "ppsspp",
            "PS3" to "aps3e", "PSVITA" to "vita3k", "SWITCH" to "eden",
            "WIIU" to "cemu", "N3DS" to "azahar", "NDS" to "retroarch",
            "GC" to "dolphin", "WII" to "dolphin", "N64" to "mupen64plus",
            "SNES" to "retroarch", "NES" to "retroarch", "GBA" to "retroarch",
            "GENESIS" to "retroarch", "SATURN" to "retroarch", "DC" to "flycast",
            "ARCADE" to "retroarch",
        )
        // Recursive-scan guards so we never hang on huge storage.
        const val MAX_DEPTH = 6
        const val MAX_VISITS = 60_000

        /**
         * v0.12: per-zip peek cap. Raised 200 MB → 450 MB so the No-Intro combined
         * "Sony - PlayStation 2 - BIOS Images" archive (~277 MB) is read. ZipFile only
         * parses the central directory (entry metadata), so peeking is cheap regardless
         * of archive size; the cap is just a sanity guard against pathological files.
         */
        const val MAX_ZIP_PEEK_BYTES = 450_000_000L
        /** Eden emulator's files dir under shared storage (keys/ + nand/ live here). */
        const val EDEN_FILES = "Android/data/dev.eden.eden_emulator/files"
        /** Max bytes we'll read for prod.keys validation (it's small UTF-8 text). */
        const val MAX_KEYS_READ_BYTES = 100_000L
        /** prod.keys looks complete if it has at least this many "= " key lines. */
        const val MIN_COMPLETE_KEY_COUNT = 20
        /** A directory with at least this many .nca files reads as a firmware set. */
        const val MIN_FIRMWARE_NCA = 5
        /** Key-name prefixes that strongly imply a complete prod.keys. */
        val MASTER_KEY_HINTS = listOf(
            "header_key", "key_area_key_application", "master_key", "aes_kek_generation",
        )
        val SKIP_DIRS = setOf(
            "android", "dcim", "pictures", "movies", "music", "whatsapp",
            "telegram", ".thumbnails", "ringtones", "notifications", "alarms",
            "podcasts", "audiobooks", "lost.dir",
        )
    }
}
