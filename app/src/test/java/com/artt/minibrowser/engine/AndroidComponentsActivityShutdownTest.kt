package com.artt.minibrowser.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AndroidComponentsActivityShutdownTest {
    @Test
    fun removalPlanClearsEveryProcessScopedBrowserStoreRecord() {
        val ids = androidComponentsFinalActivityShutdownStoreTabIds(
            rawOwnedTabIds = setOf("1"),
            relinquishedTabIds = linkedSetOf("2", "3"),
            storeTabIds = linkedSetOf("1", "2", "3", "orphan"),
            linkedStoreTabIds = linkedSetOf("2", "orphan"),
        )

        assertEquals(listOf("1", "2", "3", "orphan"), ids)
    }

    @Test
    fun linkedEngineSessionOnRawOwnedTabFailsBeforeMutation() {
        assertFailsWith<IllegalStateException> {
            androidComponentsFinalActivityShutdownStoreTabIds(
                rawOwnedTabIds = setOf("1"),
                relinquishedTabIds = emptySet(),
                storeTabIds = setOf("1"),
                linkedStoreTabIds = setOf("1"),
            )
        }
    }

    @Test
    fun relinquishedTabWithoutBrowserStoreRecordFailsBeforeMutation() {
        assertFailsWith<IllegalStateException> {
            androidComponentsFinalActivityShutdownStoreTabIds(
                rawOwnedTabIds = emptySet(),
                relinquishedTabIds = setOf("2"),
                storeTabIds = emptySet(),
                linkedStoreTabIds = emptySet(),
            )
        }
    }

    @Test
    fun pendingSessionlessFreshTabIsQueuedForRemovalAfterItsPendingAdd() {
        val ids = androidComponentsFinalActivityShutdownStoreTabIds(
            rawOwnedTabIds = emptySet(),
            relinquishedTabIds = linkedSetOf("2", "3"),
            storeTabIds = linkedSetOf("2"),
            linkedStoreTabIds = emptySet(),
            sessionlessRelinquishedTabIds = setOf("3"),
        )

        assertEquals(listOf("2", "3"), ids)
    }

    @Test
    fun suspendedRelinquishedTabDoesNotRequireCurrentEngineSessionLink() {
        val ids = androidComponentsFinalActivityShutdownStoreTabIds(
            rawOwnedTabIds = emptySet(),
            relinquishedTabIds = setOf("2"),
            storeTabIds = setOf("2"),
            linkedStoreTabIds = emptySet(),
        )

        assertEquals(listOf("2"), ids)
    }
}
