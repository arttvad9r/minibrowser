package com.artt.minibrowser.engine

import androidx.annotation.MainThread
import java.io.File

/**
 * Exact in-process session ownership transferred between Activity instances during a configuration change.
 *
 * This is deliberately not persisted: process death destroys both GeckoSession objects and the app-scoped
 * BrowserStore, after which normal versioned persistence is the restore source. The handoff contains no
 * Activity, View, Context, or Activity-scoped controller references.
 */
internal data class TabManagerRecreationHandoff(
    val tabs: List<Tab>,
    val selectedId: Long?,
    val sequence: Long,
)

/**
 * One-shot process-local bridge between the destroying and recreated MainActivity.
 *
 * The tab-store directory is the identity boundary so tests/secondary stores cannot consume each other's
 * sessions. A second unconsumed handoff for the same store is an invariant violation: silently replacing it
 * could orphan live GeckoSession ownership.
 */
internal object TabManagerRecreationHandoffRegistry {
    private val lock = Any()
    private val pendingByStore = mutableMapOf<String, TabManagerRecreationHandoff>()

    @MainThread
    fun publish(storeDir: File, handoff: TabManagerRecreationHandoff) {
        // GeckoEngineView installs a BasicSelectionActionDelegate backed by its Activity directly on
        // raw GeckoSession. It is view-owned rather than TabManager-owned, so remove it explicitly before
        // a configuration handoff can retain the destroyed Activity. The replacement EngineView will
        // install a fresh delegate when it renders the same raw session.
        handoff.tabs
            .asSequence()
            .filter { it.hasRawSessionAuthority }
            .map { it.session }
            .filter { it.selectionActionDelegate != null }
            .forEach { it.selectionActionDelegate = null }

        val key = storeDir.absolutePath
        synchronized(lock) {
            check(key !in pendingByStore) {
                "Unconsumed TabManager recreation handoff already exists for $key"
            }
            pendingByStore[key] = handoff
        }
    }

    fun hasPending(storeDir: File): Boolean = synchronized(lock) {
        storeDir.absolutePath in pendingByStore
    }

    fun consume(storeDir: File): TabManagerRecreationHandoff? = synchronized(lock) {
        pendingByStore.remove(storeDir.absolutePath)
    }

    internal fun clearForTest(storeDir: File) {
        synchronized(lock) {
            pendingByStore.remove(storeDir.absolutePath)
        }
    }
}

internal fun hasTabManagerRecreationHandoff(storeDir: File): Boolean =
    TabManagerRecreationHandoffRegistry.hasPending(storeDir)

internal fun takeTabManagerRecreationHandoff(storeDir: File): TabManagerRecreationHandoff? =
    TabManagerRecreationHandoffRegistry.consume(storeDir)
