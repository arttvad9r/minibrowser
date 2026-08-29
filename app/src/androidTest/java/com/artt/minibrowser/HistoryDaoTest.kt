package com.artt.minibrowser

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artt.minibrowser.data.AppDb
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
}
