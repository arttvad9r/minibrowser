package com.artt.minibrowser

import com.artt.minibrowser.ui.PullRefreshGestureTracker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PullRefreshGestureTrackerTest {
    private fun tracker() = PullRefreshGestureTracker(
        touchSlopPx = 10f,
        triggerDistancePx = 70f,
    )

    @Test
    fun pullRequiresPageAndGeckoEligibility() {
        val tracker = tracker()
        tracker.onDown(x = 0f, y = 0f, pageEligible = false)
        tracker.setGeckoEligible(true)

        assertEquals(0f, tracker.onMove(x = 0f, y = 100f))
        assertFalse(tracker.finish(commit = true))
    }

    @Test
    fun horizontalGestureDoesNotBecomeRefreshPull() {
        val tracker = tracker()
        tracker.onDown(x = 0f, y = 0f, pageEligible = true)
        tracker.setGeckoEligible(true)

        assertEquals(0f, tracker.onMove(x = 80f, y = 40f))
        assertFalse(tracker.finish(commit = true))
    }

    @Test
    fun pullBelowThresholdDoesNotRefresh() {
        val tracker = tracker()
        tracker.onDown(x = 0f, y = 0f, pageEligible = true)
        tracker.setGeckoEligible(true)

        assertEquals(0.5f, tracker.onMove(x = 0f, y = 45f))
        assertFalse(tracker.finish(commit = true))
    }

    @Test
    fun pullPastThresholdRefreshesOnRelease() {
        val tracker = tracker()
        tracker.onDown(x = 0f, y = 0f, pageEligible = true)
        tracker.setGeckoEligible(true)

        assertEquals(1f, tracker.onMove(x = 0f, y = 90f))
        assertTrue(tracker.finish(commit = true))
    }

    @Test
    fun cancelledPullNeverRefreshes() {
        val tracker = tracker()
        tracker.onDown(x = 0f, y = 0f, pageEligible = true)
        tracker.setGeckoEligible(true)
        tracker.onMove(x = 0f, y = 90f)

        assertFalse(tracker.finish(commit = false))
    }

    @Test
    fun lateGeckoEligibilityCanJoinSameGesture() {
        val tracker = tracker()
        tracker.onDown(x = 0f, y = 0f, pageEligible = true)

        assertEquals(0f, tracker.onMove(x = 0f, y = 45f))
        tracker.setGeckoEligible(true)
        assertEquals(0.5f, tracker.onMove(x = 0f, y = 45f))
    }
}
