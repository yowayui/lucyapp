package app.lucy.data.db

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import app.lucy.data.model.Chapter
import app.lucy.data.model.MangaItem
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream

class CatalogDatabase private constructor(private val db: SQLiteDatabase) {

    companion object {
        private const val DB_NAME = "lucy_catalog.db"
        @Volatile
        private var instance: CatalogDatabase? = null

        fun getInstance(context: Context): CatalogDatabase {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val dbFile = context.getDatabasePath(DB_NAME)
                    if (!dbFile.exists() || dbFile.length() < 1000) {
                        dbFile.parentFile?.mkdirs()
                        context.assets.open(DB_NAME).use { input ->
                            FileOutputStream(dbFile).use { output ->
                                input.copyTo(output, bufferSize = 64 * 1024)
                            }
                        }
                    }
                    val sqliteDb = SQLiteDatabase.openDatabase(
                        dbFile.path,
                        null,
                        SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
                    )
                    CatalogDatabase(sqliteDb).also { instance = it }
                }
            }
        }
    }

    private fun mapRow(cursor: Cursor): MangaItem {
        val slug = cursor.getString(cursor.getColumnIndexOrThrow("slug"))
        val title = cursor.getString(cursor.getColumnIndexOrThrow("title"))
        val eng = cursor.getString(cursor.getColumnIndexOrThrow("english_name"))
        val nat = cursor.getString(cursor.getColumnIndexOrThrow("native_name"))
        val type = cursor.getString(cursor.getColumnIndexOrThrow("type"))
        val country = cursor.getString(cursor.getColumnIndexOrThrow("country"))
        val thumb = cursor.getString(cursor.getColumnIndexOrThrow("thumb"))
        val score = if (cursor.isNull(cursor.getColumnIndexOrThrow("score"))) null else cursor.getFloat(cursor.getColumnIndexOrThrow("score"))
        val status = cursor.getString(cursor.getColumnIndexOrThrow("status"))
        val desc = cursor.getString(cursor.getColumnIndexOrThrow("description"))
        val authors = cursor.getString(cursor.getColumnIndexOrThrow("authors"))
        val genres = cursor.getString(cursor.getColumnIndexOrThrow("genres"))
        val chCount = cursor.getInt(cursor.getColumnIndexOrThrow("chapter_count"))
        val latestCh = cursor.getString(cursor.getColumnIndexOrThrow("latest_chapter")) ?: ""
        val group = cursor.getString(cursor.getColumnIndexOrThrow("source_group"))
        val isNsfw = cursor.getInt(cursor.getColumnIndexOrThrow("is_nsfw")) == 1

        return MangaItem(
            slug = slug,
            title = title,
            englishName = eng,
            nativeName = nat,
            type = type,
            country = country,
            thumb = thumb,
            score = score,
            status = status,
            description = desc,
            authors = authors,
            genres = genres,
            chapterCount = chCount,
            latestChapter = latestCh,
            sourceGroup = group,
            isNsfw = isNsfw
        )
    }

    fun getPopular(limit: Int = 40, offset: Int = 0, includeNsfw: Boolean = true): List<MangaItem> {
        val nsfwClause = if (!includeNsfw) "WHERE is_nsfw = 0" else ""
        val sql = "SELECT * FROM catalog $nsfwClause ORDER BY score DESC LIMIT ? OFFSET ?"
        val list = mutableListOf<MangaItem>()
        db.rawQuery(sql, arrayOf(limit.toString(), offset.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(mapRow(cursor))
            }
        }
        return list
    }

    fun getLatest(limit: Int = 40, offset: Int = 0, includeNsfw: Boolean = true): List<MangaItem> {
        val nsfwClause = if (!includeNsfw) "WHERE is_nsfw = 0" else ""
        val sql = "SELECT * FROM catalog $nsfwClause ORDER BY rowid DESC LIMIT ? OFFSET ?"
        val list = mutableListOf<MangaItem>()
        db.rawQuery(sql, arrayOf(limit.toString(), offset.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(mapRow(cursor))
            }
        }
        return list
    }

    fun getByType(type: String, limit: Int = 40, offset: Int = 0, includeNsfw: Boolean = true): List<MangaItem> {
        val nsfwClause = if (!includeNsfw) "AND is_nsfw = 0" else ""
        val sql = "SELECT * FROM catalog WHERE type = ? COLLATE NOCASE $nsfwClause ORDER BY score DESC LIMIT ? OFFSET ?"
        val list = mutableListOf<MangaItem>()
        db.rawQuery(sql, arrayOf(type, limit.toString(), offset.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(mapRow(cursor))
            }
        }
        return list
    }

    fun getByGenre(genre: String, limit: Int = 40, offset: Int = 0, includeNsfw: Boolean = true): List<MangaItem> {
        val nsfwClause = if (!includeNsfw) "AND is_nsfw = 0" else ""
        val sql = "SELECT * FROM catalog WHERE genres LIKE ? $nsfwClause ORDER BY score DESC LIMIT ? OFFSET ?"
        val list = mutableListOf<MangaItem>()
        db.rawQuery(sql, arrayOf("%$genre%", limit.toString(), offset.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(mapRow(cursor))
            }
        }
        return list
    }

    fun search(query: String, limit: Int = 50, includeNsfw: Boolean = true): List<MangaItem> {
        val cleanQ = query.trim().replace(Regex("[^a-zA-Z0-9\\s]"), " ")
        val tokens = cleanQ.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()

        // Match prefix query: e.g. "chainsaw* man*"
        val ftsMatch = tokens.joinToString(" ") { "$it*" }
        val nsfwClause = if (!includeNsfw) "AND c.is_nsfw = 0" else ""

        val sql = """
            SELECT c.* FROM catalog c
            JOIN catalog_fts fts ON c.slug = fts.slug
            WHERE catalog_fts MATCH ? $nsfwClause
            ORDER BY c.score DESC
            LIMIT ?
        """.trimIndent()

        val list = mutableListOf<MangaItem>()
        try {
            db.rawQuery(sql, arrayOf(ftsMatch, limit.toString())).use { cursor ->
                while (cursor.moveToNext()) {
                    list.add(mapRow(cursor))
                }
            }
        } catch (e: Exception) {
            // Fallback to simple LIKE query if FTS expression has syntax quirk
            val fallbackSql = "SELECT * FROM catalog WHERE (title LIKE ? OR english_name LIKE ?) $nsfwClause ORDER BY score DESC LIMIT ?"
            db.rawQuery(fallbackSql, arrayOf("%$query%", "%$query%", limit.toString())).use { cursor ->
                while (cursor.moveToNext()) {
                    list.add(mapRow(cursor))
                }
            }
        }
        return list
    }

    fun getBySlug(slug: String): MangaItem? {
        val sql = "SELECT * FROM catalog WHERE slug = ? LIMIT 1"
        db.rawQuery(sql, arrayOf(slug)).use { cursor ->
            if (cursor.moveToNext()) {
                return mapRow(cursor)
            }
        }
        return null
    }

    fun getChapters(slug: String): List<Chapter> {
        val sql = "SELECT chapters_blob, chapter_count, source_group FROM catalog WHERE slug = ? LIMIT 1"
        db.rawQuery(sql, arrayOf(slug)).use { cursor ->
            if (cursor.moveToNext()) {
                val blobIdx = cursor.getColumnIndexOrThrow("chapters_blob")
                val countIdx = cursor.getColumnIndexOrThrow("chapter_count")
                val groupIdx = cursor.getColumnIndexOrThrow("source_group")

                val group = cursor.getString(groupIdx)
                val count = cursor.getInt(countIdx)

                if (!cursor.isNull(blobIdx)) {
                    val bytes = cursor.getBlob(blobIdx)
                    try {
                        val uncompressed = GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader().use { it.readText() }
                        val rawList = uncompressed.split(",").filter { it.isNotBlank() }
                        return rawList.map { num ->
                            if (group == "Dojing" || group == "ShiroDoujin") {
                                Chapter(
                                    title = num.replace("-", " ").capitalizeWords(),
                                    slug = num,
                                    number = num
                                )
                            } else {
                                Chapter(
                                    title = "Chapter $num",
                                    slug = "$slug-chapter-$num",
                                    number = num
                                )
                            }
                        }
                    } catch (e: Exception) {
                        // ignore and fall back to sequential count
                    }
                }

                // Fallback: sequential chapters 1..count
                if (count > 0) {
                    return (1..count).map { i ->
                        Chapter(
                            title = "Chapter $i",
                            slug = "$slug-chapter-$i",
                            number = i.toString()
                        )
                    }
                }
            }
        }
        return emptyList()
    }

    private fun String.capitalizeWords(): String = split(" ").joinToString(" ") { word ->
        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
