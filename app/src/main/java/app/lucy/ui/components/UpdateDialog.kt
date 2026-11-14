@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.lucy.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.lucy.data.source.NetworkClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.io.IOException

data class AppUpdate(
    val versionName: String,
    val changelog: String,
    val downloadUrl: String,
    val releaseUrl: String,
    val apkUrl: String? = null
)

sealed interface UpdateCheckResult {
    data class Available(val update: AppUpdate) : UpdateCheckResult
    object UpToDate : UpdateCheckResult
    data class Failed(val message: String? = null) : UpdateCheckResult
}

object AppUpdateChecker {
    private const val REPO = "yowayui/lucyapp"
    private const val ATOM_URL = "https://github.com/$REPO/releases.atom"
    private const val LATEST_URL = "https://github.com/$REPO/releases/latest"
    private const val APK_ASSET = "lucy.apk"

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

    // Releases are read from the public Atom feed because the unauthenticated
    // GitHub REST API is limited to 60 requests per hour per IP, which makes
    // update checks fail silently on shared networks.
    suspend fun checkLatest(currentVersion: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        val atom = runCatching { fetchAtomLatest() }.getOrNull()
        if (atom != null) {
            return@withContext if (isNewer(atom.tag, currentVersion)) {
                UpdateCheckResult.Available(buildUpdate(atom.tag, atom.notes))
            } else {
                UpdateCheckResult.UpToDate
            }
        }

        val tag = runCatching { fetchLatestTagViaRedirect() }.getOrNull()
        if (!tag.isNullOrBlank()) {
            return@withContext if (isNewer(tag, currentVersion)) {
                UpdateCheckResult.Available(buildUpdate(tag, ""))
            } else {
                UpdateCheckResult.UpToDate
            }
        }

        UpdateCheckResult.Failed()
    }

    private data class AtomRelease(val tag: String, val notes: String)

    private fun fetchAtomLatest(): AtomRelease {
        val req = Request.Builder()
            .url(ATOM_URL)
            .header("Accept", "application/atom+xml")
            .header("User-Agent", "lucy-android")
            .build()
        NetworkClient.okHttp.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
            val xml = res.body?.string().orEmpty()
            val doc = Jsoup.parse(xml, "", Parser.xmlParser())
            val entry = doc.selectFirst("entry") ?: throw IOException("No releases")

            val link = entry.selectFirst("link[rel=alternate]")?.attr("href").orEmpty()
            val tag = link.substringAfterLast("/tag/", "").ifBlank {
                entry.selectFirst("id")?.text()?.substringAfterLast('/').orEmpty()
            }
            if (tag.isBlank()) throw IOException("No release tag")

            val contentHtml = entry.selectFirst("content")?.wholeText().orEmpty()
            return AtomRelease(tag.trim(), notesFromHtml(contentHtml))
        }
    }

    private fun notesFromHtml(html: String): String {
        if (html.isBlank()) return ""
        val body = Jsoup.parse(html).body()
        val parts = mutableListOf<String>()
        for (el in body.select("li, p")) {
            val text = el.text().trim()
            if (text.isEmpty()) continue
            parts += if (el.tagName().equals("li", ignoreCase = true)) "• $text" else text
        }
        return if (parts.isNotEmpty()) parts.joinToString("\n") else body.text().trim()
    }

    private fun fetchLatestTagViaRedirect(): String {
        val client = NetworkClient.okHttp.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        val req = Request.Builder()
            .url(LATEST_URL)
            .header("User-Agent", "lucy-android")
            .build()
        client.newCall(req).execute().use { res ->
            val location = res.header("Location").orEmpty()
            val tag = location.substringAfterLast("/tag/", "")
            if (tag.isBlank()) throw IOException("HTTP ${res.code}")
            return tag.trim()
        }
    }

    private fun buildUpdate(tag: String, notes: String): AppUpdate {
        val releaseUrl = "https://github.com/$REPO/releases/tag/$tag"
        val apkUrl = "https://github.com/$REPO/releases/download/$tag/$APK_ASSET"
        return AppUpdate(
            versionName = tag,
            changelog = notes,
            downloadUrl = apkUrl,
            releaseUrl = releaseUrl,
            apkUrl = apkUrl
        )
    }
}

private enum class DownloadPhase { Idle, Downloading, Verifying, Installing, NeedsPermission, Error }

private val SHA256_REGEX = Regex("(?i)SHA-?256\\s*:\\s*([0-9a-f]{64})")

