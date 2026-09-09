package com.artt.minibrowser

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artt.minibrowser.browser.BrowserPictureInPictureController
import com.artt.minibrowser.browser.BrowserPictureInPictureMediaState
import com.artt.minibrowser.engine.BrowserApp
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
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val browserApp = instrumentation.targetContext.applicationContext as BrowserApp
            val activityRef = AtomicReference<MainActivity>()
            val supported = AtomicBoolean(false)
            val requested = AtomicBoolean(false)

            // MainActivity creates TabManager after the persisted-tab preload completes. Wait for the
            // shadow bridge to publish the selected tab so this test also exercises the activity's
            // real PiP callback path rather than racing browser initialization.
            val shadowDeadline = SystemClock.uptimeMillis() + BROWSER_INITIALIZATION_TIMEOUT_MS
            var selectedShadowTabId: String? = null
            while (selectedShadowTabId == null && SystemClock.uptimeMillis() < shadowDeadline) {
                instrumentation.runOnMainSync {
                    selectedShadowTabId = browserApp.browserStore.state.selectedTabId
                }
                if (selectedShadowTabId == null) SystemClock.sleep(PIP_POLL_INTERVAL_MS)
            }
            assertTrue("BrowserStore published a selected shadow tab", selectedShadowTabId != null)
            val expectedShadowTabId = requireNotNull(selectedShadowTabId)

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

            val pipDeadline = SystemClock.uptimeMillis() + PIP_TRANSITION_TIMEOUT_MS
            var inPictureInPicture = false
            var shadowPictureInPicture = false
            while (
                (!inPictureInPicture || !shadowPictureInPicture) &&
                SystemClock.uptimeMillis() < pipDeadline
            ) {
                instrumentation.runOnMainSync {
                    inPictureInPicture = activityRef.get().isInPictureInPictureMode
                    shadowPictureInPicture = browserApp.browserStore.state.tabs
                        .firstOrNull { it.id == expectedShadowTabId }
                        ?.content
                        ?.pictureInPictureEnabled == true
                }
                if (!inPictureInPicture || !shadowPictureInPicture) {
                    SystemClock.sleep(PIP_POLL_INTERVAL_MS)
                }
            }

            assertTrue("MainActivity entered real system picture-in-picture", inPictureInPicture)
            assertTrue("BrowserStore mirrored the system picture-in-picture transition", shadowPictureInPicture)
        } finally {
            scenario.close()
        }
    }

    private companion object {
        const val BROWSER_INITIALIZATION_TIMEOUT_MS = 10_000L
        const val PIP_TRANSITION_TIMEOUT_MS = 5_000L
        const val PIP_POLL_INTERVAL_MS = 100L
    }
}
