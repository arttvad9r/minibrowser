package com.artt.minibrowser.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AndroidComponentsWebDataClearTest {
    @Test
    fun removalPlanIncludesLinkedStoreSessionsAndRelinquishedStructuralTabs() {
        val ids = androidComponentsWebDataClearStoreTabIds(
            rawOwnedTabIds = setOf("1"),
            relinquishedTabIds = linkedSetOf("2", "3"),
            openRelinquishedTabIds = setOf("2"),
            linkedStoreTabIds = linkedSetOf("2", "orphan"),
        )

        assertEquals(listOf("2", "orphan", "3"), ids)
    }

    @Test
    fun linkedEngineSessionOnRawOwnedTabFailsBeforeMutation() {
        assertFailsWith<IllegalStateException> {
            androidComponentsWebDataClearStoreTabIds(
                rawOwnedTabIds = setOf("1"),
                relinquishedTabIds = emptySet(),
                openRelinquishedTabIds = emptySet(),
                linkedStoreTabIds = setOf("1"),
            )
        }
    }

    @Test
    fun openRelinquishedTabWithoutLinkedEngineSessionFailsClosed() {
        assertFailsWith<IllegalStateException> {
            androidComponentsWebDataClearStoreTabIds(
                rawOwnedTabIds = emptySet(),
                relinquishedTabIds = setOf("2"),
                openRelinquishedTabIds = setOf("2"),
                linkedStoreTabIds = emptySet(),
            )
        }
    }

    @Test
    fun alreadyClosedRelinquishedTabCanBeRemovedWithoutLinkedSession() {
        val ids = androidComponentsWebDataClearStoreTabIds(
            rawOwnedTabIds = emptySet(),
            relinquishedTabIds = setOf("2"),
            openRelinquishedTabIds = emptySet(),
            linkedStoreTabIds = emptySet(),
        )

        assertEquals(listOf("2"), ids)
    }
}
