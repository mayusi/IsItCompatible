package io.github.mayusi.isitcompatible.compatdb

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads + serves the emulator-level fallback fixes for the interactive
 * troubleshooter from `assets/fixes/base-fixes.json`.
 *
 * Deliberately lives OUTSIDE `assets/seed/` (mirroring
 * `assets/autodetect/bios-knowledge.json`) so that [BundledCompatSource],
 * which recursively parses every JSON file under `seed/` as a CompatDb snapshot, never
 * sees this file and never logs a spurious "Failed to parse" warning for it.
 *
 * Mirrors [io.github.mayusi.isitcompatible.autodetect.BiosKnowledge]: a small
 * @Singleton that reads its asset once at construction and answers lookups.
 *
 * Scope: these are GENERIC, emulator-wide fixes that the troubleshooter appends
 * AFTER a specific game's `guide.troubleshooting` entries. The honesty contract
 * lives in the data file — only real, documented fixes, worded as "try this".
 *
 * The JSON shape is keyed by emulatorId, then by a canonical symptom key:
 * ```
 * { "emulators": { "<emulatorId>": { "<symptomKey>": [ {fix, detail}, ... ] } } }
 * ```
 * Symptom keys are the stable ids of [TroubleshootSymptom].
 */
@Singleton
class BaseFixes @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val data: BaseFixesData

    init {
        data = runCatching {
            val json = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
            JSON.decodeFromString(BaseFixesData.serializer(), json)
        }.getOrElse {
            Log.e(TAG, "Failed to load base fixes table", it)
            BaseFixesData()
        }
        Log.i(TAG, "Loaded base fixes for ${data.emulators.size} emulators")
    }

    /**
     * Ordered emulator-level fallback fixes for [emulatorId] + [symptomKey].
     * Returns an empty list when this emulator/symptom pair has no base fixes
     * (perfectly fine — the troubleshooter just shows the game's own entries).
     */
    fun fixesFor(emulatorId: String?, symptomKey: String): List<BaseFix> {
        if (emulatorId == null) return emptyList()
        return data.emulators[emulatorId]?.get(symptomKey).orEmpty()
    }

    private companion object {
        const val TAG = "BaseFixes"
        const val ASSET_PATH = "fixes/base-fixes.json"

        // Lenient so the file's "_comment"/"schema"/"dataAsOf" keys never break loading.
        val JSON = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}

/** One emulator-level fallback fix: a short instruction plus a longer how-to. */
@Serializable
data class BaseFix(
    val fix: String,
    val detail: String,
)

/** JSON root of base-fixes.json: emulatorId -> symptomKey -> ordered fixes. */
@Serializable
data class BaseFixesData(
    val schema: Int = 1,
    val emulators: Map<String, Map<String, List<BaseFix>>> = emptyMap(),
)
