package com.artt.minibrowser

import com.artt.minibrowser.data.HistoryEntry
import com.artt.minibrowser.data.collapseHistoryNoise
import com.artt.minibrowser.data.distinctRecentSites
import com.artt.minibrowser.data.isHistoryUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryPolicyTest {
    @Test fun keepsNormalWebPages() {
        assertTrue(isHistoryUrl("https://drive.google.com/drive/my-drive"))
        assertTrue(isHistoryUrl("http://localhost:8080/test"))
    }

    @Test fun dropsInternalAndNonWebPages() {
        assertFalse(isHistoryUrl("about:blank"))
        assertFalse(isHistoryUrl("about:config"))
        assertFalse(isHistoryUrl("file:///tmp/test"))
        assertFalse(isHistoryUrl("data:text/plain,test"))
        assertFalse(isHistoryUrl(""))
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

    @Test fun keepsDifferentPagesAndLaterRevisits() {
        val rows = listOf(
            HistoryEntry("https://example.com/a", "Страница A", 500_000, 1),
            HistoryEntry("https://example.com/b", "Страница B", 490_000, 1),
            HistoryEntry("https://example.com/c", "Страница A", 100_000, 1),
        )
        assertEquals(rows, collapseHistoryNoise(rows))
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
}
