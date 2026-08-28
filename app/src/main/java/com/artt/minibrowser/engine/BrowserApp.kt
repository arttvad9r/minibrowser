package com.artt.minibrowser.engine

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
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
        installFirefoxStyleImeResize()
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
     * Mirrors Firefox/Focus IME handling: keep GeckoView's own root-window inset listener intact,
     * and resize a normal Android browser-root View after the IME animation. This is deliberately
     * separate from Gecko's visual-viewport handling.
     */
    private fun installFirefoxStyleImeResize() {
        // Mozilla's ImeInsetsSynchronizer is enabled from Android 13 onward. Do not restrict this to
        // Android 15: Firefox applies the same browser-root synchronization on Android 13/14 too.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity !is MainActivity) return
                val browserRoot = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
                browserRoot.post {
                    // Firefox applies IME margins to a normal Android browser root, not to the
                    // ComposeView that renders the browser UI. android.R.id.content is our closest
                    // equivalent and also provides reliable raw WindowInsets.
                    if (browserRoot.layoutParams !is ViewGroup.MarginLayoutParams) return@post

                    ImeInsetsSynchronizer.setup(
                        targetView = browserRoot,
                        insetsSource = browserRoot,
                        synchronizeViewWithIME = false,
                        onIMEAnimationStarted = { isKeyboardShowingUp, _ ->
                            if (!isKeyboardShowingUp) {
                                browserRoot.setBottomMargin(0)
                            }
                        },
                        onIMEAnimationFinished = { isKeyboardShowingUp, keyboardHeight ->
                            if (isKeyboardShowingUp || keyboardHeight == 0) {
                                browserRoot.setBottomMargin(keyboardHeight)
                            }
                        },
                    )
                    ViewCompat.requestApplyInsets(browserRoot)
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

    private fun View.setBottomMargin(bottom: Int) {
        val params = layoutParams as? ViewGroup.MarginLayoutParams ?: return
        if (params.bottomMargin == bottom) return
        params.bottomMargin = bottom
        layoutParams = params
        requestLayout()
    }
}
