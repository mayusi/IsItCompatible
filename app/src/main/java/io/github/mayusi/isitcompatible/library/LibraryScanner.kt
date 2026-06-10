package io.github.mayusi.isitcompatible.library

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mayusi.isitcompatible.compatdb.room.GameDao
import io.github.mayusi.isitcompatible.compatdb.room.GameEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Walks SAF document trees and identifies games against the local DB.
 *
 * Two entry points:
 *  - [scanRoms] for console ROM folders (`Emulation/roms/<platform>/<game>`)
 *  - [scanPcGames] for Windows-game folders (`<root>/<gametitle>/<game>.exe`)
 *
 * Both are recursive but capped at 4 levels deep so we don't get stuck in
 * massive trees. Unmatched files are still returned (with [ScannedGame.gameId]
 * == null) so the user can see what we found and search the DB manually.
 */
@Singleton
class LibraryScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao,
) {

    suspend fun scanRoms(rootUri: Uri): List<ScannedGame> = withContext(Dispatchers.IO) {
        val all = gameDao.let { gameDaoSnapshot() }
        val identifier = GameIdentifier(all.groupBy { it.platform })
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext emptyList()
        val out = mutableListOf<ScannedGame>()
        walk(root, depth = 0, platformHint = null) { file, platformGuess ->
            val ext = file.name.orEmpty().substringAfterLast('.', "")
            val platform = platformGuess
                ?: PlatformGuess.fromExtension(ext)
                ?: return@walk
            val match = identifier.identifyRom(file.name.orEmpty(), platform)
            out += ScannedGame(
                displayName = match?.title ?: prettify(file.name.orEmpty()),
                fileName = file.name.orEmpty(),
                platformGuess = platform,
                gameId = match?.id,
                sizeBytes = file.length().takeIf { it > 0 },
            )
        }
        out
    }

    suspend fun scanPcGames(rootUri: Uri): List<ScannedGame> = withContext(Dispatchers.IO) {
        val all = gameDaoSnapshot()
        val identifier = GameIdentifier(all.groupBy { it.platform })
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext emptyList()
        val out = mutableListOf<ScannedGame>()
        walk(root, depth = 0, platformHint = "WINDOWS") { file, _ ->
            val name = file.name.orEmpty()
            if (!name.endsWith(".exe", ignoreCase = true)) return@walk
            val match = identifier.identifyPc(name)
            out += ScannedGame(
                displayName = match?.title ?: prettify(name),
                fileName = name,
                platformGuess = "WINDOWS",
                gameId = match?.id,
                sizeBytes = file.length().takeIf { it > 0 },
            )
        }
        // Deduplicate by matched gameId so multiple launchers don't double-list
        out.distinctBy { it.gameId ?: it.fileName }
    }

    private suspend fun gameDaoSnapshot(): List<GameEntity> = gameDao.all()

    private fun walk(
        dir: DocumentFile,
        depth: Int,
        platformHint: String?,
        onFile: (DocumentFile, String?) -> Unit,
    ) {
        if (depth > 4) return
        val children = try { dir.listFiles() } catch (t: Throwable) {
            Log.w(TAG, "listFiles failed at depth $depth", t); return
        }
        for (child in children) {
            if (child.isDirectory) {
                val nextHint = platformHint
                    ?: PlatformGuess.fromFolderName(child.name.orEmpty())
                walk(child, depth + 1, nextHint, onFile)
            } else if (child.isFile) {
                onFile(child, platformHint)
            }
        }
    }

    private fun prettify(raw: String): String =
        raw.substringBeforeLast('.', raw)
            .replace('_', ' ')
            .replace('.', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { raw }

    private companion object { const val TAG = "LibraryScanner" }
}
