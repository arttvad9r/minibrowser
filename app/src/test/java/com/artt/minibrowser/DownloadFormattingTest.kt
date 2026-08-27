package com.artt.minibrowser

import com.artt.minibrowser.data.formatDownloadSize
import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadFormattingTest {
    @Test fun bytesStayBytes() {
        assertEquals("0 Б", formatDownloadSize(0))
        assertEquals("1023 Б", formatDownloadSize(1023))
    }

    @Test fun formatsKilobytes() {
        assertEquals("1.0 КБ", formatDownloadSize(1024))
        assertEquals("10 КБ", formatDownloadSize(10 * 1024))
    }

    @Test fun formatsMegabytesAndGigabytes() {
        assertEquals("1.0 МБ", formatDownloadSize(1024L * 1024L))
        assertEquals("1.0 ГБ", formatDownloadSize(1024L * 1024L * 1024L))
    }

    @Test fun negativeSizeIsUnknown() {
        assertEquals("—", formatDownloadSize(-1))
    }
}
