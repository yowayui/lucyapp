package app.lucy.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lucy.BuildConfig
import app.lucy.data.db.UserDatabase
import app.lucy.data.update.ChapterUpdateScheduler
import app.lucy.ui.components.AppUpdate
import app.lucy.ui.components.AppUpdateChecker
import app.lucy.ui.components.UpdateDialog
import app.lucy.ui.theme.Newsreader
import coil.Coil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Extended icons (tree-shaken by R8)
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material.icons.outlined.ViewStream
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material.icons.outlined.HighQuality

@OptIn(ExperimentalMaterial3Api::class, coil.annotation.ExperimentalCoilApi::class)
@Composable
fun SettingsScreen(
    userDb: UserDatabase,
    currentTheme: String,
    onThemeChange: (String) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTheme by remember { mutableStateOf(currentTheme) }
    var readerMode by remember { mutableStateOf(userDb.getSetting("reader_mode", "webtoon")) }
    var keepScreenOn by remember { mutableStateOf(userDb.getSetting("keep_screen_on", "true") == "true") }
    var autoNextWebtoon by remember { mutableStateOf(userDb.getSetting("auto_next_webtoon", "false") == "true") }
    var imageQuality by remember { mutableStateOf(userDb.getSetting("image_quality", "data-saver")) }
    var dailyUpdateEnabled by remember { mutableStateOf(userDb.getSetting("daily_update_enabled", "true") == "true") }
    var lastAutoUpdateTime by remember { mutableStateOf(userDb.getSetting("last_auto_update_time", "0").toLongOrNull() ?: 0L) }

    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showClearMarkersDialog by remember { mutableStateOf(false) }
    var updateDialogData by remember { mutableStateOf<AppUpdate?>(null) }
    var checkingAppUpdate by remember { mutableStateOf(false) }

    val versionName = BuildConfig.VERSION_NAME

    fun openLink(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            scope.launch { snackbarHostState.showSnackbar("Could not open link") }
        }
    }

    val cardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    )
    val listColors = ListItemDefaults.colors(
        containerColor = Color.Transparent
    )
    val dividerColor = MaterialTheme.colorScheme.outlineVariant

    // SegmentedButton monochrome colors
    val segColors = SegmentedButtonDefaults.colors(
        activeContainerColor = MaterialTheme.colorScheme.primary,
        activeContentColor = MaterialTheme.colorScheme.onPrimary,
        activeBorderColor = MaterialTheme.colorScheme.outline,
        inactiveContainerColor = Color.Transparent,
        inactiveContentColor = MaterialTheme.colorScheme.onSurface,
        inactiveBorderColor = MaterialTheme.colorScheme.outline
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                ),
                windowInsets = WindowInsets(0)
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            // -- Appearance --
            item {
                SectionLabel("Appearance")
                Card(colors = cardColors, shape = RoundedCornerShape(16.dp)) {
                    ListItem(
                        headlineContent = { Text("Theme") },
                        leadingContent = { Icon(Icons.Outlined.Palette, null, Modifier.size(24.dp)) },
                        supportingContent = {
                            val themeOptions = listOf("system" to "System", "dark" to "Dark", "light" to "Light")
                            val themeIcons = listOf(Icons.Outlined.SettingsBrightness, Icons.Outlined.DarkMode, Icons.Outlined.LightMode)
                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) {
                                themeOptions.forEachIndexed { index, (key, label) ->
                                    SegmentedButton(
                                        selected = selectedTheme == key,
                                        onClick = {
                                            selectedTheme = key
                                            userDb.setSetting("theme", key)
                                            onThemeChange(key)
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(index, themeOptions.size),
                                        colors = segColors,
                                        icon = {
                                            SegmentedButtonDefaults.Icon(
                                                active = selectedTheme == key,
                                                activeContent = { Icon(Icons.Filled.Check, null, Modifier.size(SegmentedButtonDefaults.IconSize)) },
                                                inactiveContent = { Icon(themeIcons[index], null, Modifier.size(SegmentedButtonDefaults.IconSize)) }
                                            )
                                        }
                                    ) { Text(label, maxLines = 1) }
                                }
                            }
                        },
                        colors = listColors
                    )
                }
            }

            // -- Reader --
            item {
                SectionLabel("Reader")
                Card(colors = cardColors, shape = RoundedCornerShape(16.dp)) {
                    // Reader mode
                    ListItem(
                        headlineContent = { Text("Default Reader Mode") },
                        leadingContent = { Icon(Icons.AutoMirrored.Outlined.MenuBook, null, Modifier.size(24.dp)) },
                        supportingContent = {
                            val modeOptions = listOf("webtoon" to "Webtoon", "paged" to "Paged")
                            val modeIcons = listOf(Icons.Outlined.ViewStream, Icons.Outlined.ViewCarousel)
                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) {
                                modeOptions.forEachIndexed { index, (key, label) ->
                                    SegmentedButton(
                                        selected = readerMode == key,
                                        onClick = {
                                            readerMode = key
                                            userDb.setSetting("reader_mode", key)
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(index, modeOptions.size),
                                        colors = segColors,
                                        icon = {
                                            SegmentedButtonDefaults.Icon(
                                                active = readerMode == key,
                                                activeContent = { Icon(Icons.Filled.Check, null, Modifier.size(SegmentedButtonDefaults.IconSize)) },
                                                inactiveContent = { Icon(modeIcons[index], null, Modifier.size(SegmentedButtonDefaults.IconSize)) }
                                            )
                                        }
                                    ) { Text(label, maxLines = 1) }
                                }
                            }
                        },
                        colors = listColors
                    )

                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(start = 56.dp))

                    // Keep screen on
                    ListItem(
                        headlineContent = { Text("Keep screen on") },
                        supportingContent = { Text("Prevent screen from sleeping while reading") },
                        leadingContent = { Icon(Icons.Outlined.Visibility, null, Modifier.size(24.dp)) },
                        trailingContent = {
                            Switch(
                                checked = keepScreenOn,
                                onCheckedChange = null,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        },
                        modifier = Modifier.clickable {
                            val next = !keepScreenOn
                            keepScreenOn = next
                            userDb.setSetting("keep_screen_on", next.toString())
                        },
                        colors = listColors
                    )

                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(start = 56.dp))

                    // Auto-next
                    ListItem(
                        headlineContent = { Text("Auto-next in Webtoon") },
                        supportingContent = { Text("Load next chapter when scrolling to end") },
                        leadingContent = { Icon(Icons.Outlined.SkipNext, null, Modifier.size(24.dp)) },
                        trailingContent = {
                            Switch(
                                checked = autoNextWebtoon,
                                onCheckedChange = null,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        },
                        modifier = Modifier.clickable {
                            val next = !autoNextWebtoon
                            autoNextWebtoon = next
                            userDb.setSetting("auto_next_webtoon", next.toString())
                        },
                        colors = listColors
                    )

                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(start = 56.dp))

                    // Image quality
                    ListItem(
                        headlineContent = { Text("Image Quality (MangaDex)") },
                        leadingContent = { Icon(Icons.Outlined.Image, null, Modifier.size(24.dp)) },
                        supportingContent = {
                            val qualityOptions = listOf("data-saver" to "Data saver", "hd" to "HD")
                            val qualityIcons = listOf(Icons.Outlined.Image, Icons.Outlined.HighQuality)
                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) {
                                qualityOptions.forEachIndexed { index, (key, label) ->
                                    SegmentedButton(
                                        selected = imageQuality == key,
                                        onClick = {
                                            imageQuality = key
                                            userDb.setSetting("image_quality", key)
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(index, qualityOptions.size),
                                        colors = segColors,
                                        icon = {
                                            SegmentedButtonDefaults.Icon(
                                                active = imageQuality == key,
                                                activeContent = { Icon(Icons.Filled.Check, null, Modifier.size(SegmentedButtonDefaults.IconSize)) },
                                                inactiveContent = { Icon(qualityIcons[index], null, Modifier.size(SegmentedButtonDefaults.IconSize)) }
                                            )
                                        }
                                    ) { Text(label, maxLines = 1) }
                                }
                            }
                        },
                        colors = listColors
                    )
                }
            }

            // -- Updates --
            item {
                SectionLabel("Updates")
                Card(colors = cardColors, shape = RoundedCornerShape(16.dp)) {
                    // Daily background update toggle
                    ListItem(
                        headlineContent = { Text("Daily background update") },
                        supportingContent = { Text("Check followed series daily for new chapters") },
                        leadingContent = { Icon(Icons.Outlined.Update, null, Modifier.size(24.dp)) },
                        trailingContent = {
                            Switch(
                                checked = dailyUpdateEnabled,
                                onCheckedChange = null,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        },
                        modifier = Modifier.clickable {
                            val next = !dailyUpdateEnabled
                            dailyUpdateEnabled = next
                            userDb.setSetting("daily_update_enabled", next.toString())
                            if (next) {
                                ChapterUpdateScheduler.scheduleDaily(context)
                            } else {
                                ChapterUpdateScheduler.cancel(context)
                            }
                        },
                        colors = listColors
                    )

                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(start = 56.dp))

                    // Check now
                    val lastCheckFormatted = remember(lastAutoUpdateTime) {
                        if (lastAutoUpdateTime <= 0L) "Never"
                        else java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(lastAutoUpdateTime))
                    }
                    ListItem(
                        headlineContent = { Text("Check for updates now") },
                        supportingContent = { Text("Last checked: $lastCheckFormatted") },
                        leadingContent = { Icon(Icons.Filled.Refresh, null, Modifier.size(24.dp)) },
                        modifier = Modifier.clickable {
                            ChapterUpdateScheduler.runNow(context)
                            scope.launch { snackbarHostState.showSnackbar("Checking updates in background...") }
                        },
                        colors = listColors
                    )
                }
            }

            // -- Storage & History --
            item {
                SectionLabel("Storage & History")
                Card(colors = cardColors, shape = RoundedCornerShape(16.dp)) {
                    ListItem(
                        headlineContent = { Text("Clear image cache") },
                        supportingContent = { Text("Free up cached chapter pages on device") },
                        leadingContent = { Icon(Icons.Outlined.CleaningServices, null, Modifier.size(24.dp)) },
                        modifier = Modifier.clickable {
                            scope.launch {
                                val loader = Coil.imageLoader(context)
                                loader.memoryCache?.clear()
                                withContext(Dispatchers.IO) { loader.diskCache?.clear() }
                                snackbarHostState.showSnackbar("Image cache cleared")
                            }
                        },
                        colors = listColors
                    )

                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(start = 56.dp))

                    ListItem(
                        headlineContent = { Text("Clear reading history") },
                        supportingContent = { Text("Remove all entries from history shelf") },
                        leadingContent = { Icon(Icons.Outlined.History, null, Modifier.size(24.dp)) },
                        modifier = Modifier.clickable { showClearHistoryDialog = true },
                        colors = listColors
                    )

                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(start = 56.dp))

                    ListItem(
                        headlineContent = { Text("Clear read markers") },
                        supportingContent = { Text("Reset all read chapter indicators") },
                        leadingContent = { Icon(Icons.Outlined.DoneAll, null, Modifier.size(24.dp)) },
                        modifier = Modifier.clickable { showClearMarkersDialog = true },
                        colors = listColors
                    )
                }
            }

            // -- Connect --
            item {
                SectionLabel("Connect")
                Card(colors = cardColors, shape = RoundedCornerShape(16.dp)) {
                    ListItem(
                        headlineContent = { Text("Instagram") },
                        supportingContent = { Text("@y1peh") },
                        leadingContent = { Icon(Icons.Outlined.AlternateEmail, null, Modifier.size(24.dp)) },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp)) },
                        modifier = Modifier.clickable { openLink("https://www.instagram.com/y1peh") },
                        colors = listColors
                    )

                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(start = 56.dp))

                    ListItem(
                        headlineContent = { Text("Email") },
                        supportingContent = { Text("yuiwoip@gmail.com") },
                        leadingContent = { Icon(Icons.Filled.Email, null, Modifier.size(24.dp)) },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp)) },
                        modifier = Modifier.clickable { openLink("mailto:yuiwoip@gmail.com") },
                        colors = listColors
                    )
                }
            }

            // -- About --
            item {
                SectionLabel("About")
                Card(colors = cardColors, shape = RoundedCornerShape(16.dp)) {
                    ListItem(
                        headlineContent = {
                            Text(
                                "lucy",
                                fontFamily = Newsreader,
                                fontWeight = FontWeight.Light,
                                fontSize = 28.sp,
                                letterSpacing = (-1).sp
                            )
                        },
                        supportingContent = {
                            Column {
                                Text("Manga & Manhwa Reader")
                                Text(
                                    "Version $versionName (Release)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        },
                        leadingContent = { Icon(Icons.Filled.Info, null, Modifier.size(24.dp)) },
                        colors = listColors
                    )

                    HorizontalDivider(color = dividerColor, modifier = Modifier.padding(start = 56.dp))

                    ListItem(
                        headlineContent = { Text("Check for app update") },
                        supportingContent = {
                            Text(if (checkingAppUpdate) "Checking GitHub..." else "Look for new release")
                        },
                        leadingContent = { Icon(Icons.Outlined.Public, null, Modifier.size(24.dp)) },
                        modifier = Modifier.clickable {
                            if (checkingAppUpdate) return@clickable
                            checkingAppUpdate = true
                            scope.launch {
                                val update = AppUpdateChecker.checkLatest(BuildConfig.VERSION_NAME)
                                checkingAppUpdate = false
                                if (update != null) {
                                    updateDialogData = update
                                } else {
                                    snackbarHostState.showSnackbar("App is up to date ($versionName)")
                                }
                            }
                        },
                        colors = listColors
                    )
                }

                // Bottom spacing
                Text("", modifier = Modifier.padding(bottom = 32.dp))
            }
        }
    }

    // -- Dialogs --

    updateDialogData?.let { update ->
        UpdateDialog(
            update = update,
            context = context,
            onDismiss = { updateDialogData = null }
        )
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            icon = { Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Clear Reading History") },
            text = { Text("Are you sure you want to clear your reading history? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    userDb.clearHistory()
                    showClearHistoryDialog = false
                    scope.launch { snackbarHostState.showSnackbar("Reading history cleared") }
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

    if (showClearMarkersDialog) {
        AlertDialog(
            onDismissRequest = { showClearMarkersDialog = false },
            icon = { Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Clear Read Markers") },
            text = { Text("Are you sure you want to reset all read chapter markers?") },
            confirmButton = {
                TextButton(onClick = {
                    userDb.clearReadChapters()
                    showClearMarkersDialog = false
                    scope.launch { snackbarHostState.showSnackbar("Read markers reset") }
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
private fun SectionLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp)
    )
}
