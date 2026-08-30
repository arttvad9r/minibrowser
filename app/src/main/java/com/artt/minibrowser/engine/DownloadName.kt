package com.artt.minibrowser.engine

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

fun Map<String, String>.header(name: String): String? =
    entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

fun sanitizeFilename(raw: String?, fallback: String): String {
    val safeFallback = fallback.replace(Regex("[^A-Za-z0-9._-]"), "_")
        .trim('.', ' ', '_')
        .ifBlank { "file" }
    val candidate = raw.orEmpty()
        .replace('\\', '_')
        .replace('/', '_')
        .replace("..", "")
        .filterNot { it.isISOControl() }
        .trim('.', ' ', '_')
        .take(120)
        .trim('.', ' ', '_')
    return candidate.ifBlank { safeFallback }
}

fun parseFilename(disposition: String?, fallback: String): String {
    if (disposition == null) return sanitizeFilename(null, fallback)
    Regex("filename\\*=UTF-8''([^;]+)", RegexOption.IGNORE_CASE).find(disposition)?.let {
        val decoded = runCatching {
            URLDecoder.decode(
                it.groupValues[1].trim().replace("+", "%2B"),
                StandardCharsets.UTF_8.name(),
            )
        }.getOrNull()
        if (decoded != null) return sanitizeFilename(decoded, fallback)
    }
    Regex("filename=\"([^\"]+)\"").find(disposition)?.let {
        return sanitizeFilename(it.groupValues[1], fallback)
    }
    Regex("filename=([^;]+)").find(disposition)?.let {
        return sanitizeFilename(it.groupValues[1].trim(), fallback)
    }
    return sanitizeFilename(null, fallback)
}
