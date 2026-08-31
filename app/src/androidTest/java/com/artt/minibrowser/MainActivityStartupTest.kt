package com.artt.minibrowser

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityStartupTest {
    @Test
    fun launchesToResumedState() {
        // Smoke the real Application + MainActivity + initial Compose/Gecko wiring. Performance
        // thresholds belong to the benchmark source set; this test only guards startup correctness.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertResumed(scenario)
        }
    }

    @Test
    fun recreatesToResumedState() {
        // Keep a real Gecko-backed browser host usable across Activity recreation. This exercises
        // the Activity-bound TabManager shutdown plus persisted-session restore against the same
        // application-owned GeckoRuntime instead of relying on a synthetic state-holder test.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.recreate()
            assertResumed(scenario)
        }
    }

    private fun assertResumed(scenario: ActivityScenario<MainActivity>) {
        assertEquals(Lifecycle.State.RESUMED, scenario.state)
        scenario.onActivity { activity ->
            assertFalse(activity.isFinishing)
            assertFalse(activity.isDestroyed)
        }
    }
}
