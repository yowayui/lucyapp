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
                                input.copyTo(output, bufferSize = 256 * 1024)
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

    // ponytail: LCG deterministic daily shuffle matching web (dataset.ts); rotate daily content automatically.
    private fun getDailySeed(): Long {
        val cal = java.util.Calendar.getInstance()
        return (cal.get(java.util.Calendar.YEAR) * 10000 +
                (cal.get(java.util.Calendar.MONTH) + 1) * 100 +
                cal.get(java.util.Calendar.DAY_OF_MONTH)).toLong()
    }

    fun getDailyFeatured(limit: Int = 6, includeNsfw: Boolean = false): List<MangaItem> {
        val nsfwFilter = if (!includeNsfw) "AND is_nsfw = 0 AND type != 'Doujinshi'" else ""
        val sql = "SELECT * FROM catalog WHERE score >= 8.0 AND thumb IS NOT NULL AND thumb != '' AND description IS NOT NULL AND description != '' $nsfwFilter ORDER BY score DESC LIMIT 80"
        val pool = mutableListOf<MangaItem>()
        db.rawQuery(sql, emptyArray()).use { cursor ->
            while (cursor.moveToNext()) {
                pool.add(mapRow(cursor))
            }
        }
        if (pool.size <= limit) return pool

        var seed = getDailySeed() + 777
        val shuffled = pool.toMutableList()
        for (i in shuffled.indices.reversed()) {
            seed = (seed * 9301 + 49297) % 233280
            val rand = seed / 233280.0
            val j = (rand * (i + 1)).toInt().coerceIn(0, i)
            val temp = shuffled[i]
            shuffled[i] = shuffled[j]
            shuffled[j] = temp
        }
        return shuffled.take(limit)
    }

    fun getDailyPopular(limit: Int = 25, type: String? = null, includeNsfw: Boolean = false): List<MangaItem> {
        val poolLimit = 150
        val typeFilter = if (!type.isNullOrBlank() && !type.equals("All", ignoreCase = true)) "AND type = ? COLLATE NOCASE" else ""
        val nsfwFilter = if (!includeNsfw) "AND is_nsfw = 0 AND type != 'Doujinshi'" else ""
        val sql = "SELECT * FROM catalog WHERE thumb IS NOT NULL AND thumb != '' $typeFilter $nsfwFilter ORDER BY score DESC LIMIT $poolLimit"
        val args = if (!type.isNullOrBlank() && !type.equals("All", ignoreCase = true)) arrayOf(type) else emptyArray()

        val pool = mutableListOf<MangaItem>()
        db.rawQuery(sql, args).use { cursor ->
            while (cursor.moveToNext()) {
                pool.add(mapRow(cursor))
            }
        }
        if (pool.size <= limit) return pool

        var seed = getDailySeed()
        val shuffled = pool.toMutableList()
        for (i in shuffled.indices.reversed()) {
            seed = (seed * 9301 + 49297) % 233280
            val rand = seed / 233280.0
            val j = (rand * (i + 1)).toInt().coerceIn(0, i)
            val temp = shuffled[i]
            shuffled[i] = shuffled[j]
            shuffled[j] = temp
        }
        return shuffled.take(limit)
    }

    fun getDailyLatest(limit: Int = 40, type: String? = null, includeNsfw: Boolean = false): List<MangaItem> {
        val poolLimit = 250
        val typeFilter = if (!type.isNullOrBlank() && !type.equals("All", ignoreCase = true)) "AND type = ? COLLATE NOCASE" else ""
        val nsfwFilter = if (!includeNsfw) "AND is_nsfw = 0 AND type != 'Doujinshi'" else ""
        val sql = "SELECT * FROM catalog WHERE thumb IS NOT NULL AND thumb != '' $typeFilter $nsfwFilter ORDER BY rowid DESC LIMIT $poolLimit"
        val args = if (!type.isNullOrBlank() && !type.equals("All", ignoreCase = true)) arrayOf(type) else emptyArray()

        val pool = mutableListOf<MangaItem>()
        db.rawQuery(sql, args).use { cursor ->
            while (cursor.moveToNext()) {
                pool.add(mapRow(cursor))
            }
        }
        if (pool.size <= limit) return pool

        val daySeed = getDailySeed()
        val maxOffset = pool.size - limit
        val offset = ((daySeed * 23) % maxOffset).toInt().coerceIn(0, maxOffset)
        return pool.subList(offset, offset + limit)
    }

    fun getBrowse(
        type: String? = null,
        genre: String? = null,
        language: String? = null,
        limit: Int = 60,
        offset: Int = 0,
        includeNsfw: Boolean = false
    ): List<MangaItem> {
        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()

        conditions.add("thumb IS NOT NULL AND thumb != ''")

        if (!type.isNullOrBlank() && !type.equals("All", ignoreCase = true)) {
            conditions.add("type = ? COLLATE NOCASE")
            args.add(type)
        }

        if (!genre.isNullOrBlank() && !genre.equals("All", ignoreCase = true)) {
            conditions.add("genres LIKE ?")
            args.add("%$genre%")
        }

        // ponytail: language filter via source_group suffix convention.
        // English sources have no suffix; Indonesian sources end with "-ID".
        // Upgrade path: add a dedicated `language` column when more locales are scraped.
        if (!language.isNullOrBlank() && !language.equals("All", ignoreCase = true)) {
            when (language.uppercase()) {
                "ID" -> conditions.add("source_group LIKE '%-ID'")
                "EN" -> conditions.add("source_group NOT LIKE '%-ID'")
            }
        }

        if (!includeNsfw) {
            conditions.add("is_nsfw = 0 AND type != 'Doujinshi'")
        }

        val whereClause = "WHERE " + conditions.joinToString(" AND ")
        val sql = "SELECT * FROM catalog $whereClause ORDER BY score DESC LIMIT ? OFFSET ?"
        args.add(limit.toString())
        args.add(offset.toString())

        val list = mutableListOf<MangaItem>()
        db.rawQuery(sql, args.toTypedArray()).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(mapRow(cursor))
            }
        }
        return list
    }

    fun getPopular(limit: Int = 40, offset: Int = 0, includeNsfw: Boolean = false): List<MangaItem> {
        val nsfwClause = if (!includeNsfw) "AND is_nsfw = 0 AND type != 'Doujinshi'" else ""
        val sql = "SELECT * FROM catalog WHERE thumb IS NOT NULL AND thumb != '' $nsfwClause ORDER BY score DESC LIMIT ? OFFSET ?"
        val list = mutableListOf<MangaItem>()
        db.rawQuery(sql, arrayOf(limit.toString(), offset.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(mapRow(cursor))
            }
        }
        return list
    }

    fun getLatest(limit: Int = 40, offset: Int = 0, includeNsfw: Boolean = false): List<MangaItem> {
        val nsfwClause = if (!includeNsfw) "AND is_nsfw = 0 AND type != 'Doujinshi'" else ""
        val sql = "SELECT * FROM catalog WHERE thumb IS NOT NULL AND thumb != '' $nsfwClause ORDER BY rowid DESC LIMIT ? OFFSET ?"
        val list = mutableListOf<MangaItem>()
        db.rawQuery(sql, arrayOf(limit.toString(), offset.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(mapRow(cursor))
            }
        }
        return list
    }

    fun getByType(type: String, limit: Int = 40, offset: Int = 0, includeNsfw: Boolean = false): List<MangaItem> {
        val nsfwClause = if (!includeNsfw && !type.equals("Doujinshi", ignoreCase = true)) "AND is_nsfw = 0" else ""
        val sql = "SELECT * FROM catalog WHERE type = ? COLLATE NOCASE AND thumb IS NOT NULL AND thumb != '' $nsfwClause ORDER BY score DESC LIMIT ? OFFSET ?"
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
        val sql = "SELECT * FROM catalog WHERE genres LIKE ? AND thumb IS NOT NULL AND thumb != '' $nsfwClause ORDER BY score DESC LIMIT ? OFFSET ?"
        val list = mutableListOf<MangaItem>()
        db.rawQuery(sql, arrayOf("%$genre%", limit.toString(), offset.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(mapRow(cursor))
            }
        }
        return list
    }

    fun search(query: String, limit: Int = 50, includeNsfw: Boolean = true): List<MangaItem> {
        val cleanQ = query.trim().replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
        val tokens = cleanQ.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()

        // Match prefix query: e.g. "chainsaw* man*"
        val ftsMatch = tokens.joinToString(" ") { "$it*" }
        val nsfwClause = if (!includeNsfw) "AND c.is_nsfw = 0" else ""

        val sql = """
            SELECT c.* FROM catalog c
            JOIN catalog_fts fts ON c.slug = fts.slug
            WHERE catalog_fts MATCH ? $nsfwClause
            ORDER BY (c.thumb IS NOT NULL AND c.thumb != '') DESC, c.score DESC
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
            val fallbackSql = "SELECT * FROM catalog WHERE (title LIKE ? OR english_name LIKE ? OR native_name LIKE ?) $nsfwClause ORDER BY (thumb IS NOT NULL AND thumb != '') DESC, score DESC LIMIT ?"
            db.rawQuery(fallbackSql, arrayOf("%$query%", "%$query%", "%$query%", limit.toString())).use { cursor ->
                while (cursor.moveToNext()) {
                    list.add(mapRow(cursor))
                }
            }
        }
        return list.distinctBy { it.slug }
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
