package com.artt.minibrowser

import com.artt.minibrowser.engine.faviconOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FaviconOriginPolicyTest {
    @Test
    fun unicodeHostsBecomeNetworkSafePunycodeOrigins() {
        assertEquals(
            "https://xn--e1afmkfd.xn--p1ai",
            faviconOrigin("https://пример.рф/страница?q=1#часть"),
        )
        assertEquals(
            "https://xn--e1afmkfd.xn--p1ai",
            faviconOrigin("пример.рф"),
        )
        assertEquals(
            "http://xn--e1afmkfd.xn--p1ai:8080",
            faviconOrigin("http://пример.рф:8080/path"),
        )
    }

    @Test
    fun faviconOriginStripsCredentialsAndRejectsInvalidWebOrigins() {
        assertEquals(
            "https://xn--e1afmkfd.xn--p1ai",
            faviconOrigin("https://user:secret@пример.рф/private"),
        )
        assertEquals("https://example.com", faviconOrigin("https://example.com:443/path"))
        assertNull(faviconOrigin("https://example.com:65536/path"))
        assertNull(faviconOrigin("file:///tmp/icon.png"))
        assertNull(faviconOrigin("javascript:alert(1)"))
    }
}
