package com.artt.minibrowser.engine

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
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
        installImeHostResize()
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
     * Android 15 edge-to-edge can leave the Activity content at full display height even with
     * adjustResize. Resize the actual Activity content host to the visible area above the IME so
     * Compose remeasures its AndroidView and GeckoView receives a genuinely smaller viewport.
     * This deliberately avoids padding the host and avoids GeckoView vertical clipping: both only
     * obscure/move content, while fixed web UI needs a real layout-size change.
     */
    private fun installImeHostResize() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity !is MainActivity) return
                val content = activity.findViewById<View>(android.R.id.content) ?: return
                content.post {
                    ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
                        val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                        val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                        val keyboardOverlap = (imeBottom - navBottom).coerceAtLeast(0)
                        val fullHeight = view.rootView.height
                        val targetHeight = if (keyboardOverlap > 0 && fullHeight > keyboardOverlap) {
                            fullHeight - keyboardOverlap
                        } else {
                            ViewGroup.LayoutParams.MATCH_PARENT
                        }
                        val params = view.layoutParams
                        if (params.height != targetHeight) {
                            params.height = targetHeight
                            view.layoutParams = params
                            view.requestLayout()
                        }
                        insets
                    }
                    ViewCompat.requestApplyInsets(content)
                }
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
