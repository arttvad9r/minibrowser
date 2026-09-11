package com.artt.minibrowser.engine

import android.app.Application
import android.util.JsonWriter
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artt.minibrowser.data.EngineSessionStateEnvelope
import com.artt.minibrowser.data.PersistedSessionOwner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.createTab
import mozilla.components.concept.engine.EngineSessionState
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class RelinquishedPersistenceSnapshotTest {
    @Test
    fun relinquishedSnapshotUsesBrowserStoreAndExactBoundEngineState() {
        val engineState = TestEngineSessionState("opaque")
        val persistenceState = AndroidComponentsSessionStatePersistenceState().apply {
            bind("7", "https://live.example/page", engineState)
        }
        val snapshot = assertNotNull(
            persistenceTabSnapshotAfterRelinquish(
                id = 7L,
                lastAccess = 42L,
                browserState = browserState(
                    url = "https://live.example/page",
                    title = "Live title",
                    desktop = true,
                ),
                persistenceState = persistenceState,
                engineName = "gecko",
            ),
        )

        assertEquals("https://live.example/page", snapshot.url)
        assertEquals("Live title", snapshot.title)
        assertEquals(true, snapshot.desktop)
        assertEquals(42L, snapshot.lastAccess)
        assertFalse(snapshot.isPrivate)
        assertEquals(PersistedSessionOwner.AndroidComponents, snapshot.sessionOwner)
        assertNull(snapshot.latestSessionState)
        assertNull(snapshot.latestSessionStateUrl)
        assertNull(snapshot.serializedSessionState)
        assertNull(snapshot.serializedSessionStateUrl)
        assertNull(snapshot.serializedEngineSessionState)
        assertNull(snapshot.serializedEngineSessionStateUrl)
        assertSame(engineState, snapshot.engineSessionState)
        assertEquals("https://live.example/page", snapshot.engineSessionStateUrl)

        val persisted = serializePersistenceSnapshot(
            PersistenceSnapshot(selectedId = 7L, tabs = listOf(snapshot)),
        ).tabs.single()

        assertEquals(PersistedSessionOwner.AndroidComponents, persisted.sessionOwner)
        assertNull(persisted.sessionState)
        assertNull(persisted.sessionStateUrl)
        assertEquals(
            EngineSessionStateEnvelope(
                engine = "gecko",
                stateJson = "{\"value\":\"opaque\"}",
            ),
            persisted.engineSessionState,
        )
        assertEquals("https://live.example/page", persisted.engineSessionStateUrl)
    }

    @Test
    fun mismatchedBoundStateFailsClosedWithoutLegacyStateFallback() {
        val persistenceState = AndroidComponentsSessionStatePersistenceState().apply {
            bind("7", "https://stale.example/old", TestEngineSessionState("stale"))
        }
        val snapshot = assertNotNull(
            persistenceTabSnapshotAfterRelinquish(
                id = 7L,
                lastAccess = 42L,
                browserState = browserState(url = "https://live.example/page"),
                persistenceState = persistenceState,
                engineName = "gecko",
            ),
        )

        assertEquals("https://live.example/page", snapshot.url)
        assertEquals(PersistedSessionOwner.AndroidComponents, snapshot.sessionOwner)
        assertNull(snapshot.latestSessionState)
        assertNull(snapshot.serializedSessionState)
        assertNull(snapshot.engineSessionState)
        assertNull(snapshot.serializedEngineSessionState)

        val persisted = serializePersistenceSnapshot(
            PersistenceSnapshot(selectedId = 7L, tabs = listOf(snapshot)),
        ).tabs.single()

        assertEquals(PersistedSessionOwner.AndroidComponents, persisted.sessionOwner)
        assertNull(persisted.sessionState)
        assertNull(persisted.sessionStateUrl)
        assertNull(persisted.engineSessionState)
        assertNull(persisted.engineSessionStateUrl)
    }

    @Test
    fun missingBrowserStoreTabDropsRelinquishedPersistenceEntry() {
        val persistenceState = AndroidComponentsSessionStatePersistenceState().apply {
            bind("7", "https://stale.example/old", TestEngineSessionState("stale"))
        }

        assertNull(
            persistenceTabSnapshotAfterRelinquish(
                id = 7L,
                lastAccess = 42L,
                browserState = BrowserState(),
                persistenceState = persistenceState,
                engineName = "gecko",
            ),
        )
    }

    private fun browserState(
        url: String,
        title: String = "Live",
        desktop: Boolean = false,
    ): BrowserState = BrowserState(
        tabs = listOf(
            createTab(
                url = url,
                private = false,
                id = "7",
                title = title,
                desktopMode = desktop,
            ),
        ),
        selectedTabId = "7",
    )

    private data class TestEngineSessionState(val value: String) : EngineSessionState {
        override fun writeTo(writer: JsonWriter) {
            writer.beginObject()
            writer.name("value").value(value)
            writer.endObject()
        }
    }
}
