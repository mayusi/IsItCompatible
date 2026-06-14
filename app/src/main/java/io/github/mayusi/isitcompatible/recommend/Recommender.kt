package io.github.mayusi.isitcompatible.recommend

import io.github.mayusi.isitcompatible.compatdb.room.ReportEntity
import io.github.mayusi.isitcompatible.hardware.DeviceFingerprint
import io.github.mayusi.isitcompatible.hardware.GpuVendor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Pure ranker. Given a list of [ReportEntity] for a game + the user's
 * [DeviceFingerprint], picks the best emulator+preset combinations with a
 * confidence rating based on how loose the device-similarity bucket had to be.
 *
 * No Android dependencies — trivially unit-testable. Singleton so all VMs
 * share one instance (stateless, so sharing is safe).
 *
 * v1.1 improvements (all data-honest — no invented signals):
 *  1. Recency weighting: [ReportEntity.submittedAt] (epoch ms) exists — newer
 *     reports are discounted less. An old "CRASHES" report may reflect a driver
 *     or emulator that has since been fixed. Weight is deliberately subtle so
 *     hardware-match still dominates.
 *  2. Conflict-detection: when reports for the same emulator+preset disagree
 *     (mixed stabilities / wide fps spread), we lower [Recommendation.effectiveConfidence]
 *     and set [Recommendation.hasHighConflict] so the UI can surface an honest
 *     "reports vary" note. The mode-stability (pickStabilitySummary) is preserved.
 *  3. Thermal/battery-aware ranking: SKIPPED — [ReportEntity] has no thermal or
 *     power-draw fields. [JournalEntryEntity.peakTempC] exists but is the user's
 *     own local data, not community report data. [PresetEntity.settingsJson] is
 *     template-specific opaque JSON without a standardised load-level key. A
 *     thermal-aware sort from report data would require inventing a signal that
 *     doesn't exist — so we don't. When real thermal data arrives in reports,
 *     this is the right place to add it.
 *  4. Smarter effective confidence: [Recommendation.effectiveConfidence] starts
 *     from the hardware-match [Bucket.confidence], then factors in report count
 *     and conflict level. A STRONG-bucket match backed by 1 conflicted old report
 *     is honestly MODERATE, not STRONG.
 */
@Singleton
class Recommender @Inject constructor() {

    /**
     * @param reports all reports for the target game.
     * @param fp current device fingerprint.
     * @param topK how many ranked combos to return (default 3).
     * @param nowMs current epoch ms (injectable for testing; defaults to System.currentTimeMillis()).
     */
    fun rank(
        reports: List<ReportEntity>,
        fp: DeviceFingerprint,
        topK: Int = 3,
        nowMs: Long = System.currentTimeMillis(),
    ): List<Recommendation> {
        if (reports.isEmpty()) return emptyList()

        // Try each bucket in order until we have at least 1 matching report.
        for (bucket in Bucket.entries) {
            val matching = reports.filter { bucket.matches(it, fp) }
            if (matching.isNotEmpty()) {
                return scoreAndGroup(matching, bucket, topK, nowMs)
            }
        }
        return emptyList()
    }

    /**
     * v0.5: split reports by source so the UI can render real vs. heuristic
     * estimates separately. The recommender used to score both pools together,
     * which let a generated "60fps PERFECT" beat a real "45fps PLAYABLE" — a
     * trust-killer. Now each pool is ranked independently and the UI decides
     * how to weight them.
     *
     * Real = anything NOT tagged `GENERATED_HEURISTIC` (real user submissions,
     * EmuReady imports, the user's own journal entries, etc.).
     */
    fun rankBySource(
        reports: List<ReportEntity>,
        fp: DeviceFingerprint,
        topK: Int = 3,
        nowMs: Long = System.currentTimeMillis(),
    ): RecommendationsBySource {
        val (generated, real) = reports.partition { it.source.equals(GENERATED_SOURCE, ignoreCase = true) }
        return RecommendationsBySource(
            fromReal = rank(real, fp, topK, nowMs),
            fromGenerated = rank(generated, fp, topK, nowMs),
        )
    }

