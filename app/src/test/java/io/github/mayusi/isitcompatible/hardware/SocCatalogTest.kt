package io.github.mayusi.isitcompatible.hardware

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SocCatalogTest {

    @Test
    fun `resolves Snapdragon 8 Elite by SM model`() {
        val (family, model) = SocCatalog.resolve("SM8750", "qcom")
        assertThat(family).isEqualTo("Snapdragon 8 Elite")
        assertThat(model).isEqualTo("SM8750")
    }

    @Test
    fun `resolves Dimensity 9300 by MT model`() {
        val (family, model) = SocCatalog.resolve("MT6989", "mt6989")
        assertThat(family).isEqualTo("Dimensity 9300")
        assertThat(model).isEqualTo("MT6989")
    }

    @Test
    fun `falls back to Build_HARDWARE when SOC_MODEL is blank`() {
        val (family, _) = SocCatalog.resolve("", "RK3588")
        assertThat(family).isEqualTo("Rockchip RK3588")
    }

    @Test
    fun `returns Unknown SoC label when both inputs are junk`() {
        val (family, _) = SocCatalog.resolve(null, "qcom")
        assertThat(family).isEqualTo("Unknown SoC")
    }

    @Test
    fun `hint matcher catches Snapdragon 8 Gen 2 from junk SOC_MODEL`() {
        val (family, _) = SocCatalog.resolve("Some Vendor Snapdragon 8 Gen 2 Chip", "qcom")
        assertThat(family).isEqualTo("Snapdragon 8 Gen 2")
    }
}
