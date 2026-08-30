package com.artt.minibrowser

import com.artt.minibrowser.browser.acceptedMimeTypes
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class BrowserActivityRequestControllerTest {
    @Test
    fun acceptedMimeTypesNormalizesAndFiltersInvalidValues() {
        assertArrayEquals(
            arrayOf("image/png", "text/plain", "image/*"),
            acceptedMimeTypes(
                arrayOf(
                    "",
                    " IMAGE/PNG ",
                    "invalid",
                    "image/png",
                    "text/plain",
                    "text/",
                    "image/*",
                ),
            ),
        )
    }

    @Test
    fun acceptedMimeTypesFallsBackToWildcard() {
        assertArrayEquals(
            arrayOf("*/*"),
            acceptedMimeTypes(arrayOf("", "invalid", "text/")),
        )
    }
}
