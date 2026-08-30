package com.artt.minibrowser.data

import java.util.Locale

private const val DEFAULT_DOWNLOAD_MIME = "application/octet-stream"
private const val MIME_TOKEN_PUNCTUATION = "!#$&^_.+-"

internal fun normalizeDownloadMime(value: String?): String {
    val candidate = value
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase(Locale.ROOT)
        .orEmpty()
    val slash = candidate.indexOf('/')
    if (slash <= 0 || slash != candidate.lastIndexOf('/') || slash == candidate.lastIndex) {
        return DEFAULT_DOWNLOAD_MIME
    }

    fun validToken(token: String): Boolean = token.isNotEmpty() && token.all { char ->
        char in 'a'..'z' || char in '0'..'9' || char in MIME_TOKEN_PUNCTUATION
    }

    return if (validToken(candidate.substring(0, slash)) && validToken(candidate.substring(slash + 1))) {
        candidate
    } else {
        DEFAULT_DOWNLOAD_MIME
    }
}
