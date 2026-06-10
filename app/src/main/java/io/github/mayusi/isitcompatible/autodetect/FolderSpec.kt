package io.github.mayusi.isitcompatible.autodetect

/**
 * v0.9: mirror of EmuTran's Emulation/ folder convention + BIOS filename tables.
 *
 * We keep these identical to EmuTran (and to ES-DE / Daijishō / Cocoon Shell
 * conventions) so the scanner finds what the user already laid down with
 * EmuTran or any standard frontend. Source of truth for:
 *  - which `roms/<system>/` dirs to scan for games,
 *  - which BIOS filenames satisfy each system's requirement,
 *  - the `bios/<system>/` dirs to look in.
 *
 * BIOS matching is by filename (case-insensitive), no hashing — same as
 * EmuTran. The app never provides BIOS; it only tells you whether yours is
 * present and, if not, exactly which filename to dump.
 */
object FolderSpec {

    const val ROOT = "Emulation"

    /** rom systems we scan under Emulation/roms/<system>/. */
    val romSystems: List<String> = listOf(
        "3ds", "atari2600", "atari5200", "atari7800", "atomiswave",
        "dc", "dos", "ds", "gamegear", "gba", "gbc", "gb", "gc",
        "genesis", "mastersystem", "n64", "naomi", "nes", "ngp",
        "pce", "ps1", "ps2", "ps3", "psp", "psvita", "saturn",
        "scummvm", "snes", "switch", "wii", "wiiu", "wonderswan",
        "arcade",
    )

    /**
     * BIOS requirement per system. key = bios/<system> dir; value = the set of
     * filenames (any one present = "you have it"). Empty list = no BIOS needed
     * (we still surface the dir but mark it "not required").
     *
     * Filenames + which-emulator-uses-them mirror EmuTran's biosReadmes exactly.
     */
    val biosRequirements: List<BiosRequirement> = listOf(
        BiosRequirement(
            system = "ps1", display = "PlayStation 1", usedBy = "DuckStation",
            filenames = listOf("scph1001.bin", "scph5501.bin", "scph7001.bin", "scph1002.bin"),
            required = false, // DuckStation can run without, BIOS improves accuracy
        ),
        BiosRequirement(
            system = "ps2", display = "PlayStation 2", usedBy = "NetherSX2 / ARMSX2",
            filenames = listOf("scph-70012.bin", "scph-70004.bin", "scph-70000.bin",
                "scph70012.bin", "scph70004.bin", "scph70000.bin"),
            required = true,
        ),
        BiosRequirement(
            system = "dc", display = "Dreamcast", usedBy = "Flycast",
            filenames = listOf("dc_boot.bin", "dc_flash.bin"),
            required = true,
        ),
        BiosRequirement(
            system = "ds", display = "Nintendo DS", usedBy = "MelonDS",
            filenames = listOf("bios7.bin", "bios9.bin", "firmware.bin"),
            required = true,
        ),
        BiosRequirement(
            system = "3ds", display = "Nintendo 3DS", usedBy = "Azahar / Citra MMJ",
            filenames = listOf("aes_keys.txt"),
            required = false, // only for encrypted content
        ),
        BiosRequirement(
            system = "gba", display = "Game Boy Advance", usedBy = "RetroArch (mGBA / VBA-M)",
            filenames = listOf("gba_bios.bin"),
            required = false,
        ),
        BiosRequirement(
            system = "saturn", display = "Sega Saturn", usedBy = "RetroArch (Beetle Saturn)",
            filenames = listOf("saturn_bios.bin"),
            required = true,
        ),
        BiosRequirement(
            system = "switch", display = "Nintendo Switch", usedBy = "Eden / Citron",
            filenames = listOf("prod.keys", "title.keys"),
            required = true,
        ),
        BiosRequirement(
            system = "psp", display = "PlayStation Portable", usedBy = "PPSSPP",
            filenames = emptyList(),
            required = false, // PPSSPP needs no BIOS
        ),
        BiosRequirement(
            system = "psvita", display = "PlayStation Vita", usedBy = "Vita3K",
            filenames = listOf("psp2updat.pup"),
            required = false, // installed through Vita3K's own installer
        ),
        BiosRequirement(
            system = "wiiu", display = "Wii U", usedBy = "Cemu",
            filenames = listOf("keys.txt", "title.keys"),
            required = false,
        ),
    )

