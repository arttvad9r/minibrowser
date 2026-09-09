package com.artt.minibrowser

import com.artt.minibrowser.data.EngineSessionStateEnvelope
import com.artt.minibrowser.data.PersistedBrowserState
import com.artt.minibrowser.data.PersistedTab
import com.artt.minibrowser.data.TabStore
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class EngineSessionStateTabStoreTest {
    @Test
    fun engineSessionStateRoundTripsBesideLegacyState() {
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

        assertEquals(state, TabStore.loadState(dir))
        dir.deleteRecursively()
    }

    @Test
    fun previousTabJsonLoadsWithNoEngineState() {
        val dir = tempDir("engine-state-legacy")
        val target = File(dir, "open_tabs.json")
        target.writeText(
            """{"selectedId":1,"tabs":[{"id":1,"url":"https://example.com","sessionState":"legacy","sessionStateUrl":"https://example.com"}]}""",
        )

        val tab = TabStore.loadState(dir).tabs.single()

        assertEquals("legacy", tab.sessionState)
        assertNull(tab.engineSessionState)
        assertNull(tab.engineSessionStateUrl)
        dir.deleteRecursively()
    }

    @Test
    fun credentialSanitizationDropsEngineAndLegacySnapshots() {
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

        val tab = TabStore.loadState(dir).tabs.single()
        assertEquals("https://example.com/private", tab.url)
        assertNull(tab.sessionState)
        assertNull(tab.sessionStateUrl)
        assertNull(tab.engineSessionState)
        assertNull(tab.engineSessionStateUrl)
        dir.deleteRecursively()
    }

    private fun tempDir(name: String): File =
        File(System.getProperty("java.io.tmpdir"), "$name-${System.nanoTime()}").apply { mkdirs() }
}
