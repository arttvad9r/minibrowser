package com.artt.minibrowser

import com.artt.minibrowser.engine.reserveUniqueDownloadFile
import com.artt.minibrowser.engine.writeLegacyDownload
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LegacyDownloadWriteTest {
    @Test
    fun writesCompleteLegacyDownload() {
        val dir = Files.createTempDirectory("minibrowser-legacy-download").toFile()
        try {
            val target = dir.resolve("file.bin")
            val data = byteArrayOf(1, 2, 3, 4)

            val bytes = writeLegacyDownload(target, ByteArrayInputStream(data))

            assertEquals(data.size.toLong(), bytes)
            assertContentEquals(data, target.readBytes())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun deletesPartialFileWhenInputFails() {
        val dir = Files.createTempDirectory("minibrowser-legacy-download").toFile()
        try {
            val target = dir.resolve("partial.bin")
            var reads = 0
            val failingInput = object : InputStream() {
                override fun read(): Int = when (reads++) {
                    0 -> 1
                    1 -> 2
                    2 -> 3
                    else -> throw IOException("simulated read failure")
                }
            }

            assertFailsWith<IOException> { writeLegacyDownload(target, failingInput) }
            assertFalse(target.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun reservesDifferentPathsForSameConcurrentFilename() {
        val dir = Files.createTempDirectory("minibrowser-legacy-reserve").toFile()
        try {
            var first = dir.resolve("missing")
            var second = dir.resolve("missing")
            val a = Thread { first = reserveUniqueDownloadFile(dir, "report.pdf") }
            val b = Thread { second = reserveUniqueDownloadFile(dir, "report.pdf") }

            a.start()
            b.start()
            a.join()
            b.join()

            assertNotEquals(first.canonicalPath, second.canonicalPath)
            assertTrue(first.isFile)
            assertTrue(second.isFile)
            assertEquals(setOf("report.pdf", "report (1).pdf"), setOf(first.name, second.name))
        } finally {
            dir.deleteRecursively()
        }
    }
}
