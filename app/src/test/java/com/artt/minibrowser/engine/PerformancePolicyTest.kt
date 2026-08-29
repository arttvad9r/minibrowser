package com.artt.minibrowser.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class PerformancePolicyTest {
    @Test
    fun highMemoryDeviceKeepsLargerHotSet() {
        val gib = 1024L * 1024L * 1024L
        val policy = performancePolicyFor(12L * gib, lowRamDevice = false)
        assertEquals(12, policy.hotTabLimit)
        assertEquals(16L * 1024L * 1024L, policy.previewCacheBytes)
        assertEquals(4L * 1024L * 1024L, policy.backgroundPreviewCacheBytes)
    }

    @Test
    fun lowRamFlagOverridesPhysicalMemory() {
        val gib = 1024L * 1024L * 1024L
        val policy = performancePolicyFor(12L * gib, lowRamDevice = true)
        assertEquals(3, policy.hotTabLimit)
        assertEquals(4L * 1024L * 1024L, policy.previewCacheBytes)
    }
}
