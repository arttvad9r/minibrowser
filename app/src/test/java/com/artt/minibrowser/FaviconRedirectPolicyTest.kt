package com.artt.minibrowser

import com.artt.minibrowser.engine.FaviconFetcher
import com.artt.minibrowser.engine.faviconOrigin
import com.artt.minibrowser.engine.sameOriginFaviconRedirect
import java.net.URL
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class FaviconRedirectPolicyTest {
    private val origin = URL("https://example.com/favicon.ico")

    @Test
    fun allowsRelativeRedirectOnSameOrigin() {
        assertEquals(
            "https://example.com/assets/favicon.png",
            sameOriginFaviconRedirect(origin, "/assets/favicon.png")?.toString(),
        )
    }

    @Test
    fun allowsAbsoluteRedirectOnSameOrigin() {
        assertEquals(
            "https://example.com/icons/favicon.png",
            sameOriginFaviconRedirect(origin, "https://example.com/icons/favicon.png")?.toString(),
        )
    }

    @Test
    fun rejectsDifferentHost() {
        assertNull(sameOriginFaviconRedirect(origin, "https://cdn.example.net/favicon.png"))
    }

    @Test
    fun rejectsSchemeDowngrade() {
        assertNull(sameOriginFaviconRedirect(origin, "http://example.com/favicon.ico"))
    }

    @Test
    fun rejectsDifferentPort() {
        assertNull(sameOriginFaviconRedirect(origin, "https://example.com:8443/favicon.ico"))
    }

    @Test
    fun rejectsEmbeddedCredentials() {
        assertNull(sameOriginFaviconRedirect(origin, "https://user:pass@example.com/favicon.ico"))
    }

    @Test
    fun preservesHttpOrigin() {
        assertEquals("http://example.com", faviconOrigin("http://example.com/a/b?q=1#fragment"))
    }

    @Test
    fun preservesNonDefaultPort() {
        assertEquals("https://example.com:8443", faviconOrigin("https://example.com:8443/page"))
    }

    @Test
    fun normalizesDefaultPorts() {
        assertEquals("https://example.com", faviconOrigin("https://example.com:443/page"))
        assertEquals("http://example.com", faviconOrigin("http://example.com:80/page"))
    }

    @Test
    fun bareHostKeepsHttpsDefault() {
        assertEquals("https://example.com", faviconOrigin("example.com"))
    }

    @Test
    fun rejectsNonWebSchemes() {
        assertNull(faviconOrigin("file:///tmp/favicon.ico"))
        assertNull(faviconOrigin("about:blank"))
    }

    @Test
    fun clearDeletesDiskCache() {
        val dir = Files.createTempDirectory("minibrowser-favicons").toFile()
        FaviconFetcher.cacheFile("https://example.com", dir).apply {
            parentFile?.mkdirs()
            writeText("stale")
        }

        FaviconFetcher.clear(dir)

        assertFalse(dir.exists())
    }
}
