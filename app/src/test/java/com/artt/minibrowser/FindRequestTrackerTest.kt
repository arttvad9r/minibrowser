package com.artt.minibrowser

import com.artt.minibrowser.ui.FindRequestTracker
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FindRequestTrackerTest {
    @Test
    fun newerRequestRejectsOlderResult() {
        val tracker = FindRequestTracker()
        val first = tracker.begin()
        val second = tracker.begin()

        assertFalse(tracker.isCurrent(first))
        assertTrue(tracker.isCurrent(second))
    }

    @Test
    fun invalidationRejectsPendingResultBeforeReplacementStarts() {
        val tracker = FindRequestTracker()
        val pending = tracker.begin()

        tracker.invalidate()

        assertFalse(tracker.isCurrent(pending))
    }
}
