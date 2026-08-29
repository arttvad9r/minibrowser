package com.artt.minibrowser

import com.artt.minibrowser.engine.sameOriginFaviconRedirect
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
