package io.github.mayusi.isitcompatible.hardware

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GpuVendorTest {

    @Test
    fun `identifies Adreno from renderer string`() {
        assertThat(GpuVendor.fromRendererString("Adreno (TM) 750"))
            .isEqualTo(GpuVendor.ADRENO)
    }

    @Test
    fun `identifies Mali`() {
        assertThat(GpuVendor.fromRendererString("Mali-G715 Immortalis"))
            .isEqualTo(GpuVendor.MALI)
    }

    @Test
    fun `returns UNKNOWN for null or blank`() {
        assertThat(GpuVendor.fromRendererString(null)).isEqualTo(GpuVendor.UNKNOWN)
        assertThat(GpuVendor.fromRendererString("")).isEqualTo(GpuVendor.UNKNOWN)
    }

    @Test
    fun `returns OTHER for unrecognised renderer`() {
        assertThat(GpuVendor.fromRendererString("VideoCore VII"))
            .isEqualTo(GpuVendor.OTHER)
    }
}
