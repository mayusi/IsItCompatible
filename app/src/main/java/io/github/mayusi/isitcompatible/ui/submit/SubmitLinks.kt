package io.github.mayusi.isitcompatible.ui.submit

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import io.github.mayusi.isitcompatible.compatdb.room.GameEntity
import io.github.mayusi.isitcompatible.hardware.DeviceFingerprint
import java.net.URLEncoder

/**
 * Centralised "where do reports go" logic.
 *
 *  - Console games (everything that's not WINDOWS) → EmuReady's site for that title.
 *    We can't always deep-link to the exact game page so we open the search/listing
 *    URL and let the user pick.
 *  - Windows-translator games → our GitHub Issues template, pre-filled.
 */
object SubmitLinks {

    /**
     * The community compatibility DB repo (`mayusi/IsItCompatible-DB`) does NOT exist yet,
     * so every GitHub "submit a report / share config" link that points at it 404s.
     * Until the repo is created, keep all such links GATED OFF: the link builders return
     * null and the open helpers become no-ops, so callers naturally surface nothing.
     *
     * IMPORTANT: this flag ONLY governs the dead IsItCompatible-DB destination. EmuReady
     * links (emuready.com) are real, working destinations and are never gated.
     *
     * Flip to true once the repo exists.
     */
    const val COMMUNITY_DB_ENABLED = false

    private const val GITHUB_NEW_ISSUE =
        "https://github.com/mayusi/IsItCompatible-DB/issues/new"

    fun openSubmitFor(context: Context, game: GameEntity, fp: DeviceFingerprint?) {
        val url = when (game.platform.uppercase()) {
            // Windows-translator games point at the (non-existent) community DB repo.
            "WINDOWS" -> buildGithubIssueUrl(game, fp) ?: return
            // Console games go to EmuReady — a real, working destination.
            else -> emuReadyUrlFor(game)
        }
        openUrl(context, url)
    }

    /** Opens the EmuReady listings page (filtered by title). Always a real destination. */
    fun openEmuReady(context: Context) {
        openUrl(context, "https://emuready.com/listings")
    }

    /**
     * Opens the project's new-issue form (without a specific game).
     * Gated behind [COMMUNITY_DB_ENABLED] because the target repo doesn't exist yet.
     */
    fun openGithubIssues(context: Context) {
        if (!COMMUNITY_DB_ENABLED) return
        openUrl(context, GITHUB_NEW_ISSUE)
    }

    private fun emuReadyUrlFor(game: GameEntity): String {
        val q = URLEncoder.encode(game.title, Charsets.UTF_8.name())
        return "https://emuready.com/listings?q=$q"
    }

    /**
     * Builds the pre-filled GitHub issue URL for a Windows game, or null when the
     * community DB repo is disabled (it doesn't exist yet → the link would 404).
     * The full template is kept intact behind the flag for when the repo is created.
     */
    private fun buildGithubIssueUrl(game: GameEntity, fp: DeviceFingerprint?): String? {
        if (!COMMUNITY_DB_ENABLED) return null
        val title = URLEncoder.encode("Report: ${game.title}", Charsets.UTF_8.name())
        val body = buildString {
            appendLine("Game: ${game.title}")
            appendLine("Platform: ${game.platform}")
            appendLine("Game ID: ${game.id}")
            appendLine()
            appendLine("Device:")
            if (fp != null) {
                appendLine("- SoC: ${fp.socFamily} (${fp.socModel})")
                appendLine("- GPU: ${fp.gpuModel}")
                appendLine("- RAM: ${fp.totalRamMb / 1024} GB")
                appendLine("- Android: ${fp.androidRelease} (API ${fp.androidApi})")
            } else {
                appendLine("- (unknown)")
            }
            appendLine()
            appendLine("Emulator: <e.g. GameNative>")
            appendLine("Preset / settings: <Wine version, DXVK version, driver, etc.>")
            appendLine()
            appendLine("Performance:")
            appendLine("- Avg FPS: ")
            appendLine("- Stability: PERFECT / PLAYABLE / GLITCHY / CRASHES")
            appendLine()
            appendLine("Notes: ")
        }
        val encoded = URLEncoder.encode(body, Charsets.UTF_8.name())
        return "$GITHUB_NEW_ISSUE?title=$title&body=$encoded&labels=report"
    }

    private fun openUrl(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (t: Throwable) {
            Toast.makeText(context, "No browser available", Toast.LENGTH_SHORT).show()
        }
    }
}
