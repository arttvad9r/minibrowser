package com.artt.minibrowser.engine

import android.util.JsonWriter
import com.artt.minibrowser.data.PersistedSessionOwner
import mozilla.components.browser.state.state.createTab
import mozilla.components.concept.engine.EngineSessionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class AndroidComponentsClosedTabRestoreTest {
    @Test
    fun captureUsesBrowserStoreMetadataAndExactUrlBoundState() {
        val state = TestEngineSessionState
        val tab = createTab(
            url = "https://live.example/page",
            private = true,
            id = "7",
            title = "Live title",
            desktopMode = true,
        )

        val capture = androidComponentsClosedTabCapture(
            tab = tab,
            boundState = AndroidComponentsBoundEngineSessionState(
                state = state,
                stateUrl = "https://live.example/page",
            ),
        )

        assertEquals("https://live.example/page", capture.url)
        assertEquals("Live title", capture.title)
        assertEquals(true, capture.isPrivate)
        assertEquals(true, capture.desktop)
        assertSame(state, capture.engineSessionState)
        assertEquals("https://live.example/page", capture.engineSessionStateUrl)
    }

    @Test
    fun captureDropsStateBoundToAnotherUrl() {
        val tab = createTab(
            url = "https://live.example/current",
            private = false,
            id = "8",
            title = "Current",
        )

        val capture = androidComponentsClosedTabCapture(
            tab = tab,
            boundState = AndroidComponentsBoundEngineSessionState(
                state = TestEngineSessionState,
                stateUrl = "https://live.example/stale",
            ),
        )

        assertNull(capture.engineSessionState)
        assertNull(capture.engineSessionStateUrl)
    }

    @Test
    fun restorePlanKeepsAndroidComponentsOwnershipWithoutRawSession() {
        val state = TestEngineSessionState
        val snapshot = snapshot(
            sessionOwner = PersistedSessionOwner.AndroidComponents,
            engineSessionState = state,
            engineSessionStateUrl = "https://restore.example/page",
        )

        val plan = requireNotNull(androidComponentsClosedTabRestorePlan(snapshot))

        assertEquals(42L, plan.structuralTab.id)
        assertEquals(RawSessionOwnership.Relinquished, plan.structuralTab.rawSessionOwnership)
        assertNull(plan.structuralTab.rawSessionOrNull)
        assertEquals("https://restore.example/page", plan.structuralTab.url)
        assertEquals("Restored title", plan.structuralTab.title)
        assertEquals(true, plan.structuralTab.desktop)
        assertEquals("42", plan.storeTab.id)
        assertEquals("https://restore.example/page", plan.storeTab.content.url)
        assertEquals("Restored title", plan.storeTab.content.title)
        assertEquals(true, plan.storeTab.content.desktopMode)
        assertSame(state, plan.storeTab.engineState.engineSessionState)
        assertSame(state, plan.engineSessionState)
    }

    @Test
    fun restorePlanDropsStaleStateButNeverFallsBackToRawOwnership() {
        val snapshot = snapshot(
            sessionOwner = PersistedSessionOwner.AndroidComponents,
            engineSessionState = TestEngineSessionState,
            engineSessionStateUrl = "https://restore.example/stale",
        )

        val plan = requireNotNull(androidComponentsClosedTabRestorePlan(snapshot))

        assertEquals(RawSessionOwnership.Relinquished, plan.structuralTab.rawSessionOwnership)
        assertNull(plan.structuralTab.rawSessionOrNull)
        assertNull(plan.storeTab.engineState.engineSessionState)
        assertNull(plan.engineSessionState)
    }

    @Test
    fun rawSnapshotDoesNotProduceAndroidComponentsRestorePlan() {
        assertNull(
            androidComponentsClosedTabRestorePlan(
                snapshot(sessionOwner = PersistedSessionOwner.Raw),
            ),
        )
    }

    private fun snapshot(
        sessionOwner: PersistedSessionOwner,
        engineSessionState: EngineSessionState? = null,
        engineSessionStateUrl: String? = null,
    ) = ClosedTabSnapshot(
        id = 42L,
        index = 1,
        wasCurrent = true,
        isPrivate = false,
        url = "https://restore.example/page",
        title = "Restored title",
        desktop = true,
        lastAccess = 1234L,
        latestSessionState = null,
        latestSessionStateUrl = null,
        persistedSessionState = null,
        persistedSessionStateUrl = null,
        sessionOwner = sessionOwner,
        engineSessionState = engineSessionState,
        engineSessionStateUrl = engineSessionStateUrl,
    )

    private object TestEngineSessionState : EngineSessionState {
        override fun writeTo(writer: JsonWriter) = Unit
    }
}
