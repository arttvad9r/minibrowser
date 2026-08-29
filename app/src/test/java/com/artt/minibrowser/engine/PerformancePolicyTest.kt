package com.artt.minibrowser.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class PerformancePolicyTest {
    private val mib = 1024L * 1024L
    private val gib = 1024L * mib

    @Test
    fun highMemoryDeviceKeepsLargerForegroundSet() {
        val policy = performancePolicyFor(
            totalMemoryBytes = 12L * gib,
            appHeapBytes = 256L * mib,
            lowRamDevice = false,
        )
        assertEquals(12, policy.hotTabLimit)
        assertEquals(4, policy.backgroundHotTabLimit)
        assertEquals(16L * mib, policy.previewCacheBytes)
        assertEquals(4L * mib, policy.backgroundPreviewCacheBytes)
    }

    @Test
    fun appHeapCapsPreviewMemoryEvenWithPlentyOfPhysicalRam() {
        val policy = performancePolicyFor(
            totalMemoryBytes = 12L * gib,
            appHeapBytes = 96L * mib,
            lowRamDevice = false,
        )
        assertEquals(12, policy.hotTabLimit)
        assertEquals(6L * mib, policy.previewCacheBytes)
        assertEquals(3L * mib, policy.backgroundPreviewCacheBytes)
    }

    @Test
    fun lowRamFlagOverridesPhysicalMemory() {
        val policy = performancePolicyFor(
            totalMemoryBytes = 12L * gib,
            appHeapBytes = 512L * mib,
            lowRamDevice = true,
        )
        assertEquals(3, policy.hotTabLimit)
        assertEquals(1, policy.backgroundHotTabLimit)
        assertEquals(4L * mib, policy.previewCacheBytes)
        assertEquals(2L * mib, policy.backgroundPreviewCacheBytes)
    }
}
