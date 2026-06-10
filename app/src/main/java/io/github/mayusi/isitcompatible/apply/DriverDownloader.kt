package io.github.mayusi.isitcompatible.apply

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mayusi.isitcompatible.compatdb.room.DriverEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads a driver (Adreno / Turnip zip) from its GitHub Releases URL,
 * SHA-256 verifies if a hash is provided, and copies into the user's chosen
 * staging tree under `drivers/<filename>`.
 *
 * Files are first written to internal app cache (we can stream to a real file
 * and verify the hash deterministically) and then copied into the SAF tree.
 */
@Singleton
class DriverDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient,
) {

    sealed class Result {
        data class Ok(val relativePath: String) : Result()
        data class Failed(val reason: String) : Result()
    }

    suspend fun downloadIfNeeded(
        driver: DriverEntity,
        stagingRoot: Uri,
        onProgress: (String) -> Unit,
    ): Result = withContext(Dispatchers.IO) {
        if (driver.downloadUrl.isBlank()) {
            return@withContext Result.Failed("This preset uses the stock driver — no download needed.")
        }
        try {
            onProgress("Downloading ${driver.name}…")
            val tempFile = File(context.cacheDir, "driver-${driver.id}.zip")
            http.newCall(Request.Builder().url(driver.downloadUrl).build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.Failed("HTTP ${resp.code} from ${driver.downloadUrl}")
                }
                val body = resp.body ?: return@withContext Result.Failed("Empty response body")
                FileOutputStream(tempFile).use { out -> body.byteStream().copyTo(out) }
            }
            // SHA256 verification when we have one
            driver.sha256?.takeIf { it.isNotBlank() }?.let { expected ->
                onProgress("Verifying checksum…")
                val actual = sha256OfFile(tempFile)
                if (!actual.equals(expected, ignoreCase = true)) {
                    tempFile.delete()
                    return@withContext Result.Failed("SHA256 mismatch — file may be tampered.")
                }
            }
            onProgress("Copying to staging folder…")
            val outName = driver.downloadUrl.substringAfterLast('/').ifBlank { "driver-${driver.id}.zip" }
            val written = writeToStaging(stagingRoot, "drivers", outName, tempFile)
            tempFile.delete()
            if (written) Result.Ok("drivers/$outName")
            else Result.Failed("Couldn't write to staging folder. Re-pick it in Settings?")
        } catch (t: Throwable) {
            Log.w("DriverDownloader", "Failed", t)
            Result.Failed("Network error: ${t.message ?: t.javaClass.simpleName}")
        }
    }

    private fun writeToStaging(stagingRoot: Uri, subdir: String, fileName: String, src: File): Boolean {
        val tree = DocumentFile.fromTreeUri(context, stagingRoot) ?: return false
        val sub = tree.findFile(subdir) ?: tree.createDirectory(subdir) ?: return false
        // Replace any existing file with the same name
        sub.findFile(fileName)?.delete()
        val target = sub.createFile("application/zip", fileName) ?: return false
        return context.contentResolver.openOutputStream(target.uri)?.use { out ->
            src.inputStream().use { it.copyTo(out) }
            true
        } ?: false
    }

    private fun sha256OfFile(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                md.update(buf, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
