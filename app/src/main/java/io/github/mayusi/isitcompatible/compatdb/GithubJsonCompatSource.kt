package io.github.mayusi.isitcompatible.compatdb

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mayusi.isitcompatible.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Would fetch `compatdb.json` from the project's GitHub `main` branch — but the
 * community DB repo does NOT exist yet, so this source is DISABLED.
 *
 * Honesty (QW-5): we do NOT hit a placeholder URL pretending to sync against a
 * repo that isn't there. While [REMOTE_ENABLED] is false, [fetch] is a clean
 * no-op returning null — no network call, no error spam, no "syncing" theatre.
 * The caller falls back to the cached copy (none, since we never write one) and
 * ultimately the bundled seed, which is the honest state: bundled data only.
 *
 * The fetch/cache machinery is kept intact behind the flag so that flipping
 * [REMOTE_ENABLED] to true (once a real repo exists) restores live sync with no
 * further changes.
 *
 * TODO(Phase 5): create the real repo, point [REMOTE_URL] at a release-tag pin,
 * set [REMOTE_ENABLED] = true, and add ETag/If-None-Match for 304-cheap polls.
 */
@Singleton
class GithubJsonCompatSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient,
) : CompatSource {
    override val sourceTag = "OUR_GITHUB"

    override suspend fun fetch(): CompatSnapshot? = withContext(Dispatchers.IO) {
        // QW-5: the community DB repo doesn't exist yet. Short-circuit to null
        // so we never make a live request against a placeholder URL. This is the
        // honest "bundled data only" state — no remote is reached, and nothing
        // in the UI may claim a sync happened.
        if (!REMOTE_ENABLED) return@withContext null

        val req = Request.Builder().url(REMOTE_URL).build()
        try {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                // Cache to disk so the next sync after an outage keeps a snapshot around
                runCatching { cacheFile().writeText(body) }
                val dto = compatJson.decodeFromString(CompatDbDto.serializer(), body)
                dto.toSnapshot()
            }
        } catch (t: Throwable) {
            Log.w("GithubJsonCompatSource", "Fetch failed; trying cache", t)
            readCached()
        }
    }

    private fun cacheFile(): File = File(context.cacheDir, "compatdb_remote.json")

    private fun readCached(): CompatSnapshot? = runCatching {
        if (!cacheFile().exists()) return null
        val dto = compatJson.decodeFromString(CompatDbDto.serializer(), cacheFile().readText())
        dto.toSnapshot()
    }.getOrNull()

    private companion object {
        /**
         * QW-5 master switch. While false, [fetch] never makes a network call and
         * returns null — the app honestly runs on bundled data only. Flip to true
         * once the real community DB repo exists at [REMOTE_URL].
         */
        const val REMOTE_ENABLED = false

        // Placeholder repo — does NOT exist yet. Only used when REMOTE_ENABLED.
        const val REMOTE_URL =
            "https://raw.githubusercontent.com/mayusi/IsItCompatible-DB/main/compatdb.json"
    }
}
