package com.artt.minibrowser

import com.artt.minibrowser.engine.performancePolicyFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PerformancePolicyTest {
    private val mib = 1024L * 1024L
    private val gib = 1024L * mib

    @Test fun previewCacheNeverExceedsOneSixteenthOfAppHeap() {
        val policy = performancePolicyFor(
            totalMemoryBytes = 12L * gib,
            appHeapBytes = 32L * mib,
            lowRamDevice = false,
        )

        assertEquals(2L * mib, policy.previewCacheBytes)
        assertEquals(1L * mib, policy.backgroundPreviewCacheBytes)
        assertTrue(policy.previewCacheBytes <= policy.appHeapBytes / 16L)
    }

    @Test fun highMemoryPolicyStillHonorsRamPreviewCap() {
        val policy = performancePolicyFor(
            totalMemoryBytes = 12L * gib,
            appHeapBytes = 512L * mib,
            lowRamDevice = false,
        )

        assertEquals(12, policy.hotTabLimit)
        assertEquals(4, policy.backgroundHotTabLimit)
        assertEquals(16L * mib, policy.previewCacheBytes)
        assertEquals(4L * mib, policy.backgroundPreviewCacheBytes)
    }

    @Test fun lowRamFlagKeepsConservativeTierOnLargeDevice() {
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

    @Test fun invalidNegativeHeapCannotCreateNegativeCacheBudget() {
        val policy = performancePolicyFor(
            totalMemoryBytes = 4L * gib,
            appHeapBytes = -1L,
            lowRamDevice = false,
        )

        assertEquals(0L, policy.previewCacheBytes)
        assertEquals(0L, policy.backgroundPreviewCacheBytes)
    }
}
