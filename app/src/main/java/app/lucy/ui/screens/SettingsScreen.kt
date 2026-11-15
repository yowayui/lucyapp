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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Lock
import app.lucy.R
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
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.RadioButton
import app.lucy.ui.components.UpdateChannel

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
    var showLicenseDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showTranslationDialog by remember { mutableStateOf(false) }
    var showChannelDialog by remember { mutableStateOf(false) }

    var autoAppUpdate by remember { mutableStateOf(userDb.getSetting("app_update_auto", "true") == "true") }
    var notifyAppUpdate by remember { mutableStateOf(userDb.getSetting("app_update_notify", "false") == "true") }
    var updateChannelKey by remember { mutableStateOf(userDb.getSetting("app_update_channel", "stable")) }
    var latestAppTag by remember { mutableStateOf(userDb.getSetting("app_update_latest_tag", "")) }
    var lastAppCheck by remember { mutableStateOf(userDb.getSetting("app_update_last_check", "0").toLongOrNull() ?: 0L) }

    val versionName = BuildConfig.VERSION_NAME

    fun openLink(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            scope.launch { snackbarHostState.showSnackbar("Could not open link") }
        }
    }

    fun checkAppUpdate() {
        if (checkingAppUpdate) return
        checkingAppUpdate = true
        scope.launch {
            val result = AppUpdateChecker.checkLatest(versionName, UpdateChannel.fromKey(updateChannelKey))
            checkingAppUpdate = false
            lastAppCheck = System.currentTimeMillis()
            userDb.setSetting("app_update_last_check", lastAppCheck.toString())
            when (result) {
                is UpdateCheckResult.Available -> {
                    latestAppTag = result.update.versionName
                    userDb.setSetting("app_update_latest_tag", latestAppTag)
                    updateDialogData = result.update
                }
                is UpdateCheckResult.UpToDate -> {
                    latestAppTag = result.latestTag
                    userDb.setSetting("app_update_latest_tag", result.latestTag)
                    snackbarHostState.showSnackbar("App is up to date ($versionName)")
                }
                is UpdateCheckResult.Failed ->
                    snackbarHostState.showSnackbar("Couldn't check for updates. Try again or open GitHub.")
            }
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
                                subtitle = "Version, release notes, and channel",
                                trailingContent = {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                            .padding(horizontal = 12.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = versionName,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
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
                        Text(
                            text = "Release builds from GitHub",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(28.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .padding(20.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Update,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Current Version",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = versionName,
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (latestAppTag.isNotBlank()) "Latest: $latestAppTag" else "Not checked yet",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            Button(
                                onClick = { checkAppUpdate() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Update,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (checkingAppUpdate) "Checking..." else "Check for Update",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    item {
                        SettingsSection(title = "App updates", shape = cardShape) {
                            SettingsRow(
                                icon = Icons.Outlined.Update,
                                title = "Automatic Updates Check",
                                subtitle = "Check GitHub for new releases on launch",
                                trailingContent = {
                                    Switch(
                                        checked = autoAppUpdate,
                                        onCheckedChange = null,
                                        colors = switchColors
                                    )
                                },
                                onClick = {
                                    val next = !autoAppUpdate
                                    autoAppUpdate = next
                                    userDb.setSetting("app_update_auto", next.toString())
                                }
                            )

                            SettingsDivider(dividerColor)

                            SettingsRow(
                                icon = Icons.Outlined.Notifications,
                                title = "Enable Update Notification",
                                subtitle = "Get notified when a new release is available",
                                trailingContent = {
                                    Switch(
                                        checked = notifyAppUpdate,
                                        onCheckedChange = null,
                                        colors = switchColors
                                    )
                                },
                                onClick = {
                                    val next = !notifyAppUpdate
                                    notifyAppUpdate = next
                                    userDb.setSetting("app_update_notify", next.toString())
                                }
                            )

                            SettingsDivider(dividerColor)

                            val channel = UpdateChannel.fromKey(updateChannelKey)
                            SettingsRow(
                                icon = Icons.Outlined.SwapVert,
                                title = "Update Channel",
                                subtitle = if (channel == UpdateChannel.STABLE)
                                    "Tested releases only" else "Include development snapshots",
                                trailingContent = {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                            .padding(horizontal = 12.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = channel.label,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = { showChannelDialog = true }
                            )
                        }
                    }

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
                    item { AboutHeader(onBack = { page = null }) }

                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(32.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .padding(horizontal = 20.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(104.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF111111)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.ic_launcher_foreground),
                                    contentDescription = "Lucy",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Lucy",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                        .clickable { checkAppUpdate() }
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = if (checkingAppUpdate) "Checking..." else versionName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "UNIVERSAL",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            val buttonBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            val buttonBg = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.35f)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AboutActionButton(
                                    label = "GitHub",
                                    iconRes = R.drawable.ic_github,
                                    modifier = Modifier.weight(1f),
                                    border = buttonBorder,
                                    bg = buttonBg,
                                    onClick = { openLink("https://github.com/yowayui/lucyapp") }
                                )
                                AboutActionButton(
                                    label = "Privacy",
                                    iconVector = Icons.Outlined.Lock,
                                    modifier = Modifier.weight(1f),
                                    border = buttonBorder,
                                    bg = buttonBg,
                                    onClick = { showPrivacyDialog = true }
                                )
                            }
                        }
                    }

                    item {
                        AboutNavigationCard(
                            iconVector = Icons.Outlined.Translate,
                            title = "Contributor Translation",
                            onClick = { showTranslationDialog = true }
                        )
                    }

                    item {
                        AboutNavigationCard(
                            iconVector = Icons.Filled.Info,
                            title = "LICENSE",
                            onClick = { showLicenseDialog = true }
                        )
                    }

                    item {
                        AboutSectionTitle("Lead Developer")
                        AboutMemberCard(
                            avatarRes = R.drawable.about_dev,
                            name = "yowayui",
                            role = "Lead Developer",
                            githubUrl = "https://github.com/yowayui",
                            webUrl = "https://github.com/yowayui/lucyapp",
                            contactUrl = "https://www.instagram.com/y1peh",
                            onOpenLink = { url -> openLink(url) }
                        )
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

    if (showChannelDialog) {
        AlertDialog(
            onDismissRequest = { showChannelDialog = false },
            icon = { Icon(Icons.Outlined.SwapVert, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Update Channel") },
            text = {
                Column {
                    UpdateChannel.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    updateChannelKey = option.key
                                    userDb.setSetting("app_update_channel", option.key)
                                    showChannelDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = updateChannelKey == option.key,
                                onClick = {
                                    updateChannelKey = option.key
                                    userDb.setSetting("app_update_channel", option.key)
                                    showChannelDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = option.label,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (option == UpdateChannel.STABLE)
                                        "Tested releases only" else "Include development snapshots",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showChannelDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    if (showLicenseDialog) {
        AlertDialog(
            onDismissRequest = { showLicenseDialog = false },
            icon = { Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("LICENSE") },
            text = {
                Text(
                    "Lucy Manga & Manhwa Reader\n\n" +
                    "Copyright (c) 2026 yowayui.\nAll rights reserved.\n\n" +
                    "This software and its associated documentation files are distributed " +
                    "for personal reading and offline manga catalog access."
                )
            },
            confirmButton = {
                TextButton(onClick = { showLicenseDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            icon = { Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Privacy Policy") },
            text = {
                Text(
                    "Lucy is designed with privacy as a fundamental principle:\n\n" +
                    "• No accounts or personal data collection.\n" +
                    "• Zero telemetry, tracking, or ads.\n" +
                    "• Reading history and bookmarks remain strictly on your local device storage.\n" +
                    "• Network access is only used to query chapters and manga pages on-demand."
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showTranslationDialog) {
        AlertDialog(
            onDismissRequest = { showTranslationDialog = false },
            icon = { Icon(Icons.Outlined.Translate, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Contributor Translation") },
            text = {
                Text(
                    "Help make Lucy accessible to readers worldwide! " +
                    "Translations and localization contributions are managed via GitHub.\n\n" +
                    "Would you like to visit the repository?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showTranslationDialog = false
                    openLink("https://github.com/yowayui/lucyapp")
                }) {
                    Text("GitHub")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTranslationDialog = false }) {
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

@Composable
private fun AboutHeader(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
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
        Text(
            text = "About",
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 4.dp)
        )
    }
}

@Composable
private fun AboutActionButton(
    label: String,
    modifier: Modifier = Modifier,
    iconVector: ImageVector? = null,
    iconRes: Int? = null,
    border: BorderStroke,
    bg: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(50))
            .border(border, RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            } else if (iconVector != null) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun AboutNavigationCard(
    iconVector: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun AboutSectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun AboutMemberCard(
    avatarRes: Int,
    name: String,
    role: String,
    githubUrl: String? = null,
    webUrl: String? = null,
    contactUrl: String? = null,
    onOpenLink: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(avatarRes),
            contentDescription = name,
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = role,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            githubUrl?.let { url ->
                AboutCircleIconButton(iconRes = R.drawable.ic_github) { onOpenLink(url) }
            }
            webUrl?.let { url ->
                AboutCircleIconButton(iconVector = Icons.Outlined.Public) { onOpenLink(url) }
            }
            contactUrl?.let { url ->
                AboutCircleIconButton(iconVector = Icons.Outlined.AlternateEmail) { onOpenLink(url) }
            }
        }
    }
}

@Composable
private fun AboutCircleIconButton(
    iconVector: ImageVector? = null,
    iconRes: Int? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
        } else if (iconVector != null) {
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
