package com.artt.minibrowser.engine

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

fun Map<String, String>.header(name: String): String? =
    entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

private fun sanitizeFilenameChars(value: String, maxChars: Int = 120): String = buildString(minOf(value.length, maxChars)) {
    var offset = 0
    while (offset < value.length) {
        val codePoint = value.codePointAt(offset)
        offset += Character.charCount(codePoint)
        val type = Character.getType(codePoint)
        val unsafe = Character.isISOControl(codePoint) ||
            type == Character.FORMAT.toInt() ||
            type == Character.LINE_SEPARATOR.toInt() ||
            type == Character.PARAGRAPH_SEPARATOR.toInt()
        if (unsafe) continue
        val width = Character.charCount(codePoint)
        if (length + width > maxChars) break
        appendCodePoint(codePoint)
    }
}

fun sanitizeFilename(raw: String?, fallback: String): String {
    val safeFallback = fallback.replace(Regex("[^A-Za-z0-9._-]"), "_")
        .trim('.', ' ', '_')
        .ifBlank { "file" }
    val candidate = raw.orEmpty()
        .replace('\\', '_')
        .replace('/', '_')
        .replace("..", "")
        .let(::sanitizeFilenameChars)
        .trim('.', ' ', '_')
    return candidate.ifBlank { safeFallback }
}

fun parseFilename(disposition: String?, fallback: String): String {
    if (disposition == null) return sanitizeFilename(null, fallback)
    Regex("filename\\*=UTF-8'[^']*'([^;]+)", RegexOption.IGNORE_CASE).find(disposition)?.let {
        val decoded = runCatching {
            URLDecoder.decode(
                it.groupValues[1].trim().replace("+", "%2B"),
                StandardCharsets.UTF_8.name(),
            )
        }.getOrNull()
        if (decoded != null) return sanitizeFilename(decoded, fallback)
    }
    Regex("filename=\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(disposition)?.let {
        return sanitizeFilename(it.groupValues[1], fallback)
    }
    Regex("filename=([^;]+)", RegexOption.IGNORE_CASE).find(disposition)?.let {
        return sanitizeFilename(it.groupValues[1].trim(), fallback)
    }
    return sanitizeFilename(null, fallback)
}
