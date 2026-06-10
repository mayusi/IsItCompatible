package io.github.mayusi.isitcompatible.compatdb

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v0.6: queries K11MCH1/AdrenoToolsDrivers GitHub Releases and returns the
 * latest tag per known driver family.
 *
 * Mapping strategy: the upstream tag names follow conventions like
 * `Turnip-v25.1.0_R2` or `Turnip-mr31.p_R1`. We map those into our
 * internal driver-id buckets:
 *
 *  - `Turnip-v*`   → "turnip-NN.M.X" (the latest stable Turnip)
 *  - `Turnip-mr*`  → "turnip-mrXX" (developer builds)
 *
 * The fetcher never writes to Room itself — it's a pure data fetch.
 * [DriverSyncWorker] does the persistence so we can swap fetch strategies
 * (REST vs Atom feed vs scraping) without touching the worker.
 */
@Singleton
class DriverFetcher @Inject constructor(
    private val http: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @return latest upstream tag for each driver family, keyed by tag prefix.
     *         Empty map on network failure (caller treats as "no update info").
     */
    suspend fun fetchLatest(): Map<DriverFamily, String> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(RELEASES_URL)
            // GitHub strongly recommends a UA — anonymous calls without one
            // get rate-limited more aggressively.
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "IsItCompatible-app")
            .build()
        try {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "GitHub releases returned ${resp.code}")
                    return@withContext emptyMap()
                }
                val body = resp.body?.string() ?: return@withContext emptyMap()
                val releases = json.decodeFromString<List<GitHubRelease>>(body)
                pickLatestPerFamily(releases)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Driver release fetch failed", t)
            emptyMap()
        }
    }

    /**
     * Walk the release list in descending order (GitHub returns newest first)
     * and grab the first match per family. Drafts and prereleases are skipped.
     */
    private fun pickLatestPerFamily(releases: List<GitHubRelease>): Map<DriverFamily, String> {
        val out = mutableMapOf<DriverFamily, String>()
        for (r in releases) {
            if (r.draft || r.prerelease) continue
            val tag = r.tagName ?: continue
            val family = classify(tag) ?: continue
            // First-seen wins because GitHub orders newest-first.
            out.getOrPut(family) { tag }
        }
        return out
    }

    /**
     * Classify an upstream tag name into one of our known families. Returns
     * null for tag patterns we don't recognise.
     *
     * The K11MCH1 repo currently mixes two driver families:
     *  - Mesa **Turnip** (open-source Vulkan driver) — tags like
     *    "v26.0.0-rc08", "v25.3.0-rc.11", "v24.3.0_R6". Anything matching
     *    \d+\.\d+(\.\d+)?(_R\d+)?(-rc\.?\d+)?$ counts.
     *  - **Qualcomm proprietary** drivers — tags like "v840", "v837", "v842.6".
     *    Numeric-only tags. We track these separately because they don't pair
     *    with Adreno older than 7xx.
     */
    private fun classify(tag: String): DriverFamily? {
        // Strip leading "v" so the rest is the version string.
        val core = tag.removePrefix("v").removePrefix("V")
        return when {
            // Qualcomm proprietary: pure-numeric like "840" or "842.6". A version
            // that's all digits + optional minor (1-or-2 segments) and never has
            // a third component or rc tag.
            QUALCOMM_REGEX.matches(core) -> DriverFamily.QUALCOMM_STABLE
            // Turnip: anything with at least three numeric components or with -rc
            TURNIP_REGEX.matches(core) -> DriverFamily.TURNIP_STABLE
            else -> null
        }
    }

    /** Driver families we know how to track upstream. */
    enum class DriverFamily {
        /** Mesa Turnip open-source Vulkan driver, including release candidates. */
        TURNIP_STABLE,
        /** Qualcomm proprietary Adreno blobs (recent numeric tags). */
        QUALCOMM_STABLE,
    }

    private companion object {
        const val TAG = "DriverFetcher"
        const val RELEASES_URL =
            "https://api.github.com/repos/K11MCH1/AdrenoToolsDrivers/releases?per_page=30"

        // Qualcomm: 1-3 digit major[.minor], no third segment, no rc suffix.
        // Matches "840", "842.6". Does NOT match "26.0.0-rc08" because that has a third dot-segment.
        val QUALCOMM_REGEX = Regex("""^\d{2,4}(\.\d+)?$""")

        // Turnip: at least three numeric components OR has -rc / _R suffix.
        // Matches "26.0.0-rc08", "25.3.0", "24.3.0_R6".
        val TURNIP_REGEX = Regex("""^\d+\.\d+(\.\d+)?([._-][Rr]?[Cc]?\.?\d+)?$""")
    }

    @Serializable
    private data class GitHubRelease(
        @SerialName("tag_name") val tagName: String? = null,
        @SerialName("name") val name: String? = null,
        @SerialName("draft") val draft: Boolean = false,
        @SerialName("prerelease") val prerelease: Boolean = false,
    )
}
