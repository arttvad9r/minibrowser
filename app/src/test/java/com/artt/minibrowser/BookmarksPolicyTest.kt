package com.artt.minibrowser

import com.artt.minibrowser.data.Bookmark
import com.artt.minibrowser.data.mergeSuggestions
import com.artt.minibrowser.data.webBookmarks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class BookmarksPolicyTest {
    @Test fun validBookmarksReuseInput() {
        val bookmarks = listOf(
            Bookmark("https://example.com", "Example", "example.com", 1),
            Bookmark("http://localhost:8080", "Local", "localhost", 2),
        )

        assertSame(bookmarks, webBookmarks(bookmarks))
    }

    @Test fun malformedLegacyBookmarksAreFiltered() {
        val safe = Bookmark("https://example.com", "Example", "example.com", 1)
        val hostless = Bookmark("https://", "Broken", "", 2)
        val script = Bookmark("javascript:alert(1)", "Script", "", 3)

        assertEquals(listOf(safe), webBookmarks(listOf(safe, hostless, script)))
    }

    @Test fun omniboxMergeNeverPublishesUnsafeStoredUrls() {
        val unsafe = Bookmark("file:///tmp/private", "Private", "", 1)
        val safe = Bookmark("https://example.com", "Example", "example.com", 2)

        assertEquals(
            listOf("https://example.com"),
            mergeSuggestions(listOf(unsafe, safe), emptyList()).map { it.url },
        )
    }
}
