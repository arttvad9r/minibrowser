package com.artt.minibrowser.data

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

// Пишет историю последовательно через application-scope DbHolder (см. Db.kt).
// Один consumer сохраняет порядок Gecko callbacks: visit всегда обрабатывается раньше
// следующего title update, если callbacks пришли в таком порядке.
object HistorySink {
    private sealed interface Event {
        data class Visit(val url: String, val title: String?) : Event
        data class Title(val url: String, val title: String?) : Event
    }

    private val repo by lazy { HistoryRepository(DbHolder.db.dao()) }
    private val events = Channel<Event>(Channel.UNLIMITED)

    init {
        DbHolder.scope.launch {
            for (event in events) {
                when (event) {
                    is Event.Visit -> repo.record(event.url, event.title)
                    is Event.Title -> repo.updateTitle(event.url, event.title)
                }
            }
        }
    }

    fun record(url: String, title: String?) {
        events.trySend(Event.Visit(url, title?.takeIf { it.isNotBlank() }))
    }

    fun updateTitle(url: String, title: String?) {
        events.trySend(Event.Title(url, title))
    }
}
