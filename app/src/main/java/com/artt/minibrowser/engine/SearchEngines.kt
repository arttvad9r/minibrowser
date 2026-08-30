package com.artt.minibrowser.engine

import java.net.URI

enum class SearchEngine(val template: String) {
    GOOGLE("https://www.google.com/search?q=%s"),
    DUCKDUCKGO("https://duckduckgo.com/?q=%s"),
    YANDEX("https://yandex.ru/search/?text=%s"),
    BING("https://www.bing.com/search?q=%s");
}

private val TRANSLATION_TARGETS = setOf("ru", "en", "de", "fr")

// Перевод страницы через Google Translate proxy (translate.goog) — приём лёгких браузеров, без API-ключей.
// Хост: точки -> "-", существующие "-" -> "--"; язык оригинала auto.
fun buildTranslateUri(url: String, target: String): String? {
    val language = target.trim().lowercase().takeIf { it in TRANSLATION_TARGETS } ?: return null
    if (!isValidWebUri(url)) return null
    val u = runCatching { URI(url) }.getOrNull() ?: return null
    val host = u.host ?: return null
    if (host.equals("translate.goog", ignoreCase = true) || host.endsWith(".translate.goog", ignoreCase = true)) {
        return null
    }
    val translatedHost = host.replace("-", "--").replace(".", "-")
    val query = u.rawQuery
    val translateQuery = "_x_tr_sl=auto&_x_tr_tl=$language&_x_tr_hl=$language"
    val q = if (query.isNullOrEmpty()) "?$translateQuery" else "?$query&$translateQuery"
    return "https://$translatedHost.translate.goog${u.rawPath ?: ""}$q"
}
