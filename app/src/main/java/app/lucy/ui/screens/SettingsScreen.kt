package app.lucy.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lucy.BuildConfig
import app.lucy.data.db.UserDatabase
import app.lucy.data.update.ChapterUpdateScheduler
import app.lucy.ui.theme.Newsreader
import coil.Coil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(coil.annotation.ExperimentalCoilApi::class)
@Composable
fun SettingsScreen(
    userDb: UserDatabase,
    currentTheme: String,
    onThemeChange: (String) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTheme by remember { mutableStateOf(currentTheme) }
    var readerMode by remember { mutableStateOf(userDb.getSetting("reader_mode", "webtoon")) }
    var keepScreenOn by remember { mutableStateOf(userDb.getSetting("keep_screen_on", "true") == "true") }
    var autoNextWebtoon by remember { mutableStateOf(userDb.getSetting("auto_next_webtoon", "false") == "true") }
    var imageQuality by remember { mutableStateOf(userDb.getSetting("image_quality", "data-saver")) }
    var dailyUpdateEnabled by remember { mutableStateOf(userDb.getSetting("daily_update_enabled", "true") == "true") }
    var lastAutoUpdateTime by remember { mutableStateOf(userDb.getSetting("last_auto_update_time", "0").toLongOrNull() ?: 0L) }

    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showClearMarkersDialog by remember { mutableStateOf(false) }

    val versionName = BuildConfig.VERSION_NAME

    fun openLink(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        // Section: Appearance
        item {
            SectionHeader(title = "Appearance")

            SettingItem(title = "Theme") {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    listOf("system" to "System default", "dark" to "Dark", "light" to "Light").forEach { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedTheme = key
                                    userDb.setSetting("theme", key)
                                    onThemeChange(key)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedTheme == key,
                                onClick = {
                                    selectedTheme = key
                                    userDb.setSetting("theme", key)
                                    onThemeChange(key)
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary,
                                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // Section: Reader Preferences
        item {
            SectionHeader(title = "Reader")

            SettingItem(title = "Default Reader Mode") {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    listOf("webtoon" to "Webtoon (Vertical scroll)", "paged" to "Paged (Horizontal swipe)").forEach { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    readerMode = key
                                    userDb.setSetting("reader_mode", key)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = readerMode == key,
                                onClick = {
                                    readerMode = key
                                    userDb.setSetting("reader_mode", key)
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary,
                                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val next = !keepScreenOn
                        keepScreenOn = next
                        userDb.setSetting("keep_screen_on", next.toString())
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Keep screen on",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Prevent screen from sleeping while reading",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = keepScreenOn,
                    onCheckedChange = { next ->
                        keepScreenOn = next
                        userDb.setSetting("keep_screen_on", next.toString())
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val next = !autoNextWebtoon
                        autoNextWebtoon = next
                        userDb.setSetting("auto_next_webtoon", next.toString())
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-next in Webtoon",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Automatically load next chapter when scrolling to the end",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoNextWebtoon,
                    onCheckedChange = { next ->
                        autoNextWebtoon = next
                        userDb.setSetting("auto_next_webtoon", next.toString())
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }

            SettingItem(title = "Image Quality (MangaDex)") {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    listOf("data-saver" to "Data-saver (Faster, compressed)", "hd" to "High Quality (HD original)").forEach { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    imageQuality = key
                                    userDb.setSetting("image_quality", key)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = imageQuality == key,
                                onClick = {
                                    imageQuality = key
                                    userDb.setSetting("image_quality", key)
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary,
                                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // Section: Updates
        item {
            SectionHeader(title = "Updates")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val next = !dailyUpdateEnabled
                        dailyUpdateEnabled = next
                        userDb.setSetting("daily_update_enabled", next.toString())
                        if (next) {
                            ChapterUpdateScheduler.scheduleDaily(context)
                        } else {
                            ChapterUpdateScheduler.cancel(context)
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Daily background update",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Check followed series daily for new chapters",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = dailyUpdateEnabled,
                    onCheckedChange = { next ->
                        dailyUpdateEnabled = next
                        userDb.setSetting("daily_update_enabled", next.toString())
                        if (next) {
                            ChapterUpdateScheduler.scheduleDaily(context)
                        } else {
                            ChapterUpdateScheduler.cancel(context)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }

            val lastCheckFormatted = remember(lastAutoUpdateTime) {
                if (lastAutoUpdateTime <= 0L) {
                    "Never"
                } else {
                    java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(lastAutoUpdateTime))
                }
            }

            ClickableActionItem(
                title = "Check for updates now",
                subtitle = "Last checked: $lastCheckFormatted"
            ) {
                ChapterUpdateScheduler.runNow(context)
                Toast.makeText(context, "Checking updates in background...", Toast.LENGTH_SHORT).show()
            }
        }

        // Section: Storage & Data
        item {
            SectionHeader(title = "Storage & History")

            ClickableActionItem(
                title = "Clear image cache",
                subtitle = "Free up cached chapter pages on device"
            ) {
                scope.launch {
                    val loader = Coil.imageLoader(context)
                    loader.memoryCache?.clear()
                    withContext(Dispatchers.IO) {
                        loader.diskCache?.clear()
                    }
                    Toast.makeText(context, "Image cache cleared", Toast.LENGTH_SHORT).show()
                }
            }

            ClickableActionItem(
                title = "Clear reading history",
                subtitle = "Remove all entries from history shelf and library"
            ) {
                showClearHistoryDialog = true
            }

            ClickableActionItem(
                title = "Clear read markers",
                subtitle = "Reset all read chapter indicators"
            ) {
                showClearMarkersDialog = true
            }
        }

        // Section: Connect
        item {
            SectionHeader(title = "Connect")

            ClickableActionItem(
                title = "Instagram",
                subtitle = "@y1peh",
                trailingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Instagram",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            ) {
                openLink("https://www.instagram.com/y1peh")
            }

            ClickableActionItem(
                title = "Email",
                subtitle = "yuiwoip@gmail.com",
                trailingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Email",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            ) {
                openLink("mailto:yuiwoip@gmail.com")
            }
        }

        // Section: About
        item {
            SectionHeader(title = "About")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "lucy",
                    fontFamily = Newsreader,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 28.sp,
                        letterSpacing = (-1).sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Manga & Manhwa Reader",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Version $versionName (Release)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Clear History Confirmation
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear Reading History") },
            text = { Text("Are you sure you want to clear your reading history? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    userDb.clearHistory()
                    showClearHistoryDialog = false
                    Toast.makeText(context, "Reading history cleared", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Clear Read Markers Confirmation
    if (showClearMarkersDialog) {
        AlertDialog(
            onDismissRequest = { showClearMarkersDialog = false },
            title = { Text("Clear Read Markers") },
            text = { Text("Are you sure you want to reset all read chapter markers?") },
            confirmButton = {
                TextButton(onClick = {
                    userDb.clearReadChapters()
                    showClearMarkersDialog = false
                    Toast.makeText(context, "Read markers reset", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearMarkersDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingItem(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onBackground
        )
        content()
    }
}

@Composable
private fun ClickableActionItem(
    title: String,
    subtitle: String,
    trailingIcon: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (trailingIcon != null) {
            trailingIcon()
        }
    }
}
