package io.github.mayusi.isitcompatible.hardware

import kotlinx.serialization.Serializable

/**
 * What we know about the user's device. Used by the recommender to find
 * compatibility reports from similar hardware.
 *
 * Free-form strings rather than enums because the SoC/GPU landscape changes
 * every quarter and we don't want to ship an app update every time a new
 * chip arrives.
 */
@Serializable
data class DeviceFingerprint(
    val socFamily: String,           // e.g. "Snapdragon 8 Elite", "Snapdragon 8 Gen 2", "Dimensity 9300"
    val socModel: String,            // e.g. "SM8750", "MT6989"
    val gpuVendor: GpuVendor,
    val gpuModel: String,            // e.g. "Adreno 750", "Mali-G715 Immortalis"
    val gpuDriver: String,           // raw GL_RENDERER / VK driver string
    val totalRamMb: Int,
    val androidApi: Int,             // Build.VERSION.SDK_INT
    val androidRelease: String,      // e.g. "14"
    val vulkanApiVersion: String?,   // e.g. "1.3.275", null if we couldn't probe
    val vulkanExtensions: List<String> = emptyList(),
    val manufacturer: String,        // Build.MANUFACTURER
    val model: String,               // Build.MODEL
) {
    /** Compact human label for the wizard / settings screen. */
    val displayLine: String
        get() = "$socFamily · $gpuModel · ${totalRamMb / 1024} GB · Android $androidRelease"
}

enum class GpuVendor {
    ADRENO, MALI, POWERVR, NVIDIA, INTEL, OTHER, UNKNOWN;

    companion object {
        fun fromRendererString(renderer: String?): GpuVendor {
            if (renderer.isNullOrBlank()) return UNKNOWN
            val r = renderer.lowercase()
            return when {
                "adreno" in r -> ADRENO
                "mali" in r -> MALI
                "powervr" in r -> POWERVR
                "nvidia" in r || "tegra" in r -> NVIDIA
                "intel" in r -> INTEL
                else -> OTHER
            }
        }
    }
}
