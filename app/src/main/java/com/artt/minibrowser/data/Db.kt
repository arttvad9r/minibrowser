package com.artt.minibrowser.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

data class Scored(val url: String, val title: String, val visitedAt: Long)
data class Suggestion(val label: String, val url: String)

fun rankSuggestions(history: List<Scored>, q: String): List<Suggestion> {
    val needle = q.trim()
    return history
        .filter {
            needle.isEmpty() || it.url.contains(needle, true) || it.title.contains(needle, true)
        }
        .sortedByDescending { it.visitedAt }
        .take(8)
        .map { Suggestion(it.title.ifEmpty { it.url }, it.url) }
        .distinctBy { it.url }
}

@Entity(
    tableName = "history",
    indices = [Index(value = ["visitedAt"])],
)
data class HistoryEntry(
    @PrimaryKey val url: String, val title: String, val visitedAt: Long, val visits: Int,
)

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey val url: String, val title: String, val host: String, val position: Int,
)

@Dao interface AppDao {
    // --- history ---
    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT :limit")
    suspend fun recentHistory(limit: Int): List<HistoryEntry>

    @Query("SELECT * FROM history WHERE url LIKE '%' || :q || '%' OR title LIKE '%' || :q || '%' ORDER BY visitedAt DESC LIMIT 50")
    suspend fun searchHistory(q: String): List<HistoryEntry>

    @Query("SELECT * FROM history WHERE url = :url")
    suspend fun historyByUrl(url: String): HistoryEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(e: HistoryEntry)

    @Transaction
    suspend fun recordVisit(url: String, title: String?, now: Long) {
        val previous = historyByUrl(url)
        upsertHistory(HistoryEntry(url, title ?: previous?.title ?: url, now, (previous?.visits ?: 0) + 1))
    }

    // A title callback is metadata for a visit that was already recorded. Never create a new
    // history row here: doing so could resurrect an entry after the user clears browsing history.
    @Query("UPDATE history SET title = :title WHERE url = :url")
    suspend fun updateHistoryTitle(url: String, title: String): Int

    @Query("DELETE FROM history")
    suspend fun clearHistory()

    // --- bookmarks ---
    @Query("SELECT * FROM bookmarks ORDER BY position")
    suspend fun bookmarks(): List<Bookmark>

    @Query("SELECT * FROM bookmarks WHERE url LIKE '%' || :q || '%' OR title LIKE '%' || :q || '%' ORDER BY position LIMIT 50")
    suspend fun bookmarksMatching(q: String): List<Bookmark>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookmark(b: Bookmark)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteBookmark(url: String)

    @Query("DELETE FROM bookmarks")
    suspend fun clearBookmarks()

    @Query("UPDATE bookmarks SET title = :title WHERE url = :url")
    suspend fun renameBookmark(url: String, title: String)

    @Query("SELECT COUNT(*) FROM bookmarks WHERE url = :url")
    suspend fun bookmarkCount(url: String): Int
}

@Database(entities = [HistoryEntry::class, Bookmark::class], version = 3, exportSchema = false)
abstract class AppDb : RoomDatabase() { abstract fun dao(): AppDao }

// Application-scope: HistorySink вызывается из TabManager без жизненного цикла,
// поэтому скоуп живёт здесь (переживает активити, гаснет только с процессом).
object DbHolder {
    internal val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS index_history_visitedAt ON history(visitedAt)")
        }
    }
    internal val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS index_history_visitedAt ON history(visitedAt)")
        }
    }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var db: AppDb
        private set

    fun init(context: Context) {
        if (!::db.isInitialized) {
            db = Room.databaseBuilder(context, AppDb::class.java, "minibrowser.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
        }
    }
}
