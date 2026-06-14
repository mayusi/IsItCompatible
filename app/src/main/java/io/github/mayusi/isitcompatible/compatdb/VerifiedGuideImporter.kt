package io.github.mayusi.isitcompatible.compatdb

import android.util.Log
import io.github.mayusi.isitcompatible.compatdb.room.GuideDao
import io.github.mayusi.isitcompatible.compatdb.room.JournalDao
import io.github.mayusi.isitcompatible.compatdb.room.JournalEntryEntity
import io.github.mayusi.isitcompatible.compatdb.room.LocalVerifiedGuideDao
import io.github.mayusi.isitcompatible.compatdb.room.LocalVerifiedGuideEntity
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared logic for importing a working GameNative config as a Tier-1 verified
 * local guide. Called from two entry points:
 *
 *  1. [GameDetailViewModel.importGameNativeConfig] — SAF file-picker flow.
 *  2. [AutoTunerResultReceiver] — broadcast from the auto-tuner in the IIC fork.
 *
 * Extracting this here avoids duplicating the validate / sanitize / persist logic.
 * Both callers supply raw JSON from different sources (a file on disk vs. a
 * broadcast extra); both are treated as UNTRUSTED input and go through the same
 * validation + sanitization pipeline.
 *
 * SECURITY NOTE: the broadcast path is the higher-risk surface because any app on
 * the device can send AUTOTUNER_RESULT. The [validateAndSanitize] function is the
 * security boundary — it runs the same checks regardless of call site.
 */
