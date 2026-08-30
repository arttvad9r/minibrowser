package com.artt.minibrowser.engine

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

private const val MAX_FILENAME_CHARS = 120
private const val MAX_FILENAME_UTF8_BYTES = 240

fun Map<String, String>.header(name: String): String? =
    entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

private fun utf8Bytes(codePoint: Int): Int = when {
    codePoint <= 0x7F -> 1
    codePoint <= 0x7FF -> 2
    codePoint <= 0xFFFF -> 3
    else -> 4
}

private fun sanitizeFilenameChars(
    value: String,
    maxChars: Int = MAX_FILENAME_CHARS,
    maxUtf8Bytes: Int = MAX_FILENAME_UTF8_BYTES,
): String = buildString(minOf(value.length, maxChars)) {
    var offset = 0
    var encodedBytes = 0
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
        val encodedWidth = utf8Bytes(codePoint)
        if (length + width > maxChars || encodedBytes + encodedWidth > maxUtf8Bytes) break
        appendCodePoint(codePoint)
        encodedBytes += encodedWidth
    }
}

private fun sanitizeFilenameValue(value: String): String = value
    .replace('\\', '_')
    .replace('/', '_')
    .replace("..", "")
    .let(::sanitizeFilenameChars)
    .trim('.', ' ', '_')

fun sanitizeFilename(raw: String?, fallback: String): String {
    val safeFallback = sanitizeFilenameValue(fallback).ifBlank { "file" }
    val candidate = sanitizeFilenameValue(raw.orEmpty())
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
