package com.artt.minibrowser.engine

import android.content.Intent
import android.net.Uri
import java.net.URI

sealed interface NavigationTarget {
    data class Web(val uri: String) : NavigationTarget
    data class Internal(val uri: String) : NavigationTarget
    data class External(val uri: String) : NavigationTarget
    data class Search(val query: String) : NavigationTarget
}

private val EXTERNAL_SCHEMES = setOf("mailto", "tel", "sms", "geo", "intent", "market")

fun isExternalScheme(value: String): Boolean = value.substringBefore(':', "").lowercase() in EXTERNAL_SCHEMES

fun createSafeExternalIntent(value: String): Intent? = runCatching {
    val scheme = value.substringBefore(':', "").lowercase()
    val intent = if (scheme == "intent") {
        Intent.parseUri(value, Intent.URI_INTENT_SCHEME)
    } else {
        Intent(Intent.ACTION_VIEW, Uri.parse(value))
    }
    intent.component = null
    intent.`package` = null
    intent.selector = null
    val dataScheme = intent.data?.scheme?.lowercase()
    if (scheme == "intent" && dataScheme !in setOf("http", "https", "mailto", "tel", "sms", "geo", "market")) null else intent
}.getOrNull()

fun isAllowedWebUri(value: String): Boolean = runCatching {
    val uri = URI(value)
    uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()
}.getOrDefault(false)

fun resolveNavigation(input: String, engine: SearchEngine): NavigationTarget {
    val value = input.trim()
    if (value.isEmpty()) return NavigationTarget.Internal("about:blank")
    if (isAllowedWebUri(value)) return NavigationTarget.Web(value)
    if (value == "about:blank") return NavigationTarget.Internal(value)

    val scheme = value.substringBefore(':', "").lowercase()
    if (scheme in EXTERNAL_SCHEMES && value.contains(':')) return NavigationTarget.External(value)
    if (value.startsWith("about:", ignoreCase = true)) return NavigationTarget.Search(value)

    val hostLike = Regex("^[\\w-]+(\\.[\\w-]+)+(?:\\:\\d+)?(?:/.*)?$").matches(value)
    if (hostLike) return NavigationTarget.Web("https://$value")
    return NavigationTarget.Search(value)
}

fun buildLoadUri(input: String, engine: SearchEngine): String = when (val target = resolveNavigation(input, engine)) {
    is NavigationTarget.Web -> target.uri
    is NavigationTarget.Internal -> target.uri
    is NavigationTarget.Search -> engine.template.replace("%s", java.net.URLEncoder.encode(target.query, "UTF-8"))
    is NavigationTarget.External -> engine.template.replace("%s", java.net.URLEncoder.encode(target.uri, "UTF-8"))
}
