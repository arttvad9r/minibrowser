package com.artt.minibrowser.browser

import com.artt.minibrowser.engine.isAllowedWebUri
import java.util.ArrayDeque

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
    private val pending = ArrayDeque<String>()

    fun setHandler(value: (String) -> Unit) {
        handler = value
        while (pending.isNotEmpty()) {
            value(pending.removeFirst())
        }
    }

    fun accept(uri: String?) {
        val value = uri ?: return
        if (!isAllowedWebUri(value)) return
        handler?.invoke(value) ?: pending.addLast(value)
    }
}
