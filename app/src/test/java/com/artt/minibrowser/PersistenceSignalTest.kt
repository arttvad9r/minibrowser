package com.artt.minibrowser

import com.artt.minibrowser.engine.PersistTabCandidate
import com.artt.minibrowser.engine.PersistSignal
import com.artt.minibrowser.engine.PersistSignalQueue
import com.artt.minibrowser.engine.mergePersistSignal
import com.artt.minibrowser.engine.snapshotPersistedState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
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

    @Test
    fun writerBusyImmediateThenDirtyKeepsPriorityForNextWrite() = runBlocking {
        val queue = PersistSignalQueue { _, _ -> null }
        queue.send(PersistSignal.Dirty)
        assertEquals(PersistSignal.Dirty, queue.nextForWrite())
        queue.send(PersistSignal.Immediate)
        queue.send(PersistSignal.Dirty)
        assertEquals(PersistSignal.Immediate, queue.nextForWrite())
    }

    @Test
    fun immediateDuringDebounceIsHandledWithoutWaitingForTimeout() = runBlocking {
        val debounceStarted = CompletableDeferred<Unit>()
        val queue = PersistSignalQueue { channel, _ ->
            debounceStarted.complete(Unit)
            channel.receive()
        }
        val next = async { queue.nextForWrite() }
        queue.send(PersistSignal.Dirty)
        debounceStarted.await()
        queue.send(PersistSignal.Immediate)
        assertEquals(PersistSignal.Immediate, next.await())
    }

    @Test
    fun dirtyFloodIsCoalescedBeforeNextWrite() = runBlocking {
        val queue = PersistSignalQueue { _, _ -> null }
        repeat(1_000) { queue.send(PersistSignal.Dirty) }
        assertEquals(PersistSignal.Dirty, queue.nextForWrite())
        queue.send(PersistSignal.Immediate)
        assertEquals(PersistSignal.Immediate, queue.nextForWrite())
    }
}
