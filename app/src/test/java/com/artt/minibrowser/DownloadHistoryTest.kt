package com.artt.minibrowser

import com.artt.minibrowser.data.downloadSourceForHistory
import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadHistoryTest {
    @Test fun stripsQueryFragmentAndCredentials() {
        assertEquals(
            "https://example.com",
            downloadSourceForHistory("https://user:secret@example.com/path/file.zip?token=secret#part"),
        )
    }

    @Test fun preservesNonDefaultPortWithoutPath() {
        assertEquals(
            "https://example.com:8443",
            downloadSourceForHistory("https://example.com:8443/private/file?q=1"),
        )
    }

    @Test fun rejectsNonWebAndMalformedSources() {
        assertEquals("", downloadSourceForHistory("file:///tmp/private"))
        assertEquals("", downloadSourceForHistory("not a url"))
    }
}
