package com.artt.minibrowser.data

import kotlinx.coroutines.launch

// Пишет историю в фоне через application-scope DbHolder (см. Db.kt).
object HistorySink {
    private val repo by lazy { HistoryRepository(DbHolder.db.dao()) }

    fun record(url: String, title: String?) {
        // Пустой заголовок (страница ещё не отдала title) — сохраняем прежний.
        DbHolder.scope.launch { repo.record(url, title?.takeIf { it.isNotBlank() }) }
    }

    fun updateTitle(url: String, title: String?) {
        DbHolder.scope.launch { repo.updateTitle(url, title) }
    }
}
