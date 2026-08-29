package com.artt.minibrowser.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

// Пишет и очищает историю последовательно через application-scope DbHolder (см. Db.kt).
// Один consumer сохраняет порядок Gecko callbacks и clear: queued visit/title всегда
// обрабатываются до очистки, а поздний title не может заново создать удалённую запись.
object HistorySink {
    private sealed interface Event {
        data class Visit(val url: String, val title: String?) : Event
        data class Title(val url: String, val title: String?) : Event
        data class Clear(val completion: CompletableDeferred<Unit>) : Event
    }

    private val repo by lazy { HistoryRepository(DbHolder.db.dao()) }
    private val events = Channel<Event>(Channel.UNLIMITED)

    init {
        DbHolder.scope.launch {
            for (event in events) {
                try {
                    when (event) {
                        is Event.Visit -> repo.record(event.url, event.title)
                        is Event.Title -> repo.updateTitle(event.url, event.title)
                        is Event.Clear -> {
                            DbHolder.db.dao().clearHistory()
                            event.completion.complete(Unit)
                        }
                    }
                } catch (error: Throwable) {
                    if (event is Event.Clear) event.completion.completeExceptionally(error)
                    // A transient storage error must not permanently kill the history consumer.
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

    suspend fun clear() {
        val completion = CompletableDeferred<Unit>()
        events.send(Event.Clear(completion))
        completion.await()
    }
}
