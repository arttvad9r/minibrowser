package com.artt.minibrowser

import com.artt.minibrowser.engine.FaviconFetcher
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class FaviconCacheTest {
    @Test fun stableHostBasedPath() {
        val f1 = FaviconFetcher.cacheFile("youtube.com", File("/tmp/i"))
        val f2 = FaviconFetcher.cacheFile("YOUTUBE.COM", File("/tmp/i"))
        assertEquals(f1.path, f2.path)
        assertEquals("/tmp/i/${f1.name}", f1.path)
    }
    @Test fun differentHostsDifferentFiles() {
        assertNotEquals(FaviconFetcher.cacheFile("a.com", File("/i")).path,
                        FaviconFetcher.cacheFile("b.com", File("/i")).path)
    }
}
