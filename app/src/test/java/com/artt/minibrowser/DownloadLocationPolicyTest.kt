package com.artt.minibrowser

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadLocationPolicyTest {
    @Test
    fun acceptsOnlyAppProducedDownloadLocationSchemes() {
        assertTrue(isSupportedDownloadLocation("content://media/external/downloads/42"))
        assertTrue(isSupportedDownloadLocation("file:///storage/emulated/0/Download/report.pdf"))

        assertFalse(isSupportedDownloadLocation("https://example.com/file.pdf"))
        assertFalse(isSupportedDownloadLocation("intent://example/#Intent;scheme=https;end"))
        assertFalse(isSupportedDownloadLocation("javascript:alert(1)"))
        assertFalse(isSupportedDownloadLocation("content:/missing-authority"))
        assertFalse(isSupportedDownloadLocation("file:"))
        assertFalse(isSupportedDownloadLocation("not a uri"))
    }
}
