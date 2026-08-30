package com.artt.minibrowser

import com.artt.minibrowser.browser.ActivityRequestCoordinator
import kotlin.test.Test
import kotlin.test.assertEquals

class ActivityRequestCoordinatorTest {
    @Test
    fun startsOnlyOneRequestAndQueuesTheNext() {
        val coordinator = ActivityRequestCoordinator<String>()
        val started = mutableListOf<String>()
        val results = mutableListOf<String>()
        lateinit var finishFirst: (String) -> Unit
        coordinator.enqueue({ finish -> started += "first"; finishFirst = { value -> results += value; finish(value) } }, { results += "cancel-first" })
        coordinator.enqueue({ finish -> started += "second"; finish("done") }, { results += "cancel-second" })
        assertEquals(listOf("first"), started)
        finishFirst("first-done")
        assertEquals(listOf("first", "second"), started)
        assertEquals(listOf("first-done"), results)
    }

    @Test
    fun synchronousLaunchFailureCancelsRequestAndDoesNotWedgeQueue() {
        val coordinator = ActivityRequestCoordinator<String>()
        val events = mutableListOf<String>()

        coordinator.enqueue(
            start = {
                events += "first-start"
                error("launcher unavailable")
            },
            cancel = { events += "first-cancel" },
        )
        coordinator.enqueue(
            start = { finish ->
                events += "second-start"
                finish("done")
            },
            cancel = { events += "second-cancel" },
        )

        assertEquals(listOf("first-start", "first-cancel", "second-start"), events)
    }

    @Test
    fun failedStartCancellationCanReenterCoordinator() {
        val coordinator = ActivityRequestCoordinator<String>()
        val events = mutableListOf<String>()

        coordinator.enqueue(
            start = {
                events += "failed-start"
                error("launcher unavailable")
            },
            cancel = {
                events += "failed-cancel"
                coordinator.enqueue(
                    start = { finish ->
                        events += "replacement-start"
                        finish("done")
                    },
                    cancel = { events += "replacement-cancel" },
                )
            },
        )

        assertEquals(
            listOf("failed-start", "failed-cancel", "replacement-start"),
            events,
        )
    }

    @Test
    fun cancelAllCancelsActiveAndQueuedRequestsAndAllowsNewRequest() {
        val coordinator = ActivityRequestCoordinator<String>()
        val events = mutableListOf<String>()
        lateinit var finish: (String) -> Unit
        coordinator.enqueue({ complete -> events += "first"; finish = complete }, { events += "cancel-first" })
        coordinator.enqueue({ complete -> events += "second"; complete("second") }, { events += "cancel-second" })
        coordinator.enqueue({ complete -> events += "third"; complete("third") }, { events += "cancel-third" })
        coordinator.cancelAll()
        finish("late")
        coordinator.enqueue({ complete -> events += "new"; complete("new") }, { events += "cancel-new" })
        assertEquals(listOf("first", "cancel-first", "cancel-second", "cancel-third", "new"), events)
    }
}
