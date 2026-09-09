package com.artt.minibrowser.engine

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import mozilla.components.concept.engine.DownloadDelegate
import mozilla.components.concept.engine.Settings
import mozilla.components.concept.engine.history.HistoryTrackingDelegate
import mozilla.components.concept.engine.request.RequestInterceptor

class AndroidComponentsSessionSettingsMiddlewareTest {
    private class TestSettings : Settings() {
        override var requestInterceptor: RequestInterceptor? = null
        override var historyTrackingDelegate: HistoryTrackingDelegate? = null
        override var downloadDelegate: DownloadDelegate? = null
        override var suspendMediaWhenInactive: Boolean = false
    }

    @Test
    fun configuratorAppliesTheSessionOwnedMigrationContract() {
        val settings = TestSettings()
        val navigationRegistry = ExternalAppNavigationPolicyRegistry()
        val requestInterceptor = AndroidComponentsExternalAppRequestInterceptor(navigationRegistry)
        val historyDelegate = AndroidComponentsHistoryTrackingDelegate()
        val downloadDelegate = AndroidComponentsDownloadDelegate()
        val configurator = AndroidComponentsOwnedSessionConfigurator(
            externalNavigationPolicy = navigationRegistry,
            historyTrackingDelegateFactory = { historyDelegate },
            downloadDelegateFactory = { downloadDelegate },
            requestInterceptorFactory = { requestInterceptor },
        )

        configurator.configure(settings)

        assertSame(requestInterceptor, settings.requestInterceptor)
        assertSame(historyDelegate, settings.historyTrackingDelegate)
        assertSame(downloadDelegate, settings.downloadDelegate)
        assertTrue(settings.suspendMediaWhenInactive)
    }

    @Test
    fun constructingConfiguratorAndMiddlewareDoesNotInitializeFeatureDelegates() {
        var interceptorCreated = false
        var historyCreated = false
        var downloadCreated = false
        val configurator = AndroidComponentsOwnedSessionConfigurator(
            externalNavigationPolicy = ExternalAppNavigationPolicyRegistry(),
            historyTrackingDelegateFactory = {
                historyCreated = true
                AndroidComponentsHistoryTrackingDelegate()
            },
            downloadDelegateFactory = {
                downloadCreated = true
                AndroidComponentsDownloadDelegate()
            },
            requestInterceptorFactory = {
                interceptorCreated = true
                AndroidComponentsExternalAppRequestInterceptor(it)
            },
        )

        androidComponentsSessionSettingsMiddleware(configurator)

        assertFalse(interceptorCreated)
        assertFalse(historyCreated)
        assertFalse(downloadCreated)
    }
}
