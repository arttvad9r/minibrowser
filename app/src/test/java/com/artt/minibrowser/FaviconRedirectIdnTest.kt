package com.artt.minibrowser

import com.artt.minibrowser.engine.sameOriginFaviconRedirect
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FaviconRedirectIdnTest {
    @Test fun unicodeAndPunycodeHostsAreSameOrigin() {
        val current = URL("https://xn--e1afmkfd.xn--p1ai/favicon.ico")

        assertEquals(
            "https://пример.рф/icon.ico",
            sameOriginFaviconRedirect(current, "https://пример.рф/icon.ico")?.toString(),
        )
    }

    @Test fun differentIdnHostOrPortIsRejected() {
        val current = URL("https://xn--e1afmkfd.xn--p1ai/favicon.ico")

        assertNull(sameOriginFaviconRedirect(current, "https://другой.рф/icon.ico"))
        assertNull(sameOriginFaviconRedirect(current, "https://пример.рф:8443/icon.ico"))
    }

    @Test fun redirectUserInfoRemainsRejected() {
        val current = URL("https://xn--e1afmkfd.xn--p1ai/favicon.ico")

        assertNull(sameOriginFaviconRedirect(current, "https://user:secret@пример.рф/icon.ico"))
    }
}
