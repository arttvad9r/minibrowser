package com.artt.minibrowser.engine

import android.app.ActivityManager
import android.content.Context

private const val MIB = 1024L * 1024L
private const val GIB = 1024L * MIB

/**
 * Capability-based browser resource policy.
 *
 * Gecko sessions are primarily constrained by device-wide RAM, while app-owned bitmaps live in
 * this process' Java heap. Keep those budgets separate so a high-RAM phone with a modest heap does
 * not accidentally spend too much of its heap on tab previews.
 */
data class BrowserPerformancePolicy(
    val totalMemoryBytes: Long,
    val appHeapBytes: Long,
    val lowRamDevice: Boolean,
    val hotTabLimit: Int,
    val backgroundHotTabLimit: Int,
    val previewCacheBytes: Long,
    val backgroundPreviewCacheBytes: Long,
)

internal fun performancePolicyFor(
    totalMemoryBytes: Long,
    appHeapBytes: Long,
    lowRamDevice: Boolean,
): BrowserPerformancePolicy {
    val hotTabs = when {
        lowRamDevice || totalMemoryBytes < 4L * GIB -> 3
        totalMemoryBytes < 6L * GIB -> 4
        totalMemoryBytes < 10L * GIB -> 6
        // High-memory phones can keep a useful MRU working set warm while foregrounded.
        else -> 12
    }
    val backgroundHotTabs = when {
        lowRamDevice || totalMemoryBytes < 4L * GIB -> 1
        totalMemoryBytes < 6L * GIB -> 2
        totalMemoryBytes < 10L * GIB -> 3
        else -> 4
    }
    val ramPreviewBudget = when {
        lowRamDevice || totalMemoryBytes < 4L * GIB -> 4L * MIB
        totalMemoryBytes < 6L * GIB -> 8L * MIB
        totalMemoryBytes < 10L * GIB -> 12L * MIB
        else -> 16L * MIB
    }
    // Never let reconstructible previews consume more than roughly one sixteenth of the app heap.
    // Keep a small floor so the switcher can still retain at least a couple of downscaled cards.
    val heapPreviewBudget = (appHeapBytes / 16L).coerceAtLeast(4L * MIB)
    val previewBytes = minOf(ramPreviewBudget, heapPreviewBudget)
    val backgroundPreviewBytes = minOf(previewBytes / 2L, 4L * MIB)

    return BrowserPerformancePolicy(
        totalMemoryBytes = totalMemoryBytes,
        appHeapBytes = appHeapBytes,
        lowRamDevice = lowRamDevice,
        hotTabLimit = hotTabs,
        backgroundHotTabLimit = backgroundHotTabs,
        previewCacheBytes = previewBytes,
        backgroundPreviewCacheBytes = backgroundPreviewBytes,
    )
}

fun detectBrowserPerformancePolicy(context: Context): BrowserPerformancePolicy {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memory = ActivityManager.MemoryInfo()
    manager.getMemoryInfo(memory)
    return performancePolicyFor(
        totalMemoryBytes = memory.totalMem,
        appHeapBytes = manager.memoryClass.toLong() * MIB,
        lowRamDevice = manager.isLowRamDevice,
    )
}

object BrowserPerformance {
    @Volatile
    var policy: BrowserPerformancePolicy = performancePolicyFor(
        totalMemoryBytes = 4L * GIB,
        appHeapBytes = 256L * MIB,
        lowRamDevice = false,
    )
        private set

    fun configure(context: Context): BrowserPerformancePolicy =
        detectBrowserPerformancePolicy(context).also { policy = it }
}
