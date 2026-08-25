package com.artt.minibrowser.browser

class ActivityRequestCoordinator<T> {
    private data class Request<T>(
        val start: ( (T) -> Unit) -> Unit,
        val cancel: () -> Unit,
    )

    private val queue = ArrayDeque<Request<T>>()
    private var active: Request<T>? = null

    fun enqueue(start: ((T) -> Unit) -> Unit, cancel: () -> Unit) {
        queue += Request<T>(start, cancel)
        drain()
    }

    fun cancelAll() {
        active?.cancel()
        queue.forEach { it.cancel() }
        active = null
        queue.clear()
    }

    private fun drain() {
        if (active != null) return
        val request = queue.removeFirstOrNull() ?: return
        active = request
        request.start {
            if (active === request) active = null
            drain()
        }
    }
}
