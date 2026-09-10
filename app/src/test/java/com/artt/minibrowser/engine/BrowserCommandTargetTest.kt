package com.artt.minibrowser.engine

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BrowserCommandTargetTest {
    @Test
    fun ownedTabUsesExactRawSession() {
        val rawSession = Any()

        val target = resolveBrowserCommandTarget(
            ownership = RawSessionOwnership.Owned,
            rawSession = rawSession,
            linkedEngineSession = null as Any?,
        )

        assertTrue(target is BrowserCommandTarget.Raw<*>)
        val rawTarget = target as BrowserCommandTarget.Raw<*>
        assertSame(rawSession, rawTarget.session)
    }

    @Test
    fun relinquishedTabUsesExactLinkedEngineSession() {
        val rawSession = Any()
        val linkedEngineSession = Any()

        val target = resolveBrowserCommandTarget(
            ownership = RawSessionOwnership.Relinquished,
            rawSession = rawSession,
            linkedEngineSession = linkedEngineSession,
        )

        assertTrue(target is BrowserCommandTarget.Linked<*>)
        val linkedTarget = target as BrowserCommandTarget.Linked<*>
        assertSame(linkedEngineSession, linkedTarget.session)
    }

    @Test
    fun relinquishedTabNeverFallsBackToRawSessionWhileLinkIsMissing() {
        val target = resolveBrowserCommandTarget(
            ownership = RawSessionOwnership.Relinquished,
            rawSession = Any(),
            linkedEngineSession = null as Any?,
        )

        assertNull(target)
    }

    @Test
    fun ownedTabRejectsSimultaneousLinkedCommandTarget() {
        assertFailsWith<IllegalStateException> {
            resolveBrowserCommandTarget(
                ownership = RawSessionOwnership.Owned,
                rawSession = Any(),
                linkedEngineSession = Any(),
            )
        }
    }
}
