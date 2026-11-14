package app.lucy.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lucy.BuildConfig
import app.lucy.data.db.UserDatabase
import app.lucy.data.update.ChapterUpdateScheduler
import app.lucy.ui.components.AppUpdate
import app.lucy.ui.components.AppUpdateChecker
import app.lucy.ui.components.UpdateCheckResult
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

    val cardShape = RoundedCornerShape(24.dp)
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    // SegmentedButton monochrome colors
    val segColors = SegmentedButtonDefaults.colors(
        activeContainerColor = MaterialTheme.colorScheme.primary,
        activeContentColor = MaterialTheme.colorScheme.onPrimary,
        activeBorderColor = MaterialTheme.colorScheme.primary,
        inactiveContainerColor = Color.Transparent,
        inactiveContentColor = MaterialTheme.colorScheme.onSurface,
        inactiveBorderColor = MaterialTheme.colorScheme.outline
    )

    val switchColors = SwitchDefaults.colors(
        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
        checkedTrackColor = MaterialTheme.colorScheme.primary,
        checkedBorderColor = Color.Transparent,
        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        uncheckedBorderColor = Color.Transparent
    )

    var page by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    LaunchedEffect(page) { listState.scrollToItem(0) }

    BackHandler(enabled = page != null) { page = null }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
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
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            when (page) {
                null -> {
                    item { SettingsMainHeader(onBackClick) }

                    item {
                        SettingsGroup(shape = cardShape) {
                            SettingsRow(
                                icon = Icons.Outlined.Palette,
                                title = "Appearance",
                                subtitle = "Theme and dark mode",
                                onClick = { page = "appearance" }
                            )
                            SettingsDivider(dividerColor)
                            SettingsRow(
                                icon = Icons.AutoMirrored.Outlined.MenuBook,
                                title = "Reader",
                                subtitle = "Reading mode, quality, and behavior",
                                onClick = { page = "reader" }
                            )
                        }
                    }

                    item {
                        SettingsGroup(shape = cardShape) {
                            SettingsRow(
                                icon = Icons.Outlined.Update,
                                title = "Updates",
                                subtitle = "Daily chapter checks",
                                onClick = { page = "updates" }
                            )
                            SettingsDivider(dividerColor)
                            SettingsRow(
                                icon = Icons.Outlined.CleaningServices,
                                title = "Storage & history",
                                subtitle = "Cache, history, and read markers",
                                onClick = { page = "storage" }
                            )
                            SettingsDivider(dividerColor)
                            SettingsRow(
                                icon = Icons.Outlined.AlternateEmail,
                                title = "Connect",
                                subtitle = "Instagram and email",
                                onClick = { page = "connect" }
                            )
                        }
                    }

                    item {
                        SettingsGroup(shape = cardShape) {
                            SettingsRow(
                                icon = Icons.Filled.Info,
                                title = "About",
                                subtitle = "Version and app updates",
                                onClick = { page = "about" }
                            )
                        }
                    }
                }

                "appearance" -> {
                    item { SettingsDetailHeader("Appearance") { page = null } }

                    item {
                        SettingsSection(title = "Theme", shape = cardShape) {
                            SettingsRow(
                                icon = Icons.Outlined.Palette,
                                title = "Theme",
                                subtitle = "Choose how lucy looks",
                                supportingContent = {
                                    val themeOptions = listOf("system" to "System", "dark" to "Dark", "light" to "Light")
                                    val themeIcons = listOf(Icons.Outlined.SettingsBrightness, Icons.Outlined.DarkMode, Icons.Outlined.LightMode)
                                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
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
                                }
                            )
                        }
                    }
                }

                "reader" -> {
                    item { SettingsDetailHeader("Reader") { page = null } }

                    item {
                        SettingsSection(title = "Reading", shape = cardShape) {
                            SettingsRow(
                                icon = Icons.AutoMirrored.Outlined.MenuBook,
                                title = "Default reader mode",
                                subtitle = "Webtoon scroll or paged chapters",
                                supportingContent = {
                                    val modeOptions = listOf("webtoon" to "Webtoon", "paged" to "Paged")
                                    val modeIcons = listOf(Icons.Outlined.ViewStream, Icons.Outlined.ViewCarousel)
                                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
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
                                }
                            )

                            SettingsDivider(dividerColor)

                            SettingsRow(
                                icon = Icons.Outlined.Visibility,
                                title = "Keep screen on",
                                subtitle = "Prevent sleeping while reading",
                                trailingContent = {
                                    Switch(checked = keepScreenOn, onCheckedChange = null, colors = switchColors)
                                },
                                onClick = {
                                    val next = !keepScreenOn
                                    keepScreenOn = next
                                    userDb.setSetting("keep_screen_on", next.toString())
                                }
                            )

                            SettingsDivider(dividerColor)

                            SettingsRow(
                                icon = Icons.Outlined.SkipNext,
                                title = "Auto-next in webtoon",
                                subtitle = "Load the next chapter at the end",
                                trailingContent = {
                                    Switch(checked = autoNextWebtoon, onCheckedChange = null, colors = switchColors)
                                },
                                onClick = {
                                    val next = !autoNextWebtoon
                                    autoNextWebtoon = next
                                    userDb.setSetting("auto_next_webtoon", next.toString())
                                }
                            )

                            SettingsDivider(dividerColor)

                            SettingsRow(
                                icon = Icons.Outlined.Image,
                                title = "Image quality",
                                subtitle = "MangaDex page resolution",
                                supportingContent = {
                                    val qualityOptions = listOf("data-saver" to "Data saver", "hd" to "HD")
                                    val qualityIcons = listOf(Icons.Outlined.Image, Icons.Outlined.HighQuality)
                                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
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
                                }
                            )
                        }
                    }
                }

                "updates" -> {
                    item { SettingsDetailHeader("Updates") { page = null } }

                    item {
                        SettingsSection(title = "Chapter updates", shape = cardShape) {
                            SettingsRow(
                                icon = Icons.Outlined.Update,
                                title = "Daily background update",
                                subtitle = "Check followed series every day",
                                trailingContent = {
                                    Switch(checked = dailyUpdateEnabled, onCheckedChange = null, colors = switchColors)
                                },
                                onClick = {
                                    val next = !dailyUpdateEnabled
                                    dailyUpdateEnabled = next
                                    userDb.setSetting("daily_update_enabled", next.toString())
                                    if (next) {
                                        ChapterUpdateScheduler.scheduleDaily(context)
                                    } else {
                                        ChapterUpdateScheduler.cancel(context)
                                    }
                                }
                            )

                            SettingsDivider(dividerColor)

                            val lastCheckFormatted = remember(lastAutoUpdateTime) {
                                if (lastAutoUpdateTime <= 0L) "Never"
                                else java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
                                    .format(java.util.Date(lastAutoUpdateTime))
                            }
                            SettingsRow(
                                icon = Icons.Filled.Refresh,
                                title = "Check for updates now",
                                subtitle = "Last checked: $lastCheckFormatted",
                                onClick = {
                                    ChapterUpdateScheduler.runNow(context)
                                    scope.launch { snackbarHostState.showSnackbar("Checking updates in background...") }
                                }
                            )
                        }
                    }
                }

                "storage" -> {
                    item { SettingsDetailHeader("Storage & history") { page = null } }

                    item {
                        SettingsSection(title = "Clear data", shape = cardShape) {
                            SettingsRow(
                                icon = Icons.Outlined.CleaningServices,
                                title = "Clear image cache",
                                subtitle = "Free up cached chapter pages",
                                onClick = {
                                    scope.launch {
                                        val loader = Coil.imageLoader(context)
                                        loader.memoryCache?.clear()
                                        withContext(Dispatchers.IO) { loader.diskCache?.clear() }
                                        snackbarHostState.showSnackbar("Image cache cleared")
                                    }
                                }
                            )

                            SettingsDivider(dividerColor)

                            SettingsRow(
                                icon = Icons.Outlined.History,
                                title = "Clear reading history",
                                subtitle = "Remove all history shelf entries",
                                onClick = { showClearHistoryDialog = true }
                            )

                            SettingsDivider(dividerColor)

                            SettingsRow(
                                icon = Icons.Outlined.DoneAll,
                                title = "Clear read markers",
                                subtitle = "Reset all read chapter indicators",
                                onClick = { showClearMarkersDialog = true }
                            )
                        }
                    }
                }

                "connect" -> {
                    item { SettingsDetailHeader("Connect") { page = null } }

                    item {
                        SettingsSection(title = "Contact", shape = cardShape) {
                            SettingsRow(
                                icon = Icons.Outlined.AlternateEmail,
                                title = "Instagram",
                                subtitle = "@y1peh",
                                trailingContent = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = { openLink("https://www.instagram.com/y1peh") }
                            )

                            SettingsDivider(dividerColor)

                            SettingsRow(
                                icon = Icons.Filled.Email,
                                title = "Email",
                                subtitle = "yuiwoip@gmail.com",
                                trailingContent = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = { openLink("mailto:yuiwoip@gmail.com") }
                            )
                        }
                    }
                }

                "about" -> {
                    item { SettingsDetailHeader("About") { page = null } }

                    item {
                        SettingsSection(title = "Application", shape = cardShape) {
                            SettingsRow(
                                icon = Icons.Filled.Info,
                                title = "lucy",
                                titleContent = {
                                    Text(
                                        text = "lucy",
                                        fontFamily = Newsreader,
                                        fontWeight = FontWeight.Light,
                                        fontSize = 24.sp,
                                        letterSpacing = (-0.5).sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                subtitle = "Manga & Manhwa Reader · Version $versionName"
                            )

                            SettingsDivider(dividerColor)

                            SettingsRow(
                                icon = Icons.Outlined.Public,
                                title = "Check for app update",
                                subtitle = if (checkingAppUpdate) "Checking GitHub..." else "Look for a new release",
                                onClick = {
                                    if (checkingAppUpdate) return@SettingsRow
                                    checkingAppUpdate = true
                                    scope.launch {
                                        val result = AppUpdateChecker.checkLatest(BuildConfig.VERSION_NAME)
                                        checkingAppUpdate = false
                                        when (result) {
                                            is UpdateCheckResult.Available -> updateDialogData = result.update
                                            UpdateCheckResult.UpToDate ->
                                                snackbarHostState.showSnackbar("App is up to date ($versionName)")
                                            is UpdateCheckResult.Failed ->
                                                snackbarHostState.showSnackbar("Couldn't check for updates. Try again or open GitHub.")
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
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
private fun SettingsMainHeader(onBackClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.offset(x = (-10).dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        Text(
            text = "Settings",
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 6.dp)
        )
    }
}

@Composable
private fun SettingsDetailHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.offset(x = (-10).dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    shape: Shape,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        SettingsGroup(shape = shape, content = content)
    }
}

@Composable
private fun SettingsGroup(
    shape: Shape,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer),
        content = content
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    titleContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null
) {
    val rowModifier = if (onClick != null) {
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    } else {
        Modifier.fillMaxWidth()
    }

    Column(modifier = rowModifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(23.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                if (titleContent != null) {
                    titleContent()
                } else {
                    Text(
                        text = title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (trailingContent != null) {
                Spacer(modifier = Modifier.width(12.dp))
                Box(contentAlignment = Alignment.Center) {
                    trailingContent()
                }
            }
        }

        if (supportingContent != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
            ) {
                supportingContent()
            }
        }
    }
}

@Composable
private fun SettingsDivider(color: Color) {
    HorizontalDivider(
        color = color,
        modifier = Modifier.padding(start = 82.dp)
    )
}
