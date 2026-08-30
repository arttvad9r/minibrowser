package com.artt.minibrowser

import com.artt.minibrowser.data.PersistedBrowserState
import com.artt.minibrowser.data.PersistedTab
import com.artt.minibrowser.data.TabStore
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TabStoreRevisionAllocatorTest {
    @Test
    fun freshManagerRevisionStaysNewerThanPreviousManagerBarrier() {
        val dir = File(System.getProperty("java.io.tmpdir"), "tabs-revision-manager-${System.nanoTime()}")
        val first = PersistedBrowserState(1, listOf(PersistedTab(1, "https://first.example", "First")))
        val finalFromOldManager = PersistedBrowserState(2, listOf(PersistedTab(2, "https://old-final.example", "Old final")))
        val freshFromNewManager = PersistedBrowserState(3, listOf(PersistedTab(3, "https://new-manager.example", "New")))

        val firstRevision = TabStore.nextRevision(dir)
        assertTrue(TabStore.saveStateVersioned(dir, first, firstRevision))
        val oldManagerFinalRevision = TabStore.nextRevision(dir)
        assertTrue(TabStore.saveStateVersioned(dir, finalFromOldManager, oldManagerFinalRevision))

        val newManagerRevision = TabStore.nextRevision(dir)
        assertTrue(newManagerRevision > oldManagerFinalRevision)
        assertTrue(TabStore.saveStateVersioned(dir, freshFromNewManager, newManagerRevision))

        assertFalse(TabStore.saveStateVersioned(dir, first, firstRevision))
        assertEquals(freshFromNewManager, TabStore.loadState(dir))
        dir.deleteRecursively()
    }

    @Test
    fun allocatedRevisionsStayUniqueBeforeEitherSnapshotIsWritten() {
        val dir = File(System.getProperty("java.io.tmpdir"), "tabs-revision-reserve-${System.nanoTime()}")

        val first = TabStore.nextRevision(dir)
        val second = TabStore.nextRevision(dir)

        assertTrue(second > first)
        dir.deleteRecursively()
    }
}
