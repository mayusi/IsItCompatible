package io.github.mayusi.isitcompatible.library

/**
 * What a scan turns up. Either matched to a known DB entry (with [gameId])
 * or unmatched (just a filename we found). Unmatched entries are still useful
 * — the user can search the DB for them manually from the library.
 */
data class ScannedGame(
    val displayName: String,
    val fileName: String,
    val platformGuess: String,
    val gameId: String?,
    val sizeBytes: Long?,
)
