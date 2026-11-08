package app.lucy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lucy.data.db.CatalogDatabase
import app.lucy.data.db.UserDatabase
import app.lucy.data.model.HistoryItem
import app.lucy.data.model.MangaItem
import app.lucy.ui.components.MangaCard
import app.lucy.ui.theme.Newsreader
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    catalogDb: CatalogDatabase,
    userDb: UserDatabase,
    onMangaClick: (String) -> Unit,
    onHistoryClick: (seriesSlug: String, chapterSlug: String, chapterNum: String) -> Unit,
    onSettingsClick: () -> Unit
) {
    var selectedCategory by rememberSaveable { mutableStateOf("Manga") }
    var featuredBanners by remember { mutableStateOf<List<MangaItem>>(emptyList()) }
    var popularList by remember { mutableStateOf<List<MangaItem>>(emptyList()) }
    var latestList by remember { mutableStateOf<List<MangaItem>>(emptyList()) }
    var continueReadingList by remember { mutableStateOf<List<HistoryItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    val categories = listOf("Manga", "Manhwa", "Manhua", "All")

    LaunchedEffect(selectedCategory) {
        loading = true
        withContext(Dispatchers.IO) {
            val feat = catalogDb.getDailyFeatured(limit = 6, includeNsfw = false)
            val pop = catalogDb.getDailyPopular(limit = 25, type = selectedCategory, includeNsfw = false)
            val lat = catalogDb.getDailyLatest(limit = 45, type = selectedCategory, includeNsfw = false)
            val hist = userDb.getHistory().take(10)
            featuredBanners = feat
            popularList = pop
            latestList = lat
            continueReadingList = hist
        }
        loading = false
    }

    if (loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // App Header
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "lucy",
                    fontFamily = Newsreader,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 38.sp,
                        letterSpacing = (-1).sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )

                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        // Featured Banner Carousel
        if (featuredBanners.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                val bannerPagerState = rememberPagerState(pageCount = { featuredBanners.size })
                LaunchedEffect(bannerPagerState.pageCount) {
                    if (bannerPagerState.pageCount > 1) {
                        while (true) {
                            delay(5000)
                            val next = (bannerPagerState.currentPage + 1) % bannerPagerState.pageCount
                            bannerPagerState.animateScrollToPage(next)
                        }
                    }
                }

                Column(modifier = Modifier.padding(bottom = 14.dp)) {
                    HorizontalPager(
                        state = bannerPagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) { page ->
                        val item = featuredBanners[page]
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { onMangaClick(item.slug) }
                        ) {
                            AsyncImage(
                                model = item.thumb,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Gradient overlay
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.5f),
                                                Color.Black.copy(alpha = 0.9f)
                                            )
                                        )
                                    )
                            )

                            // Content overlay
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "Featured",
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    if (item.score != null && item.score > 0) {
                                        Box(
                                            modifier = Modifier
                                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = String.format("%.1f", item.score),
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = item.title,
                                    fontFamily = Newsreader,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (!item.genres.isNullOrBlank()) {
                                    val cleanGenres = item.genres.replace("[", "").replace("]", "").replace("\"", "")
                                    Text(
                                        text = cleanGenres,
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Dot indicators
                    if (bannerPagerState.pageCount > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            repeat(bannerPagerState.pageCount) { index ->
                                val active = bannerPagerState.currentPage == index
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp)
                                        .size(if (active) 6.dp else 5.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(
                                            if (active) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Continue Reading Shelf
        if (continueReadingList.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Continue Reading",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    items(continueReadingList) { item ->
                        Box(
                            modifier = Modifier
                                .width(125.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    val chNum = item.chapterTitle.removePrefix("Chapter ").trim().ifBlank { "1" }
                                    onHistoryClick(item.slug, item.chapterSlug, chNum)
                                }
                                .padding(8.dp)
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(0.72f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surface)
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

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    text = item.chapterTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (item.totalPages > 0) {
                                    Text(
                                        text = "${item.pageIndex + 1} / ${item.totalPages}p",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Category Filter Chips
        item(span = { GridItemSpan(maxLineSpan) }) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                items(categories) { cat ->
                    val isSelected = cat == selectedCategory
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { selectedCategory = cat }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = cat,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // Popular Section Title
        if (popularList.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(
                        text = "Popular Series",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Most read manga & manhwa",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Horizontal Popular Carousel
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    itemsIndexed(popularList) { index, manga ->
                        Box(modifier = Modifier.width(115.dp)) {
                            MangaCard(
                                manga = manga,
                                rank = index + 1,
                                onClick = { onMangaClick(manga.slug) }
                            )
                        }
                    }
                }
            }
        }

        // Latest Updates Section Title
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                Text(
                    text = "Latest Manga Updates",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Recently added & updated",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Latest Updates Grid
        items(latestList) { manga ->
            MangaCard(
                manga = manga,
                onClick = { onMangaClick(manga.slug) }
            )
        }
    }
}
