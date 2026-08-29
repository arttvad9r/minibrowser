package com.artt.minibrowser

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artt.minibrowser.data.AppDb
import com.artt.minibrowser.data.Bookmark
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryDaoTest {
    @Test
    fun titleUpdateNeverCreatesMissingVisit() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDb::class.java).build()
        try {
            val dao = db.dao()
            val url = "https://example.com/page"

            assertEquals(0, dao.updateHistoryTitle(url, "Late title"))
            assertNull(dao.historyByUrl(url))

            dao.recordVisit(url, "Initial", 123L)
            assertEquals(1, dao.updateHistoryTitle(url, "Final title"))

            val entry = dao.historyByUrl(url)
            assertEquals("Final title", entry?.title)
            assertEquals(123L, entry?.visitedAt)
            assertEquals(1, entry?.visits)
        } finally {
            db.close()
        }
    }

    @Test
    fun bookmarkSuggestionsAreBoundedAndKeepPositionOrder() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDb::class.java).build()
        try {
            val dao = db.dao()
            repeat(12) { index ->
                val position = 11 - index
                dao.upsertBookmark(
                    Bookmark(
                        url = "https://match$index.example",
                        title = "match $index",
                        host = "match$index.example",
                        position = position,
                    ),
                )
            }

            val matches = dao.bookmarksMatching("match")
            assertEquals(8, matches.size)
            assertEquals((0..7).toList(), matches.map { it.position })
        } finally {
            db.close()
        }
    }
}
