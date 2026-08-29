package com.artt.minibrowser.engine

import android.app.ActivityManager
import android.content.Context

private const val GIB = 1024L * 1024L * 1024L

/**
 * Capability-based browser resource policy.
 *
 * Do not key performance behavior to a marketing model name. Android reports the memory that is
 * actually available to this device/ROM, so the same build stays sensible after an OS update and
 * on other phones. A OnePlus 13s (12 GB RAM) lands in the high-memory tier.
 */
data class BrowserPerformancePolicy(
    val totalMemoryBytes: Long,
    val lowRamDevice: Boolean,
    val hotTabLimit: Int,
    val previewCacheBytes: Long,
    val backgroundPreviewCacheBytes: Long,
)

internal fun performancePolicyFor(
    totalMemoryBytes: Long,
    lowRamDevice: Boolean,
): BrowserPerformancePolicy {
    val hotTabs = when {
        lowRamDevice || totalMemoryBytes < 4L * GIB -> 3
        totalMemoryBytes < 6L * GIB -> 4
        totalMemoryBytes < 10L * GIB -> 6
        else -> 8
    }
    val previewBytes = when {
        lowRamDevice || totalMemoryBytes < 4L * GIB -> 4L * 1024L * 1024L
        totalMemoryBytes < 6L * GIB -> 8L * 1024L * 1024L
        totalMemoryBytes < 10L * GIB -> 12L * 1024L * 1024L
        else -> 16L * 1024L * 1024L
    }
    return BrowserPerformancePolicy(
        totalMemoryBytes = totalMemoryBytes,
        lowRamDevice = lowRamDevice,
        hotTabLimit = hotTabs,
        previewCacheBytes = previewBytes,
        backgroundPreviewCacheBytes = minOf(previewBytes, 4L * 1024L * 1024L),
    )
}

fun detectBrowserPerformancePolicy(context: Context): BrowserPerformancePolicy {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memory = ActivityManager.MemoryInfo()
    manager.getMemoryInfo(memory)
    return performancePolicyFor(memory.totalMem, manager.isLowRamDevice)
}

object BrowserPerformance {
    @Volatile
    var policy: BrowserPerformancePolicy = performancePolicyFor(4L * GIB, false)
        private set

    fun configure(context: Context): BrowserPerformancePolicy =
        detectBrowserPerformancePolicy(context).also { policy = it }
}
