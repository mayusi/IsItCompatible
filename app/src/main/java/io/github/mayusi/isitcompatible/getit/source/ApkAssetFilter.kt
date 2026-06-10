package io.github.mayusi.isitcompatible.getit.source

import io.github.mayusi.isitcompatible.getit.manifest.AppEntry
import io.github.mayusi.isitcompatible.getit.manifest.SystemTag

/**
 * Picks the best asset from a list of release-asset filenames.
 *
 * Mirrors how Obtainium itself does the filtering so users see the same
 * file EmuTran installs as what Obtainium would've picked.
 *
 * For most entries we're looking for APKs. For DRIVERS entries we're
 * looking for ZIPs instead — the K11MCH1 / MrPurple666 / StevenMXZ
 * driver repos ship .zip files containing libvulkan.so + meta.json.
 *
 * Order of preference:
 *   1. Filename matches the entry's explicit apkFilterRegEx (with invert).
 *   2. If autoFilterByArch, prefer arm64-v8a / aarch64 / arm64 in the name.
 *   3. Strip out clearly-wrong arches (x86, x86_64, armeabi) if alternatives.
 *   4. First matching asset remaining.
 */
object ApkAssetFilter {

    /** Common arch tokens we look for in asset filenames. */
    private val ARM64_HINTS = listOf("arm64-v8a", "arm64v8a", "aarch64", "arm64")
    private val OTHER_ARCH_HINTS = listOf("x86_64", "x86-64", "x86", "armeabi-v7a", "armv7", "armeabi")

    /**
     * @return Pair of (filename, AssetKind), or null if nothing matched.
     */
    fun pick(assets: List<String>, entry: AppEntry): Pair<String, AssetKind>? {
        val wantsZip = entry.system == SystemTag.DRIVERS

        // Build the candidate pool. Drivers look for .zip first, fall
        // back to .apk if the repo happens to ship those too. Everything
        // else only considers .apk.
        var pool: List<String>
        var kind: AssetKind
        if (wantsZip) {
            val zips = assets.filter { it.endsWith(".zip", ignoreCase = true) }
            if (zips.isNotEmpty()) {
                pool = zips
                kind = AssetKind.DRIVER_ZIP
            } else {
                pool = assets.filter { it.endsWith(".apk", ignoreCase = true) }
                kind = AssetKind.APK
            }
        } else {
            pool = assets.filter { it.endsWith(".apk", ignoreCase = true) }
            kind = AssetKind.APK
        }
        if (pool.isEmpty()) return null

        // 1. Apply explicit filter regex if set.
        val filter = entry.apkFilterRegEx
        if (filter.isNotBlank()) {
            val regex = runCatching { Regex(filter, RegexOption.IGNORE_CASE) }.getOrNull()
            if (regex != null) {
                val matched = pool.filter { regex.containsMatchIn(it) }
                val filtered = if (entry.invertApkFilter) pool - matched.toSet() else matched
                if (filtered.isNotEmpty()) pool = filtered
            }
        }

        // 2. Arch preference (still applies to driver zips — many are
        //    suffixed _aarch64 or similar).
        if (entry.autoFilterByArch) {
            val arm64 = pool.filter { name -> ARM64_HINTS.any { it in name.lowercase() } }
            if (arm64.isNotEmpty()) return arm64.first() to kind

            val withoutWrongArch = pool.filter { name ->
                OTHER_ARCH_HINTS.none { it in name.lowercase() }
            }
            if (withoutWrongArch.isNotEmpty()) return withoutWrongArch.first() to kind
        }

        return pool.firstOrNull()?.let { it to kind }
    }
}
