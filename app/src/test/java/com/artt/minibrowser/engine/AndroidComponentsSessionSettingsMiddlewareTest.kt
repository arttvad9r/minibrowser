package com.artt.minibrowser.engine

import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue
import mozilla.components.concept.engine.DownloadDelegate
import mozilla.components.concept.engine.Settings
import mozilla.components.concept.engine.history.HistoryTrackingDelegate

class AndroidComponentsSessionSettingsMiddlewareTest {
    private class TestSettings : Settings() {
        override var historyTrackingDelegate: HistoryTrackingDelegate? = null
        override var downloadDelegate: DownloadDelegate? = null
        override var suspendMediaWhenInactive: Boolean = false
    }

    @Test
    fun configuresOnlyTheSessionOwnedMigrationContract() {
        val settings = TestSettings()
        val historyDelegate = AndroidComponentsHistoryTrackingDelegate()
        val downloadDelegate = AndroidComponentsDownloadDelegate()

        configureAndroidComponentsOwnedSession(
            settings = settings,
            historyTrackingDelegate = historyDelegate,
            downloadDelegate = downloadDelegate,
        )

        assertSame(historyDelegate, settings.historyTrackingDelegate)
        assertSame(downloadDelegate, settings.downloadDelegate)
        assertTrue(settings.suspendMediaWhenInactive)
    }
}
