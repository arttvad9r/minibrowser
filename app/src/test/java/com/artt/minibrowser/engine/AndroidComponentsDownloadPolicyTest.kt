package com.artt.minibrowser.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import mozilla.components.support.ktx.kotlin.sanitizeFileName

class AndroidComponentsDownloadPolicyTest {
    @Test
    fun urlFallbackUsesDecodedLastPathSegment() {
        assertEquals(
            "отчет+final.pdf",
            downloadFallbackName(
                "https://example.test/files/%D0%BE%D1%82%D1%87%D0%B5%D1%82+final.pdf?token=secret",
            ),
        )
        assertEquals("download", downloadFallbackName("https://example.test/"))
        assertEquals("download", downloadFallbackName(null))
    }

    @Test
    fun delegateReturnsMiniBrowserSuggestionBeforeGeckoPostSanitization() {
        val delegate = AndroidComponentsDownloadDelegate()
        val disposition = "attachment; filename=\"report  final?.pdf\""

        assertEquals(
            "report  final?.pdf",
            delegate.guessFileName(
                contentDisposition = disposition,
                url = "https://example.test/fallback.bin",
                mimeType = "application/pdf",
            ),
        )
    }

    @Test
    fun rawFinalFilenameMatchesFutureGeckoEngineSessionPostSanitization() {
        val delegate = AndroidComponentsDownloadDelegate()
        val disposition = "attachment; filename=\"report  final?.pdf\""
        val url = "https://example.test/fallback.bin"
        val suggested = delegate.guessFileName(disposition, url, "application/pdf")

        assertEquals("report final_.pdf", suggested.sanitizeFileName())
        assertEquals(
            suggested.sanitizeFileName(),
            androidComponentsCompatibleDownloadFilename(disposition, url),
        )
    }

    @Test
    fun miniBrowserHardeningStillRunsBeforeMozillaPostSanitization() {
        val disposition = "attachment; filename=\"../report\u202Efdp.exe\""

        assertEquals(
            "reportfdp.exe",
            androidComponentsCompatibleDownloadFilename(
                disposition,
                "https://example.test/fallback.bin",
            ),
        )
    }
}
