package com.artt.minibrowser.engine

import android.app.Application
import android.os.Build
import android.os.Bundle
import com.artt.minibrowser.data.DbHolder
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

object Engine { lateinit var runtime: GeckoRuntime }

class BrowserApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // GV-дочерние процессы (:gpu, :tab, ...) наследуют Application — рантайм только в главном.
        if (Build.VERSION.SDK_INT >= 28 && Application.getProcessName().contains(":")) return
        DbHolder.init(this)
        // GV 154 убрал Builder.autoplayDefault; преф media.autoplay.default: 0 = разрешено (нужно VOT)
        Engine.runtime = GeckoRuntime.create(
            this,
            GeckoRuntimeSettings.Builder()
                .extras(Bundle().apply { putInt("media.autoplay.default", 0) })
                .build()
        )
    }
}
