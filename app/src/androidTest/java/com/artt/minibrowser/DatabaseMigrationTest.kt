package com.artt.minibrowser

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artt.minibrowser.data.AppDb
import com.artt.minibrowser.data.DbHolder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    @Test fun migratesVersionOneToThree() = runBlocking {
        migrateAndAssert(version = 1, withIndex = false)
    }

    @Test fun migratesVersionTwoWithoutIndexToThree() = runBlocking {
        migrateAndAssert(version = 2, withIndex = false)
    }

    @Test fun migratesVersionTwoWithIndexToThree() = runBlocking {
        migrateAndAssert(version = 2, withIndex = true)
    }

    private suspend fun migrateAndAssert(version: Int, withIndex: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-${System.nanoTime()}.db"
        val old = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
        old.execSQL("CREATE TABLE history (url TEXT NOT NULL, title TEXT NOT NULL, visitedAt INTEGER NOT NULL, visits INTEGER NOT NULL, PRIMARY KEY(url))")
        old.execSQL("CREATE TABLE bookmarks (url TEXT NOT NULL, title TEXT NOT NULL, host TEXT NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(url))")
        if (withIndex) old.execSQL("CREATE INDEX index_history_visitedAt ON history(visitedAt)")
        old.execSQL("INSERT INTO history(url, title, visitedAt, visits) VALUES ('https://example.com', 'Example', 123, 4)")
        old.execSQL("INSERT INTO bookmarks(url, title, host, position) VALUES ('https://example.com', 'Example bookmark', 'example.com', 2)")
        old.execSQL("PRAGMA user_version = $version")
        old.close()

        val db = Room.databaseBuilder(context, AppDb::class.java, name)
            .addMigrations(DbHolder.MIGRATION_1_2, DbHolder.MIGRATION_2_3)
            .build()
        val history = db.dao().historyByUrl("https://example.com")
        val bookmark = db.dao().bookmarks().single()
        assertEquals("Example", history?.title)
        assertEquals(123L, history?.visitedAt)
        assertEquals(4, history?.visits)
        assertEquals("Example bookmark", bookmark.title)
        assertEquals(2, bookmark.position)
        assertEquals(3, db.openHelper.readableDatabase.query("PRAGMA user_version").use { it.moveToFirst(); it.getInt(0) })
        db.openHelper.readableDatabase.query("PRAGMA index_list('history')").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            var count = 0
            while (cursor.moveToNext()) if (cursor.getString(nameIndex) == "index_history_visitedAt") count++
            assertEquals(1, count)
        }
        db.close()
        context.deleteDatabase(name)
    }
}
