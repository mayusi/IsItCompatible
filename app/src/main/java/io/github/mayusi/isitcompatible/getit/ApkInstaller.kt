package io.github.mayusi.isitcompatible.getit

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * Shared helper: hands an APK file to the system package installer via
 * FileProvider + ACTION_VIEW. Used by both [EmulatorInstaller] (emulator
 * APKs) and [io.github.mayusi.isitcompatible.ui.appupdate.AppUpdateViewModel]
 * (self-update APK).
 *
 * Returns [Result.success(Unit)] when the installer activity was started,
 * or [Result.failure] (with the underlying exception) when it couldn't be
 * launched.
 */
fun launchApkInstaller(context: Context, apkFile: File): Result<Unit> = runCatching {
    val uri: Uri = FileProvider.getUriForFile(
        context, "${context.packageName}.fileprovider", apkFile,
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
