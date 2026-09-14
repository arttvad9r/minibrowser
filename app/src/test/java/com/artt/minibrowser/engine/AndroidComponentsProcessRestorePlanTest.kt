package com.artt.minibrowser.engine

import android.util.JsonWriter
import com.artt.minibrowser.data.EngineSessionStateEnvelope
import com.artt.minibrowser.data.PersistedBrowserState
import com.artt.minibrowser.data.PersistedSessionOwner
import com.artt.minibrowser.data.PersistedTab
import mozilla.components.concept.engine.EngineSessionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class AndroidComponentsProcessRestorePlanTest {
    @Test
    fun mixedOwnershipRestoresOnlyExplicitAndroidComponentsTabsInPersistedOrder() {
        val restoredState = TestEngineSessionState
        val decodedIds = mutableListOf<Long>()
        val rawShadowEnvelope = EngineSessionStateEnvelope(
            engine = "gecko",
            stateJson = "{\"shadow\":true}",
        )
        val plan = androidComponentsProcessRestorePlan(
            state = PersistedBrowserState(
                selectedId = 4L,
                tabs = listOf(
                    PersistedTab(
                        id = 1L,
                        url = "https://raw.example/one",
                        title = "Raw shadow",
                        sessionOwner = PersistedSessionOwner.Raw,
                        engineSessionState = rawShadowEnvelope,
                        engineSessionStateUrl = "https://raw.example/one",
                    ),
                    PersistedTab(
                        id = 4L,
                        url = "https://ac.example/four",
                        title = "A-C four",
                        desktop = true,
                        sessionOwner = PersistedSessionOwner.AndroidComponents,
                    ),
                    PersistedTab(
                        id = 3L,
                        url = "https://raw.example/three",
                        title = "Raw three",
                        sessionOwner = PersistedSessionOwner.Raw,
                    ),
                    PersistedTab(
                        id = 2L,
                        url = "https://ac.example/two",
                        title = "A-C two",
                        sessionOwner = PersistedSessionOwner.AndroidComponents,
                    ),
                ),
            ),
        ) { persisted ->
            decodedIds += persisted.id
            restoredState.takeIf { persisted.id == 4L }
        }

        assertEquals(listOf(4L, 2L), decodedIds)
        assertEquals(listOf("4", "2"), plan.tabs.map { it.id })
        assertEquals(
            listOf("https://ac.example/four", "https://ac.example/two"),
            plan.tabs.map { it.content.url },
        )
        assertEquals(listOf("A-C four", "A-C two"), plan.tabs.map { it.content.title })
        assertEquals(listOf(true, false), plan.tabs.map { it.content.desktopMode })
        assertSame(restoredState, plan.tabs.first().engineState.engineSessionState)
        assertNull(plan.tabs.last().engineState.engineSessionState)
        assertEquals("4", plan.selectedTabId)
    }

    @Test
    fun selectedIdIsRetainedOnlyWhenSelectedTabIsAndroidComponentsOwned() {
        val tabs = listOf(
            PersistedTab(
                id = 1L,
                url = "https://raw.example/",
                sessionOwner = PersistedSessionOwner.Raw,
            ),
            PersistedTab(
                id = 2L,
                url = "https://ac.example/",
                sessionOwner = PersistedSessionOwner.AndroidComponents,
            ),
        )

        assertNull(
            androidComponentsProcessRestorePlan(
                state = PersistedBrowserState(selectedId = 1L, tabs = tabs),
                decodeEngineSessionState = { null },
            ).selectedTabId,
        )
        assertEquals(
            "2",
            androidComponentsProcessRestorePlan(
                state = PersistedBrowserState(selectedId = 2L, tabs = tabs),
                decodeEngineSessionState = { null },
            ).selectedTabId,
        )
    }

    @Test
    fun decodeFailureKeepsAndroidComponentsTabInPlanWithoutRawFallback() {
        val url = "https://ac.example/restored"
        val plan = androidComponentsProcessRestorePlan(
            state = PersistedBrowserState(
                selectedId = 7L,
                tabs = listOf(
                    PersistedTab(
                        id = 7L,
                        url = url,
                        title = "Restored",
                        desktop = true,
                        sessionState = "legacy-raw-state-must-not-decide-ownership",
                        sessionStateUrl = url,
                        sessionOwner = PersistedSessionOwner.AndroidComponents,
                        engineSessionState = EngineSessionStateEnvelope(
                            engine = "gecko",
                            stateJson = "{\"opaque\":true}",
                        ),
                        engineSessionStateUrl = url,
                    ),
                ),
            ),
            decodeEngineSessionState = { null },
        )

        val restored = plan.tabs.single()
        assertEquals("7", restored.id)
        assertEquals(url, restored.content.url)
        assertEquals("Restored", restored.content.title)
        assertEquals(true, restored.content.desktopMode)
        assertNull(restored.engineState.engineSessionState)
        assertEquals("7", plan.selectedTabId)
    }

    private object TestEngineSessionState : EngineSessionState {
        override fun writeTo(writer: JsonWriter) = Unit
    }
}
