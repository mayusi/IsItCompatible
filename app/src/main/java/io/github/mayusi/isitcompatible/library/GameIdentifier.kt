package io.github.mayusi.isitcompatible.library

import io.github.mayusi.isitcompatible.compatdb.room.GameEntity

/**
 * Best-effort filename → known-game match.
 *
 * Strategy:
 *  - For Windows games: look for [GameEntity.exeHints] substrings inside the
 *    candidate filename (lowercased).
 *  - For ROMs: look for [GameEntity.romHints] substrings inside the filename
 *    (lowercased, after stripping common ROM noise like brackets and tags).
 *
 * Pure, no I/O, easy to unit-test.
 */
class GameIdentifier(private val gamesByPlatform: Map<String, List<GameEntity>>) {

    fun identifyPc(fileName: String): GameEntity? {
        val needle = fileName.lowercase()
        val candidates = gamesByPlatform["WINDOWS"] ?: return null
        // Two-pass: exact exe hint, then any hint anywhere in the name.
        for (g in candidates) {
            val hints = g.exeHints?.split('|').orEmpty()
            if (hints.any { it.isNotBlank() && it == needle }) return g
        }
        for (g in candidates) {
            val hints = g.exeHints?.split('|').orEmpty()
            if (hints.any { it.isNotBlank() && it in needle }) return g
        }
        return null
    }

    fun identifyRom(fileName: String, platformGuess: String): GameEntity? {
        val needle = scrubRomName(fileName)
        val candidates = gamesByPlatform[platformGuess] ?: return null
        for (g in candidates) {
            val hints = g.romHints?.split('|').orEmpty()
            if (hints.any { it.isNotBlank() && it in needle }) return g
        }
        // Last resort — match on slug fragments
        for (g in candidates) {
            if (g.titleSlug.replace('-', ' ') in needle) return g
        }
        return null
    }

    private fun scrubRomName(raw: String): String =
        raw.lowercase()
            .substringBeforeLast('.')
            .replace(Regex("\\[[^]]*]"), " ")
            .replace(Regex("\\([^)]*\\)"), " ")
            .replace('_', ' ')
            .replace('.', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
}
