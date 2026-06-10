package io.github.mayusi.isitcompatible.getit

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * One-tap handoff to GameNative's first-party LAUNCH_GAME intent.
 *
 * GameNative exposes an EXPORTED activity intent-filter that applies a per-game
 * container config AND launches the game in a single tap — no manual file
 * import. We build that intent here and fire it.
 *
 * PROJECT RULE: the app otherwise avoids Intents to other apps ("standalone").
 * This is the one APPROVED exception — GameNative's documented first-party
 * LAUNCH_GAME API. Keep it scoped to GameNative only; do not add other cross-app
 * intents through this file.
 *
 * Intent contract (confirmed by reading GameNative's IntentLaunchManager.kt,
 * branch master:
 * https://raw.githubusercontent.com/utkarshdalal/GameNative/master/app/src/main/java/app/gamenative/utils/IntentLaunchManager.kt ):
 *   action  : "app.gamenative.LAUNCH_GAME"  (category DEFAULT)
 *   package : "app.gamenative"
 *   extras  :
 *     app_id          : Int   (required, > 0)
 *     game_source     : String (STEAM | GOG | EPIC | AMAZON, default STEAM)
 *     container_config: String (a JSON object, MAX 50 KB)
 *
 * IMPORTANT — the intent's container_config is a CURATED SUBSET of fields whose
 * semantics differ from the on-disk `<Game>_config.json` schema. Notably the
 * intent parser reads `dxwrapperConfig` as a bare VERSION string and itself
 * prefixes it with "version=", whereas the on-disk config stores a full CSV
 * (e.g. "version=async-1.10.3,framerate=0,..."). We map between the two here so
 * we feed the INTENT parser exactly what it expects, not the raw on-disk config.
 */
object GameNativeLaunch {

    private const val TAG = "GameNativeLaunch"

    /** Official GameNative package id. */
    const val PACKAGE = "app.gamenative"

    /** IIC fork package id — installs alongside the official build. */
    const val PACKAGE_IIC = "app.gamenative.iic"

    const val ACTION = "app.gamenative.LAUNCH_GAME"

    const val EXTRA_APP_ID = "app_id"
    const val EXTRA_GAME_SOURCE = "game_source"
    const val EXTRA_CONTAINER_CONFIG = "container_config"

    /** GameNative caps container_config at 50 KB; mirror that here. */
    private const val MAX_CONFIG_BYTES = 50 * 1024

    /**
     * The exact set of keys the intent's parseContainerConfig() reads. Keys NOT
     * in this set are dropped before we build the extra, both to respect the
     * curated subset and to keep us under the 50 KB cap. Confirmed against
     * IntentLaunchManager.kt (see class doc for URL).
     *
     * Type notes (how the intent parser reads each):
     *   String  : name, screenSize, envVars, graphicsDriver, graphicsDriverVersion,
     *             dxwrapper, audioDriver, wincomponents, drives, execArgs,
     *             executablePath, installPath, cpuList, cpuListWoW64,
     *             box86Version, box64Version, box86Preset, box64Preset, desktopTheme,
     *             mouseWarpOverride, offScreenRenderingMode, shaderBackend, useGLSL,
     *             videoPciDeviceID, videoMemorySize
     *   Boolean : showFPS, launchRealSteam, launchBionicSteam, wow64Mode, csmt,
     *             strictShaderMath, sdlControllerAPI, enableXInput, enableDInput,
     *             disableMouseInput
     *   Int     : startupSelection, dinputMapperType  (parser does .toByte())
     *   Special : dxwrapperConfig — parser does "version=" + value, so we pass a
     *             BARE version string, not the on-disk CSV.
     */
    private val STRING_KEYS = setOf(
        "name", "screenSize", "envVars", "graphicsDriver", "graphicsDriverVersion",
        "dxwrapper", "audioDriver", "wincomponents", "drives", "execArgs",
        "executablePath", "installPath", "cpuList", "cpuListWoW64",
        "box86Version", "box64Version", "box86Preset", "box64Preset", "desktopTheme",
        "mouseWarpOverride", "offScreenRenderingMode", "shaderBackend", "useGLSL",
        "videoPciDeviceID", "videoMemorySize",
    )
    private val BOOL_KEYS = setOf(
        "showFPS", "launchRealSteam", "launchBionicSteam", "wow64Mode", "csmt",
        "strictShaderMath", "sdlControllerAPI", "enableXInput", "enableDInput",
        "disableMouseInput",
    )
    private val INT_KEYS = setOf("startupSelection", "dinputMapperType")

    /**
     * Build the intent and fire it.
     *
     * FORK PREFERENCE: if the IIC fork (app.gamenative.iic) is installed we
     * target it first — it shares the same LAUNCH_GAME intent-filter but has
     * per-game auto-fixes (e.g. DMC HD Collection crashing intro) baked in. If
     * only the official build is installed we fall back to it. If neither is
     * installed the caller falls back to the existing export-config flow.
     *
     * @param configJson the guide's embedded on-disk GameNative config JSON
     *        (ResolvedGuide.gameNativeConfigJson). We MAP/trim it to the intent's
     *        accepted subset/format here. Pass null/blank to launch with no config
     *        override (GameNative uses the game's existing container as-is).
     * @return true if GameNative (IIC or official) accepted the intent; false if
     *         neither is installed — the caller then falls back to the export flow.
     */
    fun launchInGameNative(
        context: Context,
        appId: Int,
        gameSource: String = "STEAM",
        configJson: String? = null,
    ): Boolean {
        if (appId <= 0) {
            Log.w(TAG, "Refusing to launch: app_id must be > 0 (was $appId)")
            return false
        }
        // Prefer IIC fork; fall back to official.
        val targetPackage = when {
            isIicInstalled(context) -> PACKAGE_IIC
            isOfficialInstalled(context) -> PACKAGE
            else -> {
                Log.i(TAG, "Neither GameNative build installed — falling back to export flow")
                return false
            }
        }
        Log.i(TAG, "Launching via $targetPackage")
        val intent = Intent(ACTION).apply {
            setPackage(targetPackage)
            putExtra(EXTRA_APP_ID, appId)
            putExtra(EXTRA_GAME_SOURCE, gameSource.uppercase())
            mapToIntentConfig(configJson)?.let { putExtra(EXTRA_CONTAINER_CONFIG, it) }
        }
        return try {
            // Activity start from a non-Activity context needs NEW_TASK.
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            // GameNative isn't installed (or doesn't expose LAUNCH_GAME). The
            // caller falls back to the existing download/export-config flow.
            Log.i(TAG, "GameNative ($targetPackage) not found — falling back to export flow", e)
            false
        } catch (e: Exception) {
            Log.w(TAG, "Failed to launch GameNative ($targetPackage)", e)
            false
        }
    }

    /**
     * True if either the IIC fork OR the official GameNative build is installed.
     * Used by the ViewModel to decide whether the one-tap launch button should show.
     */
    fun isInstalled(context: Context): Boolean = isIicInstalled(context) || isOfficialInstalled(context)

    /** True if the IIC fork (app.gamenative.iic) is installed. */
    fun isIicInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(PACKAGE_IIC, 0)
        true
    }.getOrDefault(false)

    /** True if the official GameNative build (app.gamenative) is installed. */
    private fun isOfficialInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    }.getOrDefault(false)

    /**
     * Map the on-disk GameNative config JSON to the intent's accepted subset +
     * formats, and serialize it as a compact JSON string under the 50 KB cap.
     * Returns null when there's nothing usable to pass.
     *
     * Transformations vs. the on-disk schema:
     *  - Keep only keys the intent parser actually reads (see STRING/BOOL/INT_KEYS
     *    + dxwrapperConfig). Everything else (id, extraData, emulator, wineVersion,
     *    containerVariant, graphicsDriverConfig CSV, box/fex presets we don't map,
     *    etc.) is dropped — the intent doesn't consume them.
     *  - dxwrapperConfig: the on-disk value is a CSV ("version=async-1.10.3,..."),
     *    but the intent parser does `"version=" + value`. So we extract just the
     *    version token and pass it bare. If the on-disk value is already bare we
     *    pass it through.
     *  - String/Bool/Int keys are coerced to the type the parser expects so a
     *    stray quoted boolean/number in the on-disk config still lands correctly.
     */
    internal fun mapToIntentConfig(configJson: String?): String? {
        if (configJson.isNullOrBlank()) return null
        val src: JsonObject = runCatching {
            io.github.mayusi.isitcompatible.compatdb.compatJson
                .parseToJsonElement(configJson).jsonObject
        }.getOrNull() ?: return null

        val mapped = buildJsonObject {
            for ((key, value) in src) {
                when (key) {
                    "dxwrapperConfig" -> {
                        val bare = bareVersion(value.primitiveContentOrNull())
                        if (!bare.isNullOrBlank()) put("dxwrapperConfig", JsonPrimitive(bare))
                    }
                    in STRING_KEYS -> {
                        val s = value.primitiveContentOrNull()
                        if (s != null) put(key, JsonPrimitive(s))
                    }
                    in BOOL_KEYS -> {
                        coerceBool(value)?.let { put(key, JsonPrimitive(it)) }
                    }
                    in INT_KEYS -> {
                        coerceInt(value)?.let { put(key, JsonPrimitive(it)) }
                    }
                    else -> { /* not consumed by the intent parser — drop */ }
                }
            }
        }

        if (mapped.isEmpty()) return null
        val out = io.github.mayusi.isitcompatible.compatdb.compatJson
            .encodeToString(JsonObject.serializer(), mapped)

        // Guard the 50 KB cap. We already trimmed to the curated subset, so this
        // is a hard backstop; if still oversized, drop the container_config and
        // launch with just app_id + source rather than send an oversized extra.
        if (out.toByteArray(Charsets.UTF_8).size > MAX_CONFIG_BYTES) {
            Log.w(TAG, "container_config exceeds 50 KB even after trimming — omitting it")
            return null
        }
        return out
    }

    /**
     * Pull the version out of an on-disk dxwrapperConfig value. The on-disk form
     * is a CSV like "version=async-1.10.3,framerate=0,async=1"; the intent wants
     * the bare "async-1.10.3". Falls back to returning the input unchanged when
     * there's no "version=" token (i.e. it's already bare).
     */
    private fun bareVersion(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val token = raw.split(',')
            .map { it.trim() }
            .firstOrNull { it.startsWith("version=") }
        return token?.substringAfter("version=")?.trim()?.ifBlank { null } ?: raw.trim()
    }

    private fun kotlinx.serialization.json.JsonElement.primitiveContentOrNull(): String? =
        runCatching { jsonPrimitive.contentOrNull }.getOrNull()

    private fun coerceBool(value: kotlinx.serialization.json.JsonElement): Boolean? = runCatching {
        val p = value.jsonPrimitive
        p.booleanOrNull ?: when (p.content.lowercase()) {
            "true", "1" -> true
            "false", "0" -> false
            else -> null
        }
    }.getOrNull()

    private fun coerceInt(value: kotlinx.serialization.json.JsonElement): Int? =
        runCatching { value.jsonPrimitive.intOrNull ?: value.jsonPrimitive.content.toIntOrNull() }.getOrNull()
}
