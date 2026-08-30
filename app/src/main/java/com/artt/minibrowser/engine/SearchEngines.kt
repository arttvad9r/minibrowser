package com.artt.minibrowser.engine

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

enum class SearchEngine(val template: String) {
    GOOGLE("https://www.google.com/search?q=%s"),
    DUCKDUCKGO("https://duckduckgo.com/?q=%s"),
    YANDEX("https://yandex.ru/search/?text=%s"),
    BING("https://www.bing.com/search?q=%s");
}

private val TRANSLATION_TARGETS = setOf("ru", "en", "de", "fr")
private val TRANSLATE_QUERY_KEYS = setOf("_x_tr_sl", "_x_tr_tl", "_x_tr_hl")
private val IPV4_LITERAL = Regex("^\\d{1,3}(?:\\.\\d{1,3}){3}$")

internal fun normalizeTranslationTarget(target: String?): String? =
    target?.trim()?.lowercase()?.takeIf { it in TRANSLATION_TARGETS }

private fun isTranslatableHost(host: String): Boolean =
    host.contains('.') &&
        !host.contains(':') &&
        !IPV4_LITERAL.matches(host) &&
        !host.equals("localhost", ignoreCase = true) &&
        !host.endsWith(".localhost", ignoreCase = true) &&
        !host.endsWith(".local", ignoreCase = true)

private fun translateQueryKey(segment: String): String {
    val rawKey = segment.substringBefore('=')
    return runCatching {
        URLDecoder.decode(rawKey, StandardCharsets.UTF_8.name()).lowercase()
    }.getOrDefault(rawKey.lowercase())
}

internal fun sanitizeTranslateQuery(rawQuery: String?): String? {
    if (rawQuery.isNullOrEmpty()) return null
    return rawQuery
        .split('&')
        .filterNot { translateQueryKey(it) in TRANSLATE_QUERY_KEYS }
        .joinToString("&")
        .takeIf { it.isNotEmpty() }
}

// Перевод страницы через Google Translate proxy (translate.goog) — приём лёгких браузеров, без API-ключей.
// Хост: точки -> "-", существующие "-" -> "--"; язык оригинала auto.
fun buildTranslateUri(url: String, target: String): String? {
    val language = normalizeTranslationTarget(target) ?: return null
    if (!isValidWebUri(url)) return null
    val u = runCatching { URI(url) }.getOrNull() ?: return null
    val host = u.host?.trimEnd('.')?.takeIf(::isTranslatableHost) ?: return null
    val defaultPort = if (u.scheme.equals("https", ignoreCase = true)) 443 else 80
    if (u.port != -1 && u.port != defaultPort) return null
    if (host.equals("translate.goog", ignoreCase = true) || host.endsWith(".translate.goog", ignoreCase = true)) {
        return null
    }
    val translatedHost = host.replace("-", "--").replace(".", "-")
    val query = sanitizeTranslateQuery(u.rawQuery)
    val translateQuery = "_x_tr_sl=auto&_x_tr_tl=$language&_x_tr_hl=$language"
    val q = if (query.isNullOrEmpty()) "?$translateQuery" else "?$query&$translateQuery"
    val fragment = u.rawFragment?.let { "#$it" }.orEmpty()
    return "https://$translatedHost.translate.goog${u.rawPath ?: ""}$q$fragment"
}
