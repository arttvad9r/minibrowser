package com.artt.minibrowser.browser

import kotlin.test.Test
import kotlin.test.assertEquals

class NavigationControllerPendingTest {
    @Test
    fun pendingUrisAreDeliveredInArrivalOrder() {
        val controller = NavigationController()
        controller.accept("https://one.example")
        controller.accept("https://two.example/path")
        controller.accept("file:///data/local/ignored")

        val delivered = mutableListOf<String>()
        controller.setHandler(delivered::add)

        assertEquals(
            listOf("https://one.example", "https://two.example/path"),
            delivered,
        )
    }

    @Test
    fun laterUrisGoDirectlyToInstalledHandler() {
        val controller = NavigationController()
        val delivered = mutableListOf<String>()
        controller.setHandler(delivered::add)

        controller.accept("https://three.example")

        assertEquals(listOf("https://three.example"), delivered)
    }
}
