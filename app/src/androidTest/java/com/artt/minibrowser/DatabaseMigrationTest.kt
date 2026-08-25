package com.artt.minibrowser

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artt.minibrowser.data.AppDb
import com.artt.minibrowser.data.DbHolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    @Test
    fun migratesVersionOneWithoutLosingRowsOrIndex() {
        runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-${System.nanoTime()}.db"
        val old = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
        old.execSQL("CREATE TABLE history (url TEXT NOT NULL, title TEXT NOT NULL, visitedAt INTEGER NOT NULL, visits INTEGER NOT NULL, PRIMARY KEY(url))")
        old.execSQL("CREATE TABLE bookmarks (url TEXT NOT NULL, title TEXT NOT NULL, host TEXT NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(url))")
        old.execSQL("INSERT INTO history(url, title, visitedAt, visits) VALUES ('https://example.com', 'Example', 123, 4)")
        old.execSQL("INSERT INTO bookmarks(url, title, host, position) VALUES ('https://example.com', 'Example', 'example.com', 2)")
        old.execSQL("PRAGMA user_version = 1")
        old.close()

        val db = Room.databaseBuilder(context, AppDb::class.java, name)
            .addMigrations(DbHolder.MIGRATION_1_2)
            .build()
        val history = db.dao().historyByUrl("https://example.com")
        val bookmarks = db.dao().bookmarks()
        assertEquals("Example", history?.title)
        assertEquals(4, history?.visits)
        assertEquals(2, bookmarks.single().position)

        db.openHelper.readableDatabase.query("PRAGMA index_list('history')").use { cursor ->
            var found = false
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) found = found || cursor.getString(nameIndex) == "index_history_visitedAt"
            assertTrue(found)
        }
        db.close()
            context.deleteDatabase(name)
        }
    }
}
