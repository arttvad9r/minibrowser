package com.artt.minibrowser.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AndroidComponentsFreshTabTest {
    @Test
    fun freshTabSharesStructuralIdWithoutAllocatingRawOrEngineSession() {
        val plan = androidComponentsFreshTabPlan(
            id = 41L,
            url = "https://example.com/fresh",
            isPrivate = false,
        )

        assertEquals(41L, plan.structuralTab.id)
        assertEquals("https://example.com/fresh", plan.structuralTab.url)
        assertEquals(RawSessionOwnership.Relinquished, plan.structuralTab.rawSessionOwnership)
        assertNull(plan.structuralTab.rawSessionOrNull)

        assertEquals("41", plan.storeTab.id)
        assertEquals("https://example.com/fresh", plan.storeTab.content.url)
        assertFalse(plan.storeTab.content.private)
        assertNull(plan.storeTab.engineState.engineSession)
    }

    @Test
    fun freshPrivateBlankTabPreservesPrivateIdentityAndBlankUrl() {
        val plan = androidComponentsFreshTabPlan(
            id = 42L,
            url = null,
            isPrivate = true,
        )

        assertEquals("", plan.structuralTab.url)
        assertEquals(true, plan.structuralTab.isPrivate)
        assertEquals("", plan.storeTab.content.url)
        assertEquals(true, plan.storeTab.content.private)
        assertNull(plan.structuralTab.rawSessionOrNull)
        assertNull(plan.storeTab.engineState.engineSession)
    }
}
