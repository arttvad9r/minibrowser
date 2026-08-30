package com.artt.minibrowser

import com.artt.minibrowser.net.isValidWebUri
import com.artt.minibrowser.net.webUriHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UrlPolicyTest {
    @Test fun acceptsNormalHttpPorts() {
        assertTrue(isValidWebUri("https://example.com"))
        assertTrue(isValidWebUri("https://example.com:443/path"))
        assertTrue(isValidWebUri("http://localhost:8080/test"))
        assertTrue(isValidWebUri("https://example.com:65535/path"))
    }

    @Test fun acceptsUnicodeIdnHosts() {
        assertTrue(isValidWebUri("https://пример.рф/путь"))
        assertTrue(isValidWebUri("https://bücher.de:8443/"))
        assertTrue(isValidWebUri("https://例子.测试/"))
    }

    @Test fun returnsHostUsingTheSameValidationPolicy() {
        assertEquals("example.com", webUriHost("https://example.com:443/path"))
        assertEquals("пример.рф", webUriHost("https://пример.рф/путь"))
        assertEquals("bücher.de", webUriHost("https://bücher.de:8443/"))
        assertNull(webUriHost("https://example.com:65536/path"))
        assertNull(webUriHost("file:///tmp/test"))
    }

    @Test fun rejectsOutOfRangePorts() {
        assertFalse(isValidWebUri("https://example.com:0/path"))
        assertFalse(isValidWebUri("https://example.com:65536/path"))
        assertFalse(isValidWebUri("https://example.com:99999/path"))
        assertFalse(isValidWebUri("https://пример.рф:99999/path"))
    }

    @Test fun stillRejectsMalformedOrNonWebUris() {
        assertFalse(isValidWebUri("https://"))
        assertFalse(isValidWebUri("https://bad_host.example/"))
        assertFalse(isValidWebUri("file:///tmp/test"))
        assertFalse(isValidWebUri("javascript:alert(1)"))
    }
}
