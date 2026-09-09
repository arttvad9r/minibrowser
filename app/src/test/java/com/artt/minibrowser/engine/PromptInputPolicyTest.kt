package com.artt.minibrowser.engine

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PromptInputPolicyTest {
    @Test
    fun normalizesAndDeduplicatesAcceptedMimeTypes() {
        assertContentEquals(
            arrayOf("image/png", "application/vnd.api+json", "image/*"),
            acceptedPromptMimeTypes(
                arrayOf(
                    " IMAGE/PNG ",
                    "image/png",
                    "Application/Vnd.Api+Json",
                    "image/*",
                ),
            ),
        )
    }

    @Test
    fun rejectsMalformedMimeTypes() {
        assertContentEquals(
            arrayOf("text/plain"),
            acceptedPromptMimeTypes(
                arrayOf(
                    "text/plain",
                    "*/plain",
                    "text/",
                    "/plain",
                    "text/plain/extra",
                    "text/pl ain",
                    "text/@plain",
                ),
            ),
        )
    }

    @Test
    fun acceptsOnlyFullWildcardForWildcardMajorType() {
        assertContentEquals(
            arrayOf("*/*", "audio/*"),
            acceptedPromptMimeTypes(arrayOf("*/*", "*/json", "audio/*")),
        )
    }

    @Test
    fun fallsBackToAnyMimeTypeWhenNothingValidRemains() {
        assertContentEquals(
            arrayOf("*/*"),
            acceptedPromptMimeTypes(arrayOf("", "invalid", "text/")),
        )
        assertContentEquals(arrayOf("*/*"), acceptedPromptMimeTypes(emptyArray()))
    }

    @Test
    fun validatesSixDigitPromptColors() {
        assertTrue(isValidPromptColor("#000000"))
        assertTrue(isValidPromptColor("#aBcDeF"))
        assertFalse(isValidPromptColor("#fff"))
        assertFalse(isValidPromptColor("112233"))
        assertFalse(isValidPromptColor("#11223344"))
        assertFalse(isValidPromptColor("#12xz56"))
    }

    @Test
    fun invalidPromptColorFallsBackToBlack() {
        assertEquals("#123ABC", promptColorOrDefault("#123ABC"))
        assertEquals("#000000", promptColorOrDefault(null))
        assertEquals("#000000", promptColorOrDefault(""))
        assertEquals("#000000", promptColorOrDefault("red"))
    }
}
