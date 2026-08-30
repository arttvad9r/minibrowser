package com.artt.minibrowser

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artt.minibrowser.ui.TabPreviewStore
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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
            val firstHost = GeckoView(context)
            TabPreviewStore.attach(firstHost, tabId = null, url = "", isPrivate = false)
            TabPreviewStore.remove(42L)
            assertTrue(removedTabIds().contains(42L))

            // A late update from the same AndroidView host must stay blocked.
            TabPreviewStore.attach(firstHost, tabId = 42L, url = "https://old.example", isPrivate = false)
            assertTrue(removedTabIds().contains(42L))

            // Activity/AndroidView recreation creates a new GeckoView. Old capture callbacks are
            // invalidated by the host generation change, so a legitimately reused tab id is safe.
            val secondHost = GeckoView(context)
            TabPreviewStore.attach(secondHost, tabId = 42L, url = "https://new.example", isPrivate = false)
            assertFalse(removedTabIds().contains(42L))

            // Leave the process-global store neutral for other instrumentation tests.
            val cleanupHost = GeckoView(context)
            TabPreviewStore.attach(cleanupHost, tabId = null, url = "", isPrivate = false)
            TabPreviewStore.clear()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun removedTabIds(): Set<Long> {
        val field = TabPreviewStore::class.java.getDeclaredField("removedTabs")
        field.isAccessible = true
        return field.get(TabPreviewStore) as Set<Long>
    }
}