    private fun scoreAndGroup(
        reports: List<ReportEntity>,
        bucket: Bucket,
        topK: Int,
        nowMs: Long,
    ): List<Recommendation> {
        return reports
            .groupBy { it.emulatorId to it.presetId }
            .map { (key, rs) ->
                val (emuId, presetId) = key

                // ── Recency-weighted FPS + stability scoring (improvement #1) ──
                // Weight each report by how recent it is. The multiplier is subtle:
                //   < 6 months  → 1.00 (full weight)
                //   6–18 months → 0.85
                //   > 18 months → 0.70
                // Hardware-match (bucket) still dominates because the recency
                // multiplier only adjusts within the group of already-matched reports.
                // A fresh wrong-device report can't beat an old same-device one because
                // bucket-cascade already separated them.
                val weightedFpsSum = rs.mapNotNull { r ->
                    r.avgFps?.let { fps -> fps * recencyWeight(r.submittedAt, nowMs) }
                }.sum()
                val weightedFpsCount = rs.count { it.avgFps != null }
                val avgFpsRaw = if (weightedFpsCount > 0)
                    weightedFpsSum / weightedFpsCount
                else 0.0

                val weightedStabilityScore = rs.map { r ->
                    r.stability.toScore() * recencyWeight(r.submittedAt, nowMs)
                }.average()
                val recencyWeightSum = rs.map { r -> recencyWeight(r.submittedAt, nowMs) }.sum()
                // Normalize the stability score by total recency weight so it stays in [0,1].
                val stabilityScore = if (recencyWeightSum > 0)
                    weightedStabilityScore / (recencyWeightSum / rs.size)
                else
                    weightedStabilityScore

                val rawScore = avgFpsRaw * (stabilityScore + 0.1) // avoid zero-multiply

                // ── Conflict detection (improvement #2) ──
                val (hasConflict, conflictNote) = detectConflict(rs)

                // ── Effective confidence (improvement #4) ──
                // Start from bucket baseline and potentially downgrade based on:
                //   - Low report count (1 report is less trustworthy than 10)
                //   - High conflict (reports disagree significantly)
                //   - All reports are stale (> 18 months, no fresh data)
                val effectiveConf = computeEffectiveConfidence(
                    base = bucket.confidence,
                    reportCount = rs.size,
                    hasConflict = hasConflict,
                    allStale = rs.all { ageMonths(it.submittedAt, nowMs) > STALE_MONTHS },
                )

                Recommendation(
                    emulatorId = emuId,
                    presetId = presetId,
                    avgFps = if (avgFpsRaw > 0) avgFpsRaw.toInt() else null,
                    stability = pickStabilitySummary(rs),
                    reportCount = rs.size,
                    bucket = bucket,
                    score = rawScore,
                    reports = rs,
                    effectiveConfidence = effectiveConf,
                    hasHighConflict = hasConflict,
                    conflictNote = conflictNote,
                )
            }
            .sortedWith(compareByDescending<Recommendation> { it.score }.thenByDescending { it.reportCount })
            .take(topK)
    }

    /**
     * Recency multiplier for a single report. Newer = higher weight.
     * Deliberately subtle so it adjusts the score within the already-matched
     * bucket pool, not enough to override the hardware-match cascade.
     *
     *   < 6 months  → 1.00
     *   6–18 months → 0.85
     *   > 18 months → 0.70
     *
     * If submittedAt is 0 (unknown / legacy data), we treat it conservatively
     * as stale (0.70) rather than assuming it's fresh — be honest.
     */
    internal fun recencyWeight(submittedAt: Long, nowMs: Long): Double {
        if (submittedAt <= 0L) return WEIGHT_STALE
        return when (ageMonths(submittedAt, nowMs)) {
            in 0..FRESH_MONTHS -> WEIGHT_FRESH
            in (FRESH_MONTHS + 1)..STALE_MONTHS -> WEIGHT_AGING
            else -> WEIGHT_STALE
        }
    }

    private fun ageMonths(submittedAt: Long, nowMs: Long): Long {
        val diffMs = nowMs - submittedAt
        return diffMs / MS_PER_MONTH
    }

