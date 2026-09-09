package com.artt.minibrowser.engine

import kotlin.test.Test
import kotlin.test.assertContentEquals

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
}
