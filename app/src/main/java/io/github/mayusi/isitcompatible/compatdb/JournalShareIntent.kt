package io.github.mayusi.isitcompatible.compatdb

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import io.github.mayusi.isitcompatible.compatdb.room.JournalEntryEntity
import io.github.mayusi.isitcompatible.hardware.DeviceFingerprint
import java.net.URLEncoder

/**
 * v0.6: opens the community-DB GitHub repo's "new issue" page pre-filled with
 * the journal entry's data so the user can review and submit.
 *
 * No GitHub auth needed — we use the public ?title= ?body= query syntax.
 * The user can edit the body in browser before posting. If their browser is
 * not signed into GitHub, GitHub prompts them, which is the right UX (we
 * never want silent posts on their behalf).
 *
 * Privacy: only the data the user typed into the journal form + their device
 * fingerprint is included. No emails, no installed-app list, no telemetry.
 */
object JournalShareIntent {

    private const val TAG = "JournalShareIntent"

    /**
     * QW-4/QW-5 feature flag. The community DB repo [REPO] does NOT exist yet, so
     * launching the pre-filled new-issue URL 404s. While this is false, [fire] is
     * a no-op (returns false) and the UI must NOT show any "Share with community"
     * affordance — see [isEnabled] / the share button gating in GameDetailScreen.
     *
     * The entire issue-building code path below is kept intact so flipping this to
     * true (once the repo exists) restores community sharing with no further work.
     */
    const val SHARE_ENABLED = false

    /** Public probe so the UI can hide/disable the share button without a 404 risk. */
    fun isEnabled(): Boolean = SHARE_ENABLED

    /**
     * Repo to post the report to. Does NOT exist yet — guarded by [SHARE_ENABLED].
     */
    private const val REPO = "mayusi/IsItCompatible-DB"

    /**
     * Launch the user's browser to a pre-filled new-issue page for [entry].
     * Returns true if the intent was dispatched, false if no browser was found.
     */
    fun fire(
        context: Context,
        entry: JournalEntryEntity,
        gameTitle: String,
        emulatorName: String?,
        presetName: String?,
        fp: DeviceFingerprint?,
    ): Boolean {
        // QW-5: never launch a browser at a repo that doesn't exist (would 404).
        if (!SHARE_ENABLED) {
            Log.i(TAG, "Community share disabled (repo not created yet); skipping intent.")
            return false
        }
        val title = buildString {
            append("[Report] ")
            append(gameTitle)
            if (emulatorName != null) append(" on $emulatorName")
        }
        val body = buildBody(entry, gameTitle, emulatorName, presetName, fp)
        val url = "https://github.com/$REPO/issues/new" +
            "?title=" + URLEncoder.encode(title, "UTF-8") +
            "&body=" + URLEncoder.encode(body, "UTF-8") +
            "&labels=" + URLEncoder.encode("community-report,from-app", "UTF-8")

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "No browser available to receive share intent", t)
            false
        }
    }

    /** GitHub-Markdown body for the issue. Renders to a structured-looking report. */
    private fun buildBody(
        entry: JournalEntryEntity,
        gameTitle: String,
        emulatorName: String?,
        presetName: String?,
        fp: DeviceFingerprint?,
    ): String {
        val fpsLine = entry.avgFps?.let { "$it fps" } ?: "not measured"
        val sessionLine = entry.sessionMinutes?.let { "$it min" } ?: "n/a"
        val tempLine = entry.peakTempC?.let { "$it °C" } ?: "n/a"
        return """
            ### Report from Is It Compatible? app

            **Game:** $gameTitle
            **Emulator:** ${emulatorName ?: "—"}
            **Preset:** ${presetName ?: "—"}

            ### Result
            - **FPS:** $fpsLine
            - **Stability:** ${entry.stability}
            - **Session length:** $sessionLine
            - **Peak temp:** $tempLine

            ### Device
            ${fp?.toShareableText() ?: "_(not available)_"}

            ### Notes
            ${entry.notes?.takeIf { it.isNotBlank() } ?: "_(none)_"}

            ---
            _Submitted via Is It Compatible? — please review before submitting._
            _No personal info was included automatically. You can edit anything above._
        """.trimIndent()
    }
}

/** Pretty-print the parts of the fingerprint that are safe to share. */
private fun DeviceFingerprint.toShareableText(): String = buildString {
    appendLine("- **SoC:** $socFamily")
    appendLine("- **GPU:** ${gpuVendor.name} $gpuModel")
    appendLine("- **RAM:** ${totalRamMb / 1024} GB")
    append("- **Android API:** $androidApi")
}
