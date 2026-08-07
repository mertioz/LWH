package com.local.webcaster.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.sqlite.transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HistoryEntry(
    val url: String,
    val title: String,
    val domain: String,
    val lastVisited: Long,
    val visitCount: Int,
)

data class Bookmark(
    val url: String,
    val title: String,
    val domain: String,
    val createdAt: Long,
)

class BrowserDataRepository(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val database = BrowserDatabase(context.applicationContext)
    private val databaseDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    private val _frequent = MutableStateFlow<List<HistoryEntry>>(emptyList())
    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())

    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()
    val frequent: StateFlow<List<HistoryEntry>> = _frequent.asStateFlow()
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    init {
        refresh()
    }

    fun recordVisit(rawUrl: String, rawTitle: String?) = scope.launch(databaseDispatcher) {
        val url = BrowserRecordPolicy.normalizeUrl(rawUrl) ?: return@launch
        val domain = BrowserRecordPolicy.domain(url)
        val title = BrowserRecordPolicy.title(rawTitle, domain)
        val db = database.writableDatabase
        db.transaction {
            val existingCount = db.rawQuery(
                "SELECT visit_count FROM history WHERE url = ?",
                arrayOf(url),
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
            val values = ContentValues().apply {
                put("url", url)
                put("title", title)
                put("domain", domain)
                put("last_visited", System.currentTimeMillis())
                put("visit_count", (existingCount + 1).coerceAtMost(Int.MAX_VALUE))
            }
            db.insertWithOnConflict("history", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            db.execSQL(
                "DELETE FROM history WHERE url IN " +
                    "(SELECT url FROM history ORDER BY last_visited DESC LIMIT -1 OFFSET $MAX_HISTORY)"
            )
        }
        loadAll()
    }

    fun updateHistoryTitle(rawUrl: String, rawTitle: String?) = scope.launch(databaseDispatcher) {
        val url = BrowserRecordPolicy.normalizeUrl(rawUrl) ?: return@launch
        val title = BrowserRecordPolicy.title(rawTitle, BrowserRecordPolicy.domain(url))
        database.writableDatabase.update(
            "history",
            ContentValues().apply { put("title", title) },
            "url = ?",
            arrayOf(url),
        )
        loadAll()
    }

    fun deleteHistory(url: String) = scope.launch(databaseDispatcher) {
        database.writableDatabase.delete("history", "url = ?", arrayOf(url))
        loadAll()
    }

    fun clearHistory() = scope.launch(databaseDispatcher) {
        database.writableDatabase.delete("history", null, null)
        loadAll()
    }

    fun addBookmark(rawUrl: String, rawTitle: String?) = scope.launch(databaseDispatcher) {
        val url = BrowserRecordPolicy.normalizeUrl(rawUrl) ?: return@launch
        val domain = BrowserRecordPolicy.domain(url)
        val values = ContentValues().apply {
            put("url", url)
            put("title", BrowserRecordPolicy.title(rawTitle, domain))
            put("domain", domain)
            put("created_at", System.currentTimeMillis())
        }
        database.writableDatabase.insertWithOnConflict(
            "bookmarks", null, values, SQLiteDatabase.CONFLICT_IGNORE
        )
        loadAll()
    }

    fun renameBookmark(url: String, rawTitle: String) = scope.launch(databaseDispatcher) {
        val normalized = BrowserRecordPolicy.normalizeUrl(url) ?: return@launch
        val title = BrowserRecordPolicy.title(rawTitle, BrowserRecordPolicy.domain(normalized))
        database.writableDatabase.update(
            "bookmarks",
            ContentValues().apply { put("title", title) },
            "url = ?",
            arrayOf(normalized),
        )
        loadAll()
    }

    fun removeBookmark(url: String) = scope.launch(databaseDispatcher) {
        database.writableDatabase.delete("bookmarks", "url = ?", arrayOf(url))
        loadAll()
    }

    fun close() = database.close()

    private fun refresh() = scope.launch(databaseDispatcher) { loadAll() }

    private suspend fun loadAll() {
        val db = database.readableDatabase
        val history = db.rawQuery(
            "SELECT url, title, domain, last_visited, visit_count FROM history " +
                "ORDER BY last_visited DESC LIMIT $MAX_HISTORY",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        HistoryEntry(
                            url = cursor.getString(0),
                            title = cursor.getString(1),
                            domain = cursor.getString(2),
                            lastVisited = cursor.getLong(3),
                            visitCount = cursor.getInt(4),
                        )
                    )
                }
            }
        }
        val bookmarks = db.rawQuery(
            "SELECT url, title, domain, created_at FROM bookmarks ORDER BY created_at DESC",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        Bookmark(
                            url = cursor.getString(0),
                            title = cursor.getString(1),
                            domain = cursor.getString(2),
                            createdAt = cursor.getLong(3),
                        )
                    )
                }
            }
        }
        withContext(Dispatchers.Main.immediate) {
            _history.value = history
            _frequent.value = history
                .asSequence()
                .filter { it.visitCount > 1 }
                .sortedWith(compareByDescending<HistoryEntry> { it.visitCount }.thenByDescending { it.lastVisited })
                .take(MAX_FREQUENT)
                .toList()
            _bookmarks.value = bookmarks
        }
    }

    private class BrowserDatabase(context: Context) :
        SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE history (" +
                    "url TEXT PRIMARY KEY NOT NULL, " +
                    "title TEXT NOT NULL, " +
                    "domain TEXT NOT NULL, " +
                    "last_visited INTEGER NOT NULL, " +
                    "visit_count INTEGER NOT NULL DEFAULT 1)"
            )
            db.execSQL(
                "CREATE TABLE bookmarks (" +
                    "url TEXT PRIMARY KEY NOT NULL, " +
                    "title TEXT NOT NULL, " +
                    "domain TEXT NOT NULL, " +
                    "created_at INTEGER NOT NULL)"
            )
            db.execSQL("CREATE INDEX history_last_visited ON history(last_visited DESC)")
            db.execSQL("CREATE INDEX history_visit_count ON history(visit_count DESC)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private companion object {
        const val DATABASE_NAME = "browser_data.db"
        const val DATABASE_VERSION = 1
        const val MAX_HISTORY = 500
        const val MAX_FREQUENT = 8
    }
}
