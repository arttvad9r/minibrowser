package com.artt.minibrowser

import com.artt.minibrowser.data.BrowserDownload
import com.artt.minibrowser.data.DownloadFailureReason
import com.artt.minibrowser.data.DownloadStatus
import com.artt.minibrowser.data.downloadSourceForHistory
import com.artt.minibrowser.data.mergeRestoredDownloads
import com.artt.minibrowser.data.normalizeRestoredDownload
import com.artt.minibrowser.data.shouldPersistRestoredDownloadMerge
import com.artt.minibrowser.data.writeTextAtomically
import com.artt.minibrowser.engine.shouldPersistDownloadHistory
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadHistoryTest {
    @Test fun stripsQueryFragmentAndCredentials() {
        assertEquals(
            "https://example.com",
            downloadSourceForHistory("https://user:secret@example.com/path/file.zip?token=secret#part"),
        )
    }

    @Test fun preservesNonDefaultPortWithoutPath() {
        assertEquals(
            "https://example.com:8443",
            downloadSourceForHistory("https://example.com:8443/private/file?q=1"),
        )
    }

    @Test fun rejectsNonWebAndMalformedSources() {
        assertEquals("", downloadSourceForHistory("file:///tmp/private"))
        assertEquals("", downloadSourceForHistory("not a url"))
    }

    @Test fun privateDownloadsDoNotPersistAppHistory() {
        assertTrue(shouldPersistDownloadHistory(isPrivate = false))
        assertFalse(shouldPersistDownloadHistory(isPrivate = true))
    }

    @Test fun restoredInFlightDownloadBecomesSanitizedStructuredFailure() {
        val restored = BrowserDownload(
            id = "old",
            name = "archive.zip",
            sourceUrl = "https://user:secret@example.com/private/archive.zip?token=secret",
            mime = "application/zip",
            status = DownloadStatus.Downloading,
            startedAt = 10L,
        )

        val normalized = normalizeRestoredDownload(restored, now = 42L)

        assertEquals("https://example.com", normalized.sourceUrl)
        assertEquals(DownloadStatus.Failed, normalized.status)
        assertEquals(42L, normalized.finishedAt)
        assertEquals(DownloadFailureReason.Interrupted, normalized.failureReason)
        assertNull(normalized.error)
    }

    @Test fun legacyInterruptedFailureMigratesToStructuredReason() {
        val restored = BrowserDownload(
            id = "legacy",
            name = "legacy.zip",
            sourceUrl = "https://example.com",
            mime = "application/zip",
            status = DownloadStatus.Failed,
            startedAt = 10L,
            finishedAt = 20L,
            error = "Загрузка была прервана",
        )

        val normalized = normalizeRestoredDownload(restored, now = 42L)

        assertEquals(DownloadFailureReason.Interrupted, normalized.failureReason)
        assertNull(normalized.error)
        assertTrue(
            shouldPersistRestoredDownloadMerge(
                live = emptyList(),
                rawRestored = listOf(restored),
                normalizedRestored = listOf(normalized),
                discardRestoredHistory = false,
            ),
        )
    }

    @Test fun liveDownloadWinsWhenRestoreCompletesLater() {
        val live = BrowserDownload(
            id = "same",
            name = "new.bin",
            sourceUrl = "https://new.example",
            mime = "application/octet-stream",
            status = DownloadStatus.Downloading,
            startedAt = 20L,
        )
        val restoredSameId = live.copy(
            name = "old.bin",
            sourceUrl = "https://old.example",
            status = DownloadStatus.Completed,
            startedAt = 1L,
            finishedAt = 2L,
        )
        val restoredOther = restoredSameId.copy(id = "other", name = "other.bin")

        val merged = mergeRestoredDownloads(
            live = listOf(live),
            restored = listOf(restoredSameId, restoredOther),
            limit = 2,
        )

        assertEquals(listOf("same", "other"), merged.map { it.id })
        assertEquals("new.bin", merged.first().name)
        assertEquals(DownloadStatus.Downloading, merged.first().status)
    }

    @Test fun restoredMergeHonorsBoundedHistorySize() {
        val restored = (0 until 5).map { index ->
            BrowserDownload(
                id = "$index",
                name = "$index.bin",
                sourceUrl = "https://example.com",
                mime = "application/octet-stream",
                status = DownloadStatus.Completed,
                startedAt = index.toLong(),
            )
        }

        assertEquals(3, mergeRestoredDownloads(emptyList(), restored, limit = 3).size)
    }

    @Test fun settledSanitizedRestoreDoesNotRewriteDisk() {
        val restored = listOf(
            BrowserDownload(
                id = "done",
                name = "done.bin",
                sourceUrl = "https://example.com",
                mime = "application/octet-stream",
                status = DownloadStatus.Completed,
                startedAt = 1L,
                finishedAt = 2L,
            ),
        )

        assertFalse(
            shouldPersistRestoredDownloadMerge(
                live = emptyList(),
                rawRestored = restored,
                normalizedRestored = restored,
                discardRestoredHistory = false,
            ),
        )
    }

    @Test fun changedOrRacedRestoreIsPersisted() {
        val raw = BrowserDownload(
            id = "old",
            name = "old.bin",
            sourceUrl = "https://example.com/path?secret=1",
            mime = "application/octet-stream",
            status = DownloadStatus.Completed,
            startedAt = 1L,
        )
        val normalized = normalizeRestoredDownload(raw, now = 2L)
        val live = raw.copy(id = "live", sourceUrl = "https://live.example")

        assertTrue(shouldPersistRestoredDownloadMerge(emptyList(), listOf(raw), listOf(normalized), false))
        assertTrue(shouldPersistRestoredDownloadMerge(listOf(live), listOf(raw), listOf(raw), false))
        assertTrue(shouldPersistRestoredDownloadMerge(emptyList(), listOf(raw), listOf(raw), true))
    }

    @Test fun atomicWriteReplacesExistingHistoryAndRemovesTempFile() {
        val dir = Files.createTempDirectory("minibrowser-download-history").toFile()
        try {
            val target = File(dir, "downloads.json")
            target.writeText("old")

            writeTextAtomically(target, "new complete payload")

            assertEquals("new complete payload", target.readText())
            assertFalse(File(dir, "downloads.json.tmp").exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
