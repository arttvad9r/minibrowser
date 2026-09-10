package com.artt.minibrowser.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AndroidComponentsWebDataClearTest {
    @Test
    fun ownershipValidationRequiresExactRelinquishedAndLinkedSets() {
        validateAndroidComponentsWebDataClearOwnership(
            relinquishedSessionIds = setOf("1", "2"),
            linkedSessionIds = setOf("2", "1"),
        )

        assertFailsWith<IllegalStateException> {
            validateAndroidComponentsWebDataClearOwnership(
                relinquishedSessionIds = setOf("1"),
                linkedSessionIds = emptySet(),
            )
        }
        assertFailsWith<IllegalStateException> {
            validateAndroidComponentsWebDataClearOwnership(
                relinquishedSessionIds = emptySet(),
                linkedSessionIds = setOf("1"),
            )
        }
    }

    @Test
    fun unlinkAndCloseCompleteInOrderBeforeStructuralRemoval() {
        val events = mutableListOf<String>()
        val targets = listOf(
            AndroidComponentsWebDataClearTarget("1") { events += "close:1" },
            AndroidComponentsWebDataClearTarget("2") { events += "close:2" },
        )

        runAndroidComponentsWebDataClear(
            targets = targets,
            unlink = { sessionId -> events += "unlink:$sessionId" },
            removeAllTabs = { events += "remove-all" },
        )

        assertEquals(
            listOf(
                "unlink:1",
                "close:1",
                "unlink:2",
                "close:2",
                "remove-all",
            ),
            events,
        )
    }

    @Test
    fun closeFailurePreventsStructuralRemovalAndLaterStorageClearFromStarting() {
        val events = mutableListOf<String>()
        val failure = IllegalStateException("close failed")
        val targets = listOf(
            AndroidComponentsWebDataClearTarget("1") {
                events += "close:1"
                throw failure
            },
        )

        val thrown = assertFailsWith<IllegalStateException> {
            runAndroidComponentsWebDataClear(
                targets = targets,
                unlink = { sessionId -> events += "unlink:$sessionId" },
                removeAllTabs = { events += "remove-all" },
            )
        }

        assertEquals(failure, thrown)
        assertEquals(listOf("unlink:1", "close:1"), events)
    }
}
