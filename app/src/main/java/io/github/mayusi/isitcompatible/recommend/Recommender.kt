package io.github.mayusi.isitcompatible.recommend

import io.github.mayusi.isitcompatible.compatdb.room.EmulatorEntity
import io.github.mayusi.isitcompatible.compatdb.room.PresetEntity
import io.github.mayusi.isitcompatible.compatdb.room.ReportEntity
import io.github.mayusi.isitcompatible.hardware.DeviceFingerprint
import io.github.mayusi.isitcompatible.hardware.GpuVendor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure ranker. Given a list of [ReportEntity] for a game + the user's
 * [DeviceFingerprint], picks the best emulator+preset combinations with a
 * confidence rating based on how loose the device-similarity bucket had to be.
 *
 * No Android dependencies — trivially unit-testable. Singleton so all VMs
 * share one instance (stateless, so sharing is safe).
 */
@Singleton
class Recommender @Inject constructor() {

    /**
     * @param reports all reports for the target game.
     * @param fp current device fingerprint.
     * @param topK how many ranked combos to return (default 3).
     */
    fun rank(
        reports: List<ReportEntity>,
        fp: DeviceFingerprint,
        topK: Int = 3,
    ): List<Recommendation> {
        if (reports.isEmpty()) return emptyList()

        // Try each bucket in order until we have at least 1 matching report.
        for (bucket in Bucket.entries) {
            val matching = reports.filter { bucket.matches(it, fp) }
            if (matching.isNotEmpty()) {
                return scoreAndGroup(matching, bucket, topK)
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
    ): RecommendationsBySource {
        val (generated, real) = reports.partition { it.source.equals(GENERATED_SOURCE, ignoreCase = true) }
        return RecommendationsBySource(
            fromReal = rank(real, fp, topK),
            fromGenerated = rank(generated, fp, topK),
        )
    }

    private fun scoreAndGroup(
        reports: List<ReportEntity>,
        bucket: Bucket,
        topK: Int,
    ): List<Recommendation> {
        return reports
            .groupBy { it.emulatorId to it.presetId }
            .map { (key, rs) ->
                val (emuId, presetId) = key
                val avgFps = rs.mapNotNull { it.avgFps }.average().takeIf { !it.isNaN() } ?: 0.0
                val stabilityScore = rs.map { it.stability.toScore() }.average()
                val rawScore = avgFps * (stabilityScore + 0.1) // avoid zero-multiply
                Recommendation(
                    emulatorId = emuId,
                    presetId = presetId,
                    avgFps = if (avgFps > 0) avgFps.toInt() else null,
                    stability = pickStabilitySummary(rs),
                    reportCount = rs.size,
                    bucket = bucket,
                    score = rawScore,
                    reports = rs,
                )
            }
            .sortedWith(compareByDescending<Recommendation> { it.score }.thenByDescending { it.reportCount })
            .take(topK)
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
        return kotlin.math.abs(a - b) <= tolerance
    }
}

enum class Confidence { STRONG, MODERATE, WEAK, VERY_WEAK }
