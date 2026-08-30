package com.artt.minibrowser.data

import java.net.URI

private const val MEDIA_STORE_AUTHORITY = "media"

internal fun isSupportedDownloadLocation(value: String): Boolean = runCatching {
    val uri = URI(value)
    when (uri.scheme?.lowercase()) {
        "content" -> isMediaStoreDownloadRow(uri)
        "file" -> !uri.path.isNullOrBlank()
        else -> false
    }
}.getOrDefault(false)

private fun isMediaStoreDownloadRow(uri: URI): Boolean {
    if (!uri.rawAuthority.equals(MEDIA_STORE_AUTHORITY, ignoreCase = true)) return false
    if (uri.rawQuery != null || uri.rawFragment != null) return false
    val segments = uri.rawPath
        ?.split('/')
        ?.filter { it.isNotEmpty() }
        .orEmpty()
    return segments.size == 3 &&
        segments[0].isNotBlank() &&
        segments[1].equals("downloads", ignoreCase = true) &&
        segments[2].toLongOrNull()?.let { it >= 0L } == true
}
