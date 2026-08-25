package com.artt.minibrowser

import com.artt.minibrowser.engine.PersistTabCandidate
import com.artt.minibrowser.engine.PersistSignal
import com.artt.minibrowser.engine.mergePersistSignal
import com.artt.minibrowser.engine.snapshotPersistedState
import kotlin.test.Test
import kotlin.test.assertEquals

class PersistenceSignalTest {
    @Test fun immediateSignalWinsOverDirtyDebounce() {
        assertEquals(PersistSignal.Immediate, mergePersistSignal(PersistSignal.Dirty, PersistSignal.Immediate))
    }

    @Test fun snapshotUsesLatestTabStateAndExcludesPrivateTabs() {
        val normal = PersistTabCandidate(1, "https://latest.example", "Latest", false, "state-c", 3, false)
        val private = PersistTabCandidate(2, "https://private.example", "Private", false, "private-state", 3, true)
        val snapshot = snapshotPersistedState(1, listOf(normal, private))
        assertEquals("https://latest.example", snapshot.tabs.single().url)
        assertEquals("Latest", snapshot.tabs.single().title)
    }
}
