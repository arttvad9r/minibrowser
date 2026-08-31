package com.artt.minibrowser.engine

import android.app.ActivityManager
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.Trace
import com.artt.minibrowser.BuildConfig
import com.artt.minibrowser.data.DbHolder
import com.artt.minibrowser.ui.TabPreviewStore
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import java.io.File

object Engine { lateinit var runtime: GeckoRuntime }

internal const val GECKO_RUNTIME_CREATE_TRACE = "GeckoRuntime.create"

internal fun isMainApplicationProcess(currentProcess: String?, mainProcess: String): Boolean =
    currentProcess == null || currentProcess == mainProcess

class BrowserApp : Application() {
    internal val tabPreviewStore by lazy(LazyThreadSafetyMode.NONE) { TabPreviewStore() }
    private var mainProcess = false

    override fun onCreate() {
        super.onCreate()
        // GV child processes (:gpu, :tab, ...) instantiate Application as well. Resolve the actual
        // process name on API 26-27 instead of assuming the main process when /proc is unavailable.
        mainProcess = isMainApplicationProcess(currentProcessName(), applicationInfo.processName)
        if (!mainProcess) return

        // Keep Room lazy: history/bookmark access creates it only after browser UI startup.
        DbHolder.init(this)

        val performance = BrowserPerformance.configure(this)
        tabPreviewStore.configureMemoryPolicy(
            maxBytes = performance.previewCacheBytes,
            backgroundBytes = performance.backgroundPreviewCacheBytes,
        )
        // Download history is intentionally lazy: most launches never open Downloads or start a
        // transfer, so reading/parsing downloads.json must not compete with GeckoRuntime cold start.
        val contentBlocking = ContentBlocking.Settings.Builder()
            .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
            .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
            .cookieBehaviorPrivateMode(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
            .enhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.DEFAULT)
            .enhancedTrackingProtectionCategory(ContentBlocking.EtpCategory.STANDARD)
            .allowListBaselineTrackingProtection(true)
            .allowListConvenienceTrackingProtection(true)
            .build()
        Trace.beginSection(GECKO_RUNTIME_CREATE_TRACE)
        try {
            Engine.runtime = GeckoRuntime.create(
                this,
                GeckoRuntimeSettings.Builder()
                    .aboutConfigEnabled(BuildConfig.DEBUG)
                    // GeckoView logging is enabled by default. Keep it for debug builds, but avoid the
                    // formatting/IPC/logcat overhead in the release build used on the phone.
                    .debugLogging(BuildConfig.DEBUG)
                    .contentBlocking(contentBlocking)
                    .setLnaBlocking(true)
                    .build()
            )
        } finally {
            Trace.endSection()
        }
        if (!performance.lowRamDevice && performance.totalMemoryBytes >= 6L * 1024L * 1024L * 1024L) {
            // Gecko documents this as a pure performance feature. On multicore/high-memory phones,
            // parallel marking trades spare CPU cores for shorter JavaScript GC marking pauses.
            Engine.runtime.settings.setParallelMarkingEnabled(true)
        }
    }

    /**
     * Android 14+ only reliably delivers the UI_HIDDEN/BACKGROUND trim levels. Preview bitmaps are
     * fully reconstructible, so release them before the system has to reclaim or kill processes.
     */
    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (!mainProcess) return
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> tabPreviewStore.trimMemory(aggressive = true)
            level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> tabPreviewStore.trimMemory(aggressive = false)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (mainProcess) tabPreviewStore.trimMemory(aggressive = true)
    }

    private fun currentProcessName(): String? {
        if (Build.VERSION.SDK_INT >= 28) return Application.getProcessName()

        val fromProc = runCatching {
            File("/proc/self/cmdline").inputStream().use { input ->
                input.readBytes()
                    .takeWhile { it != 0.toByte() }
                    .toByteArray()
                    .toString(Charsets.UTF_8)
                    .trim()
                    .takeIf { it.isNotEmpty() }
            }
        }.getOrNull()
        if (fromProc != null) return fromProc

        val pid = Process.myPid()
        return runCatching {
            val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            manager.runningAppProcesses
                ?.firstOrNull { it.pid == pid }
                ?.processName
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
