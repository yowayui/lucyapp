package app.lucy.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.lucy.data.model.BookmarkItem
import app.lucy.data.model.Chapter
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
            CREATE TABLE read_chapters (
                series_slug TEXT NOT NULL,
                chapter_slug TEXT NOT NULL,
                read_at INTEGER NOT NULL,
                PRIMARY KEY (series_slug, chapter_slug)
            );
        """)

        db.execSQL("""
            CREATE TABLE settings (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            );
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS extra_chapters (
                series_slug TEXT NOT NULL,
                slug TEXT NOT NULL,
                number TEXT NOT NULL,
                title TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                PRIMARY KEY (series_slug, slug)
            );
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS series_updates (
                series_slug TEXT PRIMARY KEY,
                last_checked INTEGER NOT NULL,
                last_new_count INTEGER NOT NULL DEFAULT 0
            );
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS read_chapters (
                series_slug TEXT NOT NULL,
                chapter_slug TEXT NOT NULL,
                read_at INTEGER NOT NULL,
                PRIMARY KEY (series_slug, chapter_slug)
            );
        """)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS extra_chapters (
                series_slug TEXT NOT NULL,
                slug TEXT NOT NULL,
                number TEXT NOT NULL,
                title TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                PRIMARY KEY (series_slug, slug)
            );
        """)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS series_updates (
                series_slug TEXT PRIMARY KEY,
                last_checked INTEGER NOT NULL,
                last_new_count INTEGER NOT NULL DEFAULT 0
            );
        """)
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

    fun deleteHistory(slug: String) {
        writableDatabase.delete("history", "slug = ?", arrayOf(slug))
    }

    fun clearReadChapters() {
        writableDatabase.delete("read_chapters", null, null)
    }

    // --- Read Chapters ---
    fun markChapterRead(seriesSlug: String, chapterSlug: String) {
        writableDatabase.execSQL(
            "CREATE TABLE IF NOT EXISTS read_chapters (series_slug TEXT NOT NULL, chapter_slug TEXT NOT NULL, read_at INTEGER NOT NULL, PRIMARY KEY (series_slug, chapter_slug));"
        )
        val cv = ContentValues().apply {
            put("series_slug", seriesSlug)
            put("chapter_slug", chapterSlug)
            put("read_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("read_chapters", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getReadChapters(seriesSlug: String): Set<String> {
        writableDatabase.execSQL(
            "CREATE TABLE IF NOT EXISTS read_chapters (series_slug TEXT NOT NULL, chapter_slug TEXT NOT NULL, read_at INTEGER NOT NULL, PRIMARY KEY (series_slug, chapter_slug));"
        )
        val set = mutableSetOf<String>()
        readableDatabase.rawQuery("SELECT chapter_slug FROM read_chapters WHERE series_slug = ?", arrayOf(seriesSlug)).use { cursor ->
            while (cursor.moveToNext()) {
                set.add(cursor.getString(0))
            }
        }
        return set
    }

    fun getLastRead(seriesSlug: String): HistoryItem? {
        readableDatabase.rawQuery("SELECT * FROM history WHERE slug = ? LIMIT 1", arrayOf(seriesSlug)).use { cursor ->
            if (cursor.moveToNext()) {
                return HistoryItem(
                    slug = cursor.getString(cursor.getColumnIndexOrThrow("slug")),
                    title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                    thumb = cursor.getString(cursor.getColumnIndexOrThrow("thumb")),
                    chapterTitle = cursor.getString(cursor.getColumnIndexOrThrow("chapter_title")),
                    chapterSlug = cursor.getString(cursor.getColumnIndexOrThrow("chapter_slug")),
                    pageIndex = cursor.getInt(cursor.getColumnIndexOrThrow("page_index")),
                    totalPages = cursor.getInt(cursor.getColumnIndexOrThrow("total_pages")),
                    updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"))
                )
            }
        }
        return null
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

    // --- Extra / Live Chapters ---
    fun cleanBogusExtraChapters(seriesSlug: String, maxValidChapterNumber: Float) {
        if (maxValidChapterNumber <= 0f) return
        writableDatabase.execSQL(
            "CREATE TABLE IF NOT EXISTS extra_chapters (series_slug TEXT NOT NULL, slug TEXT NOT NULL, number TEXT NOT NULL, title TEXT NOT NULL, created_at INTEGER NOT NULL, PRIMARY KEY (series_slug, slug));"
        )
        val maxAllowed = maxValidChapterNumber + 50f
        val toDelete = mutableListOf<String>()
        readableDatabase.rawQuery(
            "SELECT slug, number FROM extra_chapters WHERE series_slug = ?",
            arrayOf(seriesSlug)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val chSlug = cursor.getString(0)
                val num = cursor.getString(1).toFloatOrNull()
                if (num == null || num > maxAllowed) {
                    toDelete.add(chSlug)
                }
            }
        }
        if (toDelete.isNotEmpty()) {
            writableDatabase.beginTransaction()
            try {
                for (delSlug in toDelete) {
                    writableDatabase.delete("extra_chapters", "series_slug = ? AND slug = ?", arrayOf(seriesSlug, delSlug))
                }
                writableDatabase.setTransactionSuccessful()
            } finally {
                writableDatabase.endTransaction()
            }
        }
    }

    fun replaceExtraChapters(seriesSlug: String, chapters: List<Chapter>) {
        writableDatabase.execSQL(
            "CREATE TABLE IF NOT EXISTS extra_chapters (series_slug TEXT NOT NULL, slug TEXT NOT NULL, number TEXT NOT NULL, title TEXT NOT NULL, created_at INTEGER NOT NULL, PRIMARY KEY (series_slug, slug));"
        )
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("extra_chapters", "series_slug = ?", arrayOf(seriesSlug))
            val now = System.currentTimeMillis()
            for (ch in chapters) {
                val cv = ContentValues().apply {
                    put("series_slug", seriesSlug)
                    put("slug", ch.slug)
                    put("number", ch.number)
                    put("title", ch.title)
                    put("created_at", now)
                }
                writableDatabase.insertWithOnConflict("extra_chapters", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun saveExtraChapters(seriesSlug: String, chapters: List<Chapter>) {
        if (chapters.isEmpty()) return
        writableDatabase.execSQL(
            "CREATE TABLE IF NOT EXISTS extra_chapters (series_slug TEXT NOT NULL, slug TEXT NOT NULL, number TEXT NOT NULL, title TEXT NOT NULL, created_at INTEGER NOT NULL, PRIMARY KEY (series_slug, slug));"
        )
        writableDatabase.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            for (ch in chapters) {
                val cv = ContentValues().apply {
                    put("series_slug", seriesSlug)
                    put("slug", ch.slug)
                    put("number", ch.number)
                    put("title", ch.title)
                    put("created_at", now)
                }
                writableDatabase.insertWithOnConflict("extra_chapters", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun getExtraChapters(seriesSlug: String): List<Chapter> {
        writableDatabase.execSQL(
            "CREATE TABLE IF NOT EXISTS extra_chapters (series_slug TEXT NOT NULL, slug TEXT NOT NULL, number TEXT NOT NULL, title TEXT NOT NULL, created_at INTEGER NOT NULL, PRIMARY KEY (series_slug, slug));"
        )
        val list = mutableListOf<Chapter>()
        readableDatabase.rawQuery(
            "SELECT slug, number, title FROM extra_chapters WHERE series_slug = ?",
            arrayOf(seriesSlug)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    Chapter(
                        slug = cursor.getString(0),
                        number = cursor.getString(1),
                        title = cursor.getString(2)
                    )
                )
            }
        }
        return list
    }

    // --- Series Auto Update Tracking ---
    fun getSeriesLastChecked(slug: String): Long {
        writableDatabase.execSQL(
            "CREATE TABLE IF NOT EXISTS series_updates (series_slug TEXT PRIMARY KEY, last_checked INTEGER NOT NULL, last_new_count INTEGER NOT NULL DEFAULT 0);"
        )
        readableDatabase.rawQuery("SELECT last_checked FROM series_updates WHERE series_slug = ? LIMIT 1", arrayOf(slug)).use { cursor ->
            if (cursor.moveToNext()) return cursor.getLong(0)
        }
        return 0L
    }

    fun markSeriesChecked(slug: String, newCount: Int) {
        writableDatabase.execSQL(
            "CREATE TABLE IF NOT EXISTS series_updates (series_slug TEXT PRIMARY KEY, last_checked INTEGER NOT NULL, last_new_count INTEGER NOT NULL DEFAULT 0);"
        )
        val cv = ContentValues().apply {
            put("series_slug", slug)
            put("last_checked", System.currentTimeMillis())
            put("last_new_count", newCount)
        }
        writableDatabase.insertWithOnConflict("series_updates", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getSeriesToCheck(limit: Int = 40): List<String> {
        writableDatabase.execSQL(
            "CREATE TABLE IF NOT EXISTS series_updates (series_slug TEXT PRIMARY KEY, last_checked INTEGER NOT NULL, last_new_count INTEGER NOT NULL DEFAULT 0);"
        )
        val sql = """
            SELECT s.slug FROM (
                SELECT slug, updated_at FROM bookmarks
                UNION
                SELECT slug, updated_at FROM history
            ) s
            LEFT JOIN series_updates u ON s.slug = u.series_slug
            ORDER BY COALESCE(u.last_checked, 0) ASC, s.updated_at DESC
            LIMIT ?
        """.trimIndent()

        val list = mutableListOf<String>()
        readableDatabase.rawQuery(sql, arrayOf(limit.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursor.getString(0))
            }
        }
        return list
    }
}
