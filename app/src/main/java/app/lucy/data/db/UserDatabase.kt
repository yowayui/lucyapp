package app.lucy.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.lucy.data.model.BookmarkItem
import app.lucy.data.model.HistoryItem

class UserDatabase(context: Context) : SQLiteOpenHelper(context, "user_data.db", null, 1) {

    companion object {
        @Volatile
        private var instance: UserDatabase? = null

        fun getInstance(context: Context): UserDatabase {
            return instance ?: synchronized(this) {
                instance ?: UserDatabase(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE bookmarks (
                slug TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                thumb TEXT,
                last_chapter TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            );
        """)

        db.execSQL("""
            CREATE TABLE history (
                slug TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                thumb TEXT,
                chapter_title TEXT NOT NULL,
                chapter_slug TEXT NOT NULL,
                page_index INTEGER NOT NULL,
                total_pages INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            );
        """)

        db.execSQL("""
            CREATE TABLE settings (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            );
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1
    }

    // --- Bookmarks ---
    fun isBookmarked(slug: String): Boolean {
        readableDatabase.rawQuery("SELECT 1 FROM bookmarks WHERE slug = ? LIMIT 1", arrayOf(slug)).use { cursor ->
            return cursor.moveToNext()
        }
    }

    fun toggleBookmark(slug: String, title: String, thumb: String?, chapter: String): Boolean {
        if (isBookmarked(slug)) {
            writableDatabase.delete("bookmarks", "slug = ?", arrayOf(slug))
            return false
        } else {
            val cv = ContentValues().apply {
                put("slug", slug)
                put("title", title)
                put("thumb", thumb)
                put("last_chapter", chapter)
                put("updated_at", System.currentTimeMillis())
            }
            writableDatabase.insertWithOnConflict("bookmarks", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            return true
        }
    }

    fun getBookmarks(): List<BookmarkItem> {
        val list = mutableListOf<BookmarkItem>()
        readableDatabase.rawQuery("SELECT * FROM bookmarks ORDER BY updated_at DESC", null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    BookmarkItem(
                        slug = cursor.getString(cursor.getColumnIndexOrThrow("slug")),
                        title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                        thumb = cursor.getString(cursor.getColumnIndexOrThrow("thumb")),
                        lastChapter = cursor.getString(cursor.getColumnIndexOrThrow("last_chapter")),
                        updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"))
                    )
                )
            }
        }
        return list
    }

    // --- History ---
    fun saveHistory(
        slug: String,
        title: String,
        thumb: String?,
        chapterTitle: String,
        chapterSlug: String,
        pageIndex: Int,
        totalPages: Int
    ) {
        val cv = ContentValues().apply {
            put("slug", slug)
            put("title", title)
            put("thumb", thumb)
            put("chapter_title", chapterTitle)
            put("chapter_slug", chapterSlug)
            put("page_index", pageIndex)
            put("total_pages", totalPages)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("history", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getHistory(): List<HistoryItem> {
        val list = mutableListOf<HistoryItem>()
        readableDatabase.rawQuery("SELECT * FROM history ORDER BY updated_at DESC LIMIT 100", null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    HistoryItem(
                        slug = cursor.getString(cursor.getColumnIndexOrThrow("slug")),
                        title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                        thumb = cursor.getString(cursor.getColumnIndexOrThrow("thumb")),
                        chapterTitle = cursor.getString(cursor.getColumnIndexOrThrow("chapter_title")),
                        chapterSlug = cursor.getString(cursor.getColumnIndexOrThrow("chapter_slug")),
                        pageIndex = cursor.getInt(cursor.getColumnIndexOrThrow("page_index")),
                        totalPages = cursor.getInt(cursor.getColumnIndexOrThrow("total_pages")),
                        updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"))
                    )
                )
            }
        }
        return list
    }

    fun clearHistory() {
        writableDatabase.delete("history", null, null)
    }

    // --- Settings ---
    fun getSetting(key: String, default: String): String {
        readableDatabase.rawQuery("SELECT value FROM settings WHERE key = ? LIMIT 1", arrayOf(key)).use { cursor ->
            if (cursor.moveToNext()) {
                return cursor.getString(0)
            }
        }
        return default
    }

    fun setSetting(key: String, value: String) {
        val cv = ContentValues().apply {
            put("key", key)
            put("value", value)
        }
        writableDatabase.insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }
}
