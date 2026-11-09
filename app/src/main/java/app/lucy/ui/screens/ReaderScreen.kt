package app.lucy.ui.screens

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lucy.data.db.CatalogDatabase
import app.lucy.data.db.UserDatabase
import app.lucy.data.model.Chapter
import app.lucy.data.source.ChapterLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ponytail: no custom pointer transform on individual pages to preserve native LazyColumn & HorizontalPager scroll gestures. Add specialized gesture detector only when scale > 1f.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    seriesSlug: String,
    chapterSlug: String,
    chapterNum: String,
    catalogDb: CatalogDatabase,
    userDb: UserDatabase,
    onBackClick: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var currentSlug by rememberSaveable { mutableStateOf(chapterSlug) }
    var currentNum by rememberSaveable { mutableStateOf(chapterNum) }
    var allChapters by remember { mutableStateOf<List<Chapter>>(emptyList()) }
    var readChapterSlugs by remember { mutableStateOf<Set<String>>(emptySet()) }

    var images by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var reloadCount by remember { mutableIntStateOf(0) }
    var showControls by remember { mutableStateOf(true) }
    var showChapterSheet by remember { mutableStateOf(false) }

    val defaultMode = remember { userDb.getSetting("reader_mode", "webtoon") }
    var isWebtoonMode by rememberSaveable { mutableStateOf(defaultMode == "webtoon") }

    val defaultKeepScreenOn = remember { userDb.getSetting("keep_screen_on", "true") == "true" }
    var keepScreenOn by rememberSaveable { mutableStateOf(defaultKeepScreenOn) }

    val defaultAutoNext = remember { userDb.getSetting("auto_next_webtoon", "false") == "true" }
    var autoNextWebtoon by rememberSaveable { mutableStateOf(defaultAutoNext) }
    var autoNextCountdown by remember { mutableIntStateOf(-1) }

    var currentPage by rememberSaveable { mutableIntStateOf(1) }

    val listState = rememberLazyListState()
    val pagerState = rememberPagerState(pageCount = { images.size })
    var restoredPageForSlug by rememberSaveable { mutableStateOf<String?>(null) }

    // Keep screen on handler
    val context = LocalContext.current
    DisposableEffect(keepScreenOn) {
        val window = (context as? Activity)?.window
        if (keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Load series chapter list
    LaunchedEffect(seriesSlug) {
        withContext(Dispatchers.IO) {
            val baseChs = catalogDb.getChapters(seriesSlug)
            val extraChs = userDb.getExtraChapters(seriesSlug)
            val combined = (baseChs + extraChs).distinctBy { it.number }.sortedWith { a, b ->
                val aNum = a.number.toFloatOrNull() ?: 0f
                val bNum = b.number.toFloatOrNull() ?: 0f
                aNum.compareTo(bNum)
            }
            allChapters = combined
            readChapterSlugs = userDb.getReadChapters(seriesSlug)
        }
    }

    // Load active chapter images
    LaunchedEffect(currentSlug, reloadCount) {
        loading = true
        errorMessage = null
        withContext(Dispatchers.IO) {
            val series = catalogDb.getBySlug(seriesSlug)
            val title = series?.title ?: seriesSlug
            val eng = series?.englishName
            val nat = series?.nativeName
            val mtype = series?.type ?: "Manga"
            val group = series?.sourceGroup ?: "AllManga"
            val isDataSaver = userDb.getSetting("image_quality", "data-saver") == "data-saver"

            val imgs = ChapterLoader.loadChapter(
                context = context,
                group = group,
                type = mtype,
                seriesTitle = title,
                englishName = eng,
                nativeName = nat,
                chapterNum = currentNum,
                chapterSlug = currentSlug,
                dataSaver = isDataSaver
            )

            images = imgs
            if (imgs.isEmpty()) {
                errorMessage = "Could not load chapter images from source."
            } else {
                val savedHistory = userDb.getLastRead(seriesSlug)
                val existingPage = if (savedHistory?.chapterSlug == currentSlug) savedHistory.pageIndex else 0
                userDb.saveHistory(
                    slug = seriesSlug,
                    title = title,
                    thumb = series?.thumb,
                    chapterTitle = "Chapter $currentNum",
                    chapterSlug = currentSlug,
                    pageIndex = existingPage,
                    totalPages = imgs.size
                )
                userDb.markChapterRead(seriesSlug, currentSlug)
                readChapterSlugs = userDb.getReadChapters(seriesSlug)
            }
        }
        loading = false
    }

    // Restore last read page position
    LaunchedEffect(images, currentSlug) {
        if (images.isNotEmpty() && restoredPageForSlug != currentSlug) {
            val last = withContext(Dispatchers.IO) { userDb.getLastRead(seriesSlug) }
            val target = if (last != null && last.chapterSlug == currentSlug && last.pageIndex in images.indices) {
                last.pageIndex
            } else {
                0
            }
            currentPage = target + 1
            if (target > 0) {
                if (isWebtoonMode) {
                    listState.scrollToItem(target)
                } else {
                    pagerState.scrollToPage(target)
                }
            }
            restoredPageForSlug = currentSlug
        }
    }

    // Sync mode switch scroll position
    LaunchedEffect(isWebtoonMode) {
        if (images.isNotEmpty() && currentPage > 1) {
            val target = (currentPage - 1).coerceIn(images.indices)
            if (isWebtoonMode) {
                listState.scrollToItem(target)
            } else {
                pagerState.scrollToPage(target)
            }
        }
    }

    // Save page progress on change with debounce
    LaunchedEffect(currentPage) {
        if (currentPage > 1 && images.isNotEmpty()) {
            delay(400)
            withContext(Dispatchers.IO) {
                val series = catalogDb.getBySlug(seriesSlug)
                userDb.saveHistory(
                    slug = seriesSlug,
                    title = series?.title ?: seriesSlug,
                    thumb = series?.thumb,
                    chapterTitle = "Chapter $currentNum",
                    chapterSlug = currentSlug,
                    pageIndex = currentPage - 1,
                    totalPages = images.size
                )
            }
        }
    }

    val currentIndex = allChapters.indexOfFirst { it.slug == currentSlug }.let { idx ->
        if (idx >= 0) idx else allChapters.indexOfFirst { it.number == currentNum }
    }
    val prevChapter = if (currentIndex > 0) allChapters[currentIndex - 1] else null
    val nextChapter = if (currentIndex in 0 until allChapters.size - 1) allChapters[currentIndex + 1] else null

    fun goToChapter(ch: Chapter) {
        currentSlug = ch.slug
        currentNum = ch.number
        currentPage = 1
        restoredPageForSlug = null
        autoNextCountdown = -1
    }

    // Auto-advance on webtoon scroll end
    LaunchedEffect(listState, autoNextWebtoon, nextChapter) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val total = layout.totalItemsCount
            val last = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - 1
        }.collect { reachedBottom ->
            if (reachedBottom && autoNextWebtoon && nextChapter != null && autoNextCountdown < 0) {
                autoNextCountdown = 2
                while (autoNextCountdown > 0) {
                    delay(1000)
                    autoNextCountdown--
                }
                goToChapter(nextChapter)
            } else if (!reachedBottom) {
                autoNextCountdown = -1
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                showControls = !showControls
            }
    ) {
        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Text(
                        text = "Loading chapter pages...",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        } else if (errorMessage != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = errorMessage!!,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { reloadCount++ },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Retry", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onBackClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.2f),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Back to Series")
                        }
                    }
                }
            }
        } else if (isWebtoonMode) {
            LaunchedEffect(listState) {
                snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
                    if (index < images.size) {
                        currentPage = index + 1
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(images) { _, imgUrl ->
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imgUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Page",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp, horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (nextChapter != null) {
                            Text(
                                text = if (autoNextWebtoon && autoNextCountdown >= 0) "Auto-advancing in ${autoNextCountdown}s..." else "End of Chapter $currentNum",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            Button(
                                onClick = { goToChapter(nextChapter) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth(0.7f)
                            ) {
                                Text("Next Chapter (${nextChapter.number})", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Text(
                                text = "You've reached the latest chapter",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            Button(
                                onClick = onBackClick,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.2f),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Back to Series")
                            }
                        }
                    }
                }
            }
        } else {
            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.currentPage }.collect { index ->
                    currentPage = index + 1
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(images[page])
                            .crossfade(true)
                            .build(),
                        contentDescription = "Page ${page + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Overlay: Top Bar
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.88f))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Text(
                            text = "Chapter $currentNum",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showChapterSheet = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.List,
                                contentDescription = "Chapters",
                                tint = Color.White
                            )
                        }

                        IconButton(onClick = {
                            val next = !autoNextWebtoon
                            autoNextWebtoon = next
                            scope.launch(Dispatchers.IO) {
                                userDb.setSetting("auto_next_webtoon", next.toString())
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Auto-next",
                                tint = if (autoNextWebtoon) Color.White else Color.White.copy(alpha = 0.35f)
                            )
                        }

                        IconButton(onClick = {
                            val next = !keepScreenOn
                            keepScreenOn = next
                            scope.launch(Dispatchers.IO) {
                                userDb.setSetting("keep_screen_on", next.toString())
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.WbSunny,
                                contentDescription = "Keep Screen On",
                                tint = if (keepScreenOn) Color.White else Color.White.copy(alpha = 0.35f)
                            )
                        }

                        IconButton(onClick = {
                            val next = !isWebtoonMode
                            isWebtoonMode = next
                            scope.launch(Dispatchers.IO) {
                                userDb.setSetting("reader_mode", if (next) "webtoon" else "paged")
                            }
                        }) {
                            Icon(
                                imageVector = if (isWebtoonMode) Icons.Default.ViewCarousel else Icons.Default.ViewStream,
                                contentDescription = "Toggle Reader Mode",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Overlay: Bottom Controls Bar (Navigation + Progress)
        AnimatedVisibility(
            visible = showControls && images.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.88f))
                    .padding(vertical = 8.dp, horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = { prevChapter?.let { goToChapter(it) } },
                        enabled = prevChapter != null
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous Chapter",
                            tint = if (prevChapter != null) Color.White else Color.White.copy(alpha = 0.25f)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { showChapterSheet = true }
                    ) {
                        Text(
                            text = "Ch. $currentNum",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "$currentPage / ${images.size}",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 11.sp
                        )
                    }

                    IconButton(
                        onClick = { nextChapter?.let { goToChapter(it) } },
                        enabled = nextChapter != null
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next Chapter",
                            tint = if (nextChapter != null) Color.White else Color.White.copy(alpha = 0.25f)
                        )
                    }
                }
            }
        }

        // Chapter Selection Sheet with In-Reader Chapter Search
        if (showChapterSheet) {
            ModalBottomSheet(
                onDismissRequest = { showChapterSheet = false },
                containerColor = Color(0xFF141414),
                contentColor = Color.White
            ) {
                var sheetSearchQuery by remember { mutableStateOf("") }
                val filteredChapters = remember(allChapters, sheetSearchQuery) {
                    val reversedList = allChapters.reversed()
                    if (sheetSearchQuery.isBlank()) reversedList
                    else reversedList.filter {
                        it.title.contains(sheetSearchQuery, ignoreCase = true) ||
                        it.number.contains(sheetSearchQuery, ignoreCase = true)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 24.dp)
                ) {
                    Text(
                        text = "Chapters (${allChapters.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = sheetSearchQuery,
                        onValueChange = { sheetSearchQuery = it },
                        placeholder = {
                            Text(
                                "Search chapter number or title...",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.45f)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White.copy(alpha = 0.5f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            cursorColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                    ) {
                        items(filteredChapters) { ch ->
                            val isCurrent = ch.slug == currentSlug || ch.number == currentNum
                            val isRead = readChapterSlugs.contains(ch.slug)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isCurrent) Color.White.copy(alpha = 0.15f)
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        showChapterSheet = false
                                        goToChapter(ch)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = ch.title,
                                    color = when {
                                        isCurrent -> Color.White
                                        isRead -> Color.White.copy(alpha = 0.45f)
                                        else -> Color.White.copy(alpha = 0.9f)
                                    },
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )

                                if (isCurrent) {
                                    Text(
                                        text = "Reading",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                } else if (isRead) {
                                    Text(
                                        text = "Read",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.45f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
