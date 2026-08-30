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

    @Test fun credentialLegacyBookmarkIsSanitizedBeforeUi() {
        val rawUrl = "https://user:secret@example.com/private?q=1#section"
        val bookmark = Bookmark(rawUrl, rawUrl, "example.com", 7)

        assertEquals(
            listOf(
                Bookmark(
                    url = "https://example.com/private?q=1#section",
                    title = "https://example.com/private?q=1#section",
                    host = "example.com",
                    position = 7,
                ),
            ),
            webBookmarks(listOf(bookmark)),
        )
    }

    @Test fun credentialVariantDoesNotDuplicateExistingSafeBookmark() {
        val safe = Bookmark("https://example.com/private", "Safe", "example.com", 1)
        val credential = Bookmark(
            "https://user:secret@example.com/private",
            "Credential copy",
            "example.com",
            2,
        )

        assertEquals(listOf(safe), webBookmarks(listOf(safe, credential)))
    }

    @Test fun omniboxMergeNeverPublishesUnsafeStoredUrls() {
        val unsafe = Bookmark("file:///tmp/private", "Private", "", 1)
        val credential = Bookmark(
            "https://user:secret@example.com/private",
            "Credential",
            "example.com",
            2,
        )
        val safe = Bookmark("https://example.org", "Example", "example.org", 3)

        assertEquals(
            listOf(
                "https://example.com/private",
                "https://example.org",
            ),
            mergeSuggestions(listOf(unsafe, credential, safe), emptyList()).map { it.url },
        )
    }
}
