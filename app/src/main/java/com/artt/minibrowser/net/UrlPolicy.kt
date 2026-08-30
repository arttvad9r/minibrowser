package com.artt.minibrowser.net

import java.net.URI

/** Shared HTTP(S) URL validation for navigation, persistence and user-entered browser data. */
internal fun isValidWebUri(value: String): Boolean = runCatching {
    val uri = URI(value)
    val port = uri.port
    uri.scheme?.lowercase() in setOf("http", "https") &&
        !uri.host.isNullOrBlank() &&
        (port == -1 || port in 1..65535)
}.getOrDefault(false)
