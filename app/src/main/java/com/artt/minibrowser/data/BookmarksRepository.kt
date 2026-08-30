package com.artt.minibrowser.data

import com.artt.minibrowser.net.sanitizeWebUriForPersistence
import com.artt.minibrowser.net.webUriHost
import kotlinx.coroutines.CancellationException

internal fun bookmarkTitleForPersistence(title: String): String =
    sanitizeWebUriForPersistence(title) ?: title

internal fun bookmarkForPersistence(url: String, title: String, position: Int): Bookmark? {
    val safeUrl = sanitizeWebUriForPersistence(url) ?: return null
    val host = webUriHost(safeUrl).orEmpty()
    val safeTitle = bookmarkTitleForPersistence(title).ifBlank { host }
    return Bookmark(safeUrl, safeTitle, host, position)
}

private fun sanitizedBookmark(entry: Bookmark): Bookmark? {
    val safeUrl = sanitizeWebUriForPersistence(entry.url) ?: return null
    val safeTitle = bookmarkTitleForPersistence(entry.title)
    val safeHost = webUriHost(safeUrl).orEmpty()
    if (safeUrl == entry.url && safeTitle == entry.title && safeHost == entry.host) return entry
    return entry.copy(
        url = safeUrl,
        title = safeTitle,
        host = safeHost,
    )
}

/** Drops malformed rows and normalizes URL, title and host before bookmarks reach browser UI. */
internal fun webBookmarks(entries: List<Bookmark>): List<Bookmark> {
    val canonicalUrls = HashSet<String>()
    entries.forEach { entry ->
        val safe = sanitizedBookmark(entry)
        // An already-safe primary-key URL is canonical even when its title/host metadata needs
        // cleanup. Credential-bearing aliases must never displace that real stored bookmark merely
        // because the canonical row came from an older build with stale metadata.
        if (safe != null && safe.url == entry.url) canonicalUrls += entry.url
    }

    var result: ArrayList<Bookmark>? = null
    val seenUrls = HashSet<String>()
    for (index in entries.indices) {
        val entry = entries[index]
        val safe = sanitizedBookmark(entry)
        val shadowedByCanonical = safe != null && safe.url != entry.url && safe.url in canonicalUrls
        val keep = safe != null && !shadowedByCanonical && seenUrls.add(safe.url)
        if (result == null && safe === entry && keep) continue

        if (result == null) {
            result = ArrayList(entries.size)
            for (retainedIndex in 0 until index) result.add(entries[retainedIndex])
        }
        if (keep) result.add(requireNotNull(safe))
    }
    return result ?: entries
}

class BookmarksRepository(private val dao: AppDao) {
    suspend fun all(): List<Bookmark> {
        val stored = dao.bookmarks()
        val safe = webBookmarks(stored)
        if (safe !== stored) {
            try {
                migrateLegacyRows(stored)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // The sanitized read result is already safe to display. Keep the screen usable and
                // retry storage cleanup on the next load instead of surfacing a migration-only error.
            }
        }
        return safe
    }

    suspend fun add(url: String, title: String) {
        val bookmark = bookmarkForPersistence(url, title, position = 0) ?: return
        val nextPosition = dao.maxBookmarkPosition() + 1
        dao.upsertBookmark(bookmark.copy(position = nextPosition))
    }

    suspend fun remove(url: String) {
        sanitizeWebUriForPersistence(url)?.let { dao.deleteBookmark(it) }
    }

    suspend fun clearAll() = dao.clearBookmarks()

    suspend fun rename(url: String, title: String) {
        sanitizeWebUriForPersistence(url)?.let { safeUrl ->
            dao.renameBookmark(safeUrl, bookmarkTitleForPersistence(title))
        }
    }

    suspend fun isBookmarked(url: String): Boolean {
        val safeUrl = sanitizeWebUriForPersistence(url) ?: return false
        return dao.bookmarkCount(safeUrl) > 0
    }

    /**
     * Legacy builds could persist malformed host metadata or valid HTTP(S) URLs/titles containing
     * user-info credentials. Publish only normalized copies immediately, then converge storage in
     * the background path used to load the bookmark screen. Upsert the safe key before deleting the
     * old key so process death cannot lose a bookmark; if the safe key already exists, simply
     * discard the credential-bearing duplicate.
     */
    private suspend fun migrateLegacyRows(stored: List<Bookmark>) {
        stored.forEach { entry ->
            val safe = sanitizedBookmark(entry)
            when {
                safe == null -> dao.deleteBookmark(entry.url)
                safe.url != entry.url -> {
                    if (dao.bookmarkCount(safe.url) == 0) dao.upsertBookmark(safe)
                    dao.deleteBookmark(entry.url)
                }
                safe != entry -> dao.upsertBookmark(safe)
            }
        }
    }
}
