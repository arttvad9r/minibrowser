package com.artt.minibrowser

import com.artt.minibrowser.browser.shareableBrowserUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BrowserSharePolicyTest {
    @Test fun credentialsAreRemovedButPageTargetIsPreserved() {
        assertEquals(
            "https://example.com/private?q=1#section",
            shareableBrowserUrl("https://user:secret@example.com/private?q=1#section"),
        )
    }

    @Test fun unicodeWebUrlRemainsShareable() {
        assertEquals(
            "https://пример.рф/путь?q=1#часть",
            shareableBrowserUrl("https://пример.рф/путь?q=1#часть"),
        )
    }

    @Test fun internalAndUnsafeSchemesAreNotShared() {
        assertNull(shareableBrowserUrl(null))
        assertNull(shareableBrowserUrl("about:blank"))
        assertNull(shareableBrowserUrl("file:///tmp/private"))
        assertNull(shareableBrowserUrl("javascript:alert(1)"))
    }
}
