package com.artt.minibrowser.data

fun formatDownloadSize(bytes: Long): String {
    if (bytes < 0L) return "—"
    if (bytes < 1024L) return "$bytes Б"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return if (kb < 10) "%.1f КБ".format(kb) else "%.0f КБ".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return if (mb < 10) "%.1f МБ".format(mb) else "%.0f МБ".format(mb)
    val gb = mb / 1024.0
    return if (gb < 10) "%.1f ГБ".format(gb) else "%.0f ГБ".format(gb)
}