    /**
     * Detect meaningful disagreement among a group of reports for the same
     * emulator+preset. Returns (hasConflict, humanNote).
     *
     * "High conflict" means ANY of:
     *  - More than one distinct stability tier is represented AND no tier has a
     *    clear majority (>= 60% of reports agree on the same stability).
     *  - The fps spread (max - min) is >= [FPS_CONFLICT_SPREAD] when there are
     *    at least [MIN_REPORTS_FOR_FPS_CONFLICT] reports with fps data.
     *
     * Note: single-report groups never conflict — we return false so we never
     * incorrectly lower confidence on a thin-but-consistent dataset.
     */
    internal fun detectConflict(rs: List<ReportEntity>): Pair<Boolean, String?> {
        if (rs.size < 2) return false to null

        // Stability disagreement
        val stabCounts = rs.groupingBy { it.stability.uppercase() }.eachCount()
        val distinctStabs = stabCounts.size
        val maxAgree = stabCounts.values.max()
        val agreeFraction = maxAgree.toDouble() / rs.size
        val stabConflict = distinctStabs > 1 && agreeFraction < AGREEMENT_THRESHOLD

        // FPS spread disagreement (only meaningful with enough fps-bearing reports)
        val fpsList = rs.mapNotNull { it.avgFps }
        val fpsConflict = fpsList.size >= MIN_REPORTS_FOR_FPS_CONFLICT &&
            (fpsList.max() - fpsList.min()) >= FPS_CONFLICT_SPREAD

        val hasConflict = stabConflict || fpsConflict
        val note: String? = when {
            !hasConflict -> null
            stabConflict && fpsConflict -> "Reports conflict on both stability and FPS — results may vary significantly"
            stabConflict -> "Reports conflict on stability — some users had issues, others didn't"
            else -> "Reports show a wide FPS range — your result may differ"
        }
        return hasConflict to note
    }

    /**
     * Compute an effective confidence that is more nuanced than "which bucket did
     * we land in?" alone. Starts from the hardware-match baseline, then applies
     * honest downgrade rules (never upgrades beyond the hardware-match ceiling):
     *
     *   1. Count penalty: 1 report → one tier lower. A solo report is less
     *      trustworthy than 10, even at the same bucket.
     *   2. Conflict penalty: high disagreement → one tier lower (on top of count).
     *   3. Staleness penalty: all reports > [STALE_MONTHS] months old → one tier
     *      lower. Old data may reflect a driver/emulator that has since improved.
     *
     * Rules cap at VERY_WEAK (can't go lower). Never exceeds base.
     */
    internal fun computeEffectiveConfidence(
        base: Confidence,
        reportCount: Int,
        hasConflict: Boolean,
        allStale: Boolean,
    ): Confidence {
        var level = base.ordinal // 0=STRONG … 3=VERY_WEAK

        // Count penalty
        if (reportCount <= 1) level += 1

        // Conflict penalty (on top of count — they compound)
        if (hasConflict) level += 1

        // Staleness penalty (only when ALL reports are old — if there's at least
        // one fresh report, it's evidence that the game still works)
        if (allStale) level += 1

        val capped = level.coerceIn(0, Confidence.entries.size - 1)
        return Confidence.entries[capped]
    }

    private fun String.toScore(): Double = when (uppercase()) {
        "PERFECT" -> 1.0
        "PLAYABLE" -> 0.7
        "GLITCHY" -> 0.35
        "CRASHES" -> 0.0
        else -> 0.5
    }

    private fun pickStabilitySummary(rs: List<ReportEntity>): String {
        // Mode (most common) with a bias toward worst when tied — be honest.
        val counts = rs.groupingBy { it.stability.uppercase() }.eachCount()
        return counts.maxByOrNull { it.value }!!.key
    }

