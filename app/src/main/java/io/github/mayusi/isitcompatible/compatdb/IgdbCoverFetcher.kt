package io.github.mayusi.isitcompatible.compatdb

import android.util.Log
import io.github.mayusi.isitcompatible.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v0.7 Chunk 7.4: IGDB API client for game cover art.
 *
 * IGDB is the Twitch-owned free game database. Auth flow:
 *  1. POST id.twitch.tv/oauth2/token with our client_id + secret to mint a bearer.
 *  2. POST api.igdb.com/v4/games with a text body (IGDB's quirky query DSL),
 *     including `fields cover.image_id;` to inline the cover join.
 *  3. Build the final image URL: images.igdb.com/igdb/image/upload/t_cover_big/<image_id>.jpg
 *
 * Bearer tokens last ~60 days; we cache in-memory per-session. Worse case the
 * worker re-mints on the next daily run — cheap.
 *
 * Without credentials this class is a graceful no-op: [resolveCoverUrls] just
 * returns an empty map and logs nothing in the hot path.
 */
@Singleton
class IgdbCoverFetcher @Inject constructor(
    private val http: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var bearerToken: String? = null

    /**
     * @param titles map of (gameId -> game title) you want covers for.
     * @return map of (gameId -> coverUrl) for games IGDB returned a hit on.
     *         Missing entries simply mean "no match found" — caller leaves
     *         coverUrl null.
     */
    suspend fun resolveCoverUrls(titles: Map<String, String>): Map<String, String> = withContext(Dispatchers.IO) {
        if (!hasCredentials()) {
            // Silent — the worker logs the "skipped, no credentials" once.
            return@withContext emptyMap()
        }
        if (titles.isEmpty()) return@withContext emptyMap()

        val bearer = mintTokenIfNeeded() ?: return@withContext emptyMap()
        val out = mutableMapOf<String, String>()

        // IGDB free tier caps at 4 requests/sec. We batch one title per request
        // (cheapest match accuracy-wise) but rate-limit to 4/sec.
        for ((gameId, title) in titles) {
            val coverUrl = runCatching { lookupOne(bearer, title) }.getOrNull()
            if (coverUrl != null) out[gameId] = coverUrl
            delay(260) // ~3.8 req/s, safely under the 4/s limit
        }
        out
    }

    private fun hasCredentials(): Boolean =
        BuildConfig.IGDB_CLIENT_ID.isNotBlank() && BuildConfig.IGDB_CLIENT_SECRET.isNotBlank()

    private suspend fun mintTokenIfNeeded(): String? {
        bearerToken?.let { return it }
        val url = "https://id.twitch.tv/oauth2/token" +
            "?client_id=${BuildConfig.IGDB_CLIENT_ID}" +
            "&client_secret=${BuildConfig.IGDB_CLIENT_SECRET}" +
            "&grant_type=client_credentials"
        val req = Request.Builder().url(url).post("".toRequestBody()).build()
        return try {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "Twitch token mint failed (${resp.code}); cover sync skipped")
                    null
                } else {
                    val body = resp.body?.string() ?: return@use null
                    val tok = json.decodeFromString<TwitchTokenResponse>(body).accessToken
                    bearerToken = tok
                    tok
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Twitch token mint threw", t); null
        }
    }

    private suspend fun lookupOne(bearer: String, title: String): String? {
        // IGDB DSL: case-insensitive name match, take the top hit, inline the cover image_id.
        // Escaping quotes inside the query is just doubling them for IGDB's parser.
        val sanitized = title.replace("\"", "")
        val queryBody = """search "$sanitized"; fields name, cover.image_id; limit 1;"""
        val req = Request.Builder()
            .url("https://api.igdb.com/v4/games")
            .header("Client-ID", BuildConfig.IGDB_CLIENT_ID)
            .header("Authorization", "Bearer $bearer")
            .post(queryBody.toRequestBody("text/plain".toMediaType()))
            .build()
        return http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                if (resp.code == 401) bearerToken = null // force re-mint
                null
            } else {
                val body = resp.body?.string() ?: return@use null
                val games = runCatching {
                    json.decodeFromString<List<IgdbGame>>(body)
                }.getOrDefault(emptyList())
                games.firstOrNull()?.cover?.imageId?.let {
                    "https://images.igdb.com/igdb/image/upload/t_cover_big/$it.jpg"
                }
            }
        }
    }

    @Serializable
    private data class TwitchTokenResponse(
        @SerialName("access_token") val accessToken: String,
    )

    @Serializable
    private data class IgdbGame(
        val id: Long = 0,
        val name: String? = null,
        val cover: IgdbCover? = null,
    )

    @Serializable
    private data class IgdbCover(
        val id: Long = 0,
        @SerialName("image_id") val imageId: String? = null,
    )

    private companion object {
        const val TAG = "IgdbCoverFetcher"
    }
}
