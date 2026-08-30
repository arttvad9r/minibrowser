package com.artt.minibrowser

import com.artt.minibrowser.data.PersistedBrowserState
import com.artt.minibrowser.data.PersistedTab
import com.artt.minibrowser.data.TabStore
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TabStoreTest {
    @Test fun roundTrip() {
        val dir = File(System.getProperty("java.io.tmpdir"), "tabs-${System.nanoTime()}").apply { mkdirs() }
        TabStore.save(dir, listOf("https://a.example/", "https://b.example/"))
        assertEquals(listOf("https://a.example/", "https://b.example/"), TabStore.load(dir))
        assertTrue(File(dir, "open_tabs.json").exists())
        dir.deleteRecursively()
    }

    @Test fun emptyWhenMissing() {
        val dir = File(System.getProperty("java.io.tmpdir"), "tabs-none-${System.nanoTime()}")
        assertEquals(emptyList(), TabStore.load(dir))
    }

    @Test fun privateTabsNotSaved() {
        val dir = File(System.getProperty("java.io.tmpdir"), "tabs-priv-${System.nanoTime()}").apply { mkdirs() }
        TabStore.save(dir, emptyList())
        assertEquals(emptyList(), TabStore.load(dir))
        dir.deleteRecursively()
    }

    @Test fun persistsMetadataAndSelectedTabAtomically() {
        val dir = File(System.getProperty("java.io.tmpdir"), "tabs-state-${System.nanoTime()}")
        TabStore.saveState(dir, PersistedBrowserState(7, listOf(PersistedTab(7, "https://a.example", "A", desktop = true))))
        val state = TabStore.loadState(dir)
        assertEquals(7, state.selectedId)
        assertEquals(true, state.tabs.single().desktop)
        assertTrue(!File(dir, "open_tabs.json.tmp").exists())
        dir.deleteRecursively()
    }

    @Test fun persistenceStripsCredentialsAndDropsBoundGeckoState() {
        val dir = File(System.getProperty("java.io.tmpdir"), "tabs-credentials-${System.nanoTime()}")
        val credentialUrl = "https://user:secret@example.com/private?q=1#x"
        TabStore.saveState(
            dir,
            PersistedBrowserState(
                selectedId = 1,
                tabs = listOf(
                    PersistedTab(
                        id = 1,
                        url = credentialUrl,
                        title = credentialUrl,
                        sessionState = "opaque-session-secret",
                        sessionStateUrl = credentialUrl,
                    ),
                ),
            ),
        )

        val persisted = File(dir, "open_tabs.json").readText()
        assertFalse(persisted.contains("user:secret"))
        assertFalse(persisted.contains("opaque-session-secret"))

        val tab = TabStore.loadState(dir).tabs.single()
        assertEquals("https://example.com/private?q=1#x", tab.url)
        assertEquals("https://example.com/private?q=1#x", tab.title)
        assertNull(tab.sessionState)
        assertNull(tab.sessionStateUrl)
        dir.deleteRecursively()
    }

    @Test fun legacyCredentialStateIsSanitizedAndRewritten() {
        val dir = File(System.getProperty("java.io.tmpdir"), "tabs-credential-legacy-${System.nanoTime()}").apply { mkdirs() }
        val target = File(dir, "open_tabs.json")
        target.writeText(
            """{"selectedId":1,"tabs":[{"id":1,"url":"https://user:secret@example.com/a","title":"Page","sessionState":"opaque-secret","sessionStateUrl":"https://user:secret@example.com/a"}]}""",
        )

        val tab = TabStore.loadState(dir).tabs.single()

        assertEquals("https://example.com/a", tab.url)
        assertEquals("Page", tab.title)
        assertNull(tab.sessionState)
        assertNull(tab.sessionStateUrl)
        val rewritten = target.readText()
        assertFalse(rewritten.contains("user:secret"))
        assertFalse(rewritten.contains("opaque-secret"))
        dir.deleteRecursively()
    }

    @Test fun replacingExistingStateLeavesOnlyCompleteNewJson() {
        val dir = File(System.getProperty("java.io.tmpdir"), "tabs-replace-${System.nanoTime()}")
        val first = PersistedBrowserState(1, listOf(PersistedTab(1, "https://one.example", "One")))
        val second = PersistedBrowserState(2, listOf(PersistedTab(2, "https://two.example", "Two", desktop = true)))

        TabStore.saveState(dir, first)
        TabStore.saveState(dir, second)

        assertEquals(second, TabStore.loadState(dir))
        assertTrue(!File(dir, "open_tabs.json.tmp").exists())
        dir.deleteRecursively()
    }

    @Test fun concurrentLifecycleFlushesKeepStoreReadable() {
        val dir = File(System.getProperty("java.io.tmpdir"), "tabs-concurrent-${System.nanoTime()}")
        val states = (1L..8L).map { id ->
            PersistedBrowserState(id, listOf(PersistedTab(id, "https://$id.example", "Tab $id")))
        }
        val threads = states.map { state ->
            Thread {
                repeat(12) { TabStore.saveState(dir, state) }
            }
        }

        threads.forEach(Thread::start)
        threads.forEach(Thread::join)

        assertTrue(TabStore.loadState(dir) in states)
        assertTrue(!File(dir, "open_tabs.json.tmp").exists())
        dir.deleteRecursively()
    }

    @Test fun versionedWriteRejectsSnapshotOlderThanClearBarrier() {
        val dir = File(System.getProperty("java.io.tmpdir"), "tabs-versioned-${System.nanoTime()}")
        val old = PersistedBrowserState(1, listOf(PersistedTab(1, "https://private-before-clear.example", "Old")))
        val cleared = PersistedBrowserState()

        assertTrue(TabStore.saveStateVersioned(dir, old, revision = 4))
        assertTrue(TabStore.saveStateVersioned(dir, cleared, revision = 5))
        assertFalse(TabStore.saveStateVersioned(dir, old, revision = 4))
        assertEquals(cleared, TabStore.loadState(dir))
        dir.deleteRecursively()
    }

    @Test fun corruptedStateFallsBackToEmptyAndKeepsOneBoundedBackup() {
        val dir = File(System.getProperty("java.io.tmpdir"), "tabs-corrupt-${System.nanoTime()}").apply { mkdirs() }
        val target = File(dir, "open_tabs.json")
        val backup = File(dir, "open_tabs.json.corrupt")

        target.writeText("first-corrupt")
        assertEquals(emptyList(), TabStore.loadState(dir).tabs)
        assertEquals("first-corrupt", backup.readText())
        assertFalse(target.exists())

        target.writeText("second-corrupt")
        assertEquals(emptyList(), TabStore.loadState(dir).tabs)
        assertEquals("second-corrupt", backup.readText())
        assertEquals(1, dir.listFiles()?.count { it.name.startsWith("open_tabs.json.corrupt") })

        TabStore.saveState(
            dir,
            PersistedBrowserState(1, listOf(PersistedTab(1, "https://recovered.example", "Recovered"))),
        )
        assertFalse(backup.exists())
        dir.deleteRecursively()
    }

    @Test fun filtersUnsafePersistedUrlsAndRepairsSelection() {
        val dir = File(System.getProperty("java.io.tmpdir"), "tabs-unsafe-${System.nanoTime()}")
        TabStore.saveState(
            dir,
            PersistedBrowserState(
                selectedId = 2,
                tabs = listOf(
                    PersistedTab(1, "https://safe.example", "Safe"),
                    PersistedTab(2, "file:///data/local/private.html", "File"),
                    PersistedTab(3, "javascript:alert(1)", "Script"),
                    PersistedTab(4, "https://", "Hostless"),
                    PersistedTab(5, "ABOUT:BLANK", "Blank"),
                ),
            ),
        )

        val state = TabStore.loadState(dir)

        assertNull(state.selectedId)
        assertEquals(listOf(1L, 5L), state.tabs.map { it.id })
        assertEquals(listOf("https://safe.example", "about:blank"), state.tabs.map { it.url })
        val rewritten = File(dir, "open_tabs.json").readText()
        assertFalse(rewritten.contains("file:///"))
        assertFalse(rewritten.contains("javascript:"))
        assertFalse(rewritten.contains("ABOUT:BLANK"))
        assertTrue(rewritten.contains("about:blank"))
        dir.deleteRecursively()
    }

    @Test fun filtersNonPositiveAndDuplicatePersistedIds() {
        val dir = File(System.getProperty("java.io.tmpdir"), "tabs-ids-${System.nanoTime()}")
        TabStore.saveState(
            dir,
            PersistedBrowserState(
                selectedId = 2,
                tabs = listOf(
                    PersistedTab(0, "https://zero.example", "Zero"),
                    PersistedTab(2, "https://first.example", "First"),
                    PersistedTab(2, "https://duplicate.example", "Duplicate"),
                    PersistedTab(-3, "https://negative.example", "Negative"),
                    PersistedTab(4, "https://four.example", "Four"),
                ),
            ),
        )

        val state = TabStore.loadState(dir)

        assertEquals(2L, state.selectedId)
        assertEquals(listOf(2L, 4L), state.tabs.map { it.id })
        assertEquals(listOf("First", "Four"), state.tabs.map { it.title })
        val rewritten = File(dir, "open_tabs.json").readText()
        assertFalse(rewritten.contains("Zero"))
        assertFalse(rewritten.contains("Duplicate"))
        assertFalse(rewritten.contains("Negative"))
        dir.deleteRecursively()
    }

    @Test fun readsPreviousUrlOnlyFormatAndMigratesIt() {
        val dir = File(System.getProperty("java.io.tmpdir"), "tabs-legacy-${System.nanoTime()}").apply { mkdirs() }
        val target = File(dir, "open_tabs.json")
        target.writeText("[\"https://legacy.example\"]")

        assertEquals(listOf("https://legacy.example"), TabStore.load(dir))
        assertTrue(target.readText().trimStart().startsWith("{"))
        assertEquals(listOf("https://legacy.example"), TabStore.load(dir))
        dir.deleteRecursively()
    }
}
