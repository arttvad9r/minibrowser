package com.artt.minibrowser

import com.artt.minibrowser.data.HistoryEntry
import com.artt.minibrowser.data.collapseHistoryNoise
import com.artt.minibrowser.data.distinctRecentSites
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryUnicodePolicyTest {
    @Test
    fun recentSitesTreatUnicodeHostAsARealSiteKey() {
        val newest = HistoryEntry(
            url = "https://пример.рф/новости",
            title = "Новости",
            visitedAt = 3_000,
            visits = 1,
        )
        val sameSite = HistoryEntry(
            url = "https://пример.рф/другое",
            title = "Другое",
            visitedAt = 2_000,
            visits = 1,
        )
        val otherSite = HistoryEntry(
            url = "https://книга.рф/",
            title = "Книга",
            visitedAt = 1_000,
            visits = 1,
        )

        assertEquals(
            listOf(newest, otherSite),
            distinctRecentSites(listOf(newest, sameSite, otherSite), limit = 3),
        )
    }

    @Test
    fun noiseCollapseUsesUnicodeHostInsteadOfEmptyHost() {
        val first = HistoryEntry(
            url = "https://пример.рф/a",
            title = "Одна страница",
            visitedAt = 10_000,
            visits = 1,
        )
        val sameSiteRedirect = HistoryEntry(
            url = "https://пример.рф/b",
            title = "Одна страница",
            visitedAt = 9_500,
            visits = 1,
        )
        val differentUnicodeSite = HistoryEntry(
            url = "https://другой.рф/b",
            title = "Одна страница",
            visitedAt = 9_000,
            visits = 1,
        )

        assertEquals(
            listOf(first, differentUnicodeSite),
            collapseHistoryNoise(listOf(first, sameSiteRedirect, differentUnicodeSite)),
        )
    }
}
