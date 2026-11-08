package app.lucy.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lucy.data.db.CatalogDatabase
import app.lucy.data.db.UserDatabase
import app.lucy.data.model.Chapter
import app.lucy.data.model.HistoryItem
import app.lucy.data.model.MangaItem
import app.lucy.data.source.ChapterLoader
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SeriesScreen(
    slug: String,
    catalogDb: CatalogDatabase,
    userDb: UserDatabase,
    onBackClick: () -> Unit,
    onChapterClick: (Chapter) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var manga by remember { mutableStateOf<MangaItem?>(null) }
    var chapters by remember { mutableStateOf<List<Chapter>>(emptyList()) }
    var readChapters by remember { mutableStateOf<Set<String>>(emptySet()) }
    var lastRead by remember { mutableStateOf<HistoryItem?>(null) }
    var isBookmarked by remember { mutableStateOf(false) }
    var isAscending by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var isCheckingUpdates by remember { mutableStateOf(false) }

    LaunchedEffect(slug) {
        loading = true
        var loadedManga: MangaItem? = null
        withContext(Dispatchers.IO) {
            val item = catalogDb.getBySlug(slug)
            val baseChs = catalogDb.getChapters(slug)
            val extraChs = userDb.getExtraChapters(slug)
            val combined = (baseChs + extraChs).distinctBy { it.number }.sortedWith { a, b ->
                val aNum = a.number.toFloatOrNull() ?: 0f
                val bNum = b.number.toFloatOrNull() ?: 0f
                aNum.compareTo(bNum)
            }
            val bookmarked = userDb.isBookmarked(slug)
            val readSet = userDb.getReadChapters(slug)
            val last = userDb.getLastRead(slug)

            loadedManga = item
            manga = item
            chapters = if (isAscending) combined else combined.reversed()
            isBookmarked = bookmarked
            readChapters = readSet
            lastRead = last
        }
        loading = false

        val target = loadedManga
        if (target != null &&
            !target.sourceGroup.equals("Dojing", ignoreCase = true) &&
            !target.sourceGroup.equals("ShiroDoujin", ignoreCase = true)
        ) {
            val lastChecked = withContext(Dispatchers.IO) { userDb.getSeriesLastChecked(slug) }
            val ttl = 12 * 60 * 60 * 1000L // 12 hours
            if (System.currentTimeMillis() - lastChecked > ttl) {
                withContext(Dispatchers.IO) {
                    try {
                        val liveChapters = ChapterLoader.fetchLiveChapters(
                            group = target.sourceGroup,
                            type = target.type,
                            seriesSlug = target.slug,
                            seriesTitle = target.title,
                            englishName = target.englishName,
                            nativeName = target.nativeName
                        )
                        userDb.markSeriesChecked(slug, liveChapters.size)
                        if (liveChapters.isNotEmpty()) {
                            userDb.saveExtraChapters(slug, liveChapters)
                            val baseChs = catalogDb.getChapters(slug)
                            val extraChs = userDb.getExtraChapters(slug)
                            val combined = (baseChs + extraChs).distinctBy { it.number }.sortedWith { a, b ->
                                val aNum = a.number.toFloatOrNull() ?: 0f
                                val bNum = b.number.toFloatOrNull() ?: 0f
                                aNum.compareTo(bNum)
                            }
                            withContext(Dispatchers.Main) {
                                chapters = if (isAscending) combined else combined.reversed()
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    if (loading || manga == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val item = manga!!
    val filteredChapters = remember(chapters, searchQuery) {
        if (searchQuery.isBlank()) chapters
        else chapters.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.number.contains(searchQuery, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        // App bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCheckingUpdates) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 6.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        IconButton(onClick = {
                            scope.launch {
                                isCheckingUpdates = true
                                val newChapters = withContext(Dispatchers.IO) {
                                    ChapterLoader.fetchLiveChapters(
                                        group = item.sourceGroup,
                                        type = item.type,
                                        seriesSlug = item.slug,
                                        seriesTitle = item.title,
                                        englishName = item.englishName,
                                        nativeName = item.nativeName
                                    )
                                }
                                if (newChapters.isNotEmpty()) {
                                    withContext(Dispatchers.IO) {
                                        userDb.saveExtraChapters(slug, newChapters)
                                        userDb.markSeriesChecked(slug, newChapters.size)
                                        val baseChs = catalogDb.getChapters(slug)
                                        val extraChs = userDb.getExtraChapters(slug)
                                        val combined = (baseChs + extraChs).distinctBy { it.number }.sortedWith { a, b ->
                                            val aNum = a.number.toFloatOrNull() ?: 0f
                                            val bNum = b.number.toFloatOrNull() ?: 0f
                                            aNum.compareTo(bNum)
                                        }
                                        chapters = if (isAscending) combined else combined.reversed()
                                    }
                                    Toast.makeText(context, "Chapters updated: ${chapters.size} total", Toast.LENGTH_SHORT).show()
                                } else {
                                    withContext(Dispatchers.IO) {
                                        userDb.markSeriesChecked(slug, 0)
                                    }
                                    Toast.makeText(context, "Catalog up to date", Toast.LENGTH_SHORT).show()
                                }
                                isCheckingUpdates = false
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Check for updates",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    IconButton(onClick = {
                        val newStatus = userDb.toggleBookmark(
                            item.slug,
                            item.title,
                            item.thumb,
                            item.latestChapter
                        )
                        isBookmarked = newStatus
                    }) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Header info: Cover + Details
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Cover
                Box(
                    modifier = Modifier
                        .width(110.dp)
                        .aspectRatio(0.72f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                ) {
                    if (!item.thumb.isNullOrBlank()) {
                        AsyncImage(
                            model = item.thumb,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!item.englishName.isNullOrBlank() && item.englishName != item.title) {
                        Text(
                            text = item.englishName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(text = item.type, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(text = item.status, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (item.score != null && item.score > 0) {
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = String.format("%.1f", item.score),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (!item.authors.isNullOrBlank() && item.authors != "[]") {
                        val cleanAuthors = item.authors.replace("[\"", "").replace("\"]", "").replace("\",\"", ", ")
                        Text(
                            text = "Author: $cleanAuthors",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }

        // Resume / Start Reading Button
        if (chapters.isNotEmpty()) {
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    val targetChapter = if (lastRead != null) {
                        val numFromTitle = lastRead?.chapterTitle?.replace(Regex("[^0-9.]"), "")?.trim()
                        chapters.find { it.slug == lastRead?.chapterSlug }
                            ?: chapters.find { it.number == numFromTitle }
                            ?: (if (isAscending) chapters else chapters.reversed()).firstOrNull { !readChapters.contains(it.slug) }
                            ?: (if (isAscending) chapters.firstOrNull() else chapters.lastOrNull())
                    } else {
                        if (isAscending) chapters.firstOrNull() else chapters.lastOrNull()
                    }

                    Button(
                        onClick = { targetChapter?.let { onChapterClick(it) } },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text(
                            text = if (lastRead != null) "Resume ${lastRead?.chapterTitle ?: "Reading"}" else "Start Reading",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // Synopsis
        if (!item.description.isNullOrBlank()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = "Synopsis",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // Chapters Bar with Sort Toggle
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${chapters.size} Chapters",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable {
                            isAscending = !isAscending
                            chapters = chapters.reversed()
                        }
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = "Sort order",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isAscending) "Oldest" else "Latest",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Instant Chapter Search Filter
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter chapters...", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
        }

        // Chapters List
        items(filteredChapters) { ch ->
            val isRead = readChapters.contains(ch.slug)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onChapterClick(ch) }
                    .padding(horizontal = 16.dp, vertical = 11.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isRead) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Read",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(15.dp)
                            )
                        }
                        Text(
                            text = ch.title,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isRead) FontWeight.Normal else FontWeight.Medium
                            ),
                            color = if (isRead) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    else MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Text(
                        text = if (isRead) "Read" else "Read",
                        fontSize = 11.sp,
                        color = if (isRead) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
