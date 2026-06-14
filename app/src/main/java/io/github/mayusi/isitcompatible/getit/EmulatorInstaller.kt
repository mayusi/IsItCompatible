package io.github.mayusi.isitcompatible.getit

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mayusi.isitcompatible.getit.download.ApkDownloader
import io.github.mayusi.isitcompatible.getit.manifest.EmulatorManifestRepository
import io.github.mayusi.isitcompatible.getit.source.AppSourceRouter
import io.github.mayusi.isitcompatible.getit.source.AssetKind
import io.github.mayusi.isitcompatible.getit.source.ResolveResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emitAll
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v0.10: end-to-end "Get it" flow for an emulator.
 *
 *   packageId → manifest entry → AppSourceRouter.resolve → ApkDownloader.download
 *   → FileProvider content URI → ACTION_INSTALL_PACKAGE intent
 *
 * Emits [InstallProgress] so the UI can show resolve / download% / ready /
 * failed. The final step opens the system installer — the user taps "Install"
 * there (we never silently install; that's intentional and keeps us off the
 * Shizuku/root path EmuTran uses).
 */
@Singleton
class EmulatorInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val manifest: EmulatorManifestRepository,
    private val router: AppSourceRouter,
    private val downloader: ApkDownloader,
) {

    /**
     * @param packageId the installed-or-target Android package id (from our
     *        catalog's emulator.packageId).
     * @param displayName for nicer progress messages.
     */
    fun install(packageId: String, displayName: String): Flow<InstallProgress> = flow {
        emit(InstallProgress.Resolving(displayName))

        val entry = manifest.findByPackageId(packageId)
        if (entry == null) {
            emit(InstallProgress.Failed("$displayName isn't in the download list yet — grab it from its official page."))
            return@flow
        }

        val resolved = when (val r = router.resolve(entry)) {
            is ResolveResult.Found -> r
            is ResolveResult.Failed -> {
                emit(InstallProgress.Failed("Couldn't find a download: ${r.reason}"))
                return@flow
            }
            ResolveResult.Unsupported -> {
                emit(InstallProgress.Failed("This source isn't supported automatically — use the official page."))
                return@flow
            }
        }

        emit(InstallProgress.Downloading(displayName, 0))
        var localFile: java.io.File? = null
        emitAll(
            downloader.download(resolved.apkUrl, resolved.filename, resolved.sha256).let { dlFlow ->
                flow {
                    dlFlow.collect { p ->
                        when (p) {
                            is ApkDownloader.Progress.Started -> emit(InstallProgress.Downloading(displayName, 0))
                            is ApkDownloader.Progress.Chunk -> {
                                val pct = if (p.totalBytes > 0) ((p.downloaded * 100) / p.totalBytes).toInt() else 0
                                emit(InstallProgress.Downloading(displayName, pct))
                            }
                            is ApkDownloader.Progress.Done -> { localFile = p.file }
                            is ApkDownloader.Progress.Failed -> {
                                val userMsg = if (p.message.startsWith("SHA256 mismatch")) {
                                    "This download couldn't be verified and was not installed, to keep you safe. " +
                                        "The file's checksum didn't match the published value."
                                } else {
                                    p.message
                                }
                                emit(InstallProgress.Failed(userMsg))
                            }
                        }
                    }
                }
            }
        )

        val file = localFile ?: return@flow // a Failed was already emitted

        // Signature pinning: only APKs need the cert check. Driver ZIPs go to staging.
        if (resolved.kind == AssetKind.APK) {
            val sigResult = SignatureVerifier.verifyApkSignature(
                context = context,
                apkPath = file.absolutePath,
                expectedPackageName = packageId,
                isSelfUpdate = false,
            )
            when (sigResult) {
                is SignatureVerifier.VerifyResult.Mismatch -> {
                    file.delete()
                    emit(
                        InstallProgress.Failed(
                            "This update couldn't be verified and was not installed, to keep you safe. " +
                                "$displayName's signing certificate changed — this could indicate a tampered download."
                        )
                    )
                    return@flow
                }
                is SignatureVerifier.VerifyResult.CannotVerify -> {
                    if (sigResult.fresh) {
                        // Package not installed yet — no baseline. Proceed with a note.
                        Log.i(TAG, "Fresh install of $packageId — no cert baseline, proceeding.")
                    } else {
                        // APK cert unreadable — proceed but log the anomaly.
                        Log.w(TAG, "Could not read cert from downloaded APK for $packageId: ${sigResult.reason}")
                    }
                }
                SignatureVerifier.VerifyResult.Ok -> { /* proceed */ }
            }
        }

        // Hand off to the system installer via FileProvider.
        val result = launchApkInstaller(context, file)
        if (result.isSuccess) emit(InstallProgress.ReadyToInstall(displayName))
        else {
            Log.w(TAG, "Installer launch failed", result.exceptionOrNull())
            emit(InstallProgress.Failed("Downloaded, but couldn't open the installer. File is at ${file.name}."))
        }
    }.flowOn(Dispatchers.IO)

    private companion object { const val TAG = "EmulatorInstaller" }
}

/** UI-facing progress for the Get-it flow. */
sealed interface InstallProgress {
    data class Resolving(val name: String) : InstallProgress
    data class Downloading(val name: String, val percent: Int) : InstallProgress
    data class ReadyToInstall(val name: String) : InstallProgress
    data class Failed(val message: String) : InstallProgress
}
