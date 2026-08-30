package com.artt.minibrowser

import com.artt.minibrowser.engine.SearchEngine
import com.artt.minibrowser.engine.buildLoadUri
import com.artt.minibrowser.engine.buildTranslateUri
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SearchEnginesTest {
    @Test fun urlPassthrough() =
        assertEquals("https://a.b/c", buildLoadUri("https://a.b/c", SearchEngine.GOOGLE))
    @Test fun internalUrisOnlyAllowBlankPage() {
        assertEquals("about:blank", buildLoadUri("about:blank", SearchEngine.GOOGLE))
        assertEquals("https://www.google.com/search?q=chrome%3A%2F%2Fcrash", buildLoadUri("chrome://crash", SearchEngine.GOOGLE))
    }
    @Test fun hostPortStillGetsHttps() =
        assertEquals("https://192.168.0.1:8080", buildLoadUri("192.168.0.1:8080", SearchEngine.GOOGLE))
    @Test fun bareDomainGetsHttps() =
        assertEquals("https://example.com", buildLoadUri("example.com", SearchEngine.GOOGLE))
    @Test fun wordsGoToSearch() =
        assertEquals("https://www.google.com/search?q=%D0%BB%D0%B8%D1%81%D0%B0",
                     buildLoadUri("лиса", SearchEngine.GOOGLE))
    @Test fun localhostStaysLocal() =
        assertEquals("http://localhost:8080/x", buildLoadUri("http://localhost:8080/x", SearchEngine.GOOGLE))
    @Test fun emptyIsBlank() =
        assertEquals("about:blank", buildLoadUri("  ", SearchEngine.GOOGLE))
    @Test fun translateUriBuildsProxyUrl() =
        assertEquals(
            "https://en-wikipedia-org.translate.goog/wiki/Main_Page?_x_tr_sl=auto&_x_tr_tl=ru&_x_tr_hl=ru",
            buildTranslateUri("https://en.wikipedia.org/wiki/Main_Page", "ru"))
    @Test fun translateUriEscapesDashesAndKeepsQuery() =
        assertEquals(
            "https://my--site-com.translate.goog/a?b=1&_x_tr_sl=auto&_x_tr_tl=en&_x_tr_hl=en",
            buildTranslateUri("https://my-site.com/a?b=1", "en"))
    @Test fun translateUriNormalizesSupportedTarget() =
        assertEquals(
            "https://example-com.translate.goog/a?_x_tr_sl=auto&_x_tr_tl=de&_x_tr_hl=de",
            buildTranslateUri("https://example.com/a", " DE "))
    @Test fun translateUriAcceptsDefaultPortWithoutChangingOrigin() =
        assertEquals(
            "https://example-com.translate.goog/a?_x_tr_sl=auto&_x_tr_tl=en&_x_tr_hl=en",
            buildTranslateUri("https://example.com:443/a", "en"))
    @Test fun translateUriRejectsGarbageAndLocalOrigins() {
        assertNull(buildTranslateUri("https://a.translate.goog/x", "ru"))
        assertNull(buildTranslateUri("https://TRANSLATE.GOOG/x", "ru"))
        assertNull(buildTranslateUri("about:blank", "ru"))
        assertNull(buildTranslateUri("httpx://example.com/x", "ru"))
        assertNull(buildTranslateUri("https://example.com:8443/x", "ru"))
        assertNull(buildTranslateUri("http://localhost:8080/x", "ru"))
        assertNull(buildTranslateUri("https://192.168.1.2/x", "ru"))
        assertNull(buildTranslateUri("https://[2001:db8::1]/x", "ru"))
        assertNull(buildTranslateUri("https://printer.local/x", "ru"))
        assertNull(buildTranslateUri("https://a.b/c", "  "))
        assertNull(buildTranslateUri("https://a.b/c", "ru&x=1"))
        assertNull(buildTranslateUri("https://a.b/c", "es"))
    }
}
