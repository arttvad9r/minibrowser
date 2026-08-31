package com.artt.minibrowser

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artt.minibrowser.ui.TabPreviewStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoView

@RunWith(AndroidJUnit4::class)
class TabPreviewStoreHostTest {
    @Test
    fun newGeckoViewHostReleasesRemovedTabIds() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext

        instrumentation.runOnMainSync {
            val previewStore = TabPreviewStore()
            val firstHost = GeckoView(context)
            previewStore.attach(firstHost, tabId = null, url = "", isPrivate = false)
            previewStore.remove(42L)
            assertTrue(removedTabIds(previewStore).contains(42L))

            // A late update from the same AndroidView host must stay blocked.
            previewStore.attach(firstHost, tabId = 42L, url = "https://old.example", isPrivate = false)
            assertTrue(removedTabIds(previewStore).contains(42L))

            // Activity/AndroidView recreation creates a new GeckoView. Old capture callbacks are
            // invalidated by the host generation change, so a legitimately reused tab id is safe.
            val secondHost = GeckoView(context)
            previewStore.attach(secondHost, tabId = 42L, url = "https://new.example", isPrivate = false)
            assertFalse(removedTabIds(previewStore).contains(42L))
        }
    }

    @Test
    fun storesDoNotShareRemovedTabIds() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        instrumentation.runOnMainSync {
            val firstStore = TabPreviewStore()
            val secondStore = TabPreviewStore()

            firstStore.remove(7L)

            assertTrue(removedTabIds(firstStore).contains(7L))
            assertFalse(removedTabIds(secondStore).contains(7L))
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun removedTabIds(previewStore: TabPreviewStore): Set<Long> {
        val field = TabPreviewStore::class.java.getDeclaredField("removedTabs")
        field.isAccessible = true
        return field.get(previewStore) as Set<Long>
    }
}
