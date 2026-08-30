package com.artt.minibrowser

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artt.minibrowser.data.AppDb
import com.artt.minibrowser.data.Bookmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookmarkDaoTransactionTest {
    @Test
    fun concurrentAppendsAllocateUniqueOrderedPositions() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDb::class.java).build()
        try {
            val dao = db.dao()
            coroutineScope {
                (0 until 8).map { index ->
                    async(Dispatchers.Default) {
                        dao.appendBookmark(
                            Bookmark(
                                url = "https://bookmark$index.example",
                                title = "Bookmark $index",
                                host = "bookmark$index.example",
                                position = -1,
                            ),
                        )
                    }
                }.awaitAll()
            }

            val stored = dao.bookmarks()
            assertEquals(8, stored.size)
            assertEquals((0 until 8).toList(), stored.map { it.position })
        } finally {
            db.close()
        }
    }
}
