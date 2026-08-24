package com.artt.minibrowser

import com.artt.minibrowser.engine.SearchEngine
import com.artt.minibrowser.engine.buildLoadUri
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchEnginesTest {
    @Test fun urlPassthrough() =
        assertEquals("https://a.b/c", buildLoadUri("https://a.b/c", SearchEngine.GOOGLE))
    @Test fun bareDomainGetsHttps() =
        assertEquals("https://example.com", buildLoadUri("example.com", SearchEngine.GOOGLE))
    @Test fun wordsGoToSearch() =
        assertEquals("https://www.google.com/search?q=%D0%BB%D0%B8%D1%81%D0%B0",
                     buildLoadUri("лиса", SearchEngine.GOOGLE))
    @Test fun localhostStaysLocal() =
        assertEquals("http://localhost:8080/x", buildLoadUri("http://localhost:8080/x", SearchEngine.GOOGLE))
    @Test fun emptyIsBlank() =
        assertEquals("about:blank", buildLoadUri("  ", SearchEngine.GOOGLE))
}
