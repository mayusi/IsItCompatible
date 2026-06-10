package io.github.mayusi.isitcompatible.getit.manifest

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v0.10: loads the bundled Obtainium emulator manifest (assets/getit/emulators.json)
 * and exposes it as clean [AppEntry] objects.
 *
 * The on-disk shape nests a JSON string inside JSON (Obtainium's quirk), so we
 * parse the outer envelope, then crack each entry's additionalSettings blob and
 * flatten everything into one [AppEntry]. Cached after first load.
 */
@Singleton
class EmulatorManifestRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    @Volatile private var cache: List<AppEntry>? = null

    suspend fun all(): List<AppEntry> {
        cache?.let { return it }
        return withContext(Dispatchers.IO) {
            cache?.let { return@withContext it }
            val list = runCatching {
                val raw = context.assets.open("getit/emulators.json")
                    .bufferedReader().use { it.readText() }
                val pack = json.decodeFromString(ObtainiumPackJson.serializer(), raw)
                pack.apps.map { it.toAppEntry(json) }
            }.getOrElse {
                Log.w(TAG, "Failed to load emulator manifest", it)
                emptyList()
            }
            cache = list
            Log.i(TAG, "Loaded ${list.size} emulator manifest entries")
            list
        }
    }

    /**
     * Find the manifest entry whose installed package id matches [packageId].
     * The manifest `id` is usually the Android package name (e.g.
     * org.ppsspp.ppsspp), so we match on that first, then fall back to a
     * loose contains-match for the Obtainium numeric-tracker ids.
     */
    suspend fun findByPackageId(packageId: String): AppEntry? {
        val entries = all()
        return entries.firstOrNull { it.id.equals(packageId, ignoreCase = true) }
            ?: entries.firstOrNull { it.id.contains(packageId, ignoreCase = true) }
    }

    private fun RawAppEntry.toAppEntry(json: Json): AppEntry {
        val settings = runCatching {
            json.decodeFromString(AppAdditionalSettings.serializer(), additionalSettings)
        }.getOrElse { AppAdditionalSettings() }
        val source = when {
            overrideSource?.contains("github", true) == true -> SourceKind.GITHUB
            overrideSource?.contains("gitea", true) == true -> SourceKind.GITEA
            overrideSource?.contains("html", true) == true -> SourceKind.HTML_SCRAPE
            url.contains("github.com", true) -> SourceKind.GITHUB
            url.contains("codeberg.org", true) || url.contains("gitea", true) ||
                url.contains("eden-emu.dev", true) || url.contains("citron-emu.org", true) -> SourceKind.GITEA
            else -> SourceKind.HTML_SCRAPE
        }
        return AppEntry(
            id = id,
            name = name,
            author = author,
            about = settings.about,
            sourceUrl = url,
            source = source,
            apkFilterRegEx = settings.apkFilterRegEx,
            invertApkFilter = settings.invertAPKFilter,
            autoFilterByArch = settings.autoApkFilterByArch,
            includePrereleases = settings.includePrereleases,
            fallbackToOlderReleases = settings.fallbackToOlderReleases,
            versionExtractionRegEx = settings.versionExtractionRegEx,
            filterReleaseTitlesRegEx = settings.filterReleaseTitlesByRegEx,
            categories = categories,
            trackOnly = settings.trackOnly,
            system = SystemTag.OTHER,
            recommended = false,
        )
    }

    private companion object { const val TAG = "EmuManifestRepo" }
}
