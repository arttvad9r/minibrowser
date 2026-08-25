package com.artt.minibrowser.engine

import android.app.Application
import android.os.Build
import com.artt.minibrowser.BuildConfig
import com.artt.minibrowser.data.DbHolder
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
}
