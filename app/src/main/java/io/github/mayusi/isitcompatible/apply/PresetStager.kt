package io.github.mayusi.isitcompatible.apply

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mayusi.isitcompatible.apply.templates.AzaharTemplate
import io.github.mayusi.isitcompatible.apply.templates.CemuTemplate
import io.github.mayusi.isitcompatible.apply.templates.EdenTemplate
import io.github.mayusi.isitcompatible.apply.templates.GameNativeTemplate
import io.github.mayusi.isitcompatible.apply.templates.GenericJsonTemplate
import io.github.mayusi.isitcompatible.apply.templates.NetherSx2Template
import io.github.mayusi.isitcompatible.apply.templates.PresetRenderer
import io.github.mayusi.isitcompatible.apply.templates.WinlatorTemplate
import io.github.mayusi.isitcompatible.compatdb.room.DriverDao
import io.github.mayusi.isitcompatible.compatdb.room.DriverEntity
import io.github.mayusi.isitcompatible.compatdb.room.EmulatorEntity
import io.github.mayusi.isitcompatible.compatdb.room.GameEntity
import io.github.mayusi.isitcompatible.compatdb.room.PresetEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates the apply flow: pick template → optionally download driver →
 * write config file + INSTRUCTIONS.md into the staging tree.
 */
@Singleton
class PresetStager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val driverDownloader: DriverDownloader,
    private val driverDao: DriverDao,
) {

    private val renderers: List<PresetRenderer> = listOf(
        WinlatorTemplate(),
        GameNativeTemplate(),
        NetherSx2Template(),
        EdenTemplate(),
        AzaharTemplate(),
        CemuTemplate(),
    )
    private val fallback = GenericJsonTemplate()

    suspend fun stage(
        stagingUri: Uri,
        game: GameEntity,
        emulator: EmulatorEntity,
        preset: PresetEntity,
        driver: DriverEntity?,
        // Chunk 2: verified GameNative config from the resolved guide (real flat
        // schema), or null. Forwarded to the renderer; only GameNativeTemplate
        // consumes it. Resolved by the caller (GameDetailViewModel).
        gameNativeConfigJson: String? = null,
        onProgress: (String) -> Unit,
    ): ApplyJobState = withContext(Dispatchers.IO) {
        try {
            val tree = DocumentFile.fromTreeUri(context, stagingUri)
                ?: return@withContext ApplyJobState.Error("Staging folder no longer accessible. Re-pick it in Settings.")

            val renderer = renderers.firstOrNull { emulator.id in it.emulatorIds } ?: fallback
            val rendered = renderer.render(game, emulator, preset, gameNativeConfigJson)
            val staged = mutableListOf<StagedFile>()

            onProgress("Writing config…")
            val configRel = "configs/${emulator.id}/${game.titleSlug}"
            val configFile = writeText(tree, configRel, rendered.fileName, "application/json", rendered.content)
                ?: return@withContext ApplyJobState.Error("Could not write config file to staging folder.")
            staged += StagedFile(
                label = "Config (${emulator.name})",
                displayPath = "$STAGING_BASENAME/$configRel/${rendered.fileName}",
                contentUri = configFile.uri.toString(),
            )

            var driverPath: String? = null
            if (driver != null) {
                val dl = driverDownloader.downloadIfNeeded(driver, stagingUri, onProgress)
                driverPath = when (dl) {
                    is DriverDownloader.Result.Ok -> {
                        driverDao.setInstalled(driver.id, installed = true)
                        // The downloader currently returns a relative path string;
                        // expose it as a StagedFile entry. Resolving an exact
                        // contentUri would require re-walking the tree — keep
                        // the URI as the staging root for now; the displayPath
                        // is the actionable bit users copy.
                        staged += StagedFile(
                            label = "GPU driver (${driver.name})",
                            displayPath = "$STAGING_BASENAME/${dl.relativePath}",
                            contentUri = stagingUri.toString(),
                        )
                        dl.relativePath
                    }
                    is DriverDownloader.Result.Failed -> {
                        Log.w(TAG, "Driver download skipped: ${dl.reason}")
                        null
                    }
                }
            }

            onProgress("Writing INSTRUCTIONS.txt…")
            val configRelativePath = "$configRel/${rendered.fileName}"
            val instructions = InstructionsRenderer.render(
                game = game, emulator = emulator, preset = preset,
                renderedConfig = rendered, driver = driver,
                configPath = "$STAGING_BASENAME/$configRelativePath",
                driverPath = driverPath?.let { "$STAGING_BASENAME/$it" },
            )
            // .txt (not .md): Android has no Markdown viewer, so a plain-text
            // file is the only thing every device can open by tapping it.
            val instructionsFile = writeText(tree, configRel, "INSTRUCTIONS.txt", "text/plain", instructions)
            if (instructionsFile != null) {
                staged += StagedFile(
                    label = "INSTRUCTIONS.txt",
                    displayPath = "$STAGING_BASENAME/$configRel/INSTRUCTIONS.txt",
                    contentUri = instructionsFile.uri.toString(),
                )
            }

            ApplyJobState.Done(
                instructions = instructions,
                stagingTreeUri = stagingUri.toString(),
                stagedFiles = staged,
                gameId = game.id,
                emulatorId = emulator.id,
                presetId = preset.id,
            )
        } catch (t: Throwable) {
            Log.w(TAG, "stage failed", t)
            ApplyJobState.Error(t.message ?: t.javaClass.simpleName)
        }
    }

    private fun writeText(
        tree: DocumentFile, subPath: String, fileName: String, mime: String, content: String,
    ): DocumentFile? {
        var cursor = tree
        for (seg in subPath.split('/').filter { it.isNotBlank() }) {
            cursor = cursor.findFile(seg) ?: cursor.createDirectory(seg) ?: return null
        }
        cursor.findFile(fileName)?.delete()
        val file = cursor.createFile(mime, fileName) ?: return null
        context.contentResolver.openOutputStream(file.uri)?.use { it.write(content.toByteArray()) }
            ?: return null
        return file
    }

    private companion object {
        const val TAG = "PresetStager"
        const val STAGING_BASENAME = "IsItCompatible"
    }
}
