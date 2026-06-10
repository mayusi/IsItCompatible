package io.github.mayusi.isitcompatible.compatdb

import io.github.mayusi.isitcompatible.compatdb.room.GuideDao
import io.github.mayusi.isitcompatible.compatdb.room.GuideEntity
import kotlinx.serialization.builtins.ListSerializer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v0.8: resolves the best available guide for a (game, emulator) pair and
 * parses its stored JSON back into typed steps for the UI.
 *
 * "Best" = lowest tier number among guides matching the game+emulator, else
 * the per-emulator base guide (gameId = null). The recommender already chose
 * the emulator; this just layers the right guide on top.
 *
 * Pure-ish: only touches [GuideDao]. No Android UI deps, easy to unit-test.
 */
@Singleton
class GuideResolver @Inject constructor(
    private val guideDao: GuideDao,
) {
    /**
     * @return the resolved guide for this game on this emulator, or null if the
     *         emulator has no guide at all (shouldn't happen once base guides ship).
     */
    suspend fun resolve(gameId: String, emulatorId: String): ResolvedGuide? {
        val candidates = guideDao.candidatesFor(gameId, emulatorId)
        // candidatesFor already orders by tier ASC, so the first row is the best.
        val best = candidates.firstOrNull() ?: guideDao.baseGuideFor(emulatorId) ?: return null
        return best.toResolved()
    }

    /** Just the base (Tier-4) guide, when there's no specific game context. */
    suspend fun baseGuide(emulatorId: String): ResolvedGuide? =
        guideDao.baseGuideFor(emulatorId)?.toResolved()

    private fun GuideEntity.toResolved(): ResolvedGuide {
        val steps = runCatching {
            compatJson.decodeFromString(ListSerializer(GuideStepDto.serializer()), stepsJson)
        }.getOrDefault(emptyList())
        val troubles = troubleshootingJson?.let { tj ->
            runCatching {
                compatJson.decodeFromString(ListSerializer(TroubleshootingDto.serializer()), tj)
            }.getOrDefault(emptyList())
        } ?: emptyList()
        return ResolvedGuide(
            id = id,
            gameId = gameId,
            emulatorId = emulatorId,
            tier = tier,
            sourceLabel = sourceLabel,
            sourceUrl = sourceUrl,
            dataAsOf = dataAsOf,
            steps = steps,
            troubleshooting = troubles,
            // Chunk 1: pass the opaque config string straight through. Null when
            // the guide carries no GameNative config.
            gameNativeConfigJson = gameNativeConfigJson,
        )
    }
}

/** A guide with its JSON fields parsed into typed objects, ready for the UI. */
data class ResolvedGuide(
    val id: String,
    val gameId: String?,
    val emulatorId: String,
    val tier: Int,
    val sourceLabel: String?,
    val sourceUrl: String?,
    val dataAsOf: Long,
    val steps: List<GuideStepDto>,
    val troubleshooting: List<TroubleshootingDto>,
    /**
     * Chunk 1: raw importable GameNative per-game config as a compact JSON
     * string, or null if this guide carries none. The UI/apply layer writes
     * this verbatim to the importable `<Game>_config.json`.
     */
    val gameNativeConfigJson: String? = null,
) {
    /** Stable key for checklist-progress rows: scoped to the game+emulator being set up. */
    fun progressKey(forGameId: String): String = "$forGameId:$emulatorId"

    val tierLabel: String
        get() = when (tier) {
            1 -> sourceLabel ?: "Verified by the community"
            2 -> sourceLabel ?: "Authored guide"
            3 -> sourceLabel ?: "From EmuReady"
            else -> sourceLabel ?: "Standard setup"
        }
}
