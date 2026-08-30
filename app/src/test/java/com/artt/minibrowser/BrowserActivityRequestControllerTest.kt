package com.artt.minibrowser

import com.artt.minibrowser.browser.acceptedMimeTypes
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class BrowserActivityRequestControllerTest {
    @Test
    fun acceptedMimeTypesFiltersInvalidValuesAndDuplicates() {
        assertArrayEquals(
            arrayOf("image/png", "text/plain"),
            acceptedMimeTypes(arrayOf("", "image/png", "invalid", "image/png", "text/plain")),
        )
    }

    @Test
    fun acceptedMimeTypesFallsBackToWildcard() {
        assertArrayEquals(
            arrayOf("*/*"),
            acceptedMimeTypes(arrayOf("", "invalid")),
        )
    }
}
