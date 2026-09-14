package com.artt.minibrowser.engine

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.mozilla.geckoview.GeckoSession

class HistoryVisitPolicyTest {
    @Test
    fun recordsNormalTopLevelVisit() {
        assertTrue(
            shouldRecordHistoryVisit(
                isPrivate = false,
                flags = GeckoSession.HistoryDelegate.VISIT_TOP_LEVEL,
            ),
        )
    }

    @Test
    fun dropsUnrecoverableTopLevelVisitLikeGeckoEngineSession() {
        assertFalse(
            shouldRecordHistoryVisit(
                isPrivate = false,
                flags = GeckoSession.HistoryDelegate.VISIT_TOP_LEVEL or
                    GeckoSession.HistoryDelegate.VISIT_UNRECOVERABLE_ERROR,
            ),
        )
    }

    @Test
    fun dropsNonTopLevelVisit() {
        assertFalse(shouldRecordHistoryVisit(isPrivate = false, flags = 0))
    }

    @Test
    fun dropsPrivateTopLevelVisit() {
        assertFalse(
            shouldRecordHistoryVisit(
                isPrivate = true,
                flags = GeckoSession.HistoryDelegate.VISIT_TOP_LEVEL,
            ),
        )
    }
}
