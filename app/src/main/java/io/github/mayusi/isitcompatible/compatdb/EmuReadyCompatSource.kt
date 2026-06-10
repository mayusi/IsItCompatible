package io.github.mayusi.isitcompatible.compatdb

import javax.inject.Inject
import javax.inject.Singleton

/**
 * EmuReady community data does NOT come from a live in-app fetch — by design.
 *
 * Honesty + offline rules (locked decision): the app never scrapes or calls
 * EmuReady at runtime. Instead, real EmuReady community reports are ingested at
 * BUILD time by the maintainer-run tool `tools/ingest/ingest.py`, normalised to
 * the seed DTO shape, and committed to
 *   app/src/main/assets/seed/reports/emuready-snapshot.json   (source = EMUREADY_SNAPSHOT)
 *   app/src/main/assets/seed/guides/gamenative-community.json (tier-3 community guides)
 * Those files are loaded by [BundledCompatSource] like any other seed file, and
 * the recommender treats EMUREADY_SNAPSHOT (anything != GENERATED_HEURISTIC) as
 * REAL, non-heuristic data. So EmuReady-derived data IS present and real — it
 * just arrives via the bundled snapshot, not a live call.
 *
 * This source therefore intentionally returns null: there is no live fetch to
 * perform, and pretending otherwise would be dishonest. The hook stays in the
 * merge so a future, explicitly-consented live/delta sync could be added here
 * without re-plumbing — but until then it is a deliberate no-op.
 */
@Singleton
class EmuReadyCompatSource @Inject constructor() : CompatSource {
    override val sourceTag = "EMUREADY_SNAPSHOT"

    /** No live ingest. EmuReady data ships as bundled seed (see class KDoc). */
    override suspend fun fetch(): CompatSnapshot? = null
}
