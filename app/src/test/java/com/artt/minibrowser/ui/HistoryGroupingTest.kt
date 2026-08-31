package com.artt.minibrowser.ui

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryGroupingTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 9, 1)

    @Test
    fun groupsEntriesByDayAndPreservesOrder() {
        val todayFirst = entry("today-first", at(today, 8))
        val todaySecond = entry("today-second", at(today, 20))
        val yesterday = entry("yesterday", at(today.minusDays(1), 12))
        val earlier = entry("earlier", at(today.minusDays(2), 23))

        val groups = groupHistoryByDay(
            entries = listOf(todayFirst, yesterday, todaySecond, earlier),
            zone = zone,
            today = today,
        )

        assertEquals(
            listOf(
                HistoryDayGroup.Today to listOf(todayFirst, todaySecond),
                HistoryDayGroup.Yesterday to listOf(yesterday),
                HistoryDayGroup.Earlier to listOf(earlier),
            ),
            groups,
        )
    }

    @Test
    fun usesExactMidnightBoundaries() {
        val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val yesterdayStart = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val todayEntry = entry("today", todayStart)
        val yesterdayEntry = entry("yesterday", yesterdayStart)
        val earlierEntry = entry("earlier", yesterdayStart - 1)

        val groups = groupHistoryByDay(
            entries = listOf(earlierEntry, yesterdayEntry, todayEntry),
            zone = zone,
            today = today,
        )

        assertEquals(
            listOf(
                HistoryDayGroup.Today to listOf(todayEntry),
                HistoryDayGroup.Yesterday to listOf(yesterdayEntry),
                HistoryDayGroup.Earlier to listOf(earlierEntry),
            ),
            groups,
        )
    }

    @Test
    fun omitsEmptyGroups() {
        val earlier = entry("earlier", at(today.minusDays(7), 10))

        val groups = groupHistoryByDay(
            entries = listOf(earlier),
            zone = zone,
            today = today,
        )

        assertEquals(listOf(HistoryDayGroup.Earlier to listOf(earlier)), groups)
    }

    private fun at(date: LocalDate, hour: Int): Long =
        date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun entry(id: String, visitedAt: Long) = HistoryItemUiState(
        url = "https://example.com/$id",
        title = id,
        visitedAt = visitedAt,
    )
}
