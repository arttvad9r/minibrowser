package com.artt.minibrowser

import com.artt.minibrowser.engine.FaviconFetcher
import com.artt.minibrowser.engine.faviconTempFile
import com.artt.minibrowser.engine.trimFaviconDiskCache
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FaviconCacheTest {
    @Test fun stableHostBasedPath() {
        val f1 = FaviconFetcher.cacheFile("youtube.com", File("/tmp/i"))
        val f2 = FaviconFetcher.cacheFile("YOUTUBE.COM", File("/tmp/i"))
        assertEquals(f1.path, f2.path)
        assertEquals("/tmp/i/${f1.name}", f1.path)
    }

    @Test fun differentHostsDifferentFiles() {
        assertNotEquals(
            FaviconFetcher.cacheFile("a.com", File("/i")).path,
            FaviconFetcher.cacheFile("b.com", File("/i")).path,
        )
    }

    @Test fun tempFilesAreGenerationSpecific() {
        val cache = FaviconFetcher.cacheFile("https://example.com", File("/tmp/i"))
        val oldTemp = faviconTempFile(cache, 3L)
        val newTemp = faviconTempFile(cache, 4L)

        assertNotEquals(oldTemp.path, newTemp.path)
        assertTrue(oldTemp.name.endsWith(".3.tmp"))
        assertTrue(newTemp.name.endsWith(".4.tmp"))
    }

    @Test fun trimDeletesOnlyStaleTempFiles() {
        val dir = Files.createTempDirectory("minibrowser-favicon-cache").toFile()
        try {
            val now = 100_000L
            val stale = File(dir, "old.1.tmp").apply {
                writeText("old")
                setLastModified(now - 10_000L)
            }
            val active = File(dir, "active.2.tmp").apply {
                writeText("active")
                setLastModified(now - 1_000L)
            }

            trimFaviconDiskCache(
                iconsDir = dir,
                maxFiles = 256,
                maxBytes = 32L * 1024 * 1024,
                nowMs = now,
                orphanTempTtlMs = 5_000L,
            )

            assertFalse(stale.exists())
            assertTrue(active.exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
