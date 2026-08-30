package com.artt.minibrowser

import com.artt.minibrowser.data.isSupportedDownloadLocation
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadLocationPolicyTest {
    @Test
    fun acceptsOnlyAppProducedDownloadLocations() {
        assertTrue(isSupportedDownloadLocation("content://media/external/downloads/42"))
        assertTrue(isSupportedDownloadLocation("content://media/external_primary/downloads/42"))
        assertTrue(isSupportedDownloadLocation("file:///storage/emulated/0/Download/report.pdf"))

        assertFalse(isSupportedDownloadLocation("content://media/external/images/media/42"))
        assertFalse(isSupportedDownloadLocation("content://media/external/downloads/not-an-id"))
        assertFalse(isSupportedDownloadLocation("content://media/external/downloads/42?other=1"))
        assertFalse(isSupportedDownloadLocation("content://com.android.contacts/contacts/42"))
        assertFalse(isSupportedDownloadLocation("https://example.com/file.pdf"))
        assertFalse(isSupportedDownloadLocation("intent://example/#Intent;scheme=https;end"))
        assertFalse(isSupportedDownloadLocation("javascript:alert(1)"))
        assertFalse(isSupportedDownloadLocation("content:/missing-authority"))
        assertFalse(isSupportedDownloadLocation("file:"))
        assertFalse(isSupportedDownloadLocation("not a uri"))
    }
}
