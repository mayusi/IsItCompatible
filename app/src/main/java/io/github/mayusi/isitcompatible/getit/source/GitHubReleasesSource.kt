package io.github.mayusi.isitcompatible.getit.source

import io.github.mayusi.isitcompatible.getit.manifest.AppEntry
import io.github.mayusi.isitcompatible.getit.manifest.SourceKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves an [AppEntry] backed by github.com → an APK URL by hitting
 * api.github.com/repos/{owner}/{repo}/releases/latest, parsing the
 * `assets[]` array, and running [ApkAssetFilter] against the names.
 *
 * Why we DON'T use the redirect URL shortcut: a few entries in the pack
 * use unusual asset names that github.com/owner/repo/releases/latest
 * /download/{filename} doesn't know about. Going through the API
 * guarantees correctness at the cost of one extra request per app.
 *
 * Rate-limit story: unauthenticated GitHub allows 60 calls/hour.
 * v0.x just accepts the limit. A future iteration will:
 *   - Cache ETag and send If-None-Match (304s don't count).
 *   - Optionally accept a user-supplied PAT in Settings.
 */
@Singleton
class GitHubReleasesSource @Inject constructor(
    private val cache: HttpCache,
) : AppSource {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun resolve(entry: AppEntry): ResolveResult = withContext(Dispatchers.IO) {
        if (entry.source != SourceKind.GITHUB) return@withContext ResolveResult.Unsupported

        val ownerRepo = parseOwnerRepo(entry.sourceUrl)
            ?: return@withContext ResolveResult.Failed("Not a github.com/owner/repo URL: ${entry.sourceUrl}")

        // /releases/latest excludes prereleases. If the entry wants
        // prereleases or fallback, we use the broader /releases endpoint
        // and pick the first match ourselves.
        val needsBroaderList = entry.includePrereleases || entry.fallbackToOlderReleases
        val endpoint = if (needsBroaderList) {
            "https://api.github.com/repos/$ownerRepo/releases?per_page=10"
        } else {
            "https://api.github.com/repos/$ownerRepo/releases/latest"
        }

        // Send If-None-Match if we've seen this URL before. GitHub 304s
        // don't count against the 60/hr rate limit, so the steady-state
        // cost of resolving many emulators repeatedly is near zero.
        val cached = cache.get(endpoint)
        val request = Request.Builder()
            .url(endpoint)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .apply { cached?.let { header("If-None-Match", it.etag) } }
            .build()

        try {
            client.newCall(request).execute().use { response ->
                // 304 Not Modified -> reuse cached body. Saves a rate-limit token.
                if (response.code == 304 && cached != null) {
                    return@withContext parseAndPick(cached.body, entry, ownerRepo, needsBroaderList)
                }
                if (!response.isSuccessful) {
                    return@withContext ResolveResult.Failed("GitHub ${response.code} on $ownerRepo")
                }
                val body = response.body?.string()
                    ?: return@withContext ResolveResult.Failed("Empty body from $ownerRepo")

                // Save the ETag if GitHub gave one — they always do.
                response.header("ETag")?.let { etag ->
                    cache.put(endpoint, HttpCache.Entry(etag, body, System.currentTimeMillis()))
                }

                parseAndPick(body, entry, ownerRepo, needsBroaderList)
            }
        } catch (t: Throwable) {
            ResolveResult.Failed(t.message ?: t.javaClass.simpleName)
        }
    }

    /** Shared body parser used by both fresh and 304-served paths. */
    private fun parseAndPick(
        body: String,
        entry: AppEntry,
        ownerRepo: String,
        broaderList: Boolean,
    ): ResolveResult {
        val releases: List<GhRelease> = if (broaderList) {
            json.decodeFromString(body)
        } else {
            listOf(json.decodeFromString<GhRelease>(body))
        }

        for (release in releases) {
            if (!entry.includePrereleases && release.prerelease) continue
            val assetNames = release.assets.map { it.name }
            val picked = ApkAssetFilter.pick(assetNames, entry) ?: continue
            val (pickedName, kind) = picked
            val asset = release.assets.first { it.name == pickedName }
            return ResolveResult.Found(
                apkUrl = asset.browserDownloadUrl,
                filename = asset.name,
                version = release.tagName ?: release.name ?: "unknown",
                sizeBytes = asset.size,
                kind = kind,
            )
        }
        return ResolveResult.Failed(
            "No matching asset across ${releases.size} release(s) for $ownerRepo"
        )
    }

    /** Pulls "owner/repo" from a github URL, tolerating trailing slashes / .git. */
    private fun parseOwnerRepo(url: String): String? {
        val regex = Regex("""github\.com/([^/]+)/([^/?#.]+)""", RegexOption.IGNORE_CASE)
        val match = regex.find(url) ?: return null
        return "${match.groupValues[1]}/${match.groupValues[2]}"
    }

    @Serializable
    private data class GhRelease(
        @kotlinx.serialization.SerialName("tag_name") val tagName: String? = null,
        val name: String? = null,
        val prerelease: Boolean = false,
        val draft: Boolean = false,
        val assets: List<GhAsset> = emptyList(),
    )

    @Serializable
    private data class GhAsset(
        val name: String,
        val size: Long = 0,
        @kotlinx.serialization.SerialName("browser_download_url") val browserDownloadUrl: String,
    )
}