    /**
     * v0.9.1: file-extension → system, for RECURSIVE scanning. The old scanner
     * only looked in roms/<system>/ folders; this lets us identify a game by
     * its file type no matter what folder it's sitting in. Lowercase, no dot.
     * Ambiguous extensions (.bin/.iso/.chd/.zip/.7z) are deliberately NOT here —
     * they could be anything, so we only claim a system when the extension is
     * a strong signal. Folder-name hints (below) disambiguate the rest.
     */
    val extensionToSystem: Map<String, String> = mapOf(
        // Nintendo handhelds / home
        "nds" to "ds", "3ds" to "3ds", "cia" to "3ds", "cci" to "3ds",
        "gba" to "gba", "gbc" to "gbc", "gb" to "gb",
        "n64" to "n64", "z64" to "n64", "v64" to "n64",
        "nes" to "nes", "sfc" to "snes", "smc" to "snes",
        "nsp" to "switch", "xci" to "switch",
        "rvz" to "gc", "gcm" to "gc", "wbfs" to "wii", "wad" to "wii",
        "wux" to "wiiu", "wua" to "wiiu",
        // Sega
        "gen" to "genesis", "md" to "genesis", "sms" to "mastersystem",
        "gg" to "gamegear", "32x" to "genesis",
        // Sony
        "pbp" to "psp", "cso" to "psp",
        "vpk" to "psvita",
        // PC engine / others
        "pce" to "pce", "ngp" to "ngp", "ws" to "wonderswan", "wsc" to "wonderswan",
        // Windows games (folders with these aren't ROMs but mark the platform)
        "exe" to "WINDOWS",
    )

    /**
     * Folder-name keywords → system, for disambiguating ambiguous file types
     * (.iso/.bin/.chd/.cue) by the folder they live in. e.g. a .chd inside a
     * folder named "PS2" is a PS2 game. Lowercase substring match.
     */
    val folderHintToSystem: Map<String, String> = mapOf(
        "ps2" to "ps2", "playstation 2" to "ps2", "playstation2" to "ps2",
        "ps1" to "ps1", "psx" to "ps1", "playstation" to "ps1",
        "psp" to "psp", "ps3" to "ps3", "vita" to "psvita",
        "dreamcast" to "dc", "dc" to "dc", "saturn" to "saturn",
        "gamecube" to "gc", "gcn" to "gc", "wii u" to "wiiu", "wiiu" to "wiiu",
        "wii" to "wii", "switch" to "switch", "3ds" to "3ds", "nds" to "ds",
        "n64" to "n64", "snes" to "snes", "nes" to "nes", "arcade" to "arcade",
        "mame" to "arcade",
    )

    /** Ambiguous disc/archive extensions that need a folder hint to classify. */
    val ambiguousDiscExt: Set<String> = setOf("iso", "chd", "cue", "bin", "img", "mdf")

    /** Maps a rom-system folder name to the platform tags used in our catalog. */
    val romSystemToPlatform: Map<String, String> = mapOf(
        "ps1" to "PS1", "ps2" to "PS2", "ps3" to "PS3", "psp" to "PSP",
        "psvita" to "PSVITA", "switch" to "SWITCH", "wiiu" to "WIIU",
        "3ds" to "N3DS", "ds" to "NDS", "gc" to "GC", "wii" to "WII",
        "n64" to "N64", "snes" to "SNES", "nes" to "NES", "gba" to "GBA",
        "gbc" to "GBC", "gb" to "GB", "genesis" to "GENESIS",
        "mastersystem" to "MASTERSYSTEM", "gamegear" to "GAMEGEAR",
        "saturn" to "SATURN", "dc" to "DC", "arcade" to "ARCADE",
    )
}

/** One system's BIOS situation: which files satisfy it, and is it mandatory. */
data class BiosRequirement(
    val system: String,
    val display: String,
    val usedBy: String,
    val filenames: List<String>,
    val required: Boolean,
)
