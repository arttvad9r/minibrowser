package com.artt.minibrowser

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artt.minibrowser.data.PersistedBrowserState
import com.artt.minibrowser.data.PersistedSessionOwner
import com.artt.minibrowser.data.PersistedTab
import com.artt.minibrowser.data.TabStore
import com.artt.minibrowser.engine.BrowserApp
import com.artt.minibrowser.engine.RawSessionOwnership
import com.artt.minibrowser.engine.TabManager
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserProcessRestoreOwnershipSystemTest {
    @Test
    fun androidComponentsOwnedPersistedTabRestoresWithoutRawGeckoSession() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as BrowserApp
        val dir = File(app.cacheDir, "ac-process-restore-${System.nanoTime()}")
        val tabId = 7_001L
        val url = "https://restore.example/ac"
        TabStore.saveState(
            dir,
            PersistedBrowserState(
                selectedId = tabId,
                tabs = listOf(
                    PersistedTab(
                        id = tabId,
                        url = url,
                        title = "A-C restored",
                        desktop = true,
                        lastAccess = 1234L,
                        sessionOwner = PersistedSessionOwner.AndroidComponents,
                    ),
                ),
            ),
        )

        var manager: TabManager? = null
        try {
            instrumentation.runOnMainSync {
                manager = TabManager(app.runtime, dir, app)
            }
            instrumentation.runOnMainSync {
                val restoredManager = checkNotNull(manager)
                val restored = restoredManager.tabs.value.single()
                assertEquals(tabId, restored.id)
                assertEquals(url, restored.url)
                assertEquals("A-C restored", restored.title)
                assertEquals(true, restored.desktop)
                assertEquals(1234L, restored.lastAccess)
                assertEquals(tabId, restoredManager.currentId.value)
                assertEquals(RawSessionOwnership.Relinquished, restored.rawSessionOwnership)
                assertFalse(restored.hasRawSessionAuthority)
                assertNull(restored.rawSessionOrNull)
            }
        } finally {
            manager?.let { restoredManager ->
                instrumentation.runOnMainSync { restoredManager.close() }
                // Drain TabStore's ordered IO queue before removing the isolated test directory.
                TabStore.loadState(dir)
            }
            dir.deleteRecursively()
        }
    }
}
