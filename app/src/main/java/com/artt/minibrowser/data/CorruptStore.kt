package com.artt.minibrowser.data

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Removes a corrupt live store from the normal read path while keeping one bounded diagnostic copy.
 * A newer corrupt payload replaces the older backup; if moving fails, delete the live file so the
 * app can still recover instead of retrying the same broken payload forever.
 */
internal fun quarantineCorruptFile(target: File, backup: File) {
    if (!target.isFile) return
    val moved = runCatching {
        backup.parentFile?.mkdirs()
        Files.move(target.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING)
        true
    }.getOrDefault(false)
    if (!moved) target.delete()
}
