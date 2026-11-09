package app.lucy.data.source

// MangaDex's uploads host now answers direct image requests with an
// anti-bot challenge, so the affected catalog entries fall back to covers
// from MangaUpdates or AniList instead of showing the letter placeholder.
object CoverResolver {

    private val overrides = mapOf(
        "yǒu-shòu-yān" to
            "https://cdn.mangaupdates.com/image/i326142.png",
        "jib-i-eobs-eo" to
            "https://cdn.mangaupdates.com/image/i235394.jpg",
        "hunter-x-hunter-official-colored" to
            "https://s4.anilist.co/file/anilistcdn/media/manga/cover/large/bx30026-uCvXMudMzmwI.jpg",
        "delicious-in-dungeon-world-guide-the-adventurers-bible" to
            "https://s4.anilist.co/file/anilistcdn/media/manga/cover/large/bx137907-sXmGOKbkvpVY.jpg",
        "the-girl-whos-bad-at-expressing-herself" to
            "https://s4.anilist.co/file/anilistcdn/media/manga/cover/large/bx217514-QMEsNeGvvI7b.jpg",
        "gacen-shoujo-to-ibunka-kouryuu" to
            "https://s4.anilist.co/file/anilistcdn/media/manga/cover/large/bx125580-Z1MUr7sNHYyd.jpg",
        "the-ghostly-doctor" to
            "https://s4.anilist.co/file/anilistcdn/media/manga/cover/large/bx103582-rRmcQMzSSiwn.png",
        "fujisan-daifunka-senryokugai-nintei-sareta-ossan-kaishain-mochizane-ga-tenpenchii-de-nariagari" to
            "https://s4.anilist.co/file/anilistcdn/media/manga/cover/large/bx210609-BHsP6kBQa29s.jpg",
        "nanado-nado-nado" to
            "https://cdn.mangaupdates.com/image/i343782.jpg"
    )

    fun resolve(slug: String?, thumb: String?): String? {
        if (thumb.isNullOrBlank()) return thumb
        if (!thumb.contains("uploads.mangadex.org")) return thumb
        return overrides[slug] ?: thumb
    }
}
