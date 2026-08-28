package com.artt.minibrowser.engine

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.artt.minibrowser.BuildConfig
import com.artt.minibrowser.MainActivity
import com.artt.minibrowser.data.DbHolder
import com.artt.minibrowser.data.DownloadHistory
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

object Engine { lateinit var runtime: GeckoRuntime }

class BrowserApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // GV-дочерние процессы (:gpu, :tab, ...) наследуют Application — рантайм только в главном.
        if (Build.VERSION.SDK_INT >= 28 && Application.getProcessName().contains(":")) return
        DbHolder.init(this)
        DownloadHistory.init(this)
        installMainActivityImeInsets()
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

    /**
     * Android 15+ edge-to-edge can leave the Compose host at full height even though MainActivity
     * requests adjustResize. GeckoView then receives a viewport that still extends under the IME,
     * so fixed web inputs (ChatGPT's composer is a common example) can end up behind the keyboard.
     *
     * Apply only the IME delta to the Activity content root. MainActivity already consumes system
     * bars inside Compose, so subtract the navigation-bar inset to avoid double bottom spacing.
     */
    private fun installMainActivityImeInsets() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity !is MainActivity) return
                val content = activity.findViewById<View>(android.R.id.content) ?: return
                val baseLeft = content.paddingLeft
                val baseTop = content.paddingTop
                val baseRight = content.paddingRight
                val baseBottom = content.paddingBottom
                ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
                    val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                    val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                    val keyboardBottom = (imeBottom - navBottom).coerceAtLeast(0)
                    view.setPadding(
                        baseLeft,
                        baseTop,
                        baseRight,
                        baseBottom + keyboardBottom,
                    )
                    insets
                }
                content.post { ViewCompat.requestApplyInsets(content) }
            }

            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
