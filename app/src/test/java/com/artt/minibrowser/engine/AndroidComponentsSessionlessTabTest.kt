package com.artt.minibrowser.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AndroidComponentsSessionlessTabTest {
    @Test
    fun androidComponentsOwnedTabHasNoRawSession() {
        val tab = Tab.androidComponentsOwned(id = 7L)

        assertEquals(7L, tab.id)
        assertFalse(tab.isPrivate)
        assertEquals(RawSessionOwnership.Relinquished, tab.rawSessionOwnership)
        assertFalse(tab.hasRawSessionAuthority)
        assertNull(tab.rawSessionOrNull)
        assertFailsWith<IllegalStateException> { tab.session }
    }

    @Test
    fun sessionlessFactoryPreservesPrivateIdentityWithoutMaterializingRawSession() {
        val tab = Tab.androidComponentsOwned(id = 9L, isPrivate = true)

        assertEquals(9L, tab.id)
        assertEquals(true, tab.isPrivate)
        assertEquals(RawSessionOwnership.Relinquished, tab.rawSessionOwnership)
        assertNull(tab.rawSessionOrNull)
    }
}