@Singleton
class VerifiedGuideImporter @Inject constructor(
    private val localVerifiedGuideDao: LocalVerifiedGuideDao,
    private val guideDao: GuideDao,
    private val journalDao: JournalDao,
) {

    companion object {
        private const val TAG = "VerifiedGuideImporter"
        const val GAMENATIVE_EMULATOR_ID = "gamenative"

        /**
         * The minimum set of top-level keys a genuine GameNative config must carry.
         * Mirrors [GameDetailViewModel.REQUIRED_CONFIG_KEYS].
         */
        private val REQUIRED_CONFIG_KEYS = listOf(
            "emulator", "wineVersion", "graphicsDriver", "dxwrapper",
        )

        /**
         * Sanitize a parsed GameNative config JSON object: blank device/user-specific
         * identity and volatile bits, keep reusable settings verbatim.
         *
         * Mirrors [GameDetailViewModel.sanitizeGameNativeConfig] exactly — these two
         * functions MUST stay in sync. If you update one, update the other.
         */
        fun sanitize(src: JsonObject): JsonObject = buildJsonObject {
            for ((key, value) in src) {
                when (key) {
                    "sessionMetadata" -> { /* drop — volatile telemetry */ }
                    "name" -> put("name", JsonPrimitive(""))
                    "id" -> put("id", JsonPrimitive(""))
                    "installPath" -> put("installPath", JsonPrimitive(""))
                    "executablePath" -> put("executablePath", JsonPrimitive(""))
                    "drives" -> put("drives", JsonPrimitive(""))
                    else -> put(key, value)
                }
            }
        }

        /**
         * Validate that [rawJson] is a real GameNative config.
         *
         * Returns [ValidationResult.Ok] with the sanitized [JsonObject] on success,
         * or [ValidationResult.Error] with a human-readable reason on failure.
         *
         * Applied checks (same as the SAF-import path in GameDetailViewModel):
         *  1. Parseable as JSON.
         *  2. All [REQUIRED_CONFIG_KEYS] are present.
         *  3. An "extraData" key is present and is a JSON object.
         *
         * These checks deliberately stay lenient — they reject obviously-wrong input
         * without demanding every field, so genuine auto-tuner configs always pass.
         */
        fun validateAndSanitize(rawJson: String): ValidationResult {
            if (rawJson.isBlank()) {
                return ValidationResult.Error("Config JSON is empty.")
            }
            val parsed = runCatching {
                compatJson.parseToJsonElement(rawJson).jsonObject
            }.getOrNull() ?: return ValidationResult.Error("Config JSON is not valid JSON.")

            val missing = REQUIRED_CONFIG_KEYS.filter { it !in parsed.keys }
            val hasExtraData = parsed["extraData"] is JsonObject
            if (missing.isNotEmpty() || !hasExtraData) {
                val reason = buildString {
                    append("Config does not look like a GameNative config. ")
                    if (missing.isNotEmpty()) append("Missing keys: ${missing.joinToString(", ")}. ")
                    if (!hasExtraData) append("Missing extraData object.")
                }
                return ValidationResult.Error(reason)
            }

            val sanitized = sanitize(parsed)
            return ValidationResult.Ok(sanitized)
        }
    }

    /**
     * Validate, sanitize, and persist [rawConfigJson] as a Tier-1 verified guide
     * for [gameId] under the "gamenative" emulator.
     *
     * Optionally records a [JournalEntryEntity] (pass [journalStability] null to
     * skip the journal write — e.g. when the guide was already logged elsewhere).
     *
     * @param gameId          The IIC-internal game id (from [GameDao.bySteamAppId]).
     * @param rawConfigJson   Untrusted raw JSON string from any source.
     * @param sourceLabel     Human label for the guide tier badge.
     * @param journalStability If non-null, a journal entry is created with this stability.
     * @param journalNotes    Notes text for the journal entry (ignored when [journalStability] is null).
     * @return [ImportResult.Success] or [ImportResult.Failure].
     */
    suspend fun importVerifiedConfig(
        gameId: String,
        rawConfigJson: String,
        sourceLabel: String,
        journalStability: String? = null,
        journalNotes: String? = null,
    ): ImportResult {
        // 1. Validate + sanitize (security gate — treats input as untrusted).
        val validated = validateAndSanitize(rawConfigJson)
        if (validated is ValidationResult.Error) {
            Log.w(TAG, "importVerifiedConfig rejected for gameId=$gameId: ${validated.reason}")
            return ImportResult.Failure(validated.reason)
        }
        val sanitized = (validated as ValidationResult.Ok).sanitizedJson
        val sanitizedJsonString = compatJson.encodeToString(JsonObject.serializer(), sanitized)

        val now = System.currentTimeMillis()

        // 2. Build typed guide steps (GET_APP + ACTION import).
        val steps = listOf(
            GuideStepDto(kind = "GET_APP", text = "Install GameNative"),
            GuideStepDto(
                kind = "ACTION",
                text = "Import this verified config (open the game → 3 dots → Import Config).",
            ),
        )
        val stepsJson = compatJson.encodeToString(ListSerializer(GuideStepDto.serializer()), steps)

        // 3. Persist as a DURABLE local verified guide (survives DB syncs).
        val localId = "local:$gameId:$GAMENATIVE_EMULATOR_ID:t1"
        val local = LocalVerifiedGuideEntity(
            id = localId,
            gameId = gameId,
            emulatorId = GAMENATIVE_EMULATOR_ID,
            sourceLabel = sourceLabel,
            dataAsOf = now,
            stepsJson = stepsJson,
            gameNativeConfigJson = sanitizedJsonString,
            createdAt = now,
        )
        return try {
            localVerifiedGuideDao.upsert(local)
            // Apply immediately into `guides` so the resolver sees it without
            // waiting for the next sync's replaceAll re-apply.
            guideDao.upsertAll(listOf(local.toGuideEntity()))
            Log.i(TAG, "Verified guide saved for gameId=$gameId sourceLabel='$sourceLabel'")

            // 4. Optionally record a journal entry.
            if (journalStability != null) {
                runCatching {
                    journalDao.upsert(
                        JournalEntryEntity(
                            id = UUID.randomUUID().toString(),
                            gameId = gameId,
                            emulatorId = GAMENATIVE_EMULATOR_ID,
                            presetId = null,
                            avgFps = null,
                            stability = journalStability,
                            notes = journalNotes,
                            createdAt = now,
                            sessionMinutes = null,
                            peakTempC = null,
                            driverIdAtTimeOfRun = null,
                            shareWithCommunity = false,
                        ),
                    )
                }.onFailure { e ->
                    Log.w(TAG, "Journal write failed (non-fatal) for gameId=$gameId", e)
                }
            }

            ImportResult.Success(sanitizedJsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist verified guide for gameId=$gameId", e)
            ImportResult.Failure("DB write failed: ${e.message}")
        }
    }

    sealed interface ValidationResult {
        data class Ok(val sanitizedJson: JsonObject) : ValidationResult
        data class Error(val reason: String) : ValidationResult
    }

    sealed interface ImportResult {
        data class Success(val sanitizedConfigJson: String) : ImportResult
        data class Failure(val reason: String) : ImportResult
    }
}
