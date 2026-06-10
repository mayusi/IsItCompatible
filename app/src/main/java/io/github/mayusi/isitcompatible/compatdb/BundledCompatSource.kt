package io.github.mayusi.isitcompatible.compatdb

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mayusi.isitcompatible.compatdb.room.DriverEntity
import io.github.mayusi.isitcompatible.compatdb.room.EmulatorEntity
import io.github.mayusi.isitcompatible.compatdb.room.GameEntity
import io.github.mayusi.isitcompatible.compatdb.room.GuideEntity
import io.github.mayusi.isitcompatible.compatdb.room.PresetEntity
import io.github.mayusi.isitcompatible.compatdb.room.ReportEntity
import io.github.mayusi.isitcompatible.hardware.SocGpuPairings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads every `.json` file under `assets/seed/` baked into the APK and merges them.
 *
 * Why split? With 600+ games + 2500+ reports, one monolithic seed file
 * would be a maintenance nightmare. Per-platform files (windows.json,
 * ps2.json, switch.json, etc.) plus a shared catalog.json with emulators,
 * drivers, presets keep each file bounded and contributors can update one
 * platform without conflicting on the others.
 *
 * All seed files use the same [CompatDbDto] schema. Empty arrays are fine —
 * a windows.json that only contains games + reports (no emulators) is valid.
 */
@Singleton
class BundledCompatSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : CompatSource {
    override val sourceTag = "BUNDLED"

    override suspend fun fetch(): CompatSnapshot? = withContext(Dispatchers.IO) {
        // v0.4: walk seed/ recursively so seed/games/*.json, seed/reports/*.json,
        // seed/enrichment/*.json are all picked up. Order: top-level files first
        // (catalog.json must load before reports that reference its preset ids),
        // then subdirs alphabetically — gives us a stable load order.
        // v0.7: wrapped in Dispatchers.IO so the asset walk + JSON parse never
        // blocks the calling coroutine's dispatcher (often Main from Application).
        val jsonPaths = mutableListOf<String>()
        collectJsonAssets("seed", jsonPaths)

        if (jsonPaths.isEmpty()) {
            return@withContext readLegacySingleFile()
        }

        val games = LinkedHashMap<String, GameEntity>()
        val emulators = LinkedHashMap<String, EmulatorEntity>()
        val presets = LinkedHashMap<String, PresetEntity>()
        val reports = LinkedHashMap<String, ReportEntity>()
        val drivers = LinkedHashMap<String, DriverEntity>()
        val guides = LinkedHashMap<String, GuideEntity>()

        for (path in jsonPaths) {
            try {
                val raw = context.assets.open(path).bufferedReader().use { it.readText() }
                val dto = compatJson.decodeFromString(CompatDbDto.serializer(), raw)
                val snap = dto.toSnapshot()
                snap.games.forEach { games[it.id] = it }
                snap.emulators.forEach { emulators[it.id] = it }
                snap.presets.forEach { presets[it.id] = it }
                snap.reports.forEach { reports[it.id] = it }
                snap.drivers.forEach { drivers[it.id] = it }
                snap.guides.forEach { guides[it.id] = it }
                Log.i(TAG, "Loaded $path: ${snap.games.size} games, ${snap.reports.size} reports, ${snap.guides.size} guides")
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to parse $path (skipping)", t)
            }
        }

        Log.i(TAG, "Bundled seed totals: ${games.size} games, ${emulators.size} emulators, " +
            "${presets.size} presets, ${reports.size} reports, ${drivers.size} drivers, ${guides.size} guides")

        // Validate every report's device fingerprint against the SoC/GPU pairing table.
        // Logs warnings only — never blocks the load — but catches "Snapdragon 8 Elite +
        // Adreno 750" style data bugs early.
        var mismatchCount = 0
        for (r in reports.values) {
            SocGpuPairings.validate(r.socFamily, r.gpuModel)?.let { warning ->
                if (mismatchCount < 10) {
                    Log.w(TAG, "Report ${r.id} (game=${r.gameId}): $warning")
                }
                mismatchCount++
            }
        }
        if (mismatchCount > 0) {
            Log.w(TAG, "GPU validation found $mismatchCount mismatched device fingerprint(s) in seed.")
        }

        return@withContext CompatSnapshot(
            games = games.values.toList(),
            emulators = emulators.values.toList(),
            presets = presets.values.toList(),
            reports = reports.values.toList(),
            drivers = drivers.values.toList(),
            guides = guides.values.toList(),
        )
    }

    /**
     * Recursive walk of assets/seed. Android's AssetManager.list(path) returns
     * a mixed list of files and subdirectories (no way to tell apart up front),
     * so we try to open each as a JSON file; entries that turn out to be
     * directories get list()-ed in turn.
     *
     * Output is ordered: top-level files first (e.g. catalog.json with
     * emulators+presets), then subdirs alphabetically. This guarantees that
     * by the time the reports subdir loads and references preset/emulator ids,
     * those have already been merged.
     */
    private fun collectJsonAssets(dir: String, out: MutableList<String>) {
        val entries = try {
            context.assets.list(dir)?.sorted().orEmpty()
        } catch (t: Throwable) {
            Log.w(TAG, "Couldn't list $dir", t); return
        }
        // Pass 1: JSON files at this level
        for (entry in entries) {
            if (entry.endsWith(".json", ignoreCase = true)) {
                out += "$dir/$entry"
            }
        }
        // Pass 2: subdirectories at this level
        for (entry in entries) {
            if (!entry.endsWith(".json", ignoreCase = true)) {
                // Heuristic: if list() returns non-empty for this path, it's a dir.
                val children = try { context.assets.list("$dir/$entry") } catch (_: Throwable) { null }
                if (!children.isNullOrEmpty()) {
                    collectJsonAssets("$dir/$entry", out)
                }
            }
        }
    }

    private fun readLegacySingleFile(): CompatSnapshot? = try {
        val raw = context.assets.open("compatdb_seed.json").bufferedReader().use { it.readText() }
        compatJson.decodeFromString(CompatDbDto.serializer(), raw).toSnapshot()
    } catch (t: Throwable) {
        Log.w(TAG, "Legacy single-file seed not present", t); null
    }

    private companion object { const val TAG = "BundledCompatSource" }
}
