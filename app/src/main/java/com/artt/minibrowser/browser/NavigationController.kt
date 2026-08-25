package com.artt.minibrowser.browser

import com.artt.minibrowser.engine.isAllowedWebUri

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
