package com.artt.minibrowser.net

import java.net.IDN
import java.net.URI

private data class WebAuthority(val host: String, val port: Int)

private fun webAuthority(value: String): WebAuthority? = runCatching {
    val uri = URI(value)
    val scheme = uri.scheme?.lowercase()?.takeIf { it == "http" || it == "https" }
        ?: return@runCatching null

    uri.host?.takeIf { it.isNotBlank() }?.let { host ->
        // URI already parsed IPv6 literals. For DNS hosts apply STD3 rules as an extra guard.
        if (!host.contains(':')) {
            IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).takeIf { it.isNotBlank() }
                ?: return@runCatching null
        }
        return@runCatching WebAuthority(host, uri.port)
    }

    // java.net.URI keeps a Unicode authority but reports host=null. Its URL view exposes that host,
    // after which IDN validation gives us the same DNS-syntax guarantees as the ASCII path above.
    val url = uri.toURL()
    if (!url.protocol.equals(scheme, ignoreCase = true)) return@runCatching null
    val host = url.host.takeIf { it.isNotBlank() } ?: return@runCatching null
    IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).takeIf { it.isNotBlank() }
        ?: return@runCatching null
    WebAuthority(host, url.port)
}.getOrNull()

/** Shared HTTP(S) URL validation for navigation, persistence and user-entered browser data. */
internal fun isValidWebUri(value: String): Boolean {
    val authority = webAuthority(value) ?: return false
    return authority.port == -1 || authority.port in 1..65535
}
