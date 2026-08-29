package com.artt.minibrowser

import com.artt.minibrowser.engine.PersistTabCandidate
import com.artt.minibrowser.engine.PersistSignal
import com.artt.minibrowser.engine.PersistSignalQueue
import com.artt.minibrowser.engine.PersistenceSnapshot
import com.artt.minibrowser.engine.PersistenceTabSnapshot
import com.artt.minibrowser.engine.mergePersistSignal
import com.artt.minibrowser.engine.serializePersistenceSnapshot
import com.artt.minibrowser.engine.shouldCreateBlankTabAfterClear
import com.artt.minibrowser.engine.snapshotPersistedState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
        assertEquals("https://latest.example", snapshot.tabs.single().sessionStateUrl)
    }

    @Test fun immutablePersistenceSnapshotKeepsMetadataAndFiltersPrivateTabs() {
        val snapshot = PersistenceSnapshot(
            selectedId = 1,
            tabs = listOf(
                PersistenceTabSnapshot(
                    id = 1,
                    url = "https://latest.example",
                    title = "Latest",
                    desktop = true,
                    lastAccess = 12L,
                    latestSessionState = null,
                    latestSessionStateUrl = null,
                    serializedSessionState = "state-c",
                    serializedSessionStateUrl = "https://latest.example",
                    isPrivate = false,
                ),
                PersistenceTabSnapshot(
                    id = 2,
                    url = "https://private.example",
                    title = "Private",
                    desktop = false,
                    lastAccess = 13L,
                    latestSessionState = null,
                    latestSessionStateUrl = null,
                    serializedSessionState = "private-state",
                    serializedSessionStateUrl = "https://private.example",
                    isPrivate = true,
                ),
            ),
        )
        val persisted = serializePersistenceSnapshot(snapshot)
        assertEquals(1L, persisted.selectedId)
        assertEquals(1, persisted.tabs.size)
        assertEquals("https://latest.example", persisted.tabs.single().url)
        assertEquals("state-c", persisted.tabs.single().sessionState)
        assertEquals("https://latest.example", persisted.tabs.single().sessionStateUrl)
    }

    @Test fun onlyLatestClearRestoresOneBlankTab() {
        assertTrue(
            shouldCreateBlankTabAfterClear(
                requestGeneration = 2,
                currentGeneration = 2,
                hasTabs = false,
                isClosed = false,
            ),
        )
        assertFalse(
            shouldCreateBlankTabAfterClear(
                requestGeneration = 1,
                currentGeneration = 2,
                hasTabs = false,
                isClosed = false,
            ),
        )
        assertFalse(
            shouldCreateBlankTabAfterClear(
                requestGeneration = 2,
                currentGeneration = 2,
                hasTabs = true,
                isClosed = false,
            ),
        )
        assertFalse(
            shouldCreateBlankTabAfterClear(
                requestGeneration = 2,
                currentGeneration = 2,
                hasTabs = false,
                isClosed = true,
            ),
        )
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
                check(awaitCalls <= 5) { "wait called after the 5s deadline" }
                now = awaitCalls * 1_000_000_000L + if (awaitCalls == 5) 1_000_000L else 0L
                queue.send(PersistSignal.Dirty)
                Unit
            },
        )
        queue.send(PersistSignal.Dirty)

        assertEquals(PersistSignal.Dirty, queue.nextForWrite())
        assertEquals(5, awaitCalls)
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
                assertEquals(1_500L, timeoutMs)
                now += 1_400_000_000L
                if (awaitCalls < 3) queue.send(PersistSignal.Dirty)
                null
            },
        )
        queue.send(PersistSignal.Dirty)

        assertEquals(PersistSignal.Dirty, queue.nextForWrite())
        assertEquals(3, awaitCalls)
    }

    @Test
    fun dirtySignalsCannotExtendDebouncePastFiveSeconds() = runBlocking {
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
        assertEquals(5, awaitCalls)
    }
}
