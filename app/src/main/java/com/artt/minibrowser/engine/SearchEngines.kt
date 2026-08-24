package com.artt.minibrowser.engine

import java.net.URLEncoder

enum class SearchEngine(val label: String, val template: String) {
    GOOGLE("Google", "https://www.google.com/search?q=%s"),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    YANDEX("Яндекс", "https://yandex.ru/search/?text=%s"),
    BING("Bing", "https://www.bing.com/search?q=%s");
}

private val URI_LIKE = Regex("^(https?://|about:)|^[\\w-]+(\\.[\\w-]+)+(:\\d+)?(/.*)?$")
private val SCHEME_LIKE = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

fun buildLoadUri(input: String, engine: SearchEngine): String {
    val t = input.trim()
    if (t.isEmpty()) return "about:blank"
    if (t.startsWith("https://") || t.startsWith("http://") || t.startsWith("about:") || SCHEME_LIKE.containsMatchIn(t)) return t
    if (URI_LIKE.containsMatchIn(t)) return "https://$t"
    return engine.template.replace("%s", URLEncoder.encode(t, "UTF-8"))
}
