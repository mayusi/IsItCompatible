package io.github.mayusi.isitcompatible.getit

import android.util.Log
import io.github.mayusi.isitcompatible.BuildConfig
import io.github.mayusi.isitcompatible.data.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Self-update checker for Is It Compatible?.
 *
 * Hits https://api.github.com/repos/mayusi/IsItCompatible/releases/latest,
 * compares the release tag against [BuildConfig.VERSION_NAME], and (when an
 * update is available) writes the pending-update prefs so the UI can surface
 * a banner + install flow.
 *
 * Key design decisions:
 *  - DEBUG build → always returns [UpdateCheckResult.UpToDate] immediately.
 *    The debug APK has a different applicationId (.debug suffix) so installing
 *    it over itself would fail at the OS level anyway.  The manual-check path
 *    in [AppUpdateViewModel.checkNow] has the same early-return, so even a
 *    forced manual check on debug won't show a false update. To test the full
 *    flow on a debug build, temporarily remove the DEBUG guard and set the
 *    installed version lower than the latest release tag.
 *  - 404 from GitHub means the repo has ZERO published releases (the current
 *    situation).  This is a valid, expected state — [UpdateCheckResult.NoReleasesYet]
 *    is a silent no-op; nothing is shown to the user.
 *  - In-memory ETag cache so repeat checks within a session don't burn
 *    GitHub's 60-req/hr unauthenticated rate limit.
 */
