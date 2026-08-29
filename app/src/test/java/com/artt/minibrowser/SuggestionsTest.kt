package com.artt.minibrowser

import com.artt.minibrowser.data.Scored
import com.artt.minibrowser.data.rankSuggestions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SuggestionsTest {
    private val h = listOf(
        Scored("https://youtube.com/watch?v=1", "видео котики", 100),
        Scored("https://ya.ru", "Яндекс", 200),
        Scored("https://vk.com/feed", "Новости | VK", 300),
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
        val big = (1..20).map { Scored("https://x$it.com", "t$it", it.toLong()) }
        assertTrue(rankSuggestions(big, "").size <= 8)
    }

    @Test fun duplicateUrlsStillConsumeTopEightWindow() {
        val rows = listOf(
            Scored("https://dup.example", "new", 900),
            Scored("https://dup.example", "old", 800),
            Scored("https://1.example", "1", 700),
            Scored("https://2.example", "2", 600),
            Scored("https://3.example", "3", 500),
            Scored("https://4.example", "4", 400),
            Scored("https://5.example", "5", 300),
            Scored("https://6.example", "6", 200),
            Scored("https://outside.example", "outside", 100),
        )

        val result = rankSuggestions(rows, "")
        assertEquals(7, result.size)
        assertFalse(result.any { it.url == "https://outside.example" })
    }
}
