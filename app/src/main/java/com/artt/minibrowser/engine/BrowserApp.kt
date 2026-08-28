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
     * Firefox/Focus keeps Gecko's own root IME-inset handling intact and separately resizes the
     * browser UI root after the keyboard animation. On Android 15+ this avoids both failure modes
     * we saw with manual clipping: a full-height page behind the keyboard and bottom-fixed web UI
     * being moved without the actual GeckoView/Compose viewport shrinking.
     */
    private fun installFirefoxStyleImeResize() {
        // Android 13/14 still use the normal adjustResize path in Minibrowser. Android 15+ enforces
        // edge-to-edge for targetSdk 35, which is where Firefox's explicit IME synchronization is
        // needed in our current layout.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity !is MainActivity) return
                val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
                content.post {
                    // ComponentActivity.setContent() installs one ComposeView in android.R.id.content.
                    // Resize that child, while taking insets from the non-Compose parent as Mozilla
                    // recommends (ComposeView is not a reliable source for raw IME insets).
                    val browserRoot = content.getChildAt(0) ?: return@post
                    if (browserRoot.layoutParams !is ViewGroup.MarginLayoutParams) return@post

                    ImeInsetsSynchronizer.setup(
                        targetView = browserRoot,
                        insetsSource = content,
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

    private fun View.setBottomMargin(bottom: Int) {
        val params = layoutParams as? ViewGroup.MarginLayoutParams ?: return
        if (params.bottomMargin == bottom) return
        params.bottomMargin = bottom
        layoutParams = params
        requestLayout()
    }
}
