package com.artt.minibrowser

import com.artt.minibrowser.browser.acceptedMimeTypes
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class BrowserActivityRequestControllerTest {
    @Test
    fun acceptedMimeTypesNormalizesAndFiltersInvalidValues() {
        assertArrayEquals(
            arrayOf("image/png", "text/plain", "image/*", "*/*", "application/vnd.api+json"),
            acceptedMimeTypes(
                arrayOf(
                    "",
                    " IMAGE/PNG ",
                    "invalid",
                    "image/png",
                    "text/plain",
                    "text/",
                    "image/*",
                    "*/*",
                    "*/png",
                    "text/ht ml",
                    "text/html/extra",
                    "text/html; charset=utf-8",
                    "application/vnd.api+json",
                ),
            ),
        )
    }

    @Test
    fun acceptedMimeTypesFallsBackToWildcard() {
        assertArrayEquals(
            arrayOf("*/*"),
            acceptedMimeTypes(arrayOf("", "invalid", "text/", "*/png", "text/ht ml")),
        )
    }
}
