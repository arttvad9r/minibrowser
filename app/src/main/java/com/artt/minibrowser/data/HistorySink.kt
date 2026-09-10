package com.artt.minibrowser.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

internal const val HISTORY_EVENT_QUEUE_CAPACITY = 512

/**
 * Finite, FIFO queue for non-suspending Gecko callbacks.
 *
 * History visits are user data: dropping an old/new event or allocating one coroutine per overflow
 * would either corrupt semantics or merely move the unbounded-memory problem elsewhere. Normal
 * callbacks use the non-blocking fast path. Only after [capacity] outstanding writes does the
 * producer apply lossless backpressure until the single IO consumer frees one slot.
 */
internal class LosslessBoundedQueue<T>(capacity: Int) {
    private val channel = Channel<T>(capacity)

    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    fun offer(value: T) {
        if (channel.trySend(value).isSuccess) return
        runBlocking { channel.send(value) }
    }

    suspend fun send(value: T) {
        channel.send(value)
    }

    suspend fun receive(): T = channel.receive()
}

internal interface MiniBrowserHistoryBackend {
    fun record(url: String, title: String?)
    fun updateTitle(url: String, title: String?)
    suspend fun getVisited(uris: List<String>): List<Boolean>
    suspend fun getVisited(): List<String>
}

// Пишет, читает и очищает историю последовательно через application-scope DbHolder (см. Db.kt).
// Один consumer сохраняет порядок Gecko callbacks и clear: queued visit/title всегда
// обрабатываются до очистки, а queued read видит все предшествующие изменения.
internal object HistorySink : MiniBrowserHistoryBackend {
    private sealed interface Event {
        data class Visit(val url: String, val title: String?) : Event
        data class Title(val url: String, val title: String?) : Event
        data class Clear(val completion: CompletableDeferred<Unit>) : Event
        data class GetVisited(
            val uris: List<String>,
            val completion: CompletableDeferred<List<Boolean>>,
        ) : Event
        data class GetAllVisited(val completion: CompletableDeferred<List<String>>) : Event
    }

    private val repo by lazy { HistoryRepository(DbHolder.db.dao()) }
    private val events = LosslessBoundedQueue<Event>(HISTORY_EVENT_QUEUE_CAPACITY)

    init {
        DbHolder.scope.launch {
            while (true) {
                val event = events.receive()
                try {
                    when (event) {
                        is Event.Visit -> repo.record(event.url, event.title)
                        is Event.Title -> repo.updateTitle(event.url, event.title)
                        is Event.Clear -> {
                            DbHolder.db.dao().clearHistory()
                            event.completion.complete(Unit)
                        }
                        is Event.GetVisited -> event.completion.complete(repo.getVisited(event.uris))
                        is Event.GetAllVisited -> event.completion.complete(repo.getVisited())
                    }
                } catch (error: Throwable) {
                    event.completeExceptionally(error)
                    // A transient storage error must not permanently kill the history consumer.
                }
            }
        }
    }

    override fun record(url: String, title: String?) {
        events.offer(Event.Visit(url, title?.takeIf { it.isNotBlank() }))
    }

    override fun updateTitle(url: String, title: String?) {
        events.offer(Event.Title(url, title))
    }

    override suspend fun getVisited(uris: List<String>): List<Boolean> {
        if (uris.isEmpty()) return emptyList()
        val completion = CompletableDeferred<List<Boolean>>()
        events.send(Event.GetVisited(uris.toList(), completion))
        return completion.await()
    }

    override suspend fun getVisited(): List<String> {
        val completion = CompletableDeferred<List<String>>()
        events.send(Event.GetAllVisited(completion))
        return completion.await()
    }

    suspend fun clear() {
        val completion = CompletableDeferred<Unit>()
        events.send(Event.Clear(completion))
        completion.await()
    }

    private fun Event.completeExceptionally(error: Throwable) {
        when (this) {
            is Event.Clear -> completion.completeExceptionally(error)
            is Event.GetVisited -> completion.completeExceptionally(error)
            is Event.GetAllVisited -> completion.completeExceptionally(error)
            is Event.Visit, is Event.Title -> Unit
        }
    }
}