@Singleton
class AppUpdateChecker @Inject constructor(
    private val http: OkHttpClient,
    private val prefs: UserPreferences,
) {

    private val json = Json { ignoreUnknownKeys = true }

    // In-memory ETag cache (resets on process restart, which is fine).
    // BUGFIX: stored as an atomic pair so concurrent coroutines (checkIfDue +
    // manual checkNow) always see a consistent etag+body snapshot and can never
    // observe one updated without the other (race that produced "304 but no
    // cached body").
    private val etagCache = AtomicReference<Pair<String, String>?>(null)

    companion object {
        private const val TAG = "AppUpdateChecker"
        private const val OWNER_REPO = "mayusi/IsItCompatible"
        private const val RELEASES_LATEST_URL =
            "https://api.github.com/repos/$OWNER_REPO/releases/latest"
        private const val CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L // 12 hours
    }

    /**
     * Performs one check against GitHub. Returns a [UpdateCheckResult] describing
     * what was found.  Never throws; errors are wrapped in [UpdateCheckResult.Failed].
     */
    suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        // Self-update is meaningless on the debug build (different package id,
        // different signing key).  Skip silently so the checker never triggers
        // the update flow on a dev device.
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "DEBUG build — skipping self-update check")
            return@withContext UpdateCheckResult.UpToDate
        }

        val cached = etagCache.get()
        val reqBuilder = Request.Builder()
            .url(RELEASES_LATEST_URL)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "IsItCompatible-app/${BuildConfig.VERSION_NAME}")
        cached?.let { reqBuilder.header("If-None-Match", it.first) }

        try {
            http.newCall(reqBuilder.build()).execute().use { response ->
                when (response.code) {
                    304 -> {
                        // Cache hit — parse the body we saved last time.
                        // Read the snapshot atomically: if it's null here the race
                        // that cleared it between our send and the 304 is extremely
                        // unlikely but handled gracefully.
                        val body = etagCache.get()?.second
                            ?: return@withContext UpdateCheckResult.Failed("304 but no cached body")
                        parseRelease(body)
                    }
                    404 -> {
                        // Repo exists but has no releases yet.
                        Log.d(TAG, "No releases found for $OWNER_REPO (404)")
                        UpdateCheckResult.NoReleasesYet
                    }
                    200 -> {
                        val body = response.body?.string()
                            ?: return@withContext UpdateCheckResult.Failed("Empty response body")
                        // Store ETag + body atomically so concurrent readers always
                        // see a consistent pair (never an updated etag with a stale body).
                        response.header("ETag")?.let { etag ->
                            etagCache.set(etag to body)
                        }
                        parseRelease(body)
                    }
                    else -> UpdateCheckResult.Failed("GitHub returned HTTP ${response.code}")
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Update check failed", t)
            UpdateCheckResult.Failed(t.message ?: t.javaClass.simpleName)
        }
    }

    /**
     * Debounced entry point called from Application.onCreate.
     *
     * Reads [UserPreferences] to decide whether a check is due (>12h since
     * last check and auto-check is enabled), runs [check], stamps the timestamp,
     * and writes pendingUpdate* prefs if an update was found.
     */
    suspend fun checkIfDue() {
        val snap = prefs.data.first()
        if (!snap.updateAutoCheckEnabled) return
        val now = System.currentTimeMillis()
        if (snap.lastUpdateCheckMs > 0 && (now - snap.lastUpdateCheckMs) < CHECK_INTERVAL_MS) return

        Log.d(TAG, "Scheduled update check running…")
        val result = check()
        prefs.setLastUpdateCheck(now)

        if (result is UpdateCheckResult.UpdateAvailable) {
            prefs.setPendingUpdate(
                version = result.version,
                url = result.apkUrl,
                filename = result.apkFilename,
                notes = result.patchNotes,
                sizeBytes = result.apkSizeBytes,
                sha256 = result.sha256,
            )
            Log.i(TAG, "Update available: ${result.version} sha256=${result.sha256 ?: "none"}")
        }
    }

    // ── private helpers ────────────────────────────────────────────────────────

    private fun parseRelease(body: String): UpdateCheckResult {
        val release = runCatching { json.decodeFromString<GhRelease>(body) }.getOrElse {
            return UpdateCheckResult.Failed("JSON parse error: ${it.message}")
        }

        if (release.draft || release.prerelease) {
            Log.d(TAG, "Latest release is draft/prerelease — treating as up-to-date")
            return UpdateCheckResult.UpToDate
        }

        val tag = release.tagName ?: return UpdateCheckResult.UpToDate
        val releaseVersion = normalizeVersion(tag)
        val installedVersion = normalizeVersion(BuildConfig.VERSION_NAME)

        if (!isNewer(releaseVersion, installedVersion)) {
            return UpdateCheckResult.UpToDate
        }

        // Pick APK asset: prefer arm64-v8a / arm64 in the name, fall back to first .apk.
        val apkAssets = release.assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        if (apkAssets.isEmpty()) {
            return UpdateCheckResult.Failed("Release $tag has no .apk assets")
        }
        val ARM64_HINTS = listOf("arm64-v8a", "arm64v8a", "aarch64", "arm64")
        val picked = apkAssets.firstOrNull { asset ->
            ARM64_HINTS.any { it in asset.name.lowercase() }
        } ?: apkAssets.first()

        // Extract SHA256: try release body first (primary), then a .sha256 asset (fallback).
        // Release body format: a line containing "SHA256: <64-hex>" (case-insensitive).
        val sha256 = parseSha256FromBody(release.body)
            ?: findSha256Asset(release.assets, picked.name)

        if (sha256 == null) {
            Log.w(TAG, "No SHA256 found for release $tag — will verify signature only")
        } else {
            Log.d(TAG, "SHA256 for ${picked.name}: $sha256")
        }

        return UpdateCheckResult.UpdateAvailable(
            version = tag,
            releaseTitle = release.name ?: tag,
            patchNotes = release.body ?: "",
            apkUrl = picked.browserDownloadUrl,
            apkFilename = picked.name,
            apkSizeBytes = picked.size,
            sha256 = sha256,
        )
    }

    /**
     * Parses a SHA256 hex string from the release body.
     * Looks for a line matching "SHA256: <64-hex>" (case-insensitive, leading whitespace ok).
     */
    private fun parseSha256FromBody(body: String?): String? {
        if (body.isNullOrBlank()) return null
        val regex = Regex("""(?i)SHA256:\s*([0-9a-fA-F]{64})""")
        return regex.find(body)?.groupValues?.get(1)?.lowercase()
    }

    /**
     * Looks for a release asset named "<apkName>.sha256" and would download it to
     * read the hash. For simplicity in this implementation, we detect the asset and
     * return its download URL embedded in a sentinel — callers handle the actual
     * fetch. Here we return null since the body-parse path is the primary approach;
     * a future iteration can download the .sha256 asset via HTTP.
     *
     * For now: detect presence and log it. The body-parse is the primary channel.
     */
    private fun findSha256Asset(assets: List<GhAsset>, apkName: String): String? {
        val sha256Asset = assets.firstOrNull {
            it.name.equals("$apkName.sha256", ignoreCase = true)
        }
        if (sha256Asset != null) {
            Log.d(TAG, "Found .sha256 asset: ${sha256Asset.name} at ${sha256Asset.browserDownloadUrl}")
            // Future: download sha256Asset.browserDownloadUrl and read the hex.
            // For now, note presence but return null (body-parse is the primary path).
        }
        return null
    }

    /**
     * Strips a leading v/V prefix and returns a list of integer components.
     * "v1.2.3" → [1, 2, 3].  Non-numeric parts (like "-rc1") are ignored.
     */
    private fun normalizeVersion(raw: String): List<Int> {
        return raw.trimStart('v', 'V')
            .split('.')
            .mapNotNull { part -> part.filter { it.isDigit() }.toIntOrNull() }
    }

    /**
     * Returns true if [candidate] is strictly newer than [current] using
     * lexicographic integer-tuple comparison (1.2.3 > 1.2.0, etc.).
     */
    private fun isNewer(candidate: List<Int>, current: List<Int>): Boolean {
        val maxLen = maxOf(candidate.size, current.size)
        for (i in 0 until maxLen) {
            val c = candidate.getOrElse(i) { 0 }
            val r = current.getOrElse(i) { 0 }
            if (c > r) return true
            if (c < r) return false
        }
        return false // equal
    }

}

/** Result type returned by [AppUpdateChecker.check]. */
sealed interface UpdateCheckResult {
    /** Installed version is current. */
    data object UpToDate : UpdateCheckResult

    /** An update is available. */
    data class UpdateAvailable(
        val version: String,
        val releaseTitle: String,
        val patchNotes: String,
        val apkUrl: String,
        val apkFilename: String,
        val apkSizeBytes: Long,
        /** SHA-256 hex of the APK, parsed from the release body or .sha256 asset. Null if unpublished. */
        val sha256: String? = null,
    ) : UpdateCheckResult

    /** The GitHub repo has zero published releases (expected early-lifecycle state). */
    data object NoReleasesYet : UpdateCheckResult

    /** Network error, JSON parse error, or unexpected HTTP status. */
    data class Failed(val reason: String) : UpdateCheckResult
}
