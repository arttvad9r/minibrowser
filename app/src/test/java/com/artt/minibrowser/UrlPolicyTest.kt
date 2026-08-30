package com.artt.minibrowser

import com.artt.minibrowser.net.isValidWebUri
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UrlPolicyTest {
    @Test fun acceptsNormalHttpPorts() {
        assertTrue(isValidWebUri("https://example.com"))
        assertTrue(isValidWebUri("https://example.com:443/path"))
        assertTrue(isValidWebUri("http://localhost:8080/test"))
        assertTrue(isValidWebUri("https://example.com:65535/path"))
    }

    @Test fun rejectsOutOfRangePorts() {
        assertFalse(isValidWebUri("https://example.com:0/path"))
        assertFalse(isValidWebUri("https://example.com:65536/path"))
        assertFalse(isValidWebUri("https://example.com:99999/path"))
    }

    @Test fun stillRejectsMalformedOrNonWebUris() {
        assertFalse(isValidWebUri("https://"))
        assertFalse(isValidWebUri("file:///tmp/test"))
        assertFalse(isValidWebUri("javascript:alert(1)"))
    }
}
