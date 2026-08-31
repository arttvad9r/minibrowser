package com.artt.minibrowser.browser

import com.artt.minibrowser.engine.isAllowedWebUri

/**
 * The launch Intent belongs to the Activity instance. On recreation the browser session is restored
 * from TabStore, so replaying the same launch URI would create a duplicate tab. Real subsequent
 * intents still flow through MainActivity.onNewIntent().
 */
internal fun initialExternalNavigationUri(
    intentUri: String?,
    hasSavedInstanceState: Boolean,
): String? = intentUri.takeUnless { hasSavedInstanceState }

class NavigationController {
    private var handler: ((String) -> Unit)? = null
    private var pending: String? = null

    fun setHandler(value: (String) -> Unit) {
        handler = value
        pending?.let {
            pending = null
            value(it)
        }
    }

    fun accept(uri: String?) {
        val value = uri ?: return
        if (!isAllowedWebUri(value)) return
        handler?.invoke(value) ?: run { pending = value }
    }
}
