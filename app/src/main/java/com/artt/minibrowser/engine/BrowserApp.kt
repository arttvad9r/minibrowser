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
import org.mozilla.geckoview.GeckoView

object Engine { lateinit var runtime: GeckoRuntime }

class BrowserApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // GV-дочерние процессы (:gpu, :tab, ...) наследуют Application — рантайм только в главном.
        if (Build.VERSION.SDK_INT >= 28 && Application.getProcessName().contains(":")) return
        DbHolder.init(this)
        DownloadHistory.init(this)
        installMainActivityImeClipping()
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
     * GeckoView already observes root WindowInsets for interactive-widget/visual viewport updates.
     * Android 15+ edge-to-edge means the IME can still visibly cover the bottom of the GeckoView,
     * though. Feed that obscured height into GeckoView's dedicated vertical-clipping API instead of
     * padding/resizing the Activity root; Gecko then keeps bottom-fixed web UI above the keyboard.
     */
    private fun installMainActivityImeClipping() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity !is MainActivity) return
                val content = activity.findViewById<View>(android.R.id.content) ?: return
                content.post { attachImeClipping(content) }
            }

            override fun onActivityResumed(activity: Activity) {
                if (activity !is MainActivity) return
                val content = activity.findViewById<View>(android.R.id.content) ?: return
                // Covers OEMs where the AndroidView/GeckoView is attached after onActivityCreated.
                content.post { attachImeClipping(content) }
            }

            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun attachImeClipping(root: View) {
        val geckoView = findGeckoView(root) ?: return
        geckoView.addWindowInsetsListener(IME_CLIPPING_LISTENER_KEY) { _, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val keyboardOverlap = (imeBottom - navBottom).coerceAtLeast(0)
            geckoView.setVerticalClipping(keyboardOverlap)
            insets
        }
        ViewCompat.requestApplyInsets(root.rootView)
    }

    private fun findGeckoView(view: View): GeckoView? {
        if (view is GeckoView) return view
        if (view !is ViewGroup) return null
        for (index in 0 until view.childCount) {
            findGeckoView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private companion object {
        const val IME_CLIPPING_LISTENER_KEY = "MINIBROWSER_IME_CLIPPING"
    }
}
