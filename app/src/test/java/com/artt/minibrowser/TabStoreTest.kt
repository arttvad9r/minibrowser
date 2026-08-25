package com.artt.minibrowser

import com.artt.minibrowser.data.TabStore
import com.artt.minibrowser.data.PersistedBrowserState
import com.artt.minibrowser.data.PersistedTab
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

    @Test fun persistsMetadataAndSelectedTabAtomically() {
        val dir = File(System.getProperty("java.io.tmpdir"), "tabs-state-${System.nanoTime()}")
        TabStore.saveState(dir, PersistedBrowserState(7, listOf(PersistedTab(7, "https://a.example", "A", desktop = true))))
        val state = TabStore.loadState(dir)
        assertEquals(7, state.selectedId)
        assertEquals(true, state.tabs.single().desktop)
        assertTrue(!File(dir, "open_tabs.json.tmp").exists())
        dir.deleteRecursively()
    }

    @Test fun corruptedStateFallsBackToEmpty() {
        val dir = File(System.getProperty("java.io.tmpdir"), "tabs-corrupt-${System.nanoTime()}").apply { mkdirs() }
        File(dir, "open_tabs.json").writeText("not-json")
        assertEquals(emptyList(), TabStore.loadState(dir).tabs)
        assertTrue(dir.listFiles()?.any { it.name.startsWith("open_tabs.json.corrupt-") } == true)
        dir.deleteRecursively()
    }

    @Test fun readsPreviousUrlOnlyFormat() {
        val dir = File(System.getProperty("java.io.tmpdir"), "tabs-legacy-${System.nanoTime()}").apply { mkdirs() }
        File(dir, "open_tabs.json").writeText("[\"https://legacy.example\"]")
        assertEquals(listOf("https://legacy.example"), TabStore.load(dir))
        dir.deleteRecursively()
    }
}
