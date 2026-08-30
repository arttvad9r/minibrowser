package com.artt.minibrowser

import com.artt.minibrowser.data.Bookmark
import com.artt.minibrowser.data.bookmarkForPersistence
import com.artt.minibrowser.data.bookmarkTitleForPersistence
import com.artt.minibrowser.data.mergeSuggestions
import com.artt.minibrowser.data.webBookmarks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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

    @Test fun bookmarkTitleSanitizesOnlyUrlShapedCredentials() {
        assertEquals(
            "https://example.com/private?q=1#part",
            bookmarkTitleForPersistence("https://user:secret@example.com/private?q=1#part"),
        )
        assertEquals("Обычный заголовок", bookmarkTitleForPersistence("Обычный заголовок"))
        assertEquals("", bookmarkTitleForPersistence(""))
    }

    @Test fun newCredentialBookmarkIsSanitizedBeforePersistence() {
        val rawUrl = "https://user:secret@example.com/private?q=1#section"

        assertEquals(
            Bookmark(
                url = "https://example.com/private?q=1#section",
                title = "https://example.com/private?q=1#section",
                host = "example.com",
                position = 4,
            ),
            bookmarkForPersistence(rawUrl, rawUrl, position = 4),
        )
        assertNull(bookmarkForPersistence("javascript:alert(1)", "Script", position = 4))
    }

    @Test fun newBookmarkSanitizesCredentialUrlShapedTitleIndependentlyOfUrl() {
        assertEquals(
            Bookmark(
                url = "https://example.org/page",
                title = "https://example.com/private",
                host = "example.org",
                position = 6,
            ),
            bookmarkForPersistence(
                "https://example.org/page",
                "https://user:secret@example.com/private",
                position = 6,
            ),
        )
    }

    @Test fun unicodeBookmarkUsesValidatedHostForFallbackTitle() {
        assertEquals(
            Bookmark(
                url = "https://пример.рф/страница",
                title = "пример.рф",
                host = "пример.рф",
                position = 5,
            ),
            bookmarkForPersistence("https://пример.рф/страница", "", position = 5),
        )
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

    @Test fun credentialLegacyBookmarkTitleIsSanitizedWhenUrlIsAlreadySafe() {
        val bookmark = Bookmark(
            "https://example.org/page",
            "https://user:secret@example.com/private",
            "example.org",
            8,
        )

        assertEquals(
            listOf(
                Bookmark(
                    "https://example.org/page",
                    "https://example.com/private",
                    "example.org",
                    8,
                ),
            ),
            webBookmarks(listOf(bookmark)),
        )
    }

    @Test fun legacyBookmarkHostIsRecomputedFromValidatedUrl() {
        val bookmark = Bookmark(
            "https://пример.рф/page",
            "Page",
            "user:secret@wrong.invalid",
            9,
        )

        assertEquals(
            listOf(Bookmark("https://пример.рф/page", "Page", "пример.рф", 9)),
            webBookmarks(listOf(bookmark)),
        )
    }

    @Test fun canonicalBookmarkWinsOverCredentialVariantInEitherOrder() {
        val safe = Bookmark("https://example.com/private", "Safe", "example.com", 2)
        val credential = Bookmark(
            "https://user:secret@example.com/private",
            "Credential copy",
            "example.com",
            1,
        )

        assertEquals(listOf(safe), webBookmarks(listOf(safe, credential)))
        assertEquals(listOf(safe), webBookmarks(listOf(credential, safe)))
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
