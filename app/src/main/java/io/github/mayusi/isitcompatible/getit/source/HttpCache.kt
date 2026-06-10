package io.github.mayusi.isitcompatible.getit.source

import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ETag + cached-body store for HTTP responses we want to revalidate cheaply.
 * Used by GitHubReleasesSource so re-resolving the same repo sends If-None-Match;
 * on 304 we burn no rate-limit tokens and reuse the body from cache.
 *
 * GitHub allows 60 unauthenticated requests per hour and (critically)
 * 304 responses DO NOT count against that limit. With 30+ emulators in
 * the picker, a returning user can easily blow through 60 requests.
 * This cache makes the steady-state cost ~zero.
 *
 * Note: This is a memory-only cache (no persistence). It resets on app
 * restart but that's acceptable for getit use — each run gets fresh data.
 */
@Singleton
class HttpCache @Inject constructor() {
    private val memCache = ConcurrentHashMap<String, Entry>()

    @Serializable
    data class Entry(val etag: String, val body: String, val savedAtMs: Long)

    /** Returns the cached entry for [url], from memory. */
    suspend fun get(url: String): Entry? {
        return memCache[url]
    }

    /** Store [entry] for [url]. Overwrites any previous entry. */
    suspend fun put(url: String, entry: Entry) {
        memCache[url] = entry
    }

    /** Wipe everything. */
    suspend fun clear() {
        memCache.clear()
    }
}
