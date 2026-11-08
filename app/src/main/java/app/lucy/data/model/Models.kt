package app.lucy.data.model

data class MangaItem(
    val slug: String,
    val title: String,
    val englishName: String?,
    val nativeName: String?,
    val type: String,
    val country: String,
    val thumb: String?,
    val score: Float?,
    val status: String,
    val description: String?,
    val authors: String?,
    val genres: String?,
    val chapterCount: Int,
    val latestChapter: String,
    val sourceGroup: String,
    val isNsfw: Boolean
)

data class Chapter(
    val title: String,
    val slug: String,
    val number: String
)

data class BookmarkItem(
    val slug: String,
    val title: String,
    val thumb: String?,
    val lastChapter: String,
    val updatedAt: Long
)

data class HistoryItem(
    val slug: String,
    val title: String,
    val thumb: String?,
    val chapterTitle: String,
    val chapterSlug: String,
    val pageIndex: Int,
    val totalPages: Int,
    val updatedAt: Long
)
