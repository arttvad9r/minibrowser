package com.artt.minibrowser

import com.artt.minibrowser.data.downloadSourceForHistory
import com.artt.minibrowser.data.writeTextAtomically
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

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
