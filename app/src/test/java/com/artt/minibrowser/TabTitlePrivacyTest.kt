package com.artt.minibrowser

import com.artt.minibrowser.data.PersistedBrowserState
import com.artt.minibrowser.data.PersistedTab
import com.artt.minibrowser.data.sanitizePersistedBrowserState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TabTitlePrivacyTest {
    @Test
    fun credentialUrlShapedTitleIsSanitizedAndOpaqueStateDropped() {
        val safeUrl = "https://example.org/page"
        val sanitized = sanitizePersistedBrowserState(
            PersistedBrowserState(
                selectedId = 1,
                tabs = listOf(
                    PersistedTab(
                        id = 1,
                        url = safeUrl,
                        title = "https://user:secret@example.com/private?q=1#part",
                        sessionState = "opaque-session-secret",
                        sessionStateUrl = safeUrl,
                    ),
                ),
            ),
        )

        val tab = sanitized.tabs.single()
        assertEquals(safeUrl, tab.url)
        assertEquals("https://example.com/private?q=1#part", tab.title)
        assertNull(tab.sessionState)
        assertNull(tab.sessionStateUrl)
    }
}
