package io.github.mayusi.isitcompatible.hardware

import android.app.ActivityManager
import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.util.Log

/**
 * Probes the GPU renderer string via a one-shot offscreen EGL context.
 *
 * Why an EGL pbuffer instead of [ActivityManager.getDeviceConfigurationInfo]?
 * That API only tells us the GLES major version (e.g. "3.2") — not the
 * renderer string we actually need to identify Adreno 750 vs Adreno 740.
 * The renderer is only exposed once a context is current.
 *
 * Cost: one transient ~10ms EGL context creation. Run once per app launch,
 * cache the result.
 */
internal object GpuProbe {

    private const val TAG = "GpuProbe"

    data class GlInfo(
        val vendor: String?,
        val renderer: String?,
        val version: String?,
        val extensions: List<String>,
    )

    fun probe(context: Context): GlInfo {
        // Bail early if device clearly has no GLES surface (shouldn't happen on Android, but defensive).
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (am?.deviceConfigurationInfo?.reqGlEsVersion ?: 0 < 0x20000) {
            return GlInfo(null, null, null, emptyList())
        }

        var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
        var context0: EGLContext = EGL14.EGL_NO_CONTEXT
        var surface: EGLSurface = EGL14.EGL_NO_SURFACE

        try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) return empty()
            val ver = IntArray(2)
            if (!EGL14.eglInitialize(display, ver, 0, ver, 1)) return empty()

            val configAttrs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, 4 /* EGL_OPENGL_ES2_BIT */,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(display, configAttrs, 0, configs, 0, 1, numConfigs, 0) ||
                numConfigs[0] < 1 || configs[0] == null
            ) return empty()

            val ctxAttrs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            context0 = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttrs, 0)
            if (context0 == EGL14.EGL_NO_CONTEXT) return empty()

            val pbAttrs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
            surface = EGL14.eglCreatePbufferSurface(display, configs[0], pbAttrs, 0)
            if (surface == EGL14.EGL_NO_SURFACE) return empty()
            if (!EGL14.eglMakeCurrent(display, surface, surface, context0)) return empty()

            val vendor = GLES20.glGetString(GLES20.GL_VENDOR)
            val renderer = GLES20.glGetString(GLES20.GL_RENDERER)
            val version = GLES20.glGetString(GLES20.GL_VERSION)
            val extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS)
                ?.split(' ')
                ?.filter { it.isNotBlank() }
                .orEmpty()

            return GlInfo(vendor, renderer, version, extensions)
        } catch (t: Throwable) {
            Log.w(TAG, "EGL probe failed", t)
            return empty()
        } finally {
            if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(
                    display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
                )
                if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                if (context0 != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context0)
                EGL14.eglTerminate(display)
            }
        }
    }

    private fun empty() = GlInfo(null, null, null, emptyList())
}
