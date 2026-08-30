package com.artt.minibrowser

import com.artt.minibrowser.data.quarantineCorruptFile
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CorruptStoreTest {
    @Test fun quarantineKeepsOneBoundedBackup() {
        val dir = Files.createTempDirectory("minibrowser-corrupt-store").toFile()
        try {
            val target = File(dir, "downloads.json")
            val backup = File(dir, "downloads.json.corrupt")

            target.writeText("first corrupt payload")
            quarantineCorruptFile(target, backup)
            assertFalse(target.exists())
            assertEquals("first corrupt payload", backup.readText())

            target.writeText("new corrupt payload")
            quarantineCorruptFile(target, backup)
            assertFalse(target.exists())
            assertEquals("new corrupt payload", backup.readText())
            assertEquals(setOf("downloads.json.corrupt"), dir.listFiles().orEmpty().map { it.name }.toSet())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun missingLiveFileDoesNotDeleteExistingBackup() {
        val dir = Files.createTempDirectory("minibrowser-corrupt-store-missing").toFile()
        try {
            val target = File(dir, "downloads.json")
            val backup = File(dir, "downloads.json.corrupt").apply { writeText("diagnostic") }

            quarantineCorruptFile(target, backup)

            assertEquals("diagnostic", backup.readText())
        } finally {
            dir.deleteRecursively()
        }
    }
}
