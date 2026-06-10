package io.github.mayusi.isitcompatible.autodetect

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton that loads and serves the BIOS knowledge table from assets/autodetect/bios-knowledge.json.
 * Provides intelligent matching of BIOS filenames (case-insensitive, dash/underscore normalization)
 * and region/notes hints for the UI.
 */
@Singleton
class BiosKnowledge @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val data: BiosKnowledgeData

    init {
        data = runCatching {
            val json = context.assets.open("autodetect/bios-knowledge.json").bufferedReader().use { it.readText() }
            JSON.decodeFromString<BiosKnowledgeData>(json)
        }.getOrElse {
            Log.e(TAG, "Failed to load BIOS knowledge table", it)
            BiosKnowledgeData(systems = emptyList())
        }
        Log.i(TAG, "Loaded BIOS knowledge for ${data.systems.size} systems")
    }

    /**
     * Match a filename against the knowledge table. Returns the matching system,
     * region, notes, and target directory if found; null otherwise.
     *
     * Matching is case-insensitive and tries various dash/underscore normalizations
     * to handle inconsistent user naming conventions (e.g., "scph70012.bin" matches
     * "scph-70012.bin").
     */
    fun match(filename: String): BiosMatch? {
        // Classify "ps2-dvd-*" FIRST: these are DVD-player updater ROMs, not the boot
        // BIOS. The exact table never lists them, but a stray entry must never be
        // mistaken for a boot BIOS, so short-circuit before any name table lookup.
        val base = bareBase(filename)
        PS2_DVD_REGEX.find(base)?.let {
            val region = it.groupValues.getOrNull(1)?.let { c -> regionForCode(c) } ?: "Unknown region"
            return buildPatternMatch(
                system = "ps2",
                region = region,
                notes = "PS2 DVD player ROM ($region) — this is NOT the emulator boot BIOS.",
                kind = BiosKind.DVD_ROM,
            )
        }

        val normalized = normalizeFilename(filename)
        for (sys in data.systems) {
            for (file in sys.files) {
                if (normalizeFilename(file.filename) == normalized) {
                    return BiosMatch(
                        system = sys.system,
                        display = sys.display,
                        usedBy = sys.usedBy,
                        targetDir = sys.targetDir,
                        region = file.region,
                        notes = file.notes,
                        kind = BiosKind.BOOT_BIOS,
                    )
                }
            }
        }
        // Fallback: pattern-based matching for common BIOS dump naming schemes
        // (e.g. "ps2-0230a-20080220.bin") that the exact table doesn't enumerate.
        return matchByPattern(filename)
    }

    /**
     * Pattern-based fallback. Runs only when exact normalized matching fails.
     * Recognizes common BIOS dump filename conventions:
     *  - PS2 aap/No-Intro dumps: ps2-####<region>-######## → system "ps2"
     *  - PS2 DVD player ROMs: ps2-dvd-#### → system "ps2"
     *  - Generic 4-digit SCPH (PS1, <40000) the exact table missed → system "ps1"
     */
    private fun matchByPattern(filename: String): BiosMatch? {
        // Strip any path component and the extension; operate on the bare base name.
        // (ps2-dvd-* is already handled in match() before this fallback runs.)
        val base = bareBase(filename)

        // PS2 boot-BIOS dump: ps2-####<region>-######## (region = single letter after digits)
        PS2_DUMP_REGEX.find(base)?.let {
            val region = regionForCode(it.groupValues[1])
            return buildPatternMatch(
                system = "ps2",
                region = region,
                notes = "PS2 boot BIOS dump ($region) — detected by naming pattern.",
                kind = BiosKind.BOOT_BIOS,
            )
        }

        // Generic 4-digit SCPH that the exact table missed → treat as PS1
        // (4-digit SCPH numbers below 40000 are PS1 hardware).
        SCPH_GENERIC_REGEX.find(base)?.let {
            return buildPatternMatch(
                system = "ps1",
                region = "Unknown region",
                notes = "PS1 BIOS dump — detected by naming pattern.",
                kind = BiosKind.BOOT_BIOS,
            )
        }

        return null
    }

    /** Strip path components and extension; lowercase. */
    private fun bareBase(filename: String): String = filename
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .substringBeforeLast('.')
        .lowercase()

    /** Build a BiosMatch for a pattern hit, pulling display/usedBy/targetDir from the loaded table. */
    private fun buildPatternMatch(
        system: String,
        region: String,
        notes: String,
        kind: BiosKind,
    ): BiosMatch? {
        val sys = data.systems.firstOrNull { it.system.equals(system, ignoreCase = true) } ?: return null
        return BiosMatch(
            system = sys.system,
            display = sys.display,
            usedBy = sys.usedBy,
            targetDir = sys.targetDir,
            region = region,
            notes = notes,
            kind = kind,
        )
    }

    /** Map a single BIOS region letter to a human region string. */
    private fun regionForCode(code: String): String = when (code.lowercase()) {
        "a", "u" -> "USA"
        "e" -> "Europe"
        "j" -> "Japan"
        "h" -> "Asia"
        "c" -> "China"
        "k" -> "Korea"
        "r" -> "Russia"
        else -> "Unknown region"
    }

    fun allSystems(): List<BiosSystem> = data.systems.map { it.toDomain() }

    /**
     * v0.12: canonical uncompressed size in bytes for a system's boot BIOS, or null
     * if the system has no fixed-size rule. A genuine PS2 boot BIOS dump is EXACTLY
     * 4 MB (4,194,304 bytes); see PCSX2 docs and the No-Intro PS2 BIOS set.
     */
    fun expectedBootBiosSize(system: String): Long? = when (system.lowercase()) {
        "ps2" -> PS2_BOOT_BIOS_SIZE
        else -> null
    }

    /**
     * v0.12: preference rank for choosing the headline BIOS when many are present.
     * Lower = better. USA (a/u) > Europe (e) > Japan (j) > everything else.
     */
    fun regionRank(region: String?): Int = when (region?.lowercase()) {
        "usa" -> 0
        "europe" -> 1
        "japan" -> 2
        else -> 3
    }

    /**
     * v0.12: if [md5] matches a documented known-good PS2 boot-BIOS dump, return a
     * human label for it; null otherwise. Kept small on purpose — these are the
     * canonical region anchors (USA SCPH-39001, Europe SCPH-50003, Japan SCPH-10000).
     */
    fun knownGoodPs2Label(md5: String?): String? =
        md5?.lowercase()?.let { PS2_KNOWN_GOOD_MD5[it] }

    /** Public region mapping for callers that parse a No-Intro region letter. */
    fun regionForLetter(code: String): String = regionForCode(code)

    private fun normalizeFilename(filename: String): String {
        // Lowercase + strip all dashes/underscores, then lowercase again for matching
        return filename.lowercase().replace(Regex("[_-]"), "")
    }

    private companion object {
        const val TAG = "BiosKnowledge"

        /** A genuine PS2 boot BIOS dump is exactly 4 MB uncompressed. */
        const val PS2_BOOT_BIOS_SIZE = 4_194_304L

        /**
         * Documented known-good PS2 boot-BIOS MD5s (lowercase) → human label.
         * Region anchors per PCSX2 / No-Intro: USA SCPH-39001, Europe SCPH-50003,
         * Japan SCPH-10000. Used only to upgrade the chosen candidate's status to
         * "verified good"; an unknown MD5 is NOT treated as bad (just unverified).
         */
        val PS2_KNOWN_GOOD_MD5 = mapOf(
            "9229299c0d09a8d4370a247c1f1c7503" to "USA PS2 boot BIOS (SCPH-39001)",
            "df42a2eb01651dfa8789b35124a0d433" to "Europe PS2 boot BIOS (SCPH-50003)",
            "acf4730ceb38ac9d8c7d8e21f2614600" to "Japan PS2 boot BIOS (SCPH-10000)",
        )

        // Lenient decoder so comments / extra keys (e.g. "_comment") in the
        // asset JSON never break loading.
        val JSON = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        // ps2-####<region>-######## (the region is the single letter after the digit run).
        val PS2_DUMP_REGEX = Regex("^ps2[-_]?\\d{3,4}([a-z])[-_]?\\d{6,8}.*", RegexOption.IGNORE_CASE)
        // ps2-dvd-#### (optional trailing region letter captured if present).
        val PS2_DVD_REGEX = Regex("^ps2[-_]?dvd[-_]?\\d+(?:[-_]?([a-z]))?.*", RegexOption.IGNORE_CASE)
        // Generic 4-digit scph dump (e.g. scph1001, scph-5501).
        val SCPH_GENERIC_REGEX = Regex("^scph[-_]?\\d{4}.*", RegexOption.IGNORE_CASE)
    }
}

