package com.artt.minibrowser.data

import com.artt.minibrowser.net.sanitizeWebUriForPersistence
import java.net.URI
import kotlinx.coroutines.CancellationException

internal fun bookmarkForPersistence(url: String, title: String, position: Int): Bookmark? {
    val safeUrl = sanitizeWebUriForPersistence(url) ?: return null
    val host = runCatching { URI(safeUrl).host.orEmpty() }.getOrDefault("")
    val safeTitle = when {
        title.isBlank() -> host
        title == url -> safeUrl
        else -> title
    }
    return Bookmark(safeUrl, safeTitle, host, position)
}

private fun sanitizedBookmark(entry: Bookmark): Bookmark? {
    val safe = bookmarkForPersistence(entry.url, entry.title, entry.position) ?: return null
    return if (safe == entry) entry else safe
}

/** Drops malformed rows and strips HTTP user-info before stored bookmarks reach browser UI. */
internal fun webBookmarks(entries: List<Bookmark>): List<Bookmark> {
    val canonicalUrls = HashSet<String>()
    entries.forEach { entry ->
        val safe = sanitizedBookmark(entry)
        if (safe === entry) canonicalUrls += entry.url
    }

    var result: ArrayList<Bookmark>? = null
    val seenUrls = HashSet<String>()
    for (index in entries.indices) {
        val entry = entries[index]
        val safe = sanitizedBookmark(entry)
        val shadowedByCanonical = safe != null && safe !== entry && safe.url in canonicalUrls
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
        sanitizeWebUriForPersistence(url)?.let { dao.renameBookmark(it, title) }
    }

    suspend fun isBookmarked(url: String): Boolean {
        val safeUrl = sanitizeWebUriForPersistence(url) ?: return false
        return dao.bookmarkCount(safeUrl) > 0
    }

    /**
     * Legacy builds could persist valid HTTP(S) URLs containing user-info credentials. Publish only
     * sanitized copies immediately, then converge storage in the background path used to load the
     * bookmark screen. Upsert the safe key before deleting the old key so process death cannot lose
     * a bookmark; if the safe key already exists, simply discard the credential-bearing duplicate.
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
            }
        }
    }
}
