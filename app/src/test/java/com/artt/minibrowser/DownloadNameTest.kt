package com.artt.minibrowser

import com.artt.minibrowser.engine.parseFilename
import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadNameTest {
    @Test fun quotedFilename() =
        assertEquals("report.pdf", parseFilename("attachment; filename=\"report.pdf\"", "file"))

    @Test fun rfc5987Utf8() =
        assertEquals(
            "отчет.pdf",
            parseFilename("attachment; filename*=UTF-8''%D0%BE%D1%82%D1%87%D0%B5%D1%82.pdf", "file"),
        )

    @Test fun nullDispositionFallsBack() =
        assertEquals("file", parseFilename(null, "file"))
}