/**
 * Match result from the knowledge table.
 */
data class BiosMatch(
    val system: String,
    val display: String,
    val usedBy: String,
    val targetDir: String,
    val region: String,
    val notes: String,
    /** v0.12: what KIND of dump this is — drives validation, not just naming. */
    val kind: BiosKind = BiosKind.BOOT_BIOS,
)

/**
 * v0.12: classification of a matched BIOS-like file. The scanner uses this to
 * decide whether a file can satisfy a system's boot-BIOS requirement.
 */
enum class BiosKind {
    /** The actual emulator boot BIOS (e.g. scph-#### or ps2-####<region>-########). */
    BOOT_BIOS,

    /**
     * A PS2 DVD-player updater ROM (ps2-dvd-*). NetherSX2/ARMSX2 do NOT use this;
     * it must never satisfy the ps2 boot-BIOS requirement.
     */
    DVD_ROM,
}

/**
 * Public API version of a BIOS system (no JSON serialization cruft).
 */
data class BiosSystem(
    val system: String,
    val display: String,
    val usedBy: String,
    val targetDir: String,
    val required: Boolean,
    val files: List<BiosFileInfo>,
)

data class BiosFileInfo(
    val filename: String,
    val region: String,
    val notes: String,
)

// JSON serialization models
@Serializable
internal data class BiosKnowledgeData(
    val schema: Int = 1,
    val systems: List<BiosSystemJson>,
) {
    @Serializable
    data class BiosSystemJson(
        val system: String,
        val display: String,
        val usedBy: String,
        val targetDir: String,
        val required: Boolean,
        val files: List<BiosFileJson>,
    ) {
        @Serializable
        data class BiosFileJson(
            val filename: String,
            val region: String,
            val notes: String,
        )

        fun toDomain() = BiosSystem(
            system = system,
            display = display,
            usedBy = usedBy,
            targetDir = targetDir,
            required = required,
            files = files.map { f ->
                BiosFileInfo(
                    filename = f.filename,
                    region = f.region,
                    notes = f.notes,
                )
            },
        )
    }
}
