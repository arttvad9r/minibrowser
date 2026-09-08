package com.artt.minibrowser

import com.artt.minibrowser.data.PersistedBrowserState
import com.artt.minibrowser.data.PersistedTab
import com.artt.minibrowser.data.TabStore
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun queuedFinalSnapshotCompletesBeforeFollowingPreload() {
        val dir = File(System.getProperty("java.io.tmpdir"), "tabs-preload-final-${System.nanoTime()}")
        val old = PersistedBrowserState(1, listOf(PersistedTab(1, "https://old.example", "Old")))
        val final = PersistedBrowserState(2, listOf(PersistedTab(2, "https://final.example", "Final")))
        val oldRevision = TabStore.nextRevision(dir)
        assertTrue(TabStore.saveStateVersioned(dir, old, oldRevision))

        val finalRevision = TabStore.nextRevision(dir)
        assertTrue(TabStore.enqueueStateVersioned(dir, final, finalRevision))
        // preloadStateForNextRestore is submitted to the same single-thread IO executor after the
        // queued final write, so recreation cannot overtake Activity shutdown persistence.
        assertEquals(final, TabStore.preloadStateForNextRestore(dir))
        assertEquals(final, TabStore.loadState(dir))
        assertFalse(TabStore.saveStateVersioned(dir, old, oldRevision))
        assertEquals(final, TabStore.loadState(dir))
        dir.deleteRecursively()
    }
}
