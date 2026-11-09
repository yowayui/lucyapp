package app.lucy.data.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.lucy.MainActivity
import app.lucy.data.db.CatalogDatabase
import app.lucy.data.db.UserDatabase
import app.lucy.data.source.ChapterLoader
import kotlinx.coroutines.delay

class ChapterUpdateWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "lucy_chapter_updates"
        const val NOTIFICATION_ID = 2001
    }

    override suspend fun doWork(): Result {
        val userDb = UserDatabase.getInstance(context)
        val catalogDb = CatalogDatabase.getInstance(context)

        val enabled = userDb.getSetting("daily_update_enabled", "true") == "true"
        if (!enabled) {
            return Result.success()
        }

        val slugsToCheck = userDb.getSeriesToCheck(limit = 40)
        if (slugsToCheck.isEmpty()) {
            userDb.setSetting("last_auto_update_time", System.currentTimeMillis().toString())
            return Result.success()
        }

        val updatedSeries = mutableListOf<String>()

        for (slug in slugsToCheck) {
            val item = catalogDb.getBySlug(slug) ?: continue
            val group = item.sourceGroup
            if (group.equals("Dojing", ignoreCase = true) || group.equals("ShiroDoujin", ignoreCase = true)) {
                userDb.markSeriesChecked(slug, 0)
                continue
            }

            try {
                val baseChs = catalogDb.getChapters(slug)
                val extraChs = userDb.getExtraChapters(slug)
                val currentMax = (baseChs + extraChs).mapNotNull { it.number.toFloatOrNull() }.maxOrNull() ?: 0f
                val liveChapters = ChapterLoader.fetchLiveChapters(
                    group = item.sourceGroup,
                    type = item.type,
                    seriesSlug = item.slug,
                    seriesTitle = item.title,
                    englishName = item.englishName,
                    nativeName = item.nativeName,
                    currentMaxChapter = currentMax
                )

                if (liveChapters.isNotEmpty()) {
                    val existingNums = (baseChs + extraChs).map { it.number.toFloatOrNull() ?: it.number }.toSet()
                    val newChapters = liveChapters.filter { !existingNums.contains(it.number.toFloatOrNull() ?: it.number) }

                    if (newChapters.isNotEmpty()) {
                        userDb.saveExtraChapters(slug, newChapters)
                        updatedSeries.add(item.title)
                        userDb.markSeriesChecked(slug, newChapters.size)
                    } else {
                        userDb.markSeriesChecked(slug, 0)
                    }
                } else {
                    userDb.markSeriesChecked(slug, 0)
                }
            } catch (_: Exception) {
                // Ignore transient errors per series and continue
            }

            delay(500)
        }

        userDb.setSetting("last_auto_update_time", System.currentTimeMillis().toString())

        if (updatedSeries.isNotEmpty()) {
            postNotification(updatedSeries)
        }

        return Result.success()
    }

    private fun postNotification(updatedSeries: List<String>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val perm = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            )
            if (perm != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        createNotificationChannel()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = "New Chapters Available"
        val message = if (updatedSeries.size == 1) {
            "New chapters released for ${updatedSeries.first()}"
        } else {
            "${updatedSeries.size} series updated: ${updatedSeries.take(2).joinToString(", ")}..."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Chapter Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications when followed series have new chapters"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }
}
