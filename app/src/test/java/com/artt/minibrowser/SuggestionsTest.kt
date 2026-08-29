package com.artt.minibrowser

import com.artt.minibrowser.data.Bookmark
import com.artt.minibrowser.data.HistoryEntry
import com.artt.minibrowser.data.Suggestion
import com.artt.minibrowser.data.mergeSuggestions
import com.artt.minibrowser.data.rankSuggestions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SuggestionsTest {
    private val h = listOf(
        HistoryEntry("https://youtube.com/watch?v=1", "видео котики", 100, 1),
        HistoryEntry("https://ya.ru", "Яндекс", 200, 1),
        HistoryEntry("https://vk.com/feed", "Новости | VK", 300, 1),
    )

    @Test fun filtersAndSortsByFreshness() {
        val r = rankSuggestions(h, "")
        assertEquals("https://vk.com/feed", r.first().url)
    }

    @Test fun queryMatchesUrlOrTitle() {
        val r = rankSuggestions(h, "котики")
        assertEquals(listOf("https://youtube.com/watch?v=1"), r.map { it.url })
    }

    @Test fun cappedAtEight() {
        val big = (1..20).map { HistoryEntry("https://x$it.com", "t$it", it.toLong(), 1) }
        assertTrue(rankSuggestions(big, "").size <= 8)
    }

    @Test fun duplicateUrlsStillConsumeTopEightWindow() {
        val rows = listOf(
            HistoryEntry("https://dup.example", "new", 900, 1),
            HistoryEntry("https://dup.example", "old", 800, 1),
            HistoryEntry("https://1.example", "1", 700, 1),
            HistoryEntry("https://2.example", "2", 600, 1),
            HistoryEntry("https://3.example", "3", 500, 1),
            HistoryEntry("https://4.example", "4", 400, 1),
            HistoryEntry("https://5.example", "5", 300, 1),
            HistoryEntry("https://6.example", "6", 200, 1),
            HistoryEntry("https://outside.example", "outside", 100, 1),
        )

        val result = rankSuggestions(rows, "")
        assertEquals(7, result.size)
        assertFalse(result.any { it.url == "https://outside.example" })
    }

    @Test fun bookmarksKeepPriorityAndBoundMergedResults() {
        val bookmarks = (1..8).map {
            Bookmark("https://bookmark$it.example", "Bookmark $it", "bookmark$it.example", it)
        }
        val history = listOf(Suggestion("History", "https://history.example"))

        val result = mergeSuggestions(bookmarks, history)

        assertEquals(8, result.size)
        assertTrue(result.all { it.url.startsWith("https://bookmark") })
    }

    @Test fun bookmarkHistoryOverlapIsDeduplicatedWhileFillingRemainingSlots() {
        val bookmarks = listOf(
            Bookmark("https://same.example", "Saved", "same.example", 0),
            Bookmark("https://other.example", "Other", "other.example", 1),
        )
        val history = listOf(
            Suggestion("History duplicate", "https://same.example"),
            Suggestion("Fresh", "https://fresh.example"),
        )

        assertEquals(
            listOf("https://same.example", "https://other.example", "https://fresh.example"),
            mergeSuggestions(bookmarks, history).map { it.url },
        )
    }
}
