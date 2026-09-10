package com.artt.minibrowser.engine

internal enum class HotTabSessionOwner {
    Raw,
    AndroidComponents,
}

internal data class HotTabBudgetEntry(
    val tabId: Long,
    val owner: HotTabSessionOwner,
    val lastAccess: Long,
    val canEvict: Boolean = true,
)

internal data class HotTabBudgetEviction(
    val tabId: Long,
    val owner: HotTabSessionOwner,
)

internal fun planHotTabBudget(
    entries: List<HotTabBudgetEntry>,
    selectedTabId: Long?,
    limit: Int,
): List<HotTabBudgetEviction> {
    require(limit >= 0) { "Hot-tab limit must be non-negative" }

    val evictionCount = entries.size - limit
    if (evictionCount <= 0) return emptyList()

    return entries
        .asSequence()
        .filter { it.tabId != selectedTabId && it.canEvict }
        .sortedBy { it.lastAccess }
        .take(evictionCount)
        .map { HotTabBudgetEviction(tabId = it.tabId, owner = it.owner) }
        .toList()
}
