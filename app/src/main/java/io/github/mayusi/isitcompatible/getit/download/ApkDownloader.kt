package io.github.mayusi.isitcompatible.getit.download

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Streams an APK from a URL into app-private cache storage with progress.
 *
 * App-private cache is the right home for transient APKs:
 *  - No storage permission needed.
 *  - The FileProvider in AndroidManifest exposes this dir to the installer.
 *  - Android can clean it up under pressure if we forget.
 *
 * The full Week 2 download engine will sit on top of this — adding
 * concurrency, retries with backoff, SHA256 verification, resumable
 * range requests, and a manifest-driven AppSource layer. For now the
 * goal is the smallest correct thing that proves the pipeline.
 */
@Singleton
class ApkDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Downloads [url] into the apks/ subdir of cacheDir.
     * Emits per-chunk progress so the UI can show a real bar.
     *
     * If [expectedSha256] is provided, verifies the downloaded file's SHA-256
     * against it (case-insensitive comparison). On mismatch, deletes the file
     * and emits Failed. On match or when expectedSha256 is null, emits Done.
     *
     * Returns a [Progress.Done] containing the local [File], which the
     * installer can then hand to the system installer via FileProvider.
     */
    fun download(url: String, filename: String, expectedSha256: String? = null): Flow<Progress> = flow {
        val outDir = File(context.cacheDir, "apks").apply { mkdirs() }
        val outFile = File(outDir, filename)

        val request = Request.Builder().url(url).build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(Progress.Failed("HTTP ${response.code} ${response.message}"))
                    return@use
                }
                val body = response.body ?: run {
                    emit(Progress.Failed("Empty response body"))
                    return@use
                }

                val total = body.contentLength()
                emit(Progress.Started(total))

                body.byteStream().use { input ->
                    outFile.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var downloaded = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            output.write(buf, 0, n)
                            downloaded += n
                            emit(Progress.Chunk(downloaded, total))
                        }
                    }
                }

                // Verify SHA256 if expected value provided
                if (expectedSha256 != null) {
                    val computed = computeSha256(outFile)
                    if (!computed.equals(expectedSha256, ignoreCase = true)) {
                        outFile.delete()
                        emit(Progress.Failed("SHA256 mismatch: expected $expectedSha256, got $computed"))
                        return@use
                    }
                }

                emit(Progress.Done(outFile))
            }
        } catch (t: Throwable) {
            // Catch network exceptions (UnknownHostException, SSL, cleartext
            // policy, etc.) so the coroutine surfaces a Failed state instead
            // of crashing the app.
            // BUGFIX 6a: delete any partial file left behind by a mid-download
            // failure so it is never mistaken for a complete APK on a retry.
            if (outFile.exists()) outFile.delete()
            emit(Progress.Failed(t.message ?: t.javaClass.simpleName))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Computes the SHA-256 hash of a file.
     * Reads the file in 64KB chunks and returns the hex-encoded lowercase hash.
     */
    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        file.inputStream().use { input ->
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    sealed interface Progress {
        data class Started(val totalBytes: Long) : Progress
        data class Chunk(val downloaded: Long, val totalBytes: Long) : Progress
        data class Done(val file: File) : Progress
        data class Failed(val message: String) : Progress
    }
}
