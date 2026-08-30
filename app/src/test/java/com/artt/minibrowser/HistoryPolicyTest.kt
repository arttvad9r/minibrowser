package com.artt.minibrowser

import com.artt.minibrowser.data.HistoryEntry
import com.artt.minibrowser.data.collapseHistoryNoise
import com.artt.minibrowser.data.distinctRecentSites
import com.artt.minibrowser.data.isHistoryUrl
import com.artt.minibrowser.data.webHistoryEntries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HistoryPolicyTest {
    @Test fun keepsNormalWebPages() {
        assertTrue(isHistoryUrl("https://drive.google.com/drive/my-drive"))
        assertTrue(isHistoryUrl("http://localhost:8080/test"))
    }

    @Test fun dropsInternalNonWebAndMalformedPages() {
        assertFalse(isHistoryUrl("about:blank"))
        assertFalse(isHistoryUrl("about:config"))
        assertFalse(isHistoryUrl("file:///tmp/test"))
        assertFalse(isHistoryUrl("data:text/plain,test"))
        assertFalse(isHistoryUrl("https://"))
        assertFalse(isHistoryUrl("https:///path"))
        assertFalse(isHistoryUrl("httpx://example.com"))
        assertFalse(isHistoryUrl(""))
    }

    @Test fun webHistoryFastPathReusesInput() {
        val rows = listOf(
            HistoryEntry("https://example.com/a", "A", 2, 1),
            HistoryEntry("http://example.com/b", "B", 1, 1),
        )
        assertSame(rows, webHistoryEntries(rows))
    }

    @Test fun webHistoryFilteringDropsLegacyInternalAndMalformedRows() {
        val first = HistoryEntry("https://example.com/a", "A", 4, 1)
        val internal = HistoryEntry("about:blank", "Blank", 3, 1)
        val malformed = HistoryEntry("https://", "Broken", 2, 1)
        val last = HistoryEntry("https://example.com/b", "B", 1, 1)
        assertEquals(listOf(first, last), webHistoryEntries(listOf(first, internal, malformed, last)))
    }

    @Test fun collapsesFastSameSiteSameTitleNavigationNoise() {
        val rows = listOf(
            HistoryEntry("https://drive.google.com/drive/u/0/my-drive", "Google Диск", 120_000, 1),
            HistoryEntry("https://drive.google.com/drive/u/0/home", "Google Диск", 90_000, 1),
            HistoryEntry("https://www.youtube.com/watch?v=1", "Видео", 80_000, 1),
        )
        val result = collapseHistoryNoise(rows)
        assertEquals(listOf(rows[0], rows[2]), result)
    }

    @Test fun comparesNoiseWindowWithLastRetainedEntry() {
        val rows = listOf(
            HistoryEntry("https://example.com/a", "Same page", 120_000, 1),
            HistoryEntry("https://example.com/b", "Same page", 110_000, 1),
            HistoryEntry("https://example.com/c", "Same page", 100_000, 1),
        )
        assertEquals(listOf(rows[0], rows[2]), collapseHistoryNoise(rows, windowMs = 15_000))
    }

    @Test fun sortsUnorderedInputBeforeCollapsing() {
        val newest = HistoryEntry("https://example.com/a", "Page", 120_000, 1)
        val middle = HistoryEntry("https://other.example/b", "Other", 110_000, 1)
        val oldest = HistoryEntry("https://example.com/c", "Page", 100_000, 1)

        assertEquals(
            listOf(newest, middle, oldest),
            collapseHistoryNoise(listOf(oldest, newest, middle)),
        )
    }

    @Test fun keepsDifferentPagesAndLaterRevisits() {
        val rows = listOf(
            HistoryEntry("https://example.com/a", "Страница A", 500_000, 1),
            HistoryEntry("https://example.com/b", "Страница B", 490_000, 1),
            HistoryEntry("https://example.com/c", "Страница A", 100_000, 1),
        )
        val result = collapseHistoryNoise(rows)
        assertSame(rows, result)
        assertEquals(rows, result)
    }

    @Test fun startPageRecentUsesDistinctHosts() {
        val driveNewest = HistoryEntry("https://drive.google.com/drive/my-drive", "Google Диск", 500_000, 1)
        val driveOlder = HistoryEntry("https://drive.google.com/drive/home", "Google", 490_000, 1)
        val google = HistoryEntry("https://www.google.com/search?q=test", "Google", 480_000, 1)
        val youtube = HistoryEntry("https://m.youtube.com/watch?v=1", "YouTube", 470_000, 1)

        assertEquals(
            listOf(driveNewest, google, youtube),
            distinctRecentSites(listOf(driveNewest, driveOlder, google, youtube), 3),
        )
    }

    @Test fun startPageTreatsWwwPrefixCaseInsensitively() {
        val newest = HistoryEntry("https://WWW.Example.com/new", "New", 500_000, 1)
        val older = HistoryEntry("https://www.example.com/old", "Old", 490_000, 1)

        assertEquals(
            listOf(newest),
            distinctRecentSites(listOf(newest, older), 8),
        )
    }
}
