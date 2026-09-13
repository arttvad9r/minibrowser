package com.artt.minibrowser.engine

/**
 * Activity-lifecycle bridge for the future A-C [mozilla.components.concept.engine.request.RequestInterceptor].
 *
 * The interceptor itself is application-scoped with the EngineSession configurator, but launching
 * an external Android activity must use only the currently started browser Activity. When no host is
 * bound, requests pass through to Gecko unchanged.
 */
internal class ExternalAppNavigationPolicyRegistry : ExternalAppNavigationPolicy {
    @Volatile
    private var activePolicy: ExternalAppNavigationPolicy? = null

    fun bind(policy: ExternalAppNavigationPolicy) {
        synchronized(this) {
            activePolicy = policy
        }
    }

    /** A stale Activity may only clear its own binding, never a replacement Activity's policy. */
    fun unbind(policy: ExternalAppNavigationPolicy) {
        synchronized(this) {
            if (activePolicy === policy) activePolicy = null
        }
    }

    override fun onLoadRequest(request: ExternalAppNavigationRequest): ExternalAppRequestDecision =
        activePolicy?.onLoadRequest(request) ?: ExternalAppRequestDecision.Pass
}
