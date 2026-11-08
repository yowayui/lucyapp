package app.lucy.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.lucy.data.source.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

data class AppUpdate(
    val versionName: String,
    val changelog: String,
    val downloadUrl: String,
    val releaseUrl: String
)

object AppUpdateChecker {
    private const val RELEASES_URL = "https://api.github.com/repos/yowayui/lucyapp/releases/latest"

    // ponytail: integer triplet comparison. Add pre-release channel parsing when beta tags exist.
    fun isNewer(latest: String, current: String): Boolean {
        val l = latest.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        val c = current.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(l.size, c.size)) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv > cv) return true
            if (lv < cv) return false
        }
        return false
    }

    suspend fun checkLatest(currentVersion: String): AppUpdate? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(RELEASES_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "lucy-android")
                .build()
            val res = NetworkClient.okHttp.newCall(req).execute()
            if (!res.isSuccessful) return@runCatching null
            val body = res.body?.string().orEmpty()
            if (body.isBlank()) return@runCatching null

            val json = JSONObject(body)
            val tagName = json.optString("tag_name", "").trim()
            if (!isNewer(tagName, currentVersion)) return@runCatching null

            val notes = json.optString("body", "").trim()
            val releaseUrl = json.optString("html_url", "https://github.com/yowayui/lucyapp/releases")
            var downloadUrl = ""

            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        downloadUrl = asset.optString("browser_download_url", "")
                        break
                    }
                }
            }
            if (downloadUrl.isBlank()) downloadUrl = releaseUrl

            AppUpdate(
                versionName = tagName,
                changelog = notes,
                downloadUrl = downloadUrl,
                releaseUrl = releaseUrl
            )
        }.getOrNull()
    }
}

@Composable
fun UpdateDialog(
    update: AppUpdate,
    context: Context,
    onDismiss: () -> Unit
) {
    fun openUri(uriStr: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.NewReleases,
                        contentDescription = "New version",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "New version available!",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = update.versionName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (update.changelog.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Commits",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = update.changelog,
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .clickable { openUri(update.releaseUrl) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Open on GitHub",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        openUri(update.downloadUrl)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Download", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Not now", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
