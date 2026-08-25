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
}
