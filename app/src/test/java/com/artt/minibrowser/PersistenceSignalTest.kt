package com.artt.minibrowser

import com.artt.minibrowser.engine.PersistTabCandidate
import com.artt.minibrowser.engine.PersistSignal
import com.artt.minibrowser.engine.PersistSignalQueue
import com.artt.minibrowser.engine.PersistenceSnapshot
import com.artt.minibrowser.engine.PersistenceTabSnapshot
import com.artt.minibrowser.engine.mergePersistSignal
import com.artt.minibrowser.engine.serializePersistenceSnapshot
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

    @Test fun immutablePersistenceSnapshotKeepsMetadataAndFiltersPrivateTabs() {
        val snapshot = PersistenceSnapshot(
            selectedId = 1,
            tabs = listOf(
                PersistenceTabSnapshot(1, "https://latest.example", "Latest", true, 12L, null, "state-c", false),
                PersistenceTabSnapshot(2, "https://private.example", "Private", false, 13L, null, "private-state", true),
            ),
        )
        val persisted = serializePersistenceSnapshot(snapshot)
        assertEquals(1L, persisted.selectedId)
        assertEquals(1, persisted.tabs.size)
        assertEquals("https://latest.example", persisted.tabs.single().url)
        assertEquals("state-c", persisted.tabs.single().sessionState)
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

    @Test
    fun dirtyFloodStopsWaitingAfterHardDeadline() = runBlocking {
        var now = 0L
        var awaitCalls = 0
        lateinit var queue: PersistSignalQueue
        queue = PersistSignalQueue(
            nowNanos = { now },
            awaitNextOrTimeout = { _, _ ->
                awaitCalls++
                check(awaitCalls <= 3) { "wait called after the 3s deadline" }
                now = when (awaitCalls) {
                    1 -> 1_000_000_000L
                    2 -> 2_000_000_000L
                    3 -> 3_001_000_000L
                    else -> 3_001_000_000L
                }
                queue.send(PersistSignal.Dirty)
                Unit
            },
        )
        queue.send(PersistSignal.Dirty)

        assertEquals(PersistSignal.Dirty, queue.nextForWrite())
        assertEquals(3, awaitCalls)
    }

    @Test
    fun dirtySignalsExtendTrailingDebounceButNotHardMaximum() = runBlocking {
        var now = 0L
        var awaitCalls = 0
        lateinit var queue: PersistSignalQueue
        queue = PersistSignalQueue(
            nowNanos = { now },
            awaitNextOrTimeout = { _, timeoutMs ->
                awaitCalls++
                assertEquals(1_000L, timeoutMs)
                now += 900_000_000L
                if (awaitCalls < 3) queue.send(PersistSignal.Dirty)
                null
            },
        )
        queue.send(PersistSignal.Dirty)

        assertEquals(PersistSignal.Dirty, queue.nextForWrite())
        assertEquals(3, awaitCalls)
    }

    @Test
    fun dirtySignalsCannotExtendDebouncePastThreeSeconds() = runBlocking {
        var now = 0L
        var awaitCalls = 0
        lateinit var queue: PersistSignalQueue
        queue = PersistSignalQueue(
            nowNanos = { now },
            awaitNextOrTimeout = { _, _ ->
                awaitCalls++
                now += 1_000_000_000L
                queue.send(PersistSignal.Dirty)
                Unit
            },
        )
        queue.send(PersistSignal.Dirty)

        assertEquals(PersistSignal.Dirty, queue.nextForWrite())
        assertEquals(3, awaitCalls)
    }
}
