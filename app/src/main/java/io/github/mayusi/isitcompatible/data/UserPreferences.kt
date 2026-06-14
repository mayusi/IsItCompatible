package io.github.mayusi.isitcompatible.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mayusi.isitcompatible.hardware.DeviceFingerprint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

/**
 * Persisted user preferences. Backed by DataStore. All fields are nullable until
 * the first-launch wizard fills them in.
 *
 * URIs are stored as strings (SAF tree URIs the user picked); we'll take
 * persistable read/write permissions on them so they survive reboot.
 */
@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val json = Json { ignoreUnknownKeys = true }

    val data: Flow<Snapshot> = context.dataStore.data.map { p ->
        Snapshot(
            wizardComplete = p[Keys.WIZARD_COMPLETE] ?: false,
            romFolderUri = p[Keys.ROM_FOLDER_URI],
            pcFolderUri = p[Keys.PC_FOLDER_URI],
            stagingFolderUri = p[Keys.STAGING_FOLDER_URI],
            fingerprint = p[Keys.FINGERPRINT_JSON]?.let {
                runCatching { json.decodeFromString<DeviceFingerprint>(it) }.getOrNull()
            },
            // v0.6: last successful sync timestamp + remote-sources-reached marker
            // so the Updates screen can show "Last synced N hours ago" and an
            // honest "no remote configured" hint when only the bundled seed loaded.
            lastSyncEpochMs = p[Keys.LAST_SYNC_EPOCH_MS] ?: 0L,
            lastSyncRemoteReached = p[Keys.LAST_SYNC_REMOTE_REACHED] ?: false,
            // IIC round-trip: pending session delivered by the GameNative fork.
            pendingSessionGameId = p[Keys.PENDING_SESSION_GAME_ID],
            pendingSessionMinutes = p[Keys.PENDING_SESSION_MINUTES],
            pendingSessionShowedFps = p[Keys.PENDING_SESSION_SHOWED_FPS] ?: false,
            // Feature C: avg fps + derived stability from the fork session (forward-compat extras).
            pendingSessionAvgFps = p[Keys.PENDING_SESSION_AVG_FPS],
            pendingSessionStability = p[Keys.PENDING_SESSION_STABILITY],
            // v0.11: Windows quick-start onboarding card dismissed flag.
            windowsQuickStartDismissed = p[Keys.WINDOWS_QUICK_START_DISMISSED] ?: false,
            // Feature B: true once we've shown the POST_NOTIFICATIONS rationale
            // dialog at favorite-time so we never nag the user again.
            notifPermAsked = p[Keys.NOTIF_PERM_ASKED] ?: false,
            // self-update
            lastUpdateCheckMs = p[Keys.LAST_UPDATE_CHECK_MS] ?: 0L,
            updateAutoCheckEnabled = p[Keys.UPDATE_AUTO_CHECK_ENABLED] ?: true,
            pendingUpdateVersion = p[Keys.PENDING_UPDATE_VERSION],
            pendingUpdateUrl = p[Keys.PENDING_UPDATE_URL],
            pendingUpdateFilename = p[Keys.PENDING_UPDATE_FILENAME],
            pendingUpdateNotes = p[Keys.PENDING_UPDATE_NOTES],
            pendingUpdateSize = p[Keys.PENDING_UPDATE_SIZE] ?: 0L,
            pendingUpdateSha256 = p[Keys.PENDING_UPDATE_SHA256],
        )
    }

    suspend fun setWizardComplete(value: Boolean) =
        context.dataStore.edit { it[Keys.WIZARD_COMPLETE] = value }

    suspend fun setRomFolderUri(uri: String?) =
        context.dataStore.edit { prefs ->
            if (uri == null) prefs.remove(Keys.ROM_FOLDER_URI) else prefs[Keys.ROM_FOLDER_URI] = uri
        }

    suspend fun setPcFolderUri(uri: String?) =
        context.dataStore.edit { prefs ->
            if (uri == null) prefs.remove(Keys.PC_FOLDER_URI) else prefs[Keys.PC_FOLDER_URI] = uri
        }

    suspend fun setStagingFolderUri(uri: String?) =
        context.dataStore.edit { prefs ->
            if (uri == null) prefs.remove(Keys.STAGING_FOLDER_URI) else prefs[Keys.STAGING_FOLDER_URI] = uri
        }

    suspend fun setFingerprint(fp: DeviceFingerprint) =
        context.dataStore.edit {
            it[Keys.FINGERPRINT_JSON] = json.encodeToString(DeviceFingerprint.serializer(), fp)
        }

    /**
     * v0.6: stamp the moment a [CompatDbRepository.sync] call returns, plus
     * a flag indicating whether any remote source actually delivered data.
     * Used by the Updates screen to render "Last synced N hours ago" and
     * an honest "bundled only" caveat when the remote repo 404'd.
     */
    suspend fun setLastSync(epochMs: Long, remoteReached: Boolean) =
        context.dataStore.edit {
            it[Keys.LAST_SYNC_EPOCH_MS] = epochMs
            it[Keys.LAST_SYNC_REMOTE_REACHED] = remoteReached
        }

    /** v0.11: dismiss the Windows quick-start onboarding card permanently. */
    suspend fun dismissWindowsQuickStart() =
        context.dataStore.edit { it[Keys.WINDOWS_QUICK_START_DISMISSED] = true }

    /**
     * Feature B: mark that we have already prompted (or decided not to prompt)
     * for POST_NOTIFICATIONS at favorite-time. Call this regardless of whether
     * the user granted or denied so we only ever ask once.
     */
    suspend fun markNotifPermAsked() =
        context.dataStore.edit { it[Keys.NOTIF_PERM_ASKED] = true }

    // ── self-update prefs ──────────────────────────────────────────────────────

    suspend fun setLastUpdateCheck(epochMs: Long) =
        context.dataStore.edit { it[Keys.LAST_UPDATE_CHECK_MS] = epochMs }

    suspend fun setUpdateAutoCheck(enabled: Boolean) =
        context.dataStore.edit { it[Keys.UPDATE_AUTO_CHECK_ENABLED] = enabled }

    /** Write all pending-update fields at once. */
    suspend fun setPendingUpdate(
        version: String,
        url: String,
        filename: String,
        notes: String,
        sizeBytes: Long,
        sha256: String? = null,
    ) = context.dataStore.edit { p ->
        p[Keys.PENDING_UPDATE_VERSION] = version
        p[Keys.PENDING_UPDATE_URL] = url
        p[Keys.PENDING_UPDATE_FILENAME] = filename
        p[Keys.PENDING_UPDATE_NOTES] = notes
        p[Keys.PENDING_UPDATE_SIZE] = sizeBytes
        if (sha256 != null) p[Keys.PENDING_UPDATE_SHA256] = sha256
        else p.remove(Keys.PENDING_UPDATE_SHA256)
    }

    /** Clear any pending-update state (user dismissed or install completed). */
    suspend fun clearPendingUpdate() = context.dataStore.edit { p ->
        p.remove(Keys.PENDING_UPDATE_VERSION)
        p.remove(Keys.PENDING_UPDATE_URL)
        p.remove(Keys.PENDING_UPDATE_FILENAME)
        p.remove(Keys.PENDING_UPDATE_NOTES)
        p.remove(Keys.PENDING_UPDATE_SIZE)
        p.remove(Keys.PENDING_UPDATE_SHA256)
    }

    /**
     * IIC round-trip: persist a pending session delivered by the GameNative fork
     * broadcast. [gameId] is the IIC-internal game id (from [GameEntity.id]).
     * Set [gameId] to null to clear the pending session once the user has seen it.
     *
     * Feature C: [avgFps] and [stability] are optional forward-compat extras that
     * the fork may send in a future update. When present, the journal form pre-fills
     * both fields so the user only confirms rather than entering data.
     */
    suspend fun setPendingSession(
        gameId: String?,
        sessionMinutes: Int?,
        showedFps: Boolean,
        avgFps: Int? = null,
        stability: String? = null,
    ) = context.dataStore.edit { p ->
        if (gameId == null) {
            p.remove(Keys.PENDING_SESSION_GAME_ID)
            p.remove(Keys.PENDING_SESSION_MINUTES)
            p.remove(Keys.PENDING_SESSION_SHOWED_FPS)
            p.remove(Keys.PENDING_SESSION_AVG_FPS)
            p.remove(Keys.PENDING_SESSION_STABILITY)
        } else {
            p[Keys.PENDING_SESSION_GAME_ID] = gameId
            if (sessionMinutes != null) {
                p[Keys.PENDING_SESSION_MINUTES] = sessionMinutes
            } else {
                p.remove(Keys.PENDING_SESSION_MINUTES)
            }
            p[Keys.PENDING_SESSION_SHOWED_FPS] = showedFps
            if (avgFps != null) {
                p[Keys.PENDING_SESSION_AVG_FPS] = avgFps
            } else {
                p.remove(Keys.PENDING_SESSION_AVG_FPS)
            }
            if (stability != null) {
                p[Keys.PENDING_SESSION_STABILITY] = stability
            } else {
                p.remove(Keys.PENDING_SESSION_STABILITY)
            }
        }
    }

    data class Snapshot(
        val wizardComplete: Boolean,
        val romFolderUri: String?,
        val pcFolderUri: String?,
        val stagingFolderUri: String?,
        val fingerprint: DeviceFingerprint?,
        val lastSyncEpochMs: Long = 0L,
        val lastSyncRemoteReached: Boolean = false,
        /** IIC round-trip: IIC game id of the session the fork just reported, or null. */
        val pendingSessionGameId: String? = null,
        /** IIC round-trip: approximate session length in minutes from the fork. */
        val pendingSessionMinutes: Int? = null,
        /** IIC round-trip: whether the FPS HUD was on during the fork session. */
        val pendingSessionShowedFps: Boolean = false,
        /**
         * Feature C: avg FPS delivered by the fork (forward-compat extra).
         * Null when the fork didn't send it (current fork version) — the journal
         * form treats null as "not measured" and leaves fpsKnown=false.
         */
        val pendingSessionAvgFps: Int? = null,
        /**
         * Feature C: stability string delivered by the fork (forward-compat extra).
         * When null, the form defaults to PLAYABLE. When present, pre-selects the
         * derived stability so the user just confirms.
         */
        val pendingSessionStability: String? = null,
        /** v0.11: true once the user dismisses the Windows quick-start onboarding card. */
        val windowsQuickStartDismissed: Boolean = false,
        /**
         * Feature B: true once we have shown (or decided to skip) the contextual
         * POST_NOTIFICATIONS rationale at favorite-time. Prevents repeated nags —
         * we ask at most once no matter how many games the user favorites.
         */
        val notifPermAsked: Boolean = false,
        // self-update
        val lastUpdateCheckMs: Long = 0L,
        val updateAutoCheckEnabled: Boolean = true,
        val pendingUpdateVersion: String? = null,
        val pendingUpdateUrl: String? = null,
        val pendingUpdateFilename: String? = null,
        val pendingUpdateNotes: String? = null,
        val pendingUpdateSize: Long = 0L,
        /** SHA-256 hex of the pending APK, or null if the release didn't publish one. */
        val pendingUpdateSha256: String? = null,
    )

    private object Keys {
        val WIZARD_COMPLETE = booleanPreferencesKey("wizard_complete")
        val ROM_FOLDER_URI = stringPreferencesKey("rom_folder_uri")
        val PC_FOLDER_URI = stringPreferencesKey("pc_folder_uri")
        val STAGING_FOLDER_URI = stringPreferencesKey("staging_folder_uri")
        val FINGERPRINT_JSON = stringPreferencesKey("fingerprint_json")
        val LAST_SYNC_EPOCH_MS = longPreferencesKey("last_sync_epoch_ms")
        val LAST_SYNC_REMOTE_REACHED = booleanPreferencesKey("last_sync_remote_reached")
        // IIC round-trip: pending session from the GameNative fork broadcast.
        val PENDING_SESSION_GAME_ID = stringPreferencesKey("pending_session_game_id")
        val PENDING_SESSION_MINUTES = intPreferencesKey("pending_session_minutes")
        val PENDING_SESSION_SHOWED_FPS = booleanPreferencesKey("pending_session_showed_fps")
        // Feature C: forward-compat avg_fps + stability extras from the fork.
        val PENDING_SESSION_AVG_FPS = intPreferencesKey("pending_session_avg_fps")
        val PENDING_SESSION_STABILITY = stringPreferencesKey("pending_session_stability")
        // v0.11: Windows quick-start onboarding card.
        val WINDOWS_QUICK_START_DISMISSED = booleanPreferencesKey("windows_quick_start_dismissed")
        // Feature B: POST_NOTIFICATIONS contextual ask-once flag.
        val NOTIF_PERM_ASKED = booleanPreferencesKey("notif_perm_asked")
        // self-update
        val LAST_UPDATE_CHECK_MS = longPreferencesKey("last_update_check_ms")
        val UPDATE_AUTO_CHECK_ENABLED = booleanPreferencesKey("update_auto_check_enabled")
        val PENDING_UPDATE_VERSION = stringPreferencesKey("pending_update_version")
        val PENDING_UPDATE_URL = stringPreferencesKey("pending_update_url")
        val PENDING_UPDATE_FILENAME = stringPreferencesKey("pending_update_filename")
        val PENDING_UPDATE_NOTES = stringPreferencesKey("pending_update_notes")
        val PENDING_UPDATE_SIZE = longPreferencesKey("pending_update_size")
        val PENDING_UPDATE_SHA256 = stringPreferencesKey("pending_update_sha256")
    }
}
