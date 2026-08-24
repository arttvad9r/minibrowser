package com.artt.minibrowser.engine

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import java.net.URLDecoder

fun parseFilename(disposition: String?, fallback: String): String {
    if (disposition == null) return fallback
    Regex("filename\\*=UTF-8''([^;]+)", RegexOption.IGNORE_CASE).find(disposition)?.let {
        return URLDecoder.decode(it.groupValues[1].trim(), "UTF-8")
    }
    Regex("filename=\"([^\"]+)\"").find(disposition)?.let { return it.groupValues[1] }
    Regex("filename=([^;]+)").find(disposition)?.let { return it.groupValues[1].trim() }
    return fallback
}

// ponytail: setDestinationInExternalPublicDir на API 26–28 требует WRITE_EXTERNAL_STORAGE;
// личное устройство Android 15+ — при SecurityException просто молча пропускаем.
fun enqueueDownload(
    context: Context,
    uri: String,
    filenameFallback: String,
    headers: Map<String, String> = emptyMap(),
) {
    val name = parseFilename(headers["Content-Disposition"], filenameFallback)
    val req = DownloadManager.Request(Uri.parse(uri))
        .setTitle(name)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, name)
    headers["User-Agent"]?.let { req.addRequestHeader("User-Agent", it) }
    headers["Referer"]?.let { req.addRequestHeader("Referer", it) }
    context.getSystemService(DownloadManager::class.java).enqueue(req)
}
