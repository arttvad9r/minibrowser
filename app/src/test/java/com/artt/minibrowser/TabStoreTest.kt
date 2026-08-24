package com.artt.minibrowser

import com.artt.minibrowser.data.TabStore
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TabStoreTest {
    @Test fun roundTrip() {
        val dir = File(System.getProperty("java.io.tmpdir"), "tabs-${System.nanoTime()}").apply { mkdirs() }
        TabStore.save(dir, listOf("https://a.example/", "https://b.example/"))
        assertEquals(listOf("https://a.example/", "https://b.example/"), TabStore.load(dir))
        assertTrue(File(dir, "open_tabs.json").exists())
        dir.deleteRecursively()
    }
    @Test fun emptyWhenMissing() {
        val dir = File(System.getProperty("java.io.tmpdir"), "tabs-none-${System.nanoTime()}")
        assertEquals(emptyList(), TabStore.load(dir))
    }
    @Test fun privateTabsNotSaved() {
        val dir = File(System.getProperty("java.io.tmpdir"), "tabs-priv-${System.nanoTime()}").apply { mkdirs() }
        TabStore.save(dir, emptyList())
        assertEquals(emptyList(), TabStore.load(dir))
        dir.deleteRecursively()
    }
}
