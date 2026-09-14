package com.artt.minibrowser

import com.artt.minibrowser.data.EngineSessionStateEnvelope
import com.artt.minibrowser.data.PersistedBrowserState
import com.artt.minibrowser.data.PersistedSessionOwner
import com.artt.minibrowser.data.PersistedTab
import com.artt.minibrowser.data.TabStore
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EngineSessionStateTabStoreTest {
    @Test
    fun engineSessionStateRoundTripsBesideLegacyStateWithoutChangingRawOwnership() {
        val dir = tempDir("engine-state-roundtrip")
        val state = PersistedBrowserState(
            selectedId = 1,
            tabs = listOf(
                PersistedTab(
                    id = 1,
                    url = "https://example.com/page",
                    sessionState = "legacy-gecko-state",
                    sessionStateUrl = "https://example.com/page",
                    engineSessionState = EngineSessionStateEnvelope(
                        engine = "gecko",
                        stateJson = "{\"history\":[]}",
                    ),
                    engineSessionStateUrl = "https://example.com/page",
                ),
            ),
        )

        TabStore.saveState(dir, state)

        val loaded = TabStore.loadState(dir)
        assertEquals(state, loaded)
        assertEquals(PersistedSessionOwner.Raw, loaded.tabs.single().sessionOwner)
        assertFalse(
            File(dir, "open_tabs.json").readText().contains("android_components"),
            "An A-C restore payload alone must never imply A-C lifetime ownership",
        )
        dir.deleteRecursively()
    }

    @Test
    fun androidComponentsOwnerRoundTripsWithoutRequiringRestorePayload() {
        val dir = tempDir("engine-owner-roundtrip")
        val state = PersistedBrowserState(
            selectedId = 7,
            tabs = listOf(
                PersistedTab(
                    id = 7,
                    url = "https://example.com/live",
                    sessionOwner = PersistedSessionOwner.AndroidComponents,
                ),
            ),
        )

        TabStore.saveState(dir, state)

        val persisted = File(dir, "open_tabs.json").readText()
        assertTrue(persisted.contains("\"session_owner\":\"android_components\""))
        assertEquals(state, TabStore.loadState(dir))
        dir.deleteRecursively()
    }

    @Test
    fun previousTabJsonLoadsAsRawOwnedWithNoEngineState() {
        val dir = tempDir("engine-state-legacy")
        val target = File(dir, "open_tabs.json")
        target.writeText(
            """{"selectedId":1,"tabs":[{"id":1,"url":"https://example.com","sessionState":"legacy","sessionStateUrl":"https://example.com"}]}""",
        )

        val tab = TabStore.loadState(dir).tabs.single()

        assertEquals("legacy", tab.sessionState)
        assertEquals(PersistedSessionOwner.Raw, tab.sessionOwner)
        assertNull(tab.engineSessionState)
        assertNull(tab.engineSessionStateUrl)
        dir.deleteRecursively()
    }

    @Test
    fun credentialSanitizationDropsEngineLegacySnapshotsAndOwnershipClaim() {
        val dir = tempDir("engine-state-credentials")
        val credentialUrl = "https://user:secret@example.com/private"
        TabStore.saveState(
            dir,
            PersistedBrowserState(
                selectedId = 1,
                tabs = listOf(
                    PersistedTab(
                        id = 1,
                        url = credentialUrl,
                        title = "Private",
                        sessionState = "legacy-opaque-secret",
                        sessionStateUrl = credentialUrl,
                        sessionOwner = PersistedSessionOwner.AndroidComponents,
                        engineSessionState = EngineSessionStateEnvelope(
                            engine = "gecko",
                            stateJson = "{\"opaque\":\"engine-secret\"}",
                        ),
                        engineSessionStateUrl = credentialUrl,
                    ),
                ),
            ),
        )

        val persisted = File(dir, "open_tabs.json").readText()
        assertFalse(persisted.contains("user:secret"))
        assertFalse(persisted.contains("legacy-opaque-secret"))
        assertFalse(persisted.contains("engine-secret"))
        assertFalse(persisted.contains("android_components"))

        val tab = TabStore.loadState(dir).tabs.single()
        assertEquals("https://example.com/private", tab.url)
        assertEquals(PersistedSessionOwner.Raw, tab.sessionOwner)
        assertNull(tab.sessionState)
        assertNull(tab.sessionStateUrl)
        assertNull(tab.engineSessionState)
        assertNull(tab.engineSessionStateUrl)
        dir.deleteRecursively()
    }

    private fun tempDir(name: String): File =
        File(System.getProperty("java.io.tmpdir"), "$name-${System.nanoTime()}").apply { mkdirs() }
}
