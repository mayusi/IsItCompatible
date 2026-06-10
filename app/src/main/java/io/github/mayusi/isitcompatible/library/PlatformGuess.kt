package io.github.mayusi.isitcompatible.library

/** Heuristic mapping from a ROM file extension or folder name to a platform tag. */
internal object PlatformGuess {

    /** Maps file extension (no leading dot, lowercase) to a platform tag. */
    private val byExtension: Map<String, String> = mapOf(
        // Sony
        "iso" to "PS2", "bin" to "PS2", "chd" to "PS2", "cso" to "PSP",
        "psp" to "PSP", "ps2" to "PS2", "pbp" to "PSP",
        // Nintendo
        "nsp" to "SWITCH", "xci" to "SWITCH",
        "3ds" to "N3DS", "cia" to "N3DS", "cci" to "N3DS",
        "wbfs" to "WII", "wad" to "WII", "rvz" to "WII",
        "wud" to "WIIU", "wux" to "WIIU", "rpx" to "WIIU",
        "nds" to "NDS",
        "gba" to "GBA", "gb" to "GB", "gbc" to "GBC",
        "n64" to "N64", "z64" to "N64", "v64" to "N64",
        "smc" to "SNES", "sfc" to "SNES",
        "nes" to "NES", "fds" to "NES",
        "gen" to "GENESIS", "md" to "GENESIS", "smd" to "GENESIS",
        // Sega
        "gdi" to "DC", "cdi" to "DC",
    )

    /** Maps folder name (lowercased) to a platform tag — used when the user organises by system. */
    private val byFolder: Map<String, String> = mapOf(
        "ps2" to "PS2", "ps1" to "PS1", "psp" to "PSP", "psvita" to "PSVITA",
        "switch" to "SWITCH", "3ds" to "N3DS", "ds" to "NDS",
        "wii" to "WII", "wiiu" to "WIIU", "gc" to "GC", "gamecube" to "GC",
        "n64" to "N64", "snes" to "SNES", "nes" to "NES",
        "gba" to "GBA", "gb" to "GB", "gbc" to "GBC",
        "dc" to "DC", "dreamcast" to "DC",
        "genesis" to "GENESIS", "megadrive" to "GENESIS",
    )

    fun fromExtension(ext: String): String? = byExtension[ext.lowercase()]
    fun fromFolderName(folder: String): String? = byFolder[folder.lowercase()]
}