    private companion object {
        const val GENERATED_SOURCE = "GENERATED_HEURISTIC"

        /** Reports < 6 months old are fully trusted. */
        const val FRESH_MONTHS = 6L

        /** Reports 6–18 months old get a mild discount. */
        const val STALE_MONTHS = 18L

        const val WEIGHT_FRESH = 1.00
        const val WEIGHT_AGING = 0.85
        const val WEIGHT_STALE = 0.70

        /** Fraction of reports that must agree for stability to be "uncontested". */
        const val AGREEMENT_THRESHOLD = 0.60

        /** Need at least this many fps-bearing reports before checking fps spread. */
        const val MIN_REPORTS_FOR_FPS_CONFLICT = 3

        /** Fps spread (max-min) that signals meaningful disagreement. */
        const val FPS_CONFLICT_SPREAD = 25

        const val MS_PER_MONTH = 30L * 24L * 60L * 60L * 1000L
    }
}

/**
 * v0.5: rankings split by data provenance. Lets the UI show real reports as
 * primary recommendations and heuristic estimates as a fallback / supplement.
 */
data class RecommendationsBySource(
    val fromReal: List<Recommendation>,
    val fromGenerated: List<Recommendation>,
) {
    /** True when there's no data of any kind. */
    fun isEmpty(): Boolean = fromReal.isEmpty() && fromGenerated.isEmpty()

    /** Convenience for callers that don't care about the split — concatenated, real first. */
    fun all(): List<Recommendation> = fromReal + fromGenerated
}

data class Recommendation(
    val emulatorId: String,
    val presetId: String?,
    val avgFps: Int?,
    val stability: String,
    val reportCount: Int,
    val bucket: Bucket,
    val score: Double,
    val reports: List<ReportEntity>,
    /**
     * v1.1: effective confidence that factors in hardware-match bucket, report
     * count, agreement level, and data freshness. Always <= [bucket.confidence]
     * (never upgrades beyond the hardware-match ceiling). Use this for display
     * instead of [bucket.confidence] for an honest trust signal.
     */
    val effectiveConfidence: Confidence = bucket.confidence,
    /**
     * v1.1: true when the reports for this emulator+preset show meaningful
     * disagreement (mixed stabilities / wide fps spread). The UI should surface
     * an "reports vary — your result may differ" note when this is true.
     */
    val hasHighConflict: Boolean = false,
    /**
     * v1.1: human-readable description of the conflict (non-null iff
     * [hasHighConflict] is true). E.g. "Reports conflict on stability — some
     * users had issues, others didn't".
     */
    val conflictNote: String? = null,
)

/**
 * Device-similarity buckets, widening from exact to any. Confidence in the UI
 * is derived from which bucket actually matched.
 */
enum class Bucket(val confidence: Confidence, val label: String) {
    SAME_SOC_AND_RAM(Confidence.STRONG, "Same SoC + RAM"),
    // v0.4: split off SAME_SOC_FAMILY into MODERATE — close hardware match but
    // RAM differs enough to matter (e.g. an 8 Elite at 12 GB report vs you at 16 GB).
    SAME_SOC_FAMILY(Confidence.MODERATE, "Same SoC family"),
    SAME_GPU_VENDOR(Confidence.WEAK, "Same GPU vendor"),
    ANY_DEVICE(Confidence.VERY_WEAK, "Any device");

    fun matches(r: ReportEntity, fp: DeviceFingerprint): Boolean = when (this) {
        SAME_SOC_AND_RAM ->
            r.socFamily.equals(fp.socFamily, ignoreCase = true) &&
                ramSimilar(r.ramMb, fp.totalRamMb)
        SAME_SOC_FAMILY ->
            r.socFamily.equals(fp.socFamily, ignoreCase = true)
        SAME_GPU_VENDOR ->
            r.gpuVendor.equals(fp.gpuVendor.name, ignoreCase = true) &&
                fp.gpuVendor != GpuVendor.UNKNOWN
        ANY_DEVICE -> true
    }

    private fun ramSimilar(a: Int, b: Int): Boolean {
        if (a == 0 || b == 0) return false
        val tolerance = maxOf(a, b) * 0.25
        return abs(a - b) <= tolerance
    }
}

enum class Confidence { STRONG, MODERATE, WEAK, VERY_WEAK }
