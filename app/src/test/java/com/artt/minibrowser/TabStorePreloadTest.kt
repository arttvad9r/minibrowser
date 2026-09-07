package com.artt.minibrowser

import com.artt.minibrowser.data.PersistedBrowserState
import com.artt.minibrowser.data.PersistedTab
import com.artt.minibrowser.data.TabStore
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class TabStorePreloadTest {
    @Test
    fun preloadedStateIsConsumedExactlyOnce() {
        val dir = File(System.getProperty("java.io.tmpdir"), "tabs-preload-${System.nanoTime()}")
        val first = PersistedBrowserState(1, listOf(PersistedTab(1, "https://one.example", "One")))
        val second = PersistedBrowserState(2, listOf(PersistedTab(2, "https://two.example", "Two")))
        TabStore.saveState(dir, first)

        TabStore.preloadStateForNextRestore(dir)
        File(dir, "open_tabs.json").writeText(
            """{"selectedId":2,"tabs":[{"id":2,"url":"https://two.example","title":"Two"}]}""",
        )

        assertEquals(first, TabStore.loadState(dir))
        assertEquals(second, TabStore.loadState(dir))
        dir.deleteRecursively()
    }

    @Test
    fun newerStoreWriteInvalidatesPreloadedSnapshot() {
        val dir = File(System.getProperty("java.io.tmpdir"), "tabs-preload-write-${System.nanoTime()}")
        val first = PersistedBrowserState(1, listOf(PersistedTab(1, "https://one.example", "One")))
        val second = PersistedBrowserState(2, listOf(PersistedTab(2, "https://two.example", "Two")))
        TabStore.saveState(dir, first)

        TabStore.preloadStateForNextRestore(dir)
        TabStore.saveState(dir, second)

        assertEquals(second, TabStore.loadState(dir))
        dir.deleteRecursively()
    }
}
