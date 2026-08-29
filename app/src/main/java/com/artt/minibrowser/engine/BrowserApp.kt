package com.artt.minibrowser.engine

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import com.artt.minibrowser.BuildConfig
import com.artt.minibrowser.data.DbHolder
import com.artt.minibrowser.data.DownloadHistory
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import java.io.File

object Engine { lateinit var runtime: GeckoRuntime }

internal fun isMainApplicationProcess(currentProcess: String?, mainProcess: String): Boolean =
    currentProcess == null || currentProcess == mainProcess

class BrowserApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // GV child processes (:gpu, :tab, ...) instantiate Application as well. Resolve the actual
        // process name on API 26-27 instead of assuming the main process when /proc is unavailable.
        if (!isMainApplicationProcess(currentProcessName(), applicationInfo.processName)) return
        DbHolder.init(this)
        DownloadHistory.init(this)
        val contentBlocking = ContentBlocking.Settings.Builder()
            .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
            .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
            .cookieBehaviorPrivateMode(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
            .enhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.DEFAULT)
            .enhancedTrackingProtectionCategory(ContentBlocking.EtpCategory.STANDARD)
            .allowListBaselineTrackingProtection(true)
            .allowListConvenienceTrackingProtection(true)
            .build()
        Engine.runtime = GeckoRuntime.create(
            this,
            GeckoRuntimeSettings.Builder()
                .aboutConfigEnabled(BuildConfig.DEBUG)
                .contentBlocking(contentBlocking)
                .setLnaBlocking(true)
                .build()
        )
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
