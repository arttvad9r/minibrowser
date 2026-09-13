package com.artt.minibrowser.browser

import kotlin.test.Test
import kotlin.test.assertEquals

class NavigationControllerTest {
    @Test
    fun allowedUrisAreDeliveredImmediatelyAndInvalidUrisAreIgnored() {
        val delivered = mutableListOf<String>()
        val controller = NavigationController(delivered::add)

        controller.accept("https://one.example")
        controller.accept("https://two.example/path")
        controller.accept("file:///data/local/ignored")
        controller.accept(null)

        assertEquals(
            listOf("https://one.example", "https://two.example/path"),
            delivered,
        )
    }
}
