package io.github.mayusi.isitcompatible.autodetect

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings

/**
 * v0.9: opt-in MANAGE_EXTERNAL_STORAGE gate for the Auto-Detect tab.
 *
 * Why all-files-access and not SAF? Same reason EmuTran chose it: the AYN Odin 3
 * (and several vendor builds) reject SAF tree-grants even on user-created
 * folders, which makes silent scanning of the Emulation/ tree impossible. This
 * permission is requested ONLY when the user enters Auto-Detect and taps
 * "grant" — the rest of the app never touches it, so the app stays clean for
 * anyone who doesn't use this tab.
 *
 * On Android 10 (API 29) MANAGE_EXTERNAL_STORAGE doesn't exist; we fall back to
 * the legacy READ_EXTERNAL_STORAGE model (requestLegacyExternalStorage is set
 * in the manifest application tag for that API level).
 */
object AllFilesAccess {

    /** True if we can read the whole external store right now. */
    fun isGranted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            // API 29: legacy storage. We assume granted if the read perm is held;
            // the caller checks the runtime perm separately on that path.
            true
        }

    /**
     * Intent that opens the system "all files access" settings page for THIS app.
     * On API 30+ this is the only way to obtain MANAGE_EXTERNAL_STORAGE — there's
     * no runtime dialog. Returns null on API 29 (no such screen).
     */
    fun settingsIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** The default Emulation/ root on internal shared storage. */
    fun defaultEmulationRoot(): java.io.File =
        java.io.File(Environment.getExternalStorageDirectory(), FolderSpec.ROOT)
}
