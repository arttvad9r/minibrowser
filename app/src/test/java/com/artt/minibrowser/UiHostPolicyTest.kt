package com.artt.minibrowser

import com.artt.minibrowser.ui.hostOf
import kotlin.test.Test
import kotlin.test.assertEquals

class UiHostPolicyTest {
    @Test fun unicodeHostUsesSharedWebPolicy() {
        assertEquals("пример.рф", hostOf("https://пример.рф/путь"))
        assertEquals("bücher.de", hostOf("https://bücher.de/path"))
    }

    @Test fun malformedOrNonWebUrlHasNoHost() {
        assertEquals("", hostOf("https://"))
        assertEquals("", hostOf("file:///tmp/test"))
    }
}
