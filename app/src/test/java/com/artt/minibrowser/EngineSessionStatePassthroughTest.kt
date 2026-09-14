package com.artt.minibrowser

import com.artt.minibrowser.data.EngineSessionStateEnvelope
import com.artt.minibrowser.data.PersistedSessionOwner
import com.artt.minibrowser.engine.PersistenceSnapshot
import com.artt.minibrowser.engine.PersistenceTabSnapshot
import com.artt.minibrowser.engine.serializePersistenceSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EngineSessionStatePassthroughTest {
    private val envelope = EngineSessionStateEnvelope(
        engine = "gecko",
        stateJson = "{\"value\":\"opaque\"}",
    )

    @Test
    fun boundEngineStateSurvivesRawOwnedPersistence() {
        val persisted = serializePersistenceSnapshot(snapshot(engineStateUrl = "https://example.com/page"))
            .tabs.single()

        assertEquals(PersistedSessionOwner.Raw, persisted.sessionOwner)
        assertEquals(envelope, persisted.engineSessionState)
        assertEquals("https://example.com/page", persisted.engineSessionStateUrl)
    }

    @Test
    fun staleEngineStateIsDroppedInsteadOfReboundToCurrentUrl() {
        val persisted = serializePersistenceSnapshot(snapshot(engineStateUrl = "https://example.com/old"))
            .tabs.single()

        assertNull(persisted.engineSessionState)
        assertNull(persisted.engineSessionStateUrl)
    }

    @Test
    fun unboundEngineStateIsDropped() {
        val persisted = serializePersistenceSnapshot(snapshot(engineStateUrl = null))
            .tabs.single()

        assertNull(persisted.engineSessionState)
        assertNull(persisted.engineSessionStateUrl)
    }

    private fun snapshot(engineStateUrl: String?): PersistenceSnapshot = PersistenceSnapshot(
        selectedId = 1L,
        tabs = listOf(
            PersistenceTabSnapshot(
                id = 1L,
                url = "https://example.com/page",
                title = "Example",
                desktop = false,
                lastAccess = 1L,
                latestSessionState = null,
                latestSessionStateUrl = null,
                serializedSessionState = null,
                serializedSessionStateUrl = null,
                isPrivate = false,
                serializedEngineSessionState = envelope,
                serializedEngineSessionStateUrl = engineStateUrl,
            ),
        ),
    )
}
