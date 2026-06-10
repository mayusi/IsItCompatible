package io.github.mayusi.isitcompatible.hardware

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the device fingerprint. Pure read-only — never writes to sysfs, never
 * needs root or Shizuku.
 *
 * Pulls from:
 *  - [Build] (SoC model, manufacturer, model, Android version)
 *  - [ActivityManager.MemoryInfo] (total RAM)
 *  - [GpuProbe] (GPU renderer string via one-shot EGL context)
 *
 * Vulkan probe is deferred — JNI/NDK setup adds Phase-1 noise. Phase 4 swaps
 * the [vulkanApiVersion]/[vulkanExtensions] nulls for real values once we
 * add the NDK side-band.
 */
@Singleton
class HardwareFingerprinter @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun fingerprint(): DeviceFingerprint {
        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN }
        } else null

        val (socFamily, resolvedModel) = SocCatalog.resolve(socModel, Build.HARDWARE)
        val gpu = try {
            GpuProbe.probe(context)
        } catch (t: Throwable) {
            Log.w("Fingerprint", "GPU probe failed", t)
            GpuProbe.GlInfo(null, null, null, emptyList())
        }
        val vendor = GpuVendor.fromRendererString(gpu.renderer)

        val memInfo = ActivityManager.MemoryInfo().also {
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
                .getMemoryInfo(it)
        }
        val totalRamMb = (memInfo.totalMem / (1024L * 1024L)).toInt()

        return DeviceFingerprint(
            socFamily = socFamily,
            socModel = resolvedModel,
            gpuVendor = vendor,
            gpuModel = gpu.renderer?.cleanRenderer() ?: "Unknown GPU",
            gpuDriver = gpu.version ?: "Unknown driver",
            totalRamMb = totalRamMb,
            androidApi = Build.VERSION.SDK_INT,
            androidRelease = Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString(),
            vulkanApiVersion = null,
            vulkanExtensions = emptyList(),
            manufacturer = Build.MANUFACTURER ?: "Unknown",
            model = Build.MODEL ?: "Unknown",
        )
    }

    // GL renderer strings often look like "Adreno (TM) 750" — strip the noise.
    private fun String.cleanRenderer(): String =
        replace("(TM)", "", ignoreCase = true)
            .replace("(R)", "", ignoreCase = true)
            .replace(Regex("\\s+"), " ")
            .trim()
}
