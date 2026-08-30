package com.artt.minibrowser

import com.artt.minibrowser.data.downloadSourceForHistory
import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadSourceIdnTest {
    @Test fun unicodeSourceKeepsOriginOnly() {
        assertEquals(
            "https://пример.рф",
            downloadSourceForHistory("https://user:secret@пример.рф/файл.zip?token=secret#part"),
        )
    }

    @Test fun unicodeSourcePreservesExplicitPort() {
        assertEquals(
            "http://bücher.de:8080",
            downloadSourceForHistory("http://bücher.de:8080/private/file?q=1"),
        )
    }
}
