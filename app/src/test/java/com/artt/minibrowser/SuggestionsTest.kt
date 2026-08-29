package com.artt.minibrowser

import com.artt.minibrowser.data.Scored
import com.artt.minibrowser.data.rankSuggestions
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
