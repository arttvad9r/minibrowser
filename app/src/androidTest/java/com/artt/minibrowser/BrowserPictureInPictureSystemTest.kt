package com.artt.minibrowser

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artt.minibrowser.browser.BrowserPictureInPictureController
import com.artt.minibrowser.browser.BrowserPictureInPictureMediaState
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserPictureInPictureSystemTest {
    @Test
    fun eligibleStateEntersRealSystemPictureInPicture() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            val activityRef = AtomicReference<MainActivity>()
            val supported = AtomicBoolean(false)
            val requested = AtomicBoolean(false)

            scenario.onActivity { activity ->
                activityRef.set(activity)
                val controller = BrowserPictureInPictureController(activity)
                supported.set(controller.supported)
                if (controller.supported) {
                    controller.update(
                        BrowserPictureInPictureMediaState(
                            fullscreenVideo = true,
                            playing = true,
                            videoWidth = 1920,
                            videoHeight = 1080,
                        ),
                    )
                    requested.set(controller.enterIfEligible())
                }
            }

            assertTrue(
                "The API 36 phone instrumentation target must expose system picture-in-picture",
                supported.get(),
            )
            assertTrue("The platform accepted the picture-in-picture request", requested.get())

            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val deadline = SystemClock.uptimeMillis() + PIP_TRANSITION_TIMEOUT_MS
            var inPictureInPicture = false
            while (!inPictureInPicture && SystemClock.uptimeMillis() < deadline) {
                instrumentation.runOnMainSync {
                    inPictureInPicture = activityRef.get().isInPictureInPictureMode
                }
                if (!inPictureInPicture) SystemClock.sleep(PIP_POLL_INTERVAL_MS)
            }

            assertTrue("MainActivity entered real system picture-in-picture", inPictureInPicture)
        } finally {
            scenario.close()
        }
    }

    private companion object {
        const val PIP_TRANSITION_TIMEOUT_MS = 5_000L
        const val PIP_POLL_INTERVAL_MS = 100L
    }
}
