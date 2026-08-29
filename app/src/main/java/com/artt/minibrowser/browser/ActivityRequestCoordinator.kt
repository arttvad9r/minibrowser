package com.artt.minibrowser.browser

/**
 * Serializes ActivityResult-style requests without letting a failed launcher wedge the queue.
 * Gecko callbacks are not assumed to arrive on the same thread, so queue state is guarded by a
 * private lock. User callbacks are cancelled outside the lock to avoid running arbitrary code
 * while the coordinator owns its state.
 */
class ActivityRequestCoordinator<T> {
    private data class Request<T>(
        val start: (((T) -> Unit) -> Unit),
        val cancel: () -> Unit,
    )

    private val lock = Any()
    private val queue = ArrayDeque<Request<T>>()
    private var active: Request<T>? = null

    fun enqueue(start: ((T) -> Unit) -> Unit, cancel: () -> Unit) {
        val next = synchronized(lock) {
            queue += Request(start, cancel)
            takeNextLocked()
        }
        next?.let(::startRequest)
    }

    fun cancelAll() {
        val cancelled = synchronized(lock) {
            buildList {
                active?.let(::add)
                addAll(queue)
            }.also {
                active = null
                queue.clear()
            }
        }
        cancelled.forEach { request -> runCatching { request.cancel() } }
    }

    private fun takeNextLocked(): Request<T>? {
        if (active != null) return null
        return queue.removeFirstOrNull()?.also { active = it }
    }

    private fun startRequest(request: Request<T>) {
        var failure: Throwable? = null
        synchronized(lock) {
            if (active !== request) return
            try {
                request.start { complete(request) }
            } catch (error: Throwable) {
                failure = error
            }
        }

        if (failure != null) {
            val stillActive = synchronized(lock) { active === request }
            if (stillActive) {
                runCatching { request.cancel() }
                complete(request)
            }
        }
    }

    private fun complete(request: Request<T>) {
        val next = synchronized(lock) {
            if (active !== request) {
                null
            } else {
                active = null
                takeNextLocked()
            }
        }
        next?.let(::startRequest)
    }
}