@Composable
fun UpdateDialog(
    update: AppUpdate,
    context: Context,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var phase by remember { mutableStateOf(DownloadPhase.Idle) }
    var progress by remember { mutableIntStateOf(0) }
    var indeterminate by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var apkFile by remember { mutableStateOf<File?>(null) }
    var pendingInstall by remember { mutableStateOf<File?>(null) }

    fun openUri(uriStr: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    fun deleteApkFiles() {
        apkFile?.delete()
        apkFile = null
        pendingInstall?.delete()
        pendingInstall = null
    }

    fun fail(message: String) {
        errorText = message
        phase = DownloadPhase.Error
        deleteApkFiles()
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun startInstall(file: File) {
        phase = DownloadPhase.Installing
        scope.launch(Dispatchers.IO) {
            ApkInstaller.install(context, file) { result ->
                scope.launch {
                    when (result) {
                        is InstallResult.Failure -> fail(result.message)
                        InstallResult.Success, InstallResult.PendingUserAction -> {
                            // Installer UI is in front; the app is replaced on success.
                        }
                    }
                }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        deleteApkFiles()
        progress = 0
        indeterminate = false
        phase = DownloadPhase.Idle
    }

    fun dismiss() {
        downloadJob?.cancel()
        downloadJob = null
        deleteApkFiles()
        onDismiss()
    }

    fun startDownload() {
        val url = update.apkUrl
        if (url.isNullOrBlank()) {
            fail("No APK asset found in the latest release. Use Open on GitHub instead.")
            return
        }
        if (downloadJob?.isActive == true) return

        errorText = null
        phase = DownloadPhase.Downloading
        progress = 0
        indeterminate = false

        downloadJob = scope.launch {
            try {
                val file = downloadApk(context, url, update.versionName) { percent ->
                    if (percent == null) {
                        indeterminate = true
                    } else {
                        indeterminate = false
                        progress = percent
                    }
                }
                apkFile = file

                phase = DownloadPhase.Verifying
                val expected = SHA256_REGEX.find(update.changelog)?.groupValues?.getOrNull(1)
                if (expected != null) {
                    val actual = withContext(Dispatchers.IO) { ApkInstaller.sha256(file) }
                    if (!actual.equals(expected, ignoreCase = true)) {
                        fail("Checksum mismatch. Update cancelled. Use Open on GitHub as fallback.")
                        return@launch
                    }
                }

                if (!ApkInstaller.canInstall(context)) {
                    pendingInstall = file
                    phase = DownloadPhase.NeedsPermission
                    ApkInstaller.openUnknownSourcesSettings(context)
                } else {
                    startInstall(file)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                if (isActive) {
                    fail(t.message ?: "Download failed. Check your connection and try again.")
                }
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val file = pendingInstall
                if (file != null && file.exists() && ApkInstaller.canInstall(context)) {
                    pendingInstall = null
                    startInstall(file)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        onDispose {
            downloadJob?.cancel()
            apkFile?.delete()
            pendingInstall?.delete()
        }
    }

    Dialog(
        onDismissRequest = { dismiss() },
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

                if (errorText != null && (phase == DownloadPhase.Error || phase == DownloadPhase.Idle)) {
                    Text(
                        text = errorText.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                when (phase) {
                    DownloadPhase.Downloading,
                    DownloadPhase.Verifying,
                    DownloadPhase.Installing -> {
                        if (phase == DownloadPhase.Downloading && !indeterminate) {
                            LinearWavyProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            LinearWavyProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = when (phase) {
                                DownloadPhase.Downloading ->
                                    if (indeterminate) "Downloading..." else "Downloading... $progress%"
                                DownloadPhase.Verifying -> "Verifying checksum..."
                                else -> "Opening installer..."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = {
                                if (phase == DownloadPhase.Downloading) {
                                    cancelDownload()
                                } else {
                                    dismiss()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Text(
                                text = if (phase == DownloadPhase.Downloading) "Cancel" else "Not now",
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    DownloadPhase.NeedsPermission -> {
                        Text(
                            text = "Allow Lucy to install unknown apps, then come back to finish the update.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { ApkInstaller.openUnknownSourcesSettings(context) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("Open settings", fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { dismiss() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Text("Not now", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    else -> {
                        Button(
                            onClick = { startDownload() },
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
                            onClick = { dismiss() },
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
    }
}

private suspend fun downloadApk(
    context: Context,
    url: String,
    versionName: String,
    onProgress: (Int?) -> Unit
): File = withContext(Dispatchers.IO) {
    val dir = File(context.cacheDir, "updates").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }

    val safeVersion = versionName.removePrefix("v").replace(Regex("[^A-Za-z0-9._-]"), "_")
    val target = File(dir, "lucy-$safeVersion.apk")
    val partial = File(dir, "${target.name}.part")

    val request = Request.Builder()
        .url(url)
        .header("Accept", "application/octet-stream")
        .build()

    try {
        NetworkClient.okHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Download failed (HTTP ${response.code})")
            }
            val body = response.body ?: throw IOException("Empty download response")
            val total = body.contentLength()
            var reportedUnknown = false

            body.byteStream().use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    var lastPercent = -1

                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read

                        if (total > 0) {
                            val percent = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        } else if (!reportedUnknown) {
                            reportedUnknown = true
                            onProgress(null)
                        }
                    }

                    if (total > 0 && downloaded != total) {
                        throw IOException("Incomplete download")
                    }
                }
            }
        }

        if (!partial.renameTo(target)) {
            partial.copyTo(target, overwrite = true)
            partial.delete()
        }
        onProgress(100)
        target
    } catch (t: Throwable) {
        partial.delete()
        target.delete()
        throw t
    }
}
