package com.artt.minibrowser.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

// ponytail: иконки через DDG-сервис (наружу уходит только домен);
// заменить на парсинг <link rel="icon"> страницы, если нужна автономность
object FaviconFetcher {
    fun cacheFile(host: String, iconsDir: File): File {
        val md5 = MessageDigest.getInstance("MD5").digest(host.lowercase().trim().toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(iconsDir, "$md5.png")
    }

    suspend fun fetch(host: String, iconsDir: File): File = withContext(Dispatchers.IO) {
        val dst = cacheFile(host, iconsDir)
        if (dst.exists()) return@withContext dst
        iconsDir.mkdirs()
        val conn = URL("https://icons.duckduckgo.com/ip3/${host.lowercase()}.ico").openConnection() as HttpURLConnection
        conn.connectTimeout = 5000; conn.readTimeout = 5000
        runCatching { conn.inputStream.use { input -> dst.outputStream().use { input.copyTo(it) } } }
            .onFailure { dst.delete() }
        dst
    }
}
