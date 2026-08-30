package com.artt.minibrowser

import com.artt.minibrowser.engine.header
import com.artt.minibrowser.engine.parseFilename
import com.artt.minibrowser.engine.sanitizeFilename
import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadNameTest {
    @Test fun quotedFilename() =
        assertEquals("report.pdf", parseFilename("attachment; filename=\"report.pdf\"", "file"))

    @Test fun plainFilenameParameterIsCaseInsensitive() =
        assertEquals("report.pdf", parseFilename("attachment; FILENAME=\"report.pdf\"", "file"))

    @Test fun rfc5987Utf8() =
        assertEquals(
            "отчет.pdf",
            parseFilename("attachment; filename*=UTF-8''%D0%BE%D1%82%D1%87%D0%B5%D1%82.pdf", "file"),
        )

    @Test fun rfc5987LanguageTagIsAccepted() =
        assertEquals(
            "отчет.pdf",
            parseFilename("attachment; filename*=UTF-8'ru'%D0%BE%D1%82%D1%87%D0%B5%D1%82.pdf", "file"),
        )

    @Test fun malformedRfc5987FallsBackInsteadOfThrowing() =
        assertEquals("file", parseFilename("attachment; filename*=UTF-8''broken%ZZname.pdf", "file"))

    @Test fun malformedRfc5987CanFallThroughToPlainFilename() =
        assertEquals(
            "safe.pdf",
            parseFilename("attachment; filename*=UTF-8''broken%ZZ; filename=\"safe.pdf\"", "file"),
        )

    @Test fun nullDispositionFallsBack() =
        assertEquals("file", parseFilename(null, "file"))

    @Test fun sanitizesTraversalAndSeparators() {
        assertEquals("secret.txt", sanitizeFilename("../../secret.txt", "file"))
        assertEquals("foo_bar.txt", sanitizeFilename("..\\foo/bar.txt", "file"))
    }

    @Test fun sanitizesControlsAndLength() {
        assertEquals("report.pdf", sanitizeFilename("report\u0000.pdf\n", "file"))
        assert(sanitizeFilename("x".repeat(300), "file").length <= 120)
    }

    @Test fun headersAreCaseInsensitive() {
        assertEquals(
            "attachment; filename=\"x.txt\"",
            mapOf("content-disposition" to "attachment; filename=\"x.txt\"").header("Content-Disposition"),
        )
    }
}
