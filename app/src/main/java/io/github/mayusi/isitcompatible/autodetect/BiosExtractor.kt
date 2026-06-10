package io.github.mayusi.isitcompatible.autodetect

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton that extracts BIOS files from zip archives to the target directory.
 * All IO operations run on Dispatchers.IO.
 */
@Singleton
class BiosExtractor @Inject constructor() {

    /**
     * Extract a single entry from a zip archive to the target directory.
     * Creates parent directories as needed.
     *
     * @param archivePath Absolute path to the zip file
     * @param innerEntry Path of the entry inside the zip (e.g., "folder/file.bin")
     * @param targetDir Target directory relative to /storage/emulated/0/ (e.g., "Emulation/bios/ps2")
     * @return Result with the path to the extracted file on success
     */
    suspend fun extract(
        archivePath: String,
        innerEntry: String,
        targetDir: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val storageRoot = AllFilesAccess.defaultEmulationRoot().parentFile
                ?: throw IllegalStateException("Cannot determine storage root")
            val target = File(storageRoot, targetDir)

            // Ensure the target directory exists
            if (!target.exists()) {
                if (!target.mkdirs()) {
                    throw IllegalStateException("Failed to create target directory: $target")
                }
            }

            // Extract the entry from the zip
            val zipFile = ZipFile(archivePath)
            val entry = zipFile.getEntry(innerEntry)
                ?: throw IllegalStateException("Entry not found in zip: $innerEntry")

            val filename = File(innerEntry).name
            val outputFile = File(target, filename)

            zipFile.getInputStream(entry).use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            Log.i(TAG, "Extracted $innerEntry from $archivePath to ${outputFile.absolutePath}")
            outputFile.absolutePath
        }
    }

    private companion object {
        const val TAG = "BiosExtractor"
    }
}
