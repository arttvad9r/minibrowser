package com.artt.minibrowser.engine

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

fun Map<String, String>.header(name: String): String? = entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

fun sanitizeFilename(raw: String?, fallback: String): String {
    val safeFallback = fallback.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('.', ' ', '_').ifBlank { "file" }
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
        return sanitizeFilename(URLDecoder.decode(it.groupValues[1].trim().replace("+", "%2B"), StandardCharsets.UTF_8.name()), fallback)
    }
    Regex("filename=\"([^\"]+)\"").find(disposition)?.let { return sanitizeFilename(it.groupValues[1], fallback) }
    Regex("filename=([^;]+)").find(disposition)?.let { return sanitizeFilename(it.groupValues[1].trim(), fallback) }
    return sanitizeFilename(null, fallback)
}

// ponytail: setDestinationInExternalPublicDir на API 26–28 требует WRITE_EXTERNAL_STORAGE;
// личное устройство Android 15+ — при SecurityException просто молча пропускаем.
fun enqueueDownload(
    context: Context,
    uri: String,
    filenameFallback: String,
    headers: Map<String, String> = emptyMap(),
) {
    val disposition = headers.header("Content-Disposition")
    val name = parseFilename(disposition, filenameFallback)
    val req = DownloadManager.Request(Uri.parse(uri))
        .setTitle(name)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, name)
    headers.header("User-Agent")?.let { req.addRequestHeader("User-Agent", it) }
    headers.header("Referer")?.let { req.addRequestHeader("Referer", it) }
    context.getSystemService(DownloadManager::class.java).enqueue(req)
}
