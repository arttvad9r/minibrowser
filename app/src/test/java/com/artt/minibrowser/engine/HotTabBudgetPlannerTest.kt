package com.artt.minibrowser.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HotTabBudgetPlannerTest {
    @Test
    fun withinBudgetDoesNothing() {
        val entries = listOf(
            HotTabBudgetEntry(1, HotTabSessionOwner.Raw, lastAccess = 10),
            HotTabBudgetEntry(2, HotTabSessionOwner.AndroidComponents, lastAccess = 20),
        )

        assertTrue(planHotTabBudget(entries, selectedTabId = 2, limit = 2).isEmpty())
    }

    @Test
    fun evictsGloballyColdestSessionsAcrossOwners() {
        val entries = listOf(
            HotTabBudgetEntry(1, HotTabSessionOwner.Raw, lastAccess = 10),
            HotTabBudgetEntry(2, HotTabSessionOwner.AndroidComponents, lastAccess = 20),
            HotTabBudgetEntry(3, HotTabSessionOwner.Raw, lastAccess = 30),
            HotTabBudgetEntry(4, HotTabSessionOwner.AndroidComponents, lastAccess = 40),
        )

        assertEquals(
            listOf(
                HotTabBudgetEviction(1, HotTabSessionOwner.Raw),
                HotTabBudgetEviction(2, HotTabSessionOwner.AndroidComponents),
            ),
            planHotTabBudget(entries, selectedTabId = 4, limit = 2),
        )
    }

    @Test
    fun selectedSessionIsNeverEvictedEvenWhenItIsColdest() {
        val entries = listOf(
            HotTabBudgetEntry(1, HotTabSessionOwner.AndroidComponents, lastAccess = 10),
            HotTabBudgetEntry(2, HotTabSessionOwner.Raw, lastAccess = 20),
            HotTabBudgetEntry(3, HotTabSessionOwner.AndroidComponents, lastAccess = 30),
        )

        assertEquals(
            listOf(HotTabBudgetEviction(2, HotTabSessionOwner.Raw)),
            planHotTabBudget(entries, selectedTabId = 1, limit = 2),
        )
    }

    @Test
    fun nonEvictableSessionStillConsumesBudgetButIsSkipped() {
        val entries = listOf(
            HotTabBudgetEntry(1, HotTabSessionOwner.Raw, lastAccess = 10, canEvict = false),
            HotTabBudgetEntry(2, HotTabSessionOwner.AndroidComponents, lastAccess = 20),
            HotTabBudgetEntry(3, HotTabSessionOwner.Raw, lastAccess = 30),
        )

        assertEquals(
            listOf(HotTabBudgetEviction(2, HotTabSessionOwner.AndroidComponents)),
            planHotTabBudget(entries, selectedTabId = 3, limit = 2),
        )
    }

    @Test
    fun returnsOnlySafeEvictionsWhenBudgetCannotBeReached() {
        val entries = listOf(
            HotTabBudgetEntry(1, HotTabSessionOwner.Raw, lastAccess = 10, canEvict = false),
            HotTabBudgetEntry(2, HotTabSessionOwner.AndroidComponents, lastAccess = 20),
            HotTabBudgetEntry(3, HotTabSessionOwner.Raw, lastAccess = 30, canEvict = false),
            HotTabBudgetEntry(4, HotTabSessionOwner.AndroidComponents, lastAccess = 40),
        )

        assertEquals(
            listOf(HotTabBudgetEviction(2, HotTabSessionOwner.AndroidComponents)),
            planHotTabBudget(entries, selectedTabId = 4, limit = 1),
        )
    }

    @Test
    fun negativeLimitIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            planHotTabBudget(emptyList(), selectedTabId = null, limit = -1)
        }
    }
}
