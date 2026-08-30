package com.artt.minibrowser.engine

import android.content.Intent
import android.net.Uri
import com.artt.minibrowser.net.isValidWebUri as isSharedValidWebUri

sealed interface NavigationTarget {
    data class Web(val uri: String) : NavigationTarget
    data class Internal(val uri: String) : NavigationTarget
    data class External(val uri: String) : NavigationTarget
    data class Search(val query: String) : NavigationTarget
}

private val EXTERNAL_SCHEMES = setOf("mailto", "tel", "sms", "geo", "intent", "market")
private val DIRECT_EXTERNAL_SCHEMES = EXTERNAL_SCHEMES - "intent"
private val BLOCKED_INTENT_DATA_SCHEMES = setOf(
    "javascript", "data", "file", "content", "about", "chrome", "resource", "moz-extension",
)

fun isExternalScheme(value: String): Boolean = value.substringBefore(':', "").lowercase() in EXTERNAL_SCHEMES

internal fun selectSafeExternalUri(direct: String?, fallback: String?): String? {
    val directScheme = direct?.substringBefore(':', "")?.lowercase()
    val safeDirect = when {
        direct != null && directScheme in setOf("http", "https") -> direct.takeIf(::isValidWebUri)
        direct != null && directScheme in DIRECT_EXTERNAL_SCHEMES -> direct.takeIf { it.substringAfter(':', "").isNotBlank() }
        else -> null
    }
    return safeDirect ?: fallback?.takeIf(::isValidWebUri)
}

/**
 * A popup may legitimately start as an empty about:blank window and navigate afterwards.
 * The later navigation still goes through NavigationDelegate.onLoadRequest, so allowing an
 * empty initial target does not allow javascript:/data:/file: pages through this gate.
 */
internal fun isAllowedPopupTarget(uri: String?): Boolean {
    val value = uri?.trim()
    return value.isNullOrEmpty() || value.equals("about:blank", ignoreCase = true) || isValidWebUri(value)
}

/**
 * Build a browser-safe external intent. For intent:// links we preserve the decoded data URI
 * and package, but deliberately drop arbitrary action/component/selector/extras supplied by a
 * page. CATEGORY_BROWSABLE is required so only activities explicitly exposed to browsers match.
 */
fun createSafeExternalIntent(value: String): Intent? = runCatching {
    val scheme = value.substringBefore(':', "").lowercase()
    if (scheme == "intent") {
        val parsed = Intent.parseUri(value, Intent.URI_INTENT_SCHEME)
        val data = parsed.data?.takeIf(::isSafeIntentData) ?: return@runCatching null
        Intent(Intent.ACTION_VIEW, data).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            parsed.`package`?.takeIf(::isSafePackageName)?.let(::setPackage)
        }
    } else {
        if (scheme !in DIRECT_EXTERNAL_SCHEMES) return@runCatching null
        val uri = Uri.parse(value)
        val safe = selectSafeExternalUri(uri.toString(), null) ?: return@runCatching null
        Intent(Intent.ACTION_VIEW, Uri.parse(safe)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
    }
}.getOrNull()

/** Valid HTTP(S) fallback embedded in an intent:// URI, if one was provided. */
fun safeExternalFallbackUrl(value: String): String? = runCatching {
    if (!value.startsWith("intent:", ignoreCase = true)) return@runCatching null
    Intent.parseUri(value, Intent.URI_INTENT_SCHEME)
        .getStringExtra("browser_fallback_url")
        ?.takeIf(::isValidWebUri)
}.getOrNull()

private fun isSafeIntentData(uri: Uri): Boolean {
    val scheme = uri.scheme?.lowercase() ?: return false
    if (scheme in BLOCKED_INTENT_DATA_SCHEMES || scheme == "intent") return false
    if (scheme in setOf("http", "https")) return isValidWebUri(uri.toString())
    return uri.schemeSpecificPart?.isNotBlank() == true
}

private fun isSafePackageName(value: String): Boolean =
    value.length <= 255 && Regex("^[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+$").matches(value)

internal fun isValidWebUri(value: String): Boolean = isSharedValidWebUri(value)

fun isAllowedWebUri(value: String): Boolean = isValidWebUri(value)

fun resolveNavigation(input: String): NavigationTarget {
    val value = input.trim()
    if (value.isEmpty()) return NavigationTarget.Internal("about:blank")
    if (isAllowedWebUri(value)) return NavigationTarget.Web(value)
    if (value.equals("about:blank", ignoreCase = true)) return NavigationTarget.Internal("about:blank")

    val scheme = value.substringBefore(':', "").lowercase()
    if (scheme in EXTERNAL_SCHEMES && value.contains(':')) return NavigationTarget.External(value)
    if (value.startsWith("about:", ignoreCase = true)) return NavigationTarget.Search(value)

    val localhost = Regex("^localhost(?::\\d+)?(?:/.*)?$", RegexOption.IGNORE_CASE).matches(value)
    if (localhost) {
        val candidate = "http://$value"
        if (isValidWebUri(candidate)) return NavigationTarget.Web(candidate)
    }

    val hostLike = Regex("^[\\w-]+(\\.[\\w-]+)+(?:\\:\\d+)?(?:/.*)?$").matches(value)
    if (hostLike) {
        val candidate = "https://$value"
        if (isValidWebUri(candidate)) return NavigationTarget.Web(candidate)
    }
    return NavigationTarget.Search(value)
}

fun buildLoadUri(input: String, engine: SearchEngine): String = when (val target = resolveNavigation(input)) {
    is NavigationTarget.Web -> target.uri
    is NavigationTarget.Internal -> target.uri
    is NavigationTarget.Search -> engine.template.replace("%s", java.net.URLEncoder.encode(target.query, "UTF-8"))
    is NavigationTarget.External -> engine.template.replace("%s", java.net.URLEncoder.encode(target.uri, "UTF-8"))
}
