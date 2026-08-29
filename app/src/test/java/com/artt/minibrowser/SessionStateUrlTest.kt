package com.artt.minibrowser

import com.artt.minibrowser.engine.SessionStateSelection
import com.artt.minibrowser.engine.selectSessionStateForUrl
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionStateUrlTest {
    @Test
    fun latestStateWinsWhenItBelongsToCurrentUrl() {
        assertEquals(
            SessionStateSelection("latest", "https://example.com/new"),
            selectSessionStateForUrl(
                tabUrl = "https://example.com/new",
                latestState = "latest",
                latestStateUrl = "https://example.com/new",
                serializedState = "persisted",
                serializedStateUrl = "https://example.com/new",
            ),
        )
    }

    @Test
    fun staleLatestStateFallsBackToMatchingPersistedState() {
        assertEquals(
            SessionStateSelection("persisted", "https://example.com/new"),
            selectSessionStateForUrl(
                tabUrl = "https://example.com/new",
                latestState = "old latest",
                latestStateUrl = "https://example.com/old",
                serializedState = "persisted",
                serializedStateUrl = "https://example.com/new",
            ),
        )
    }

    @Test
    fun noStateIsPersistedWhenAllStatesBelongToOtherUrls() {
        assertEquals(
            SessionStateSelection(null, null),
            selectSessionStateForUrl(
                tabUrl = "https://example.com/new",
                latestState = "old latest",
                latestStateUrl = "https://example.com/old",
                serializedState = "older persisted",
                serializedStateUrl = "https://example.com/older",
            ),
        )
    }
}
