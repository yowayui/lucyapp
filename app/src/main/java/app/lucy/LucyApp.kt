package app.lucy

import android.app.Application
import app.lucy.data.db.CatalogDatabase
import app.lucy.data.db.UserDatabase
import app.lucy.data.source.NetworkClient
import app.lucy.data.update.ChapterUpdateScheduler
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy

class LucyApp : Application(), ImageLoaderFactory {

    lateinit var catalogDb: CatalogDatabase
        private set

    lateinit var userDb: UserDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        userDb = UserDatabase.getInstance(this)
        catalogDb = CatalogDatabase.getInstance(this)

        if (userDb.getSetting("daily_update_enabled", "true") == "true") {
            ChapterUpdateScheduler.scheduleDaily(this)
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(NetworkClient.okHttp)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(250L * 1024 * 1024) // 250 MB disk cache
                    .build()
            }
            .respectCacheHeaders(false)
            .networkCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
    }
}
