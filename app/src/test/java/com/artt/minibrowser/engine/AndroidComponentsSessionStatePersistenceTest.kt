package com.artt.minibrowser.engine

import android.util.JsonWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import mozilla.components.concept.engine.EngineSessionState

class AndroidComponentsSessionStatePersistenceTest {
    @Test
    fun correlatedStateBindsExactOpaqueStateToCallbackUrl() {
        val persistence = AndroidComponentsSessionStatePersistenceState()
        val observer = AndroidComponentsSessionStatePersistenceObserver("7", persistence)
        val engineState = TestEngineSessionState()

        observer.withRawSessionStateUrl("https://example.com/page") {
            observer.onStateUpdated(engineState)
        }

        val snapshot = persistence.snapshot("7")
        assertSame(engineState, snapshot?.state)
        assertEquals("https://example.com/page", snapshot?.stateUrl)
    }

    @Test
    fun correlatedStateReportsExactBoundUrlForPersistenceWakeup() {
        val persistence = AndroidComponentsSessionStatePersistenceState()
        val changes = mutableListOf<Pair<String, String?>>()
        val observer = AndroidComponentsSessionStatePersistenceObserver(
            sessionId = "7",
            persistenceState = persistence,
            onPersistenceStateChanged = { sessionId, stateUrl -> changes += sessionId to stateUrl },
        )

        observer.withRawSessionStateUrl("https://example.com/page") {
            observer.onStateUpdated(TestEngineSessionState())
        }

        assertEquals(
            listOf<Pair<String, String?>>("7" to "https://example.com/page"),
            changes,
        )
    }

    @Test
    fun persistenceWakeupPolicyOnlyAllowsNonPrivateRelinquishedTabs() {
        assertTrue(
            shouldRequestAndroidComponentsSessionStatePersist(
                isPrivate = false,
                ownership = RawSessionOwnership.Relinquished,
            ),
        )
        assertFalse(
            shouldRequestAndroidComponentsSessionStatePersist(
                isPrivate = true,
                ownership = RawSessionOwnership.Relinquished,
            ),
        )
        assertFalse(
            shouldRequestAndroidComponentsSessionStatePersist(
                isPrivate = false,
                ownership = RawSessionOwnership.Owned,
            ),
        )
    }

    @Test
    fun missingOrBlankUrlFailsClosedInsteadOfKeepingState() {
        val persistence = AndroidComponentsSessionStatePersistenceState()
        val observer = AndroidComponentsSessionStatePersistenceObserver("7", persistence)
        val previous = TestEngineSessionState()
        persistence.bind("7", "https://example.com/old", previous)

        observer.withRawSessionStateUrl("   ") {
            observer.onStateUpdated(TestEngineSessionState())
        }

        assertNull(persistence.snapshot("7"))
    }

    @Test
    fun missingCorrelatedStateClearsPreviousSnapshotAndReportsInvalidation() {
        val persistence = AndroidComponentsSessionStatePersistenceState()
        val changes = mutableListOf<Pair<String, String?>>()
        val observer = AndroidComponentsSessionStatePersistenceObserver(
            sessionId = "7",
            persistenceState = persistence,
            onPersistenceStateChanged = { sessionId, stateUrl -> changes += sessionId to stateUrl },
        )
        persistence.bind("7", "https://example.com/old", TestEngineSessionState())

        observer.withRawSessionStateUrl("https://example.com/new") {
            // A stock delegate that unexpectedly emits no EngineSessionState must not leave old data.
        }

        assertNull(persistence.snapshot("7"))
        assertEquals(listOf<Pair<String, String?>>("7" to null), changes)
    }

    @Test
    fun uncorrelatedEngineStateClearsPreviousSnapshotAndReportsInvalidation() {
        val persistence = AndroidComponentsSessionStatePersistenceState()
        val changes = mutableListOf<Pair<String, String?>>()
        val observer = AndroidComponentsSessionStatePersistenceObserver(
            sessionId = "7",
            persistenceState = persistence,
            onPersistenceStateChanged = { sessionId, stateUrl -> changes += sessionId to stateUrl },
        )
        persistence.bind("7", "https://example.com/old", TestEngineSessionState())

        observer.onStateUpdated(TestEngineSessionState())

        assertNull(persistence.snapshot("7"))
        assertEquals(listOf<Pair<String, String?>>("7" to null), changes)
    }

    @Test
    fun throwingInvalidationCallbackDoesNotLeakCorrelationContext() {
        val persistence = AndroidComponentsSessionStatePersistenceState()
        val observer = AndroidComponentsSessionStatePersistenceObserver(
            sessionId = "7",
            persistenceState = persistence,
            onPersistenceStateChanged = { _, _ -> error("persist signal failed") },
        )

        assertFailsWith<IllegalStateException> {
            observer.withRawSessionStateUrl("https://example.com/first") {
                // Force fail-closed invalidation and a throwing external signal callback.
            }
        }

        assertFailsWith<IllegalStateException> {
            observer.onStateUpdated(TestEngineSessionState())
        }
        assertNull(persistence.snapshot("7"))
    }

    @Test
    fun retainDropsOnlySnapshotsWithoutLiveBrowserStoreTabs() {
        val persistence = AndroidComponentsSessionStatePersistenceState()
        val kept = TestEngineSessionState()
        persistence.bind("7", "https://example.com/kept", kept)
        persistence.bind("8", "https://example.com/dropped", TestEngineSessionState())

        persistence.retain(setOf("7", "9"))

        assertSame(kept, persistence.snapshot("7")?.state)
        assertNull(persistence.snapshot("8"))
        assertNull(persistence.snapshot("9"))
    }

    private class TestEngineSessionState : EngineSessionState {
        override fun writeTo(writer: JsonWriter) = Unit
    }
}
